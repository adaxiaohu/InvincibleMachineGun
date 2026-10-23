package com.codigohasta.addon.utils.slimefunhelper.hacks.utils.config;

public record Vec2(double x, double y) {
    public static Vec2 of(double x, double y) {
        return new Vec2(x, y);
    }

    public net.minecraft.util.math.Vec2f toVec2f() {
        return new net.minecraft.util.math.Vec2f((float) x, (float) y);
    }
}
