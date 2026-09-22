package com.codigohasta.addon.utils.slimefunhelper.hacks.utils.config;

public record Vec3(double x,double y,double z) {
    public static Vec3 of(double x,double y,double z){return new Vec3(x,y,z);}
    public net.minecraft.util.math.Vec3d toVec3d(){return new net.minecraft.util.math.Vec3d(x,y,z);}
}
