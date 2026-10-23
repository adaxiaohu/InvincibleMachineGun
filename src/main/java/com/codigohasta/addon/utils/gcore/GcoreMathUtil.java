package com.codigohasta.addon.utils.gcore;

import net.minecraft.util.math.Vec3d;

/**
 * 数学工具。
 *
 * <p>移植自 {@code dev.godden.core.Utils.MathUtil}（GCore）。
 * 源是 Kotlin `object`（单例），Java 里改为**静态方法**。
 *
 * <p>TP 锚实际只用到 {@link #squaredDistance}。其余方法一并移植以保持闭包完整
 * （其中 {@code getStraightVec(Vec3d, Rotation, double)} 依赖 LiquidBounce 的
 * {@code Rotation}，见 {@link GcoreRotation} 的处理说明）。
 */
public final class GcoreMathUtil {

    private GcoreMathUtil() {
    }

    /** 两点之间距离的平方（不做开方）。 */
    public static double squaredDistance(double fromX, double fromY, double fromZ,
                                         double toX, double toY, double toZ) {
        double d = toX - fromX;
        double e = toY - fromY;
        double f = toZ - fromZ;
        return d * d + e * e + f * f;
    }

    /**
     * 区间内随机取一个整数。
     *
     * <p>源用 {@code Math.random()}，保留同样实现（不是 MC 的随机源）。
     */
    public static int getRandomIntFromRange(GcoreIntRange intRange) {
        return Math.min(intRange.getStart(), intRange.getStep())
            + (int) Math.floor(Math.abs(intRange.getStep() - intRange.getStart() + 1) * Math.random());
    }

    /** 从 from 指向 to 的单位方向向量乘以 {@code speedPerTick}。 */
    public static Vec3d getVelocity(Vec3d fromVec, Vec3d toVec, double speedPerTick) {
        Vec3d subtract = toVec.subtract(fromVec);
        double dis = Math.sqrt(subtract.x * subtract.x + subtract.y * subtract.y + subtract.z * subtract.z);
        double step = speedPerTick / dis;
        return new Vec3d(subtract.x * step, subtract.y * step, subtract.z * step);
    }

    /** 从 {@code fromVec} 沿指向 {@code toVec} 的方向走 {@code range} 格。 */
    public static Vec3d getStraightVec(Vec3d fromVec, Vec3d toVec, double range) {
        Vec3d vec = getVelocity(fromVec, toVec, range);
        return fromVec.add(vec);
    }

    /** 从 {@code fromVec} 沿 {@code rotation} 朝向走 {@code range} 格。 */
    public static Vec3d getStraightVec(Vec3d fromVec, GcoreRotation rotation, double range) {
        Vec3d vec = rotation.getRotationVec();
        return fromVec.add(vec.x * range, vec.y * range, vec.z * range);
    }
}
