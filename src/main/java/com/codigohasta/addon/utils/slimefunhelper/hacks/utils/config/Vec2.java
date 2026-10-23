package com.codigohasta.addon.utils.slimefunhelper.hacks.utils.config;

/**
 * A labelled pair of doubles, used by settings that carry two named parameters.
 *
 * <p>Ported from SlimefunHelper 1.1.7.2 {@code me.matl114.hacks.utils.config.Vec2}.
 * Upstream's version carries an NBT codec and a GUI editor built on {@code SubScreenWidget};
 * the IMG port serialises structured settings as JSON through
 * {@code ConfigBuilder.ObjectSetting} (see ConfigBuilder.java:112-120), so only the plain
 * data carrier is needed here. This mirrors IMG's existing {@link Vec3}/{@link LabelVec3}.
 */
public record Vec2(double x, double y) {
    public static Vec2 of(double x, double y) {
        return new Vec2(x, y);
    }

    public net.minecraft.util.math.Vec2f toVec2f() {
        return new net.minecraft.util.math.Vec2f((float) x, (float) y);
    }
}
