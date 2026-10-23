package com.codigohasta.addon.utils.gcore;

import net.minecraft.util.math.Vec3d;

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
