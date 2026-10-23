package com.codigohasta.addon.utils.gcore;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * 陷阱放置工具 —— 移植自 {@code dev.godden.core.Utils.TrapUtil}（GCore）。
 *
 * <p><b>只移植「GWebAura 用到的 3 个方法」</b>，其余方法
 * （{@code getTrapPosFromList} / {@code getGTrapPosFromList} /
 * {@code getEntitySurroundPos}）**只有 {@code ModuleGTrap} 才用**，
 * 本模块不需要，故不移植 —— 避免引入死代码。
 *
 * <h2>三个方法各自干什么</h2>
 * <ol>
 *     <li>{@link #getBoxColliePosList} —— 把一个 AABB 覆盖到的**所有方块坐标**列出来
 *         （从 floor(min) 到 floor(max) 的三重循环）。</li>
 *     <li>{@link #getPlaceDataFromPosList} —— <b>核心</b>。给定「想放的格子」，
 *         为每个格子算<b>从哪个面放</b>：
 *         <ul>
 *             <li>{@code MC1_14} 版本：直接「对着格子向下怼」（{@code (pos, DOWN, pos)}）。</li>
 *             <li>其它版本：在该格子 ±{@code scanBorder} 邻域里找一个**空气且够得着**的
 *                 邻格，再从它的反方向放回目标格。</li>
 *         </ul>
 *         注意「够得着」= 眼到方块中心 {@code ≤ 6}（硬编码 {@code 36.0}）**且**
 *         手到方块中心 {@code < placeRange²}。</li>
 *     <li>{@link #getTpVecFromPlaceDataList} —— 在放置点周围 ±{@code scanBorder} 里
 *         找一个**站位** {@code tpVec}：自身不卡碰撞，且站位到**每个**放置点都够得着、
 *         且站位 AABB 不与任何放置点的目标格相交。找到就返回（**按 x→y→z 顺序取第一个**）。</li>
 * </ol>
 *
 * <h2>与源的差异（如实记录）</h2>
 * <ul>
 *     <li>源是 Kotlin {@code object}，这里改为 {@code final class} + 静态方法
 *         （与项目其它 {@code Gcore*} 工具类一致）。</li>
 *     <li>源的 {@code EntriesMappings.entries$0}（Kotlin 枚举条目缓存）
 *         直接用 {@code Direction.values()} 代替，语义相同。</li>
 *     <li>{@code CollectionsKt.minByOrThrow} 用显式循环代替 —— 源里对空集合会抛
 *         {@code NoSuchElementException}，这里在两处调用前都已保证非空
 *         （见下方注释），所以不会出现行为差异。</li>
 * </ul>
 */
public final class GcoreTrapUtil {

    private GcoreTrapUtil() {
    }

    /**
     * 把 AABB 覆盖到的所有方块坐标列出来。
     *
     * <p>对应源 {@code TrapUtil.getBoxColliePosList}。
     * 边界用 {@code floor}：所以贴边的方块也算在内。
     */
    public static List<BlockPos> getBoxColliePosList(Box box) {
        List<BlockPos> list = new ArrayList<>();
        int maxX = (int) Math.floor(box.maxX);
        int maxY = (int) Math.floor(box.maxY);
        int maxZ = (int) Math.floor(box.maxZ);
        int minX = (int) Math.floor(box.minX);
        int minY = (int) Math.floor(box.minY);
        int minZ = (int) Math.floor(box.minZ);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    list.add(new BlockPos(x, y, z));
                }
            }
        }
        return list;
    }

    /**
     * 为每个「想放的格子」算出一个 {@link GcorePlaceData}（放哪个面、朝哪放）。
     *
     * <p>对应源 {@code TrapUtil.getPlaceDataFromPosList}。
     *
     * @param version       放置版本。{@code MC1_14} 走「向下怼」捷径。
     * @param wantPlacePos  想要放成的格子（目标格）。
     * @param placedPosList 已经放了 / 已经占用的格子，参与邻域去重。
     * @param placeVec      放置者（玩家）脚部坐标。
     * @param placerEyeY    放置者眼高（1.62 站立 / 1.27 潜行）。
     * @param scanBorder    邻域半径。
     * @param placeRange    手长上限（方块中心到手心的距离上限）。
     */
    public static List<GcorePlaceData> getPlaceDataFromPosList(
            GcoreEnumType.BlockPlaceVersion version,
            List<BlockPos> wantPlacePos,
            List<BlockPos> placedPosList,
            Vec3d placeVec,
            double placerEyeY,
            int scanBorder,
            double placeRange) {

        List<GcorePlaceData> placeDataList = new ArrayList<>();
        List<BlockPos> placePosList = new ArrayList<>(placedPosList);

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) {
            return placeDataList;
        }

        // 源在循环外取一次实体列表
        List<Entity> entities = GcoreTickUpdate.INSTANCE.getWorldEntitiesList();

        Box placerBox = GcorePlayerUtil.PlayerState.Normal.getBBox(placeVec);
        double placeRangeSq = placeRange * placeRange;
        Vec3d placerEyeVec = placeVec.add(0.0, placerEyeY, 0.0);

        for (BlockPos pos : wantPlacePos) {
            // 目标格本身压着玩家 → 跳过（否则会把方块放进自己身体里）
            if (new Box(
                pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0).intersects(placerBox)) {
                continue;
            }

            // ── MC1_14：不找面，直接「向下怼」──
            if (version == GcoreEnumType.BlockPlaceVersion.MC1_14) {
                placeDataList.add(new GcorePlaceData(pos, Direction.DOWN, pos));
                placePosList.add(pos);
                continue;
            }

            // ── 其它版本：在 ±scanBorder 邻域里找最近的「空气 + 够得着」的格 ──
            List<BlockPos> closetPosList = new ArrayList<>(placePosList);

            for (int x = pos.getX() - scanBorder; x <= pos.getX() + scanBorder; x++) {
                for (int y = pos.getY() - scanBorder; y <= pos.getY() + scanBorder; y++) {
                    for (int z = pos.getZ() - scanBorder; z <= pos.getZ() + scanBorder; z++) {
                        BlockPos curPos = new BlockPos(x, y, z);

                        // ★ 条件**逐字照抄源**，并已用 javap 复核字节码分支目标：
                        //     415: isAir(curPos)      → 假则跳到 touchCheck
                        //     425: contains(curPos)   → 假则「跳过 add」
                        //     441: touchCheck(...)    → 假则「跳过 add」
                        //     447: closetPosList.add(curPos)
                        //   即 `if ((isAir && !contains) || !touchCheck) continue;`
                        //
                        //   语义（反直觉但正确）：收下的是
                        //     「**非空气**（或已在列表里）且**够得着**」的格子。
                        //   因为 closetPosList 的元素随后被当作**用来点击的参照方块**
                        //   —— 放方块必须对着一个实心面点击，所以参照物必须是实心的。
                        //   ⚠ 源码里写的是 `isAir(...) && !contains(...)`，
                        //     两者都有「非」的直觉容易被读反，故此处保留源运算符顺序。
                        if ((mc.world.isAir(curPos) && !closetPosList.contains(curPos))
                            || !touchCheck(placeVec, placerEyeVec, placeRangeSq, curPos)) {
                            continue;
                        }
                        closetPosList.add(curPos);
                    }
                }
            }

            // 源用 minByOrThrow；此处已保证「closetPosList 至少含 placedPosList 的内容」，
            // 但为稳妥仍判空（源会在空时抛异常，我们不抛，行为更安全）。
            if (closetPosList.isEmpty()) {
                continue;
            }

            Vec3d posCenter = Vec3d.ofCenter(pos);
            BlockPos closetPos = null;
            double bestDist = Double.MAX_VALUE;
            for (BlockPos candidate : closetPosList) {
                double d = candidate.toCenterPos().distanceTo(posCenter);
                if (d < bestDist) {
                    bestDist = d;
                    closetPos = candidate;
                }
            }
            if (closetPos == null) {
                continue;
            }

            // ── 从「最近的格」的六个邻居里，挑一个可放置的面 ──
            List<GcorePlaceData> dataList = new ArrayList<>();
            for (Direction facing : Direction.values()) {
                BlockPos facingPos = closetPos.offset(facing);
                if (!GcoreBlockUtil.canPlaceAtPos(facingPos, entities)) {
                    continue;
                }
                dataList.add(new GcorePlaceData(facingPos, facing.getOpposite(), closetPos));
            }

            if (dataList.isEmpty()) {
                continue;
            }

            // 取「放置位置」离目标格中心最近的那个面
            GcorePlaceData closetData = null;
            double bestPlaceDist = Double.MAX_VALUE;
            for (GcorePlaceData candidate : dataList) {
                double d = candidate.getPlacePos().toCenterPos().distanceTo(posCenter);
                if (d < bestPlaceDist) {
                    bestPlaceDist = d;
                    closetData = candidate;
                }
            }
            if (closetData == null) {
                continue;
            }

            placeDataList.add(closetData);
            placePosList.add(closetData.getPlacePos());
        }

        return placeDataList;
    }

    /**
     * 找一个能站、且够得着**所有**放置点的站位。
     *
     * <p>对应源 {@code TrapUtil.getTpVecFromPlaceDataList}。
     *
     * <p>以**第一个放置点**为中心，在 ±{@code scanBorder} 里从 (x,y,z) 顺序扫，
     * 返回第一个满足条件的 {@code (x+0.5, y, z+0.5)}。
     *
     * @return 站位；找不到返回 {@code null}。
     */
    public static Vec3d getTpVecFromPlaceDataList(List<GcorePlaceData> placeDataList,
                                                  int scanBorder, double placeRange) {
        if (placeDataList.isEmpty()) {
            return null;
        }

        int centerX = placeDataList.get(0).getPlacePos().getX();
        int centerY = placeDataList.get(0).getPlacePos().getY();
        int centerZ = placeDataList.get(0).getPlacePos().getZ();

        for (int x = centerX - scanBorder; x <= centerX + scanBorder; x++) {
            for (int y = centerY - scanBorder; y <= centerY + scanBorder; y++) {
                for (int z = centerZ - scanBorder; z <= centerZ + scanBorder; z++) {

                    // ★ tpVec 的 y 不加 0.5 —— 这是「脚部坐标」
                    Vec3d tpVec = new Vec3d(x + 0.5, y + 0.0, z + 0.5);

                    if (!GcoreBlockUtil.checkNoPosCollie(tpVec)) {
                        continue;
                    }

                    boolean ok = true;
                    Box placerBox = GcorePlayerUtil.PlayerState.Normal.getBBox(tpVec);
                    for (GcorePlaceData it : placeDataList) {
                        if (touchCheckForTp(placeRange, tpVec, it.getPlacePos())) {
                            BlockPos targetPos = it.getTargetPos();
                            // ★★ 这里必须是 `!intersects`（漏掉 `!` 会让本方法几乎恒返回 null，
                            //    模块表现为「完全没反应」—— 本项目真实踩过）。
                            //
                            //    语义：站位够得着该放置点、且**站位身体不与该目标格重叠**
                            //    （否则等于站在「马上要放方块的那格里」）→ 这一项通过，继续看下一项。
                            //
                            //    字节码依据（javap，偏移 270/337/340）：
                            //      270: ifeq 340   touchCheck 为假 → 走到 340 置 flag=false
                            //      337: ifeq 346   intersects 为**假** → 跳过置位（即 continue）
                            //      340: flag = false
                            if (!placerBox.intersects(new Box(
                                targetPos.getX(), targetPos.getY(), targetPos.getZ(),
                                targetPos.getX() + 1.0, targetPos.getY() + 1.0, targetPos.getZ() + 1.0))) {
                                continue;
                            }
                        }
                        ok = false;
                    }
                    if (!ok) {
                        continue;
                    }
                    return tpVec;
                }
            }
        }
        return null;
    }

    /**
     * 「眼到方块中心 ≤ 6 且 手到方块中心 < placeRange²」。
     *
     * <p>对应源 {@code getPlaceDataFromPosList$touchCheck}。
     * {@code 36.0} 是源里**硬编码**的 6² （服务端交互距离），不是参数。
     */
    static boolean touchCheck(Vec3d placeVec, Vec3d placerEyeVec, double placeRangeSq, BlockPos pos) {
        double disToPosCenter = placeVec.squaredDistanceTo(
            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        return Vec3d.ofCenter(pos).squaredDistanceTo(placerEyeVec) <= 36.0
            && disToPosCenter < placeRangeSq;
    }

    /**
     * 站位到放置点是否够得着。
     *
     * <p>对应源 {@code getTpVecFromPlaceDataList$touchCheck$3}。
     * 注意这里眼睛位置是**站位 + 1.62**（硬编码），且用 {@code placeRange} 而非其平方。
     */
    static boolean touchCheckForTp(double placeRange, Vec3d vec, BlockPos placePos) {
        double disToPosCenter = vec.squaredDistanceTo(
            placePos.getX() + 0.5, placePos.getY() + 0.5, placePos.getZ() + 0.5);
        return Vec3d.ofCenter(placePos).squaredDistanceTo(vec.add(0.0, 1.62, 0.0)) <= 36.0
            && disToPosCenter < placeRange * placeRange;
    }

    // ─────────────────── 供 ModuleGTrap 用的方法 ───────────────────
    //
    // 下面这三段只有 ModuleGTrap 需要（WebAura 只用到上面三个）。
    // 因为用户要求「天罗地网」支持 UseTrap 分支，所以一并移植。

    /**
     * 目标身体周围一圈里所有可替换的格子。
     *
     * <p>对应源 {@code TrapUtil.getEntitySurroundPos(entity, double, top)}。
     *
     * @param entity 目标
     * @param bl     true = 把「身体侧面（非最底一层、除 UP 外）」也算进去
     * @param top    true = 把「最底一层」也算进去
     */
    public static List<BlockPos> getEntitySurroundPos(Entity entity, boolean bl, boolean top) {
        List<BlockPos> list = new ArrayList<>();        // 最底一层（y == minY）
        List<BlockPos> doubleAndTop = new ArrayList<>(); // 其余层

        Box box = entity.getBoundingBox();
        int maxX = (int) Math.floor(box.maxX);
        int maxY = (int) Math.floor(box.maxY);
        int maxZ = (int) Math.floor(box.maxZ);
        int minX = (int) Math.floor(box.minX);
        int minY = (int) Math.floor(box.minY);
        int minZ = (int) Math.floor(box.minZ);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (y > minY) {
                        doubleAndTop.add(new BlockPos(x, y, z));
                    }
                    if (y == minY) {
                        list.add(new BlockPos(x, minY, z));
                    }
                }
            }
        }

        List<BlockPos> colliedPos = getBoxColliePosList(box);
        List<BlockPos> surroundPos = getTrapPosFromList(list, (pos, dir) -> true);
        surroundPos.addAll(getTrapPosFromList(doubleAndTop,
            (pos, dir) -> bl && dir != Direction.UP && !colliedPos.contains(pos)));
        surroundPos.addAll(getTrapPosFromList(doubleAndTop,
            (pos, dir) -> top && dir == Direction.UP && !colliedPos.contains(pos)));
        return surroundPos;
    }

    /**
     * 给定一批格子，返回它们**四周**可替换、且不压着活体实体、且通过 {@code invoke} 的格子。
     *
     * <p>对应源 {@code TrapUtil.getTrapPosFromList}。
     */
    public static List<BlockPos> getTrapPosFromList(List<BlockPos> list,
                                                    GcoreBiPredicate<BlockPos, Direction> invoke) {
        List<BlockPos> trapPosList = new ArrayList<>();
        List<Entity> entities = GcoreTickUpdate.INSTANCE.getWorldEntitiesList();
        for (BlockPos pos : list) {
            for (Direction facing : Direction.values()) {
                BlockPos facingPos = pos.offset(facing);
                if (!GcoreBlockUtil.canReplaceable(facingPos) || trapPosList.contains(facingPos)) {
                    continue;
                }
                if (GcoreEntityUtil.checkAnyLivingEntityCollieInEntities(unitBox(facingPos), entities)
                    || !invoke.test(facingPos, facing)) {
                    continue;
                }
                trapPosList.add(facingPos);
            }
        }
        return trapPosList;
    }

    /**
     * 「GTrap」版的包围格搜索：从种子格向外扩张，把碰撞到的区域一并算进来。
     *
     * <p>对应源 {@code TrapUtil.getGTrapPosFromList}。
     */
    public static List<BlockPos> getGTrapPosFromList(List<BlockPos> list,
                                                     GcoreBiPredicate<BlockPos, Direction> invoke) {
        List<BlockPos> trapPosList = new ArrayList<>();
        List<BlockPos> loadedPos = new ArrayList<>();
        List<BlockPos> newColliedList = new ArrayList<>();
        List<Entity> entities = GcoreTickUpdate.INSTANCE.getWorldEntitiesList();

        for (BlockPos pos : list) {
            searchColliedPos(newColliedList, loadedPos, entities, pos);
        }
        for (BlockPos pos : new ArrayList<>(newColliedList)) {
            addPos(newColliedList, trapPosList, loadedPos, invoke, pos);
        }

        // ★ 诊断：本方法只返回最终结果，中间量（种子数 / 扩张后碰撞区）全丢了。
        //   而「★没找到任何可围的格子」占困阵失败的 36%（204/565），
        //   却无法判断是「种子为空」「扩张失败」还是「addPos 全被否」。
        //   这里补一行，把三个中间量都记下来。
        GcoreWebTrapDiag.logf("包围格",
            "种子=%d → 扩张后=%d → 可围=%d",
            list.size(), newColliedList.size(), trapPosList.size());

        return trapPosList;
    }

    /**
     * 对应源 {@code getGTrapPosFromList$searchColliedPos}。
     *
     * <p>从 {@code pos} 向六个方向扩张：碰到「不压活体实体」的格子时，
     * 还要求它**不可替换**（说明是实心方块，可以当作扩张的锚点），且不卡碰撞；
     * 否则（压着活体实体）无条件收下。
     *
     * <p><b>★ 一处「冗余重复」的处理</b>：源里 {@code collidesWithAnyBlock}
     * 被**连着调用了两次**（参数完全相同，中间没有副作用）。我用 {@code javap}
     * 复核过字节码，偏移 {@code 208} 与 {@code 278} 确实是两次独立调用，
     * 反编译源码（447/450 行）也一致 —— 所以是**编译产物里真实存在的冗余**。
     * 由于同一个纯函数、同样参数、之间无副作用，两次调用结果必然相同，
     * 故此处**只保留一次**。行为等价，仅少一次无谓的世界查询。
     */
    private static void searchColliedPos(List<BlockPos> newColliedList, List<BlockPos> loadedPos,
                                         List<Entity> entities, BlockPos pos) {
        for (Direction facing : Direction.values()) {
            BlockPos facingPos = pos.offset(facing);
            if (newColliedList.contains(facingPos) || loadedPos.contains(facingPos)) {
                continue;
            }
            Box facingBox = unitBox(facingPos);

            if (!GcoreEntityUtil.checkAnyLivingEntityCollieInEntities(facingBox, entities)) {
                // 不压活体实体 → 必须是实心「不可替换」的方块才收
                if (GcoreBlockUtil.canReplaceable(facingPos)) {
                    continue;
                }
                if (GcoreBlockUtil.collidesWithAnyBlock(facingBox)) {
                    continue;
                }
                loadedPos.add(facingPos);
                newColliedList.add(facingPos);
                searchColliedPos(newColliedList, loadedPos, entities, facingPos);
                continue;
            }

            // 压着活体实体 → 直接收下并继续扩张
            loadedPos.add(facingPos);
            newColliedList.add(facingPos);
            searchColliedPos(newColliedList, loadedPos, entities, facingPos);
        }
    }

    /** 单个方块格的单位 AABB。 */
    private static Box unitBox(BlockPos pos) {
        return new Box(
            pos.getX(), pos.getY(), pos.getZ(),
            pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
    }

    /** 对应源 {@code getGTrapPosFromList$addPos}。 */
    private static void addPos(List<BlockPos> newColliedList, List<BlockPos> trapPosList,
                               List<BlockPos> loadedPos,
                               GcoreBiPredicate<BlockPos, Direction> invoke, BlockPos pos) {
        for (Direction facing : Direction.values()) {
            BlockPos facingPos = pos.offset(facing);
            if (newColliedList.contains(facingPos) || trapPosList.contains(facingPos)
                || loadedPos.contains(facingPos)) {
                continue;
            }
            if (!invoke.test(facingPos, facing) || !GcoreBlockUtil.canReplaceable(facingPos)) {
                continue;
            }
            loadedPos.add(facingPos);
            trapPosList.add(facingPos);
        }
    }
}
