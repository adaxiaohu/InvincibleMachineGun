package com.codigohasta.addon.utils.gcore;

import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.function.Function;

public final class GcoreAimUtil {

    public static final double DEFAULT_EYE_HEIGHT = 1.62;

    private GcoreAimUtil() {
    }

    // ─────────────────────────── checkCanHit ───────────────────────────

    /**
     * 见 {@link #checkCanHit(Vec3d, double, Vec3d, double, double, double, Function)}，用默认眼高与方块查询器。
     */
    public static boolean checkCanHit(Vec3d attackerVec, Vec3d victimVec, double victimEyeHeight,
                                      double hitRange, double throughWallRange) {
        return checkCanHit(attackerVec, DEFAULT_EYE_HEIGHT, victimVec, victimEyeHeight,
            hitRange, throughWallRange, GcoreRaytraceUtils.defaultBlockStateProvider());
    }

    /**
     * 见 {@link #checkCanHit(Vec3d, double, Vec3d, double, double, double, Function)}，
     * 用默认方块查询器（{@code world::getBlockState}）。
     */
    public static boolean checkCanHit(Vec3d attackerVec, double attackerEyeHeight,
                                      Vec3d victimVec, double victimEyeHeight,
                                      double hitRange, double throughWallRange) {
        return checkCanHit(attackerVec, attackerEyeHeight, victimVec, victimEyeHeight,
            hitRange, throughWallRange, GcoreRaytraceUtils.defaultBlockStateProvider());
    }

    public static boolean checkCanHit(Vec3d attackerVec, double attackerEyeHeight,
                                      Vec3d victimVec, double victimEyeHeight,
                                      double hitRange, double throughWallRange,
                                      Function<BlockPos, BlockState> blockStateProvider) {
        double sqDis = attackerVec.squaredDistanceTo(victimVec);
        double sqHitRange = hitRange * hitRange;
        double sqThroughWallRange = throughWallRange * throughWallRange;

        if (sqDis >= sqHitRange) {
            return false;
        }

        Vec3d attackerEyeVec = attackerVec.add(0.0, attackerEyeHeight, 0.0);
        Vec3d victimEyeVec = victimVec.add(0.0, victimEyeHeight, 0.0);

        if (checkCanSee(attackerEyeVec, victimEyeVec, blockStateProvider)) {
            return sqDis < sqHitRange;
        } else {
            return sqDis < sqThroughWallRange;
        }
    }

    // ─────────────────────────── checkCanSee ───────────────────────────

    /** 见 {@link #checkCanSee(Vec3d, Vec3d, Function)}，用默认方块查询器。 */
    public static boolean checkCanSee(Vec3d fromVec, Vec3d toVec) {
        return checkCanSee(fromVec, toVec, GcoreRaytraceUtils.defaultBlockStateProvider());
    }

    public static boolean checkCanSee(Vec3d fromVec, Vec3d toVec,
                                      Function<BlockPos, BlockState> blockStateProvider) {
        MinecraftClient mc = MinecraftClient.getInstance();

        // 源：new RaytraceUtils.SimpleRaycastContext(COLLIDER, NONE, ShapeContext.of(player))
        RaycastContext context = new GcoreSimpleRaycastContext(
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            ShapeContext.of(mc.player));

        //                   resultPredicate = hit != null,
        //                   fluidStateProvider = world::getFluidState
        BlockHitResult result = GcoreRaytraceUtils.raytrace(
            fromVec, toVec, context, true,
            blockStateProvider,
            GcoreRaytraceUtils.defaultFluidStateProvider(),
            GcoreRaytraceUtils.DEFAULT_STATE_PREDICATE,
            GcoreRaytraceUtils.DEFAULT_RESULT_PREDICATE);

        return result == null;
    }
}
