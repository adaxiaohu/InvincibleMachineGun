package com.codigohasta.addon.utils.gcore;

import net.minecraft.util.math.Vec3d;

/**
 * TP 落点 + 配套方案。
 *
 * <p>移植自 {@code dev.godden.core.Utils.ClassUtil.TpAnchorPlaceData}（GCore）。
 *
 * <p>{@code tpVec} 是「传送到哪」，{@code placeData} 是「到那之后干什么」。
 */
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

    /**
     * 该方案的「自伤」。
     *
     * <p>排序用（源按自伤升序取最优）。取 {@code placeData} 的
     * {@code damageToSelf} —— 若带了保护方块，源用的是
     * {@code protectData.getDamageToSelf()}（构造 {@code AnchorPlaceData} 时已传入），
     * 所以这里直接读 {@code placeData} 的值即可。
     */
    public float selfDamage() {
        return this.placeData != null ? this.placeData.getDamageToSelf() : Float.MAX_VALUE;
    }

    @Override
    public String toString() {
        return "TpAnchorPlaceData(" + this.tpVec + ", " + this.placeData + ")";
    }
}
