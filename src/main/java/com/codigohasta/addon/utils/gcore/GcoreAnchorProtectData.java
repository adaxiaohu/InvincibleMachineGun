package com.codigohasta.addon.utils.gcore;

import net.minecraft.util.math.BlockPos;

public final class GcoreAnchorProtectData {

    private final BlockPos protectPos;
    private final GcorePlaceData placeData;
    private final float damageToSelf;

    public GcoreAnchorProtectData(BlockPos protectPos, GcorePlaceData placeData, float damageToSelf) {
        this.protectPos = protectPos;
        this.placeData = placeData;
        this.damageToSelf = damageToSelf;
    }

    public BlockPos getProtectPos() {
        return this.protectPos;
    }

    public GcorePlaceData getPlaceData() {
        return this.placeData;
    }

    /** 放了保护方块后，自己会吃的伤害。 */
    public float getDamageToSelf() {
        return this.damageToSelf;
    }

    @Override
    public String toString() {
        return "AnchorProtectData(" + this.protectPos + ", self=" + this.damageToSelf + ")";
    }
}
