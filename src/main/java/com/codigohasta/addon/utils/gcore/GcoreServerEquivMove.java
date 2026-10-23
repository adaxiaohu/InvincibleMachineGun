package com.codigohasta.addon.utils.gcore;

import com.codigohasta.addon.utils.slimefunhelper.utils.CollisionUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.BiPredicate;

/**
 * 「服务端等价」移动判定引擎（步骤 ①）。
 *
 * <p><b>解决什么问题</b>：源 GCore 的 {@link GcoreBlockUtil#checkCanMove} 只做
 * **L 形走廊的包围盒近似**判定 —— 把「起点→终点的位移」拆成「先纯 X、再纯 Z」
 * 两个包围盒，且**两个盒子的 Y 范围都取自起点**。这与服务端真正执行的
 * {@code player.move(MoverType.PLAYER, delta)} 三维扫掠不是一回事。
 *
 * <p>结果：客户端认为「包围盒不碰撞 → 可走」，服务端扫掠后说「你被挡住了」，
 * 于是 {@code movedWrongly} → {@code internalTeleport(该包之前的位置)} + s08 回拉。
 * 实测日志已证实：s08 的 {@code serverPos} **精确等于** GCore 自己发出的
 * {@code backVClip}（= {@code toVec + 1.8}，半空），见档案 4.8 / 4.11。
 *
 * <p><b>实现方式（方案 X）</b>：碰撞采集**交给** {@link CollisionUtil}
 * —— 它是从 Paper 的碰撞优化代码移植来的（19000+ 行），也正是本项目里
 * sfIMG {@code MovTasks} 那条**已被实战验证可用**的路径所用的同一个实现。
 * 本类只负责「把 {@code extraPosMap} 虚拟方块接进去」这一件事。
 *
 * <p><b>★ 三个关键设计选择</b>：
 * <ol>
 *     <li><b>完全不改动实体</b>。有效版 {@code MovTasks.simulateMovement} 靠临时
 *         {@code entity.setPosition(from)} 再还原，因此**只能在主线程**跑；
 *         而 TP 锚跑在虚拟线程上。本类改为**显式传入 {@link Box}**、
 *         只调纯函数 {@code performCollisions} 与只读的采集函数 → **线程安全**。</li>
 *     <li><b>支持虚拟方块</b>。{@link CollisionUtil} 的 {@code predicate} 是**纯过滤**
 *         （只能跳过、不能替换）。源的 {@code extraPosMap} 是**替换**语义。
 *         两者可以精确对接：用 {@code predicate} **排除**虚拟方块坐标上的世界方块，
 *         再把虚拟方块自己的形状**追加**进结果 —— 合起来就是「替换」。</li>
 *     <li><b>采集「方块 + hard-colliding 实体」</b>。服务端 {@code player.move()}
 *         同样会被这类实体挡住，而 TP 落点常常就在**目标实体**身上；
 *         故走 {@code getCollisions}（= 方块 | 实体），与有效版
 *         {@code MovTasks.collectBoxInvolvingInMovements} 同款入口。</li>
 * </ol>
 */
public final class GcoreServerEquivMove {

    private GcoreServerEquivMove() {
    }

    // ─────────────────── 模式开关（供 GcoreBlockUtil 分发） ───────────────────

    /**
     * 当前线程正在执行的 TP 模块名（如 {@code "TpAnchorAura"}）。
     *
     * <p><b>为什么用 {@link ThreadLocal} 而不是改函数签名</b>：
     * {@code checkCanMove} 的调用点分散在 3 个工具类、共 10 处，
     * 而且 {@code GcoreTpAnchorUtil} 在**候选点搜索阶段**就会调它（早于 {@code doTpMove}）。
     * 逐层加参数要动十几个签名；用线程局部状态则**每个模块只需在自己的入口
     * 包一层 {@link #enter(String)}**，所有嵌套调用自动生效，且天然线程安全
     * （TP 规划跑在各自的虚拟线程上，互不干扰）。
     */
    private static final ThreadLocal<String> CURRENT_MODULE = new ThreadLocal<>();

    /**
     * 「该模块是否启用服务端等价模式」的判定钩子。
     *
     * <p>由 {@code TPServerEquivalent} 模块在构造时装配。
     * 这样 {@code utils.gcore} 包**不需要 import modules 包**，避免循环依赖。
     * 默认恒 false → 未装配时一律走源逻辑（安全默认）。
     */
    private static volatile java.util.function.Predicate<String> gate = name -> false;

    /** 装配判定钩子（由 {@code TPServerEquivalent} 调用）。 */
    public static void setGate(java.util.function.Predicate<String> predicate) {
        gate = predicate == null ? (name -> false) : predicate;
    }

    /**
     * 标记「本线程接下来的 TP 计算属于 {@code moduleName} 模块」。
     *
     * <p>必须用 try-with-resources，确保异常时也能清掉标记
     * （否则虚拟线程复用时串味）。
     */
    public static Scope enter(String moduleName) {
        String previous = CURRENT_MODULE.get();
        CURRENT_MODULE.set(moduleName);
        diag("ENTER mod=" + moduleName + " th=" + Thread.currentThread().getName());
        return new Scope(previous);
    }

    /** 见 {@link #enter(String)}。 */
    public static final class Scope implements AutoCloseable {
        private final String previous;
        private boolean closed;

        private Scope(String previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (this.closed) {
                return;
            }
            this.closed = true;
            if (this.previous == null) {
                CURRENT_MODULE.remove();
            } else {
                CURRENT_MODULE.set(this.previous);
            }
        }
    }

    /** 当前线程的模块名；没有标记时返回 {@code null}。 */
    public static String currentModule() {
        return CURRENT_MODULE.get();
    }

    /** 「本次 {@code checkCanMove} 是否该走服务端等价模式」。 */
    public static boolean shouldUseForCurrentThread() {
        String module = CURRENT_MODULE.get();
        return module != null && gate.test(module);
    }

    // ─────────────────── 【临时诊断】引擎仪表 ───────────────────
    //
    // 目的：回答「换了引擎还是回弹」到底是
    //   (A) 引擎太宽松（批准了服务端拒绝的段落），还是
    //   (B) 引擎压根没生效。
    //
    // ★ 只读：以下代码不改变任何判定结果，只把中间量写进独立日志文件。
    // ★ 采完数据后连同本段一起删除。
    //
    // 关键待查量（对应我提出的 H1/H2/H3）：
    //   voxels=0   → H1：碰撞采集返回空 → 判定恒「可达」
    //   og=..      → H2：onGround/stepHeight 分支是否被误用
    //   delta      → 水平差是否贴着 0.0625 阈值

    /** 诊断开关（由 TPServerEquivalent 模块的 log-decisions 控制）。 */
    private static volatile boolean DIAG_ENABLED = false;

    private static java.io.BufferedWriter DIAG_WRITER;
    private static boolean DIAG_STARTED = false;
    private static long DIAG_START_MS;

    public static void setDiagEnabled(boolean enabled) {
        DIAG_ENABLED = enabled;
    }

    public static boolean isDiagEnabled() {
        return DIAG_ENABLED;
    }

    /** 见 {@link #setDiagEnabled}。 */
    private static synchronized void diag(String s) {
        if (!DIAG_ENABLED) {
            return;
        }
        try {
            if (DIAG_WRITER == null) {
                var dir = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir();
                DIAG_WRITER = java.nio.file.Files.newBufferedWriter(
                    dir.resolve("gcore-servequiv.log"),
                    java.nio.charset.StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
                if (!DIAG_STARTED) {
                    DIAG_STARTED = true;
                    DIAG_START_MS = System.currentTimeMillis();
                    DIAG_WRITER.write("=== GcoreServerEquivMove diag start " + DIAG_START_MS + " ===");
                    DIAG_WRITER.newLine();
                }
            }
            DIAG_WRITER.write(s);
            DIAG_WRITER.newLine();
            DIAG_WRITER.flush();
        } catch (Throwable ignored) {
            // 诊断绝不能影响游戏
        }
    }

    // ─────────────────── 服务端判据 ───────────────────

    /**
     * 服务端的「moved wrongly」阈值平方
     * （{@code spigot.yml: moved-wrongly-threshold: 0.0625}，即 0.25 格）。
     *
     * <p>服务端代码（{@code ServerGamePacketListenerImpl#handleMovePlayer}）：
     * <pre>
     * this.player.move(MoverType.PLAYER, new Vec3d(d3, d4, d5));  // 真实扫掠
     * d3 = d - this.player.getX();
     * d4 = d1 - this.player.getY();
     * if (d4 &gt; -0.5 || d4 &lt; 0.5) d4 = 0.0;                        // ★ 恒真 → Y 永远归零
     * d5 = d2 - this.player.getZ();
     * d7 = d3*d3 + d4*d4 + d5*d5;
     * if (d7 &gt; 0.0625) { movedWrongly = true; }
     * </pre>
     *
     * <p><b>★★ 关于那个 Y 特判（已用字节码核实）</b>：
     * {@code if (d4 > -0.5 || d4 < 0.5)} 里的 {@code ||} 让它成为**恒真式**
     * —— 任何实数都满足「&gt; -0.5 或 &lt; 0.5」。字节码证实
     * （{@code .tmp-verify/H.txt} 偏移 976–995）：
     * <pre>
     * 976: dload 34 / 978: ldc2_w -0.5d / 981: dcmpl / 982: ifgt 994   // d4 > -0.5 → 归零
     * 985: dload 34 / 987: ldc2_w  0.5d / 990: dcmpg / 991: ifge 997   // d4 >= 0.5 → 跳过
     * 994: dconst_0 / 995: dstore 34                                  // d4 = 0.0
     * </pre>
     * ⇒ **竖直偏差恒被归零**，{@code d7} 只统计**水平**距离。
     *
     * <p>两个推论：
     * <ol>
     *     <li>解释了有效版 {@code MovTasks.validMovementAsServer} 为何只比 x/z —— 它是对的。</li>
     *     <li>VClip 的**竖直**抬升本身不会被 {@code movedWrongly} 拦；
     *         被拦的是抬升之后的**水平段**（扫掠在水平方向被墙/地面挡住）。</li>
     * </ol>
     */
    public static final double MOVED_WRONGLY_THRESHOLD = 0.0625;

    // ─────────────────── 公开 API ───────────────────

    /**
     * 从 {@code fromVec} 走到 {@code toVec}，服务端会不会接受？
     *
     * <p>这是 {@link GcoreBlockUtil#checkCanMove} 的「服务端等价」替代品。
     */
    public static boolean canMoveAsServer(Vec3d fromVec, Vec3d toVec,
                                          Map<BlockPos, BlockState> extraPosMap,
                                          GcorePlayerUtil.PlayerState state) {
        Entity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return true; // 没玩家就无从判定；与源「不可判定时宁可放行」的保守取向一致
        }
        return canMoveAsServer(fromVec, toVec, extraPosMap, state,
            player.getStepHeight(), player.isOnGround());
    }

    /** 见 {@link #canMoveAsServer(Vec3d, Vec3d, Map, GcorePlayerUtil.PlayerState)}。 */
    public static boolean canMoveAsServer(Vec3d fromVec, Vec3d toVec,
                                          Map<BlockPos, BlockState> extraPosMap,
                                          GcorePlayerUtil.PlayerState state,
                                          double stepHeight, boolean onGround) {
        Vec3d movement = toVec.subtract(fromVec);
        if (movement.lengthSquared() < 1.0E-12) {
            return true;
        }

        Entity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return true;
        }

        Box box = state.getBBox(fromVec);
        Vec3d actual = sweptMove(player, box, movement, extraPosMap, stepHeight, onGround);
        boolean accepted = acceptedAsServer(movement, actual);

        // ★ 服务端的**第二条**拒绝路径：CLIPPED_INTO_BLOCK。
        //   只有第一条（movedWrongly）通过时才需要检查它 —— 与服务端
        //   `if (!noPhysics && !sleeping && allowMovement)` 的条件一致。
        boolean clipped = false;
        if (accepted) {
            clipped = hasNewCollision(player, box, movement, extraPosMap);
            accepted = !clipped;
        }

        if (DIAG_ENABLED && shouldDiag(movement)) {
            double dx = movement.x - actual.x;
            double dz = movement.z - actual.z;
            diag(String.format(Locale.ROOT,
                "DECIDE mod=%s state=%s from=[%.4f,%.4f,%.4f] to=[%.4f,%.4f,%.4f] "
                    + "mv=[%.4f,%.4f,%.4f] sim=[%.4f,%.4f,%.4f] "
                    + "step=%.3f og=%b virtual=%d dxz2=%.6f thr=%.4f CLIP=%b ACCEPT=%b",
                currentModule(), state,
                fromVec.x, fromVec.y, fromVec.z, toVec.x, toVec.y, toVec.z,
                movement.x, movement.y, movement.z, actual.x, actual.y, actual.z,
                stepHeight, onGround,
                extraPosMap == null ? 0 : extraPosMap.size(),
                dx * dx + dz * dz, MOVED_WRONGLY_THRESHOLD, clipped, accepted));
        }

        return accepted;
    }

    /**
     * 服务端 {@code hasNewCollision} 的等价实现 —— **第二条拒绝路径**。
     *
     * <p><b>为什么必须有它</b>：服务端玩家分支有两条**并列**的拒绝路径
     * （反编译 {@code ServerGamePacketListenerImpl} line 1641-1652）：
     * <pre>
     * boolean movedWrongly = ...;                    // 第一条：只比水平
     * boolean allowMovement = noPhysics || sleeping || !movedWrongly;
     * this.player.absSnapTo(d, d1, d2, f, f1);       // 先把玩家**放到目标点**
     * if (!noPhysics && !sleeping && allowMovement) {
     *     AABB newBox = this.player.getBoundingBox();        // 目标点的碰撞箱
     *     if (didCollide || !boundingBox.equals(newBox)) {
     *         allowMovement = !this.hasNewCollision(level, player, boundingBox, newBox);
     *     }
     * }
     * </pre>
     * 而 {@code hasNewCollision} 是：
     * <pre>
     * CollisionUtil.getCollisions(level, entity, newBox, voxels, aabbs, 6, null, null);
     * for (AABB box : aabbs)      if (!voxelShapeIntersect(box, oldBox))       return true;
     * for (VoxelShape v : voxels) if (!voxelShapeIntersectNoEmpty(v, oldBox))  return true;
     * return false;
     * </pre>
     *
     * <p><b>语义</b>：把玩家瞬移到目标点后，采集**目标点 AABB 内**的所有碰撞体；
     * 只要**任何一个**碰撞体与「起点 AABB」**不相交**，就说明玩家
     * 「钻进了一个原本没碰到的方块里」→ {@code CLIPPED_INTO_BLOCK} → 拒绝。
     *
     * <p><b>★ 这条路径是三维的</b>，与 {@code movedWrongly}「忽略 Y」**完全无关**。
     * 这解释了先前观测到的全部现象：竖直穿墙 5.8 格时水平虽毫无阻挡，
     * 但终点落进了原本没碰到的方块 → 被拒。我先前只实现了第一条判据，
     * 于是把服务端拒绝的段落**全部放行**（实测 9/14 条 s08 都是这种情况）。
     *
     * <p><b>flags 用 6</b>（{@code COLLIDE_WITH_UNLOADED_CHUNKS|CHECK_BORDER}）
     * —— 照抄服务端原样。未加载区块会被当作**实心**，这对远端 TP 至关重要。
     *
     * @param oldBox 起点碰撞箱
     * @param requestedMovement 该包**请求**的位移
     *        （服务端在 {@code absSnapTo(d,d1,d2)} 之后取 {@code getBoundingBox()}，
     *         即目标点 = 起点 + 请求位移，**不是**扫掠受限后的位移）
     */
    private static boolean hasNewCollision(Entity entity, Box oldBox, Vec3d requestedMovement,
                                           Map<BlockPos, BlockState> extraPosMap) {
        Box newBox = oldBox.offset(requestedMovement);

        List<VoxelShape> voxels = new ArrayList<>();
        List<Box> aabbs = new ArrayList<>();
        collectCollisions(entity, newBox, extraPosMap, voxels, aabbs,
            CollisionUtil.COLLISION_FLAG_COLLIDE_WITH_UNLOADED_CHUNKS
                | CollisionUtil.COLLISION_FLAG_CHECK_BORDER);

        // 与服务端逐字对应：任一个「不与起点相交」的碰撞体 = 钻入新方块
        int hitIdx = -1;
        String hitKind = "-";
        for (int i = 0; i < aabbs.size(); i++) {
            if (!CollisionUtil.voxelShapeIntersect(aabbs.get(i), oldBox)) {
                hitIdx = i;
                hitKind = "aabb";
                break;
            }
        }
        if (hitIdx < 0) {
            for (int i = 0; i < voxels.size(); i++) {
                if (!CollisionUtil.voxelShapeIntersectNoEmpty(voxels.get(i), oldBox)) {
                    hitIdx = i;
                    hitKind = "voxel";
                    break;
                }
            }
        }

        if (DIAG_ENABLED && (shouldDiag(requestedMovement) || requestedMovement.lengthSquared() > 4.0)) {
            diag(String.format(Locale.ROOT,
                "CLIPDETAIL mv=[%.4f,%.4f,%.4f] old=[%.3f,%.3f,%.3f -> %.3f,%.3f,%.3f] "
                    + "new=[%.3f,%.3f,%.3f -> %.3f,%.3f,%.3f] vox=%d aabb=%d hit=%s#%d RESULT=%b",
                requestedMovement.x, requestedMovement.y, requestedMovement.z,
                oldBox.minX, oldBox.minY, oldBox.minZ, oldBox.maxX, oldBox.maxY, oldBox.maxZ,
                newBox.minX, newBox.minY, newBox.minZ, newBox.maxX, newBox.maxY, newBox.maxZ,
                voxels.size(), aabbs.size(), hitKind, hitIdx, hitIdx >= 0));
        }

        return hitIdx >= 0;
    }

    /**
     * 「该位置能不能站人」（源的 {@code checkNoPosCollie} 的服务端等价版）。
     *
     * <p>语义与源一致：该 AABB **不与任何方块碰撞** 才算可站。
     * 但碰撞来源换成 {@link CollisionUtil}（Paper 同源），并支持虚拟方块。
     *
     * <p><b>为什么也要接新引擎</b>：{@code findVClipVecToMove} 用它判定
     * 「候选抬升点本身卡不卡方块」。若这里误判（用源的 L 近似），
     * 会选到一个**本身就非法**的抬升点 —— 之后无论怎么算都白搭。
     */
    public static boolean noPosCollisionAsServer(Vec3d vec,
                                                 Map<BlockPos, BlockState> extraPosMap,
                                                 GcorePlayerUtil.PlayerState state) {
        Entity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return true;
        }
        Box box = state.getBBox(vec);
        List<VoxelShape> voxels = new ArrayList<>();
        List<Box> aabbs = new ArrayList<>();
        collectCollisions(player, box, extraPosMap, voxels, aabbs);
        return voxels.isEmpty() && aabbs.isEmpty();
    }

    /**
     * 诊断过滤：只记「有实际位移」的判定。
     *
     * <p><b>★ 第二轮修正</b>：原先用「水平位移 &gt; 1 格」，结果把**纯竖直抬升包**
     * （水平 = 0）全部过滤掉了 —— 而 VClip 的抬升段恰恰是最可疑的一环。
     * 改为按**三维**位移长度过滤。
     */
    private static boolean shouldDiag(Vec3d movement) {
        return movement.lengthSquared() > 1.0;
    }

    /**
     * 服务端判据：模拟后的位移与服务端「请求的位移」差多少（**只比水平分量**）。
     *
     * <p>严格对应服务端（见 {@link #MOVED_WRONGLY_THRESHOLD} 的字节码说明）：
     * <pre>
     * d4 = 0.0;                    // ★ 竖直分量恒被归零
     * movedWrongly = (d3*d3 + d5*d5) &gt; 0.0625;
     * </pre>
     * 也与有效版 {@code MovTasks.validMovementAsServer} 的写法一致：
     * {@code s2(expect.x - sim.x) + s2(expect.z - sim.z) < 0.0625}。
     *
     * <p>创造模式直接放行 —— 服务端对创造模式玩家同样跳过该检查。
     */
    public static boolean acceptedAsServer(Vec3d expect, Vec3d sim) {
        var interactionManager = MinecraftClient.getInstance().interactionManager;
        if (interactionManager != null
            && interactionManager.getCurrentGameMode().isCreative()) {
            return true;
        }
        double dx = expect.x - sim.x;
        double dz = expect.z - sim.z;
        return dx * dx + dz * dz < MOVED_WRONGLY_THRESHOLD;
    }

    /**
     * 真实扫掠：把一个 AABB 沿 {@code movement} 移动，返回**实际能走到的位移**。
     *
     * <p><b>逐行复刻服务端 {@code net.minecraft.world.entity.Entity#collide(Vec3)}</b>
     * （反编译 {@code luminol-1.21.11.jar}，第 1619–1668 行）。
     * <b>不修改任何实体</b>：只读 world 方块与传入的 {@code box}。
     *
     * <p><b>★ 为什么不再复用 {@code MovTasks.collideWithTrustedList}</b>：
     * 它把台阶抬升写成「无条件试 {@code stepHeight}」：
     * <pre>
     * performCollisions(new Vec3(movement.x, stepHeight, movement.z), box, ...)
     * </pre>
     * 而服务端必须先由 {@link #calculateStepHeights} 从**实际方块 Y 坐标**
     * 枚举**存在的**候选台阶。二者在「墙边俯冲」时结果相反 ——
     * 实测（见档案 4.19）：`MovTasks` 版报 dxz2=0.0000「畅通」，
     * 服务端判 dxz2≈3.26「被挡」，导致服务端 `movedWrongly` 回拉。
     * 隔离实验：光加服务端门槛仍是 1/5，换成候选枚举枚举后 5/5 复现。
     *
     * <p>{@code MovTasks} 那样写对它的原用途（`Attack.java` 寻路，只求"可达"）
     * 未必有错；但**精确预测服务端是否回拉**时必须照抄服务端。
     */
    public static Vec3d sweptMove(Entity entity, Box box, Vec3d movement,
                                  Map<BlockPos, BlockState> extraPosMap,
                                  double stepHeight, boolean onGround) {
        // ── 服务端 line 1629-1634：全零直接返回 ──
        if (movement.x == 0.0 && movement.y == 0.0 && movement.z == 0.0) {
            return movement;
        }

        List<VoxelShape> voxels = new ArrayList<>();
        List<Box> aabbs = new ArrayList<>();

        // 服务端 line 1638：采集框
        //   x、z 都为零 → cutDownwards/cutUpwards；否则 → expandTowards(movement)
        Box collector;
        if (movement.x == 0.0 && movement.z == 0.0) {
            collector = movement.y < 0.0
                ? CollisionUtil.cutDownwards(box, movement.y)
                : CollisionUtil.cutUpwards(box, movement.y);
        } else {
            collector = box.stretch(movement.x, movement.y, movement.z);
        }
        collectCollisions(entity, collector, extraPosMap, voxels, aabbs);

        if (DIAG_ENABLED && shouldDiag(movement)) {
            diag(String.format(Locale.ROOT,
                "COLLECT mv=[%.4f,%.4f,%.4f] box=[%.3f,%.3f,%.3f -> %.3f,%.3f,%.3f] "
                    + "collector=[%.2f,%.2f,%.2f -> %.2f,%.2f,%.2f] step=%.3f og=%b vox=%d aabb=%d",
                movement.x, movement.y, movement.z,
                box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
                collector.minX, collector.minY, collector.minZ,
                collector.maxX, collector.maxY, collector.maxZ,
                stepHeight, onGround, voxels.size(), aabbs.size()));
        }

        // ── 服务端 line 1643：基础扫掠 ──
        Vec3d collided = CollisionUtil.performCollisions(movement, box, voxels, aabbs);

        boolean collidedX = collided.x != movement.x;
        boolean collidedY = collided.y != movement.y;
        boolean collidedZ = collided.z != movement.z;
        boolean collidedDownwards = collidedY && movement.y < 0.0;

        // ── 服务端 line 1648：抬升门槛 ──
        if ((!collidedDownwards && !onGround) || (!collidedX && !collidedZ)) {
            return collided;
        }

        // ── 服务端 line 1654：collidedYBox ──
        Box collidedYBox = collidedDownwards ? box.offset(0.0, collided.y, 0.0) : box;

        // ── 服务端 line 1655-1658：stepRetrievalBox ──
        Box stepRetrievalBox = collidedYBox.stretch(movement.x, stepHeight, movement.z);
        if (!collidedDownwards) {
            stepRetrievalBox = new Box(
                stepRetrievalBox.minX, stepRetrievalBox.minY - 1.0E-5, stepRetrievalBox.minZ,
                stepRetrievalBox.maxX, stepRetrievalBox.maxY, stepRetrievalBox.maxZ);
        }

        List<VoxelShape> stepVoxels = new ArrayList<>();
        // 服务端此处用 `stepAABBs = entityAABBs`（**只**带上初始的实体盒，
        // 不含初始的方块盒），再追加 stepRetrievalBox 的方块盒。
        // 本环境的 hard-colliding 实体数恒为 0（实测 214/214 全为 0），
        // 故等价地**从空开始**，只采集 stepRetrievalBox 的方块盒 ——
        // 这既避免把初始方块盒错误带入，也与离线复算的模型一致。
        List<Box> stepAabbs = new ArrayList<>();
        collectCollisions(entity, stepRetrievalBox, extraPosMap, stepVoxels, stepAabbs);

        // ── 服务端 line 1662-1666：逐个候选台阶尝试 ──
        for (double step : calculateStepHeights(collidedYBox, stepVoxels, stepAabbs,
                                                stepHeight, collided.y)) {
            Vec3d stepResult = CollisionUtil.performCollisions(
                new Vec3d(movement.x, step, movement.z), collidedYBox, stepVoxels, stepAabbs);

            if (stepResult.horizontalLengthSquared() <= collided.horizontalLengthSquared()) {
                continue;
            }
            return stepResult.add(0.0, collidedYBox.minY - box.minY, 0.0);
        }

        return collided;
    }

    /**
     * 逐行复刻服务端 {@code Entity.calculateStepHeights}
     * （反编译 {@code luminol-1.21.11.jar}，第 667–700 行）。
     *
     * <p>**只枚举「实际存在」的台阶高度**：从命中的碰撞体
     * （VoxelShape 的 Y 坐标 / AABB 的 minY 与 maxY）相对 {@code box.minY} 的偏移，
     * 取 {@code 0 ≤ step ≤ stepHeight} 且 {@code step != collidedY} 的值。
     *
     * <p>这是与 {@code MovTasks} 的关键差异：后者无条件整格抬 {@code stepHeight}，
     * 会「爬过」本不该翻越的墙。
     */
    private static double[] calculateStepHeights(Box box, List<VoxelShape> voxels, List<Box> aabbs,
                                                 double stepHeight, double collidedY) {
        TreeSet<Double> candidates = new TreeSet<>();

        // 服务端用 shape.moonrise$rootCoordinatesY()（该形状全部 Y 坐标）；
        // 这里等价地取每个包围盒的 minY / maxY —— 对立方体与台阶形状结果一致。
        for (int i = 0; i < voxels.size(); i++) {
            for (Box bb : voxels.get(i).getBoundingBoxes()) {
                addStepCandidate(candidates, bb.minY, box.minY, stepHeight, collidedY);
                addStepCandidate(candidates, bb.maxY, box.minY, stepHeight, collidedY);
            }
        }

        for (int i = 0; i < aabbs.size(); i++) {
            Box aabb = aabbs.get(i);
            addStepCandidate(candidates, aabb.minY, box.minY, stepHeight, collidedY);
            addStepCandidate(candidates, aabb.maxY, box.minY, stepHeight, collidedY);
        }

        double[] out = new double[candidates.size()];
        int i = 0;
        for (double v : candidates) {
            out[i++] = v;
        }
        return out;
    }

    /** 服务端 {@code calculateStepHeights} 的单点过滤：{@code 0 <= step <= stepHeight} 且 {@code step != collidedY}。 */
    private static void addStepCandidate(TreeSet<Double> into, double yCoord, double boxMinY,
                                         double stepHeight, double collidedY) {
        // 注意：服务端先判 `step > stepHeight` 提前 break；此处等价地逐个过滤。
        double step = yCoord - boxMinY;
        if (step > stepHeight) {
            return;
        }
        if (step < 0.0 || step == collidedY) {
            return;
        }
        into.add(step);
    }

    // ─────────────────── 内部 ───────────────────

    /**
     * 采集 {@code aabb} 范围内的碰撞体。
     *
     * <p><b>① 真实世界方块 + 实体</b>：直接委托 {@link CollisionUtil#getCollisions}
     * —— 与有效版 {@code MovTasks.collectBoxInvolvingInMovements} 同源（Paper 优化代码）。
     * 它自带 ±1 格的边界扩展、用 section palette 迭代（比逐格 {@code getBlockState} 快得多）、
     * 并用 **entity-aware 的形状上下文**（{@code LazyEntityCollisionContext}）
     * 取代我先前误用的 {@code ShapeContext.absent()}。
     *
     * <p><b>② 虚拟方块（{@code extraPosMap}）</b>：源的语义是**替换**。
     * 做法：
     * <ul>
     *     <li>先用 {@code predicate} **排除**虚拟方块坐标上的世界方块
     *         （predicate 返回 false → 该方块被跳过）；</li>
     *     <li>再把虚拟方块自身的碰撞形状**追加**进结果。</li>
     * </ul>
     * 两者合起来等价于「用虚拟方块顶替世界方块」。
     * 注意 predicate 只用于**排除**，虚拟方块坐标必定命中（{@code containsKey}），
     * 所以不会把不该跳的方块跳掉。
     *
     * <p><b>★ 采集「方块 + 实体」</b>：改用有效版同款入口
     * {@link CollisionUtil#getCollisions}（= 方块 | hard-colliding 实体），
     * 取代先前的纯方块版本。理由：服务端 {@code player.move()}
     * 同样会被 hard-colliding 实体挡住，而 TP 落点常常就在**目标实体**身上；
     * 先前排除实体等于对这条阻挡来源完全失明。
     */
    private static void collectCollisions(Entity entity, Box aabb,
                                          Map<BlockPos, BlockState> extraPosMap,
                                          List<VoxelShape> intoVoxel, List<Box> intoAabb) {
        collectCollisions(entity, aabb, extraPosMap, intoVoxel, intoAabb,
            CollisionUtil.COLLISION_FLAG_CHECK_BORDER);
    }

    /**
     * 见 {@link #collectCollisions(Entity, Box, Map, List, List)}，可指定 flags。
     *
     * <p><b>flags 的两种取值（都有来源依据）</b>：
     * <ul>
     *     <li>{@code CHECK_BORDER}(4)：有效版 {@code MovTasks} 的取值
     *         （它传 {@code ignoreUnloadedChunk=true}）。</li>
     *     <li>{@code COLLIDE_WITH_UNLOADED_CHUNKS|CHECK_BORDER}(6)：**服务端
     *         {@code hasNewCollision} 的原样取值** —— 未加载区块会被当作**实心**
     *         （追加 {@code getBoxForChunk} 整列方框）。</li>
     * </ul>
     * 两者对未加载区块的处理不同，而远端 TP 的落点常常正好在未加载区块里 ——
     * 这是本引擎先前漏判的重要一环。
     */
    private static void collectCollisions(Entity entity, Box aabb,
                                          Map<BlockPos, BlockState> extraPosMap,
                                          List<VoxelShape> intoVoxel, List<Box> intoAabb,
                                          int collisionFlags) {
        var world = entity.getEntityWorld();
        if (world == null) {
            return;
        }

        boolean hasVirtual = extraPosMap != null && !extraPosMap.isEmpty();

        // ① 真实世界方块（predicate 负责排除虚拟方块坐标）
        BiPredicate<BlockState, BlockPos> excludeVirtual = hasVirtual
            ? (state, pos) -> !extraPosMap.containsKey(pos)
            : null;

        CollisionUtil.getCollisions(
            world, entity, aabb, intoVoxel, intoAabb,
            collisionFlags,
            excludeVirtual, null, null);

        if (!hasVirtual) {
            return;
        }

        // ② 追加虚拟方块（源的「即将放上去的方块」）
        ShapeContext context = ShapeContext.of(entity);
        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (Map.Entry<BlockPos, BlockState> entry : extraPosMap.entrySet()) {
            BlockPos blockPos = entry.getKey();
            BlockState state = entry.getValue();
            if (blockPos == null || state == null || state.isAir()) {
                continue; // AIR = 「移除该处方块」→ 已在 ① 被排除，无需追加
            }

            int x = blockPos.getX();
            int y = blockPos.getY();
            int z = blockPos.getZ();
            pos.set(x, y, z);

            VoxelShape shape = state.getCollisionShape(world, pos, context);
            if (shape.isEmpty()) {
                continue;
            }

            VoxelShape offset = shape.offset(x, y, z);
            if (!CollisionUtil.voxelShapeIntersectNoEmpty(offset, aabb)) {
                continue;
            }
            intoVoxel.add(offset);
        }
    }
}
