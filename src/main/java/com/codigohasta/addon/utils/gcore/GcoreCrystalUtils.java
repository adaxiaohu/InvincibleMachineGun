package com.codigohasta.addon.utils.gcore;

import net.minecraft.world.explosion.Explosion;

public final class GcoreCrystalUtils {

    public static final float CRYSTAL_POWER = 6.0f;

    private GcoreCrystalUtils() {
    }

    /**
     * 构造一个「末影水晶」的爆炸实例。
     *
     * <p>源（1.20.4）直接 {@code new Explosion(world, null, 0,0,0, 6.0f, true,
     * DestructionType.DESTROY_WITH_DECAY)}。
     *
     * <p><b>版本适配</b>：同 {@link GcoreAnchorUtils} —— 1.21.11 的
     * {@code Explosion} 是接口，用 {@link GcoreFakeExplosion} 承载威力值。
     */
    public static Explosion getExplosion() {
        return new GcoreFakeExplosion(CRYSTAL_POWER);
    }
}
