package com.codigohasta.addon.utils.gcore;

public final class GcoreAnchorPlaceData {

    private final Type type;
    private final GcoreAnchorData anchorData;
    private final GcorePlaceData placeData;
    private final GcoreAnchorProtectData placeProtect;
    private float damageToSelf;

    public GcoreAnchorPlaceData(Type type, GcoreAnchorData anchorData,
                                GcorePlaceData placeData, GcoreAnchorProtectData placeProtect,
                                float damageToSelf) {
        this.type = type;
        this.anchorData = anchorData;
        this.placeData = placeData;
        this.placeProtect = placeProtect;
        this.damageToSelf = damageToSelf;
    }

    public GcoreAnchorPlaceData(Type type, GcoreAnchorData anchorData) {
        this(type, anchorData, null, null, 999.0f);
    }

    public GcoreAnchorPlaceData(Type type, GcoreAnchorData anchorData, GcorePlaceData placeData) {
        this(type, anchorData, placeData, null, 999.0f);
    }

    public Type getType() {
        return this.type;
    }

    public GcoreAnchorData getAnchorData() {
        return this.anchorData;
    }

    public GcorePlaceData getPlaceData() {
        return this.placeData;
    }

    public GcoreAnchorProtectData getPlaceProtect() {
        return this.placeProtect;
    }

    public float getDamageToSelf() {
        return this.damageToSelf;
    }

    public void setDamageToSelf(float damageToSelf) {
        this.damageToSelf = damageToSelf;
    }

    @Override
    public String toString() {
        return "AnchorPlaceData(" + this.type + ", " + this.anchorData
            + ", self=" + this.damageToSelf + ")";
    }

    /** 放置类型。 */
    public enum Type {
        /** 在空地上放新锚。 */
        Place,
        /** 给锚充能。 */
        Power,
        /** 点燃已有的锚。 */
        Ignite
    }
}
