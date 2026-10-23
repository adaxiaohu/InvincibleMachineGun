package com.codigohasta.addon.utils.gcore;

/**
 * 一个「放锚方案」。
 *
 * <p>移植自 {@code dev.godden.core.Utils.ClassUtil.AnchorPlaceData}（GCore）。
 *
 * <p>字段与源一一对应：
 * <ul>
 *     <li>{@code type} —— {@link Type#Place}（空地放新锚）/ {@link Type#Power} /
 *         {@link Type#Ignite}（已有点火）</li>
 *     <li>{@code anchorData} —— 锚位置、爆炸中心、对目标伤害</li>
 *     <li>{@code placeData} —— 怎么放（承载面 + 朝向），可为 null</li>
 *     <li>{@code placeProtect} —— 保护方块方案，可为 null</li>
 *     <li>{@code damageToSelf} —— 自己会吃的伤害（**可变**，源里会 set）</li>
 * </ul>
 *
 * <p><b>默认值</b>：源里 {@code damageToSelf} 的默认参数是 {@code 999.0f}
 * （见合成构造器的 {@code n & 0x10 -> f = 999.0f}）。
 * 这里用重载表达：不传时就是 999.0f。
 */
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

    /** 见主构造器，{@code damageToSelf} 取源的默认值 999.0f。 */
    public GcoreAnchorPlaceData(Type type, GcoreAnchorData anchorData) {
        this(type, anchorData, null, null, 999.0f);
    }

    /** 见主构造器，{@code damageToSelf} 取源的默认值 999.0f。 */
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
