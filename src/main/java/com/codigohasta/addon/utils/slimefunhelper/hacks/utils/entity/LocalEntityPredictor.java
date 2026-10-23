package com.codigohasta.addon.utils.slimefunhelper.hacks.utils.entity;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/** Ported from upstream 1.1.7.2 {@code me.matl114.hacks.utils.entity.LocalEntityPredictor}. */
public record LocalEntityPredictor(Entity entity) implements Predictor {
    @Override
    public Vec3d getKnownDeltaMovement() {
        return Vec3d.ZERO;
    }

    @Override
    public Vec3d getCurrentPos() {
        return entity.getEntityPos();
    }

    @Override
    public Vec3d predict(int ticksLater, int method, int useTickBefore) {
        return entity.getEntityPos();
    }

    @Override
    public void tick() {}
}
