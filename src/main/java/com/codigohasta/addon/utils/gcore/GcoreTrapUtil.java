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

public final class GcoreTrapUtil {

    private GcoreTrapUtil() {
    }

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
     * @param version       
     * @param wantPlacePos  
     * @param placedPosList
     * @param placeVec     
     * @param placerEyeY   
     * @param scanBorder    
     * @param placeRange   
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
                        if ((mc.world.isAir(curPos) && !closetPosList.contains(curPos))
                            || !touchCheck(placeVec, placerEyeVec, placeRangeSq, curPos)) {
                            continue;
                        }
                        closetPosList.add(curPos);
                    }
                }
            }

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
