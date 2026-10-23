package com.codigohasta.addon.utils.gcore;

import net.minecraft.util.math.Vec3d;

public final class GcoreTpAnchorPlaceData {

    private final Vec3d tpVec;
    private final GcoreAnchorPlaceData placeData;

    public GcoreTpAnchorPlaceData(Vec3d tpVec, GcoreAnchorPlaceData placeData) {
        this.tpVec = tpVec;
        this.placeData = placeData;
    }

    public Vec3d getTpVec() {
        return this.tpVec;
    }

    public GcoreAnchorPlaceData getPlaceData() {
        return this.placeData;
    }

    public float selfDamage() {
        return this.placeData != null ? this.placeData.getDamageToSelf() : Float.MAX_VALUE;
    }

    @Override
    public String toString() {
        return "TpAnchorPlaceData(" + this.tpVec + ", " + this.placeData + ")";
    }
}
