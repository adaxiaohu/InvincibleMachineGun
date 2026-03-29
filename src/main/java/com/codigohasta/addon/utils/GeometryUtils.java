package com.codigohasta.addon.utils;

import net.minecraft.util.math.Vec3d;

public class GeometryUtils {
    public static double distance(Vec3d a, Vec3d b) {
        return a.distanceTo(b);
    }
    
    public static Vec3d interpolate(Vec3d a, Vec3d b, double t) {
        return new Vec3d(
            a.x + (b.x - a.x) * t,
            a.y + (b.y - a.y) * t,
            a.z + (b.z - a.z) * t
        );
    }
    
    public static boolean hasLineOfSight(Vec3d from, Vec3d to) {
        // 简化视线检查
        return true;
    }
}