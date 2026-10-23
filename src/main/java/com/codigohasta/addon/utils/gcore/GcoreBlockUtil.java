package com.codigohasta.addon.utils.gcore;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class GcoreBlockUtil {

    private static final Map<BlockPos, BlockState> bufferMap = new LinkedHashMap<>();

    /**
     * {@code collidesWithAnyBlock} 的默认条件：source 里是
     * {@code { it -> state.getBlock() == Blocks.COBWEB }}。
     *
     * <p>即「碰到蜘蛛网就算碰撞」。
     */
    private static final Predicate<BlockState> CONDITION_1 =
        state -> state.getBlock() == Blocks.COBWEB;

    /**
     * {@code collidesWithAnyBlock} 里用于「是否无条件的阻挡」的默认条件。
     *
     * <p>源 {@code BlockUtil$collidesWithAnyBlock$1} 恒返回 {@code false}
     * （只有形状相交才算碰撞）。
     */
    private static final Predicate<BlockState> CONDITION_FALSE = state -> false;

    private GcoreBlockUtil() {
    }

    /** 取虚拟方块图（可变，调用方可往里塞「即将放置的方块」）。 */
    public static Map<BlockPos, BlockState> getBufferMap() {
        return bufferMap;
    }

    // ─────────────────────────── 碰撞检测 ───────────────────────────

    public static boolean checkCanMove(Vec3d fromVec, Vec3d toVec,
                                       Map<BlockPos, BlockState> extraPosMap,
                                       GcorePlayerUtil.PlayerState state) {
        // ★★ 步骤 ① 的开关点：若当前线程所在模块启用了「服务端等价」模式，
        //    改用真实扫掠判定（GcoreServerEquivMove），否则完全按源逻辑走。
        //    这里是全项目唯一的「可达性判定」分发点，所以只需在这里分派一次。
        if (GcoreServerEquivMove.shouldUseForCurrentThread()) {
            return GcoreServerEquivMove.canMoveAsServer(fromVec, toVec, extraPosMap, state);
        }

        Box box = state.getBBox(fromVec);

        Box xOffsetBox = box.offset(toVec.x - fromVec.x, 0.0, 0.0);
        Box xBox = new Box(
            Math.max(box.maxX, xOffsetBox.maxX),
            Math.max(box.maxY, xOffsetBox.maxY),
            Math.max(box.maxZ, xOffsetBox.maxZ),
            Math.min(box.minX, xOffsetBox.minX),
            Math.min(box.minY, xOffsetBox.minY),
            Math.min(box.minZ, xOffsetBox.minZ));

        Box zOffsetBox = xOffsetBox.offset(0.0, 0.0, toVec.z - fromVec.z);
        Box zBox = new Box(
            Math.max(xOffsetBox.maxX, zOffsetBox.maxX),
            Math.max(xOffsetBox.maxY, zOffsetBox.maxY),
            Math.max(xOffsetBox.maxZ, zOffsetBox.maxZ),
            Math.min(xOffsetBox.minX, zOffsetBox.minX),
            Math.min(xOffsetBox.minY, zOffsetBox.minY),
            Math.min(xOffsetBox.minZ, zOffsetBox.minZ));

        return !collidesWithAnyBlock(xBox, extraPosMap, CONDITION_1)
            && !collidesWithAnyBlock(zBox, extraPosMap, CONDITION_1);
    }

    /** 见 {@link #checkCanMove}，用默认虚拟图与 Normal 姿态。 */
    public static boolean checkCanMove(Vec3d fromVec, Vec3d toVec) {
        return checkCanMove(fromVec, toVec, bufferMap, GcorePlayerUtil.PlayerState.Normal);
    }

    public static boolean checkNoPosCollie(Vec3d vec, Map<BlockPos, BlockState> extraPosMap,
                                           GcorePlayerUtil.PlayerState state) {
        // ★★ 与 checkCanMove 相同的分发：启用服务端等价模式时，
        //    这里判定「候选抬升点本身卡不卡方块」，若误判会选到本身就非法的点。
        if (GcoreServerEquivMove.shouldUseForCurrentThread()) {
            return GcoreServerEquivMove.noPosCollisionAsServer(vec, extraPosMap, state);
        }
        return !collidesWithAnyBlock(state.getBBox(vec), extraPosMap, CONDITION_FALSE);
    }

    /** 见 {@link #checkNoPosCollie}。 */
    public static boolean checkNoPosCollie(Vec3d vec) {
        return checkNoPosCollie(vec, bufferMap, GcorePlayerUtil.PlayerState.Normal);
    }

    /** 见 {@link #checkNoPosCollie}，坐标形式。 */
    public static boolean checkNoPosCollie(double x, double y, double z,
                                           Map<BlockPos, BlockState> extraPosSet,
                                           GcorePlayerUtil.PlayerState state) {
        return !collidesWithAnyBlock(state.getBBox(x, y, z), extraPosSet, CONDITION_FALSE);
    }

    /** 见 {@link #checkNoPosCollie(double, double, double, Map, GcorePlayerUtil.PlayerState)}。 */
    public static boolean checkNoPosCollie(double x, double y, double z) {
        return checkNoPosCollie(x, y, z, bufferMap, GcorePlayerUtil.PlayerState.Normal);
    }

    /** 遍历 AABB 覆盖到的所有方块坐标。 */
    public static void forEachCollidesPos(Box bbox, Consumer<BlockPos> invoke) {
        int minX = (int) Math.floor(bbox.minX);
        int minY = (int) Math.floor(bbox.minY);
        int minZ = (int) Math.floor(bbox.minZ);
        int maxX = (int) Math.floor(bbox.maxX);
        int maxY = (int) Math.floor(bbox.maxY);
        int maxZ = (int) Math.floor(bbox.maxZ);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    invoke.accept(new BlockPos(x, y, z));
                }
            }
        }
    }

    public static boolean collidesWithAnyBlock(Box bbox, Map<BlockPos, BlockState> extraPosMap,
                                               Predicate<BlockState> condition2) {
        BlockPos.Mutable pos = new BlockPos.Mutable(0, 0, 0);

        int minX = (int) Math.floor(bbox.minX);
        int minY = (int) Math.floor(bbox.minY) - 1;
        int minZ = (int) Math.floor(bbox.minZ);
        int maxX = (int) Math.floor(bbox.maxX);
        int maxY = (int) Math.floor(bbox.maxY);
        int maxZ = (int) Math.floor(bbox.maxZ);

        var world = MinecraftClient.getInstance().world;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos.set(x, y, z);

                    BlockState state = extraPosMap.containsKey(pos)
                        ? extraPosMap.get(pos)
                        : world.getBlockState(pos);

                    if (condition2.test(state)) {
                        return true;
                    }

                    for (Box box : state.getCollisionShape(world, pos).getBoundingBoxes()) {
                        if (!bbox.intersects(
                            box.minX + (double) x, box.minY + (double) y, box.minZ + (double) z,
                            box.maxX + (double) x, box.maxY + (double) y, box.maxZ + (double) z)) {
                            continue;
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** 见 {@link #collidesWithAnyBlock}，用「恒 false」的默认条件。 */
    public static boolean collidesWithAnyBlock(Box bbox, Map<BlockPos, BlockState> extraPosMap) {
        return collidesWithAnyBlock(bbox, extraPosMap, CONDITION_FALSE);
    }

    /** 见 {@link #collidesWithAnyBlock}，全部用默认参数。 */
    public static boolean collidesWithAnyBlock(Box bbox) {
        return collidesWithAnyBlock(bbox, bufferMap, CONDITION_FALSE);
    }

    // ─────────────────────────── 放置判定 ───────────────────────────

    /**
     * 在 {@code targetPos} 周围找一个可放置的面。
     *
     * <p>遍历六个方向，第一个让 {@code invoke} 通过的就是结果：
     * 放置点是相邻方块，朝向是它的反方向（即朝目标方块）。
     */
    public static GcorePlaceData getPlaceDataToPos(BlockPos targetPos,
                                                   GcoreBiPredicate<BlockPos, Direction> invoke) {
        for (Direction dir : Direction.values()) {
            BlockPos pos = targetPos.offset(dir);
            if (!invoke.test(pos, dir)) continue;
            return new GcorePlaceData(pos, dir.getOpposite(), targetPos);
        }
        return null;
    }

    /** 该位置能否被替换（空气或可替换方块）。 */
    public static boolean canReplaceable(BlockPos pos) {
        BlockState stats = MinecraftClient.getInstance().world.getBlockState(pos);
        return stats.isAir() || stats.isReplaceable();
    }

    /**
     * 该位置能否放方块。
     *
     * <p>三个条件：
     * <ol>
     *     <li>Y 在世界高度范围内（{@code bottomY <= y <= topY}）</li>
     *     <li>当前方块可替换</li>
     *     <li>没有可交互实体占着这一格</li>
     * </ol>
     */
    public static boolean canPlaceAtPos(BlockPos pos, List<Entity> list) {
        var world = MinecraftClient.getInstance().world;

        // 版本差异：1.20.4 的 world.getTopY() 在 1.21.11 改名为 getTopYInclusive()
        if (pos.getY() > world.getTopYInclusive() || pos.getY() < world.getBottomY()) {
            return false;
        }

        if (canReplaceable(pos)) {
            return !GcoreEntityUtil.checkAnyLivingEntityCollieInEntities(new Box(
                pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0), list);
        }
        return false;
    }

    /** 见 {@link #canPlaceAtPos}，用世界实体列表。 */
    public static boolean canPlaceAtPos(BlockPos pos) {
        return canPlaceAtPos(pos, GcoreTickUpdate.getWorldEntitiesList());
    }

    /**
     * 找「实际放置面」—— 处理 {@code MC1_14} 与旧版本的差异。
     *
     * <p>{@code MC1_14}（默认）可以直接朝下放；
     * 旧版本必须找一个相邻的**不透明方块**，并满足：
     * <ul>
     *     <li>眼到方块中心距离 ≤ 6（{@code 36.0} 硬编码平方值）</li>
     *     <li>脚到方块中心距离 &lt; {@code placeRange}</li>
     * </ul>
     */
    public static GcorePlaceData getActualPlaceDataToPos(GcoreEnumType.BlockPlaceVersion version,
                                                         BlockPos placeAtPos, Vec3d placeVec,
                                                         double placerEyeY, double placeRange) {
        double placeRangeSq = placeRange * placeRange;
        Vec3d placerEyeVec = placeVec.add(0.0, placerEyeY, 0.0);

        GcoreBiPredicate<BlockPos, Direction> placeDataCheck = (pos, dir) -> {
            double disToPosCenter = placeVec.squaredDistanceTo(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            return MinecraftClient.getInstance().world.getBlockState(pos).isOpaque()
                && Vec3d.ofCenter(new Vec3i(pos.getX(), pos.getY(), pos.getZ())).squaredDistanceTo(placerEyeVec) <= 36.0
                && disToPosCenter < placeRangeSq;
        };

        if (version == GcoreEnumType.BlockPlaceVersion.MC1_14) {
            return new GcorePlaceData(placeAtPos, Direction.DOWN, placeAtPos);
        }

        for (Direction dir : Direction.values()) {
            BlockPos pos = placeAtPos.offset(dir);
            if (!placeDataCheck.test(pos, dir)) continue;
            return new GcorePlaceData(pos, dir.getOpposite(), placeAtPos);
        }
        return null;
    }

    public static Vec3d findVclipHole(Vec3d vec, double vclip) {
        int i;
        int n;

        if (vclip > 0.0 && 0 <= (n = (int) vclip)) {
            do {
                i = n--;
                Vec3d vec1 = new Vec3d(vec.x, vec.y + (double) i, vec.z);
                if (!checkNoPosCollie(vec)) continue;
                return vec1;
            } while (0 <= n);
        }
        if (vclip < 0.0 && 0 <= (n = (int) vclip)) {
            do {
                i = n--;
                Vec3d vec2 = new Vec3d(vec.x, vec.y - (double) i, vec.z);
                if (!checkNoPosCollie(vec)) continue;
                return vec2;
            } while (0 <= n);
        }
        return vec;
    }
}
