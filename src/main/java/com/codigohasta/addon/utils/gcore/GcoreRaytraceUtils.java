package com.codigohasta.addon.utils.gcore;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;

import java.util.function.BiPredicate;
import java.util.function.Function;

public final class GcoreRaytraceUtils {

    private GcoreRaytraceUtils() {
    }

    public static final GcoreTriPredicate DEFAULT_STATE_PREDICATE =
        (blockState, fluidState, pos) -> blockState != null && !blockState.isAir();

    public static final BiPredicate<BlockHitResult, BlockPos> DEFAULT_RESULT_PREDICATE =
        (result, pos) -> result != null;

    public static Function<BlockPos, BlockState> defaultBlockStateProvider() {
        return pos -> MinecraftClient.getInstance().world.getBlockState(pos);
    }

    public static Function<BlockPos, FluidState> defaultFluidStateProvider() {
        return pos -> MinecraftClient.getInstance().world.getFluidState(pos);
    }

    /** 见 {@link #raytrace(Vec3d, Vec3d, RaycastContext, boolean, Function, Function, GcoreTriPredicate, BiPredicate)}，全默认。 */
    public static BlockHitResult raytrace(Vec3d start, Vec3d end, RaycastContext context) {
        return raytrace(start, end, context, true,
            defaultBlockStateProvider(), defaultFluidStateProvider(),
            DEFAULT_STATE_PREDICATE, DEFAULT_RESULT_PREDICATE);
    }

    /** 见 {@link #raytrace(Vec3d, Vec3d, RaycastContext, boolean, Function, Function, GcoreTriPredicate, BiPredicate)}，指定方块查询器。 */
    public static BlockHitResult raytrace(Vec3d start, Vec3d end, RaycastContext context,
                                          Function<BlockPos, BlockState> blockStateProvider) {
        return raytrace(start, end, context, true,
            blockStateProvider, defaultFluidStateProvider(),
            DEFAULT_STATE_PREDICATE, DEFAULT_RESULT_PREDICATE);
    }

    public static BlockHitResult raytrace(Vec3d start, Vec3d end, RaycastContext context,
                                          boolean mutable,
                                          Function<BlockPos, BlockState> blockStateProvider,
                                          Function<BlockPos, FluidState> fluidStateProvider,
                                          GcoreTriPredicate statePredicate,
                                          BiPredicate<BlockHitResult, BlockPos> resultPredicate) {
        ClientWorld world = MinecraftClient.getInstance().world;

        // 源：if (start == end) → null（引用比较，Kotlin 的 == 对 data class 是 equals，
        if (start == end) {
            return null;
        }

        // ★ 起点微调（-1.0E-7），start 与 end 各一次、方向相反
        double startX = MathHelper.lerp(-1.0E-7, start.x, end.x);
        double startY = MathHelper.lerp(-1.0E-7, start.y, end.y);
        double startZ = MathHelper.lerp(-1.0E-7, start.z, end.z);
        double endX = MathHelper.lerp(-1.0E-7, end.x, start.x);
        double endY = MathHelper.lerp(-1.0E-7, end.y, start.y);
        double endZ = MathHelper.lerp(-1.0E-7, end.z, start.z);

        int currentX = MathHelper.floor(startX);
        int currentY = MathHelper.floor(startY);
        int currentZ = MathHelper.floor(startZ);

        BlockPos.Mutable mutablePos = new BlockPos.Mutable(currentX, currentY, currentZ);

        // ── 首个格子：先判一次（源在进循环前就检查起点格） ──
        BlockPos pos = mutablePos;
        BlockState blockState = blockStateProvider.apply(mutablePos);
        FluidState fluidState = fluidStateProvider.apply(mutablePos);
        Boolean shouldCheck = statePredicate.test(blockState, fluidState, pos);
        if (shouldCheck == null) {
            return null;
        }
        if (shouldCheck) {
            BlockHitResult hit = clip(world, context, start, end, pos, blockState, fluidState);
            if (resultPredicate.test(hit, pos)) {
                return hit;
            }
        }

        // ── DDA 步进 ──
        double deltaX = endX - startX;
        double deltaY = endY - startY;
        double deltaZ = endZ - startZ;
        int signX = (int) Math.signum(deltaX);
        int signY = (int) Math.signum(deltaY);
        int signZ = (int) Math.signum(deltaZ);
        double stepX = (signX == 0) ? Double.MAX_VALUE : (double) signX / deltaX;
        double stepY = (signY == 0) ? Double.MAX_VALUE : (double) signY / deltaY;
        double stepZ = (signZ == 0) ? Double.MAX_VALUE : (double) signZ / deltaZ;
        double progressX = stepX * (signX > 0 ? 1.0 - MathHelper.fractionalPart(startX) : MathHelper.fractionalPart(startX));
        double progressY = stepY * (signY > 0 ? 1.0 - MathHelper.fractionalPart(startY) : MathHelper.fractionalPart(startY));
        double progressZ = stepZ * (signZ > 0 ? 1.0 - MathHelper.fractionalPart(startZ) : MathHelper.fractionalPart(startZ));

        while (!(progressX > 1.0) || !(progressY > 1.0) || !(progressZ > 1.0)) {
            if (progressX < progressY) {
                if (progressX < progressZ) {
                    currentX += signX;
                    progressX += stepX;
                } else {
                    currentZ += signZ;
                    progressZ += stepZ;
                }
            } else if (progressY < progressZ) {
                currentY += signY;
                progressY += stepY;
            } else {
                currentZ += signZ;
                progressZ += stepZ;
            }

            mutablePos.set(currentX, currentY, currentZ);
            BlockPos posX = mutable ? mutablePos : new BlockPos(currentX, currentY, currentZ);
            BlockState blockStateX = blockStateProvider.apply(posX);
            FluidState fluidStateX = fluidStateProvider.apply(posX);
            shouldCheck = statePredicate.test(blockStateX, fluidStateX, posX);
            if (shouldCheck == null) {
                return null;
            }
            if (shouldCheck) {
                BlockHitResult hit = clip(world, context, start, end, posX, blockStateX, fluidStateX);
                if (resultPredicate.test(hit, posX)) {
                    return hit;
                }
            }
        }

        return null;
    }

    private static BlockHitResult clip(ClientWorld world, RaycastContext context,
                                       Vec3d start, Vec3d end, BlockPos pos,
                                       BlockState blockState, FluidState fluidState) {
        BlockHitResult blockHit = null;
        if (blockState != null) {
            VoxelShape blockShape = context.getBlockShape(blockState, world, pos);
            blockHit = world.raycastBlock(start, end, pos, blockShape, blockState);
        }

        BlockHitResult fluidHit = null;
        if (fluidState != null) {
            VoxelShape fluidShape = context.getFluidShape(fluidState, world, pos);
            fluidHit = fluidShape.raycast(start, end, pos);
        }

        double blockDist = (blockHit != null) ? start.squaredDistanceTo(blockHit.getPos()) : Double.MAX_VALUE;
        double fluidDist = (fluidHit != null) ? end.squaredDistanceTo(fluidHit.getPos()) : Double.MAX_VALUE;

        return (blockDist <= fluidDist) ? blockHit : fluidHit;
    }
}
