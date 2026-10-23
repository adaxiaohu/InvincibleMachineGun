package com.codigohasta.addon.utils.gcore;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

public final class GcoreCrystalVecAndDamage {

    private final Vec3d vec;
    private final float damage;
    private final Entity crystalEntity;

    public GcoreCrystalVecAndDamage(Vec3d vec, float damage, Entity crystalEntity) {
        this.vec = vec;
        this.damage = damage;
        this.crystalEntity = crystalEntity;
    }

    public Vec3d getVec() {
        return this.vec;
    }

    public float getDamage() {
        return this.damage;
    }

    public Entity getCrystalEntity() {
        return this.crystalEntity;
    }

    @Override
    public String toString() {
        return "CrystalVecAndDamage(vec=" + this.vec + ", damage=" + this.damage
            + ", crystal=" + this.crystalEntity + ")";
    }
}
