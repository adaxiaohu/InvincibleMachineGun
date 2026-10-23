package com.codigohasta.addon.utils.gcore;

import net.minecraft.world.explosion.Explosion;

public final class GcoreAnchorUtils {

    public static final float ANCHOR_POWER = 5.0f;

    private GcoreAnchorUtils() {
    }

    public static Explosion getExplosion() {
        return new GcoreFakeExplosion(ANCHOR_POWER);
    }
}
