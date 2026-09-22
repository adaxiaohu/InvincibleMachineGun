package com.codigohasta.addon.utils.slimefunhelper.hacks.utils.config;

public record LabelVec3(String xLabel,String yLabel,String zLabel,Vec3 value) {
    public double x(){return value.x();} public double y(){return value.y();} public double z(){return value.z();}
    public Vec3 getValue(){return value;}
}
