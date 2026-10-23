package com.codigohasta.addon.utils.gcore;

import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;

public final class GcoreTpCrystalAndObsidianPlaceData {

    private final Vec3d tpVec;
    private final ArrayList<GcoreCrystalAndObsidianPlaceData> placeDataList;

    public GcoreTpCrystalAndObsidianPlaceData(Vec3d tpVec,
                                              ArrayList<GcoreCrystalAndObsidianPlaceData> placeDataList) {
        this.tpVec = tpVec;
        this.placeDataList = placeDataList;
    }

    public Vec3d getTpVec() {
        return this.tpVec;
    }

    public ArrayList<GcoreCrystalAndObsidianPlaceData> getPlaceDataList() {
        return this.placeDataList;
    }

    @Override
    public String toString() {
        return "TpCrystalAndObsidianPlaceData(tp=" + this.tpVec
            + ", count=" + (this.placeDataList == null ? 0 : this.placeDataList.size()) + ")";
    }
}
