package com.codigohasta.addon.utils.gcore;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class GcoreCrystalAndObsidianPlaceData {

    private final Type type;
    private GcorePlaceData placeData;
    private final Vec3d expVec;
    private final float damage;
    private final BlockPos obsidianPos;
    private final Vec3d placeVec;

    public GcoreCrystalAndObsidianPlaceData(Type type, GcorePlaceData placeData,
                                            Vec3d expVec, float damage,
                                            BlockPos obsidianPos, Vec3d placeVec) {
        this.type = type;
        this.placeData = placeData;
        this.expVec = expVec;
        this.damage = damage;
        this.obsidianPos = obsidianPos;
        this.placeVec = placeVec;
    }

    public GcoreCrystalAndObsidianPlaceData(Type type, GcorePlaceData placeData,
                                            Vec3d expVec, float damage,
                                            BlockPos obsidianPos) {
        this(type, placeData, expVec, damage, obsidianPos, null);
    }

    public Type getType() {
        return this.type;
    }

    public GcorePlaceData getPlaceData() {
        return this.placeData;
    }

    public void setPlaceData(GcorePlaceData placeData) {
        this.placeData = placeData;
    }

    public Vec3d getExpVec() {
        return this.expVec;
    }

    public float getDamage() {
        return this.damage;
    }

    public BlockPos getObsidianPos() {
        return this.obsidianPos;
    }

    public Vec3d getPlaceVec() {
        return this.placeVec;
    }

    @Override
    public String toString() {
        return "CrystalAndObsidianPlaceData(" + this.type + ", obs=" + this.obsidianPos
            + ", dmg=" + this.damage + ")";
    }

    /** 放置类型。 */
    public enum Type {
        /** 底座（黑曜石/基岩）已存在 —— 只放水晶。 */
        PlaceCrystal,
        /** 需要自己先放黑曜石，再放水晶。 */
        PlaceObsidian
    }
}
