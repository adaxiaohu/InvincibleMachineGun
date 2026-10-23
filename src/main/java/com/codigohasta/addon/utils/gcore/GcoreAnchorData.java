package com.codigohasta.addon.utils.gcore;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class GcoreAnchorData {

    private final BlockPos anchorPos;
    private final Vec3d expVec;
    private final float damage;

    public GcoreAnchorData(BlockPos anchorPos, Vec3d expVec, float damage) {
        this.anchorPos = anchorPos;
        this.expVec = expVec;
        this.damage = damage;
    }

    public BlockPos getAnchorPos() {
        return this.anchorPos;
    }

    public Vec3d getExpVec() {
        return this.expVec;
    }

    /** 该锚对目标的伤害。 */
    public float getDamage() {
        return this.damage;
    }

    @Override
    public String toString() {
        return "AnchorData(" + this.anchorPos + ", " + this.expVec + ", dmg=" + this.damage + ")";
    }
}
