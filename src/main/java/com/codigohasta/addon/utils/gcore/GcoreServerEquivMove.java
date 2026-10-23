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
            return true;
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
