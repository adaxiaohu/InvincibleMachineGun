package com.codigohasta.addon.utils.gcore;

import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;

/**
 * 一次 TP 水晶攻击的完整结果。
 *
 * <p>移植自 {@code dev.godden.core.Utils.ClassUtil.TpCrystalAndObsidianPlaceData}
 * （GCore，源 11 行）。
 *
 * <p>字段与源一一对应：
 * <ul>
 *     <li>{@code tpVec} —— 我该传送到哪（一个站位服务下面所有方块）</li>
 *     <li>{@code placeDataList} —— 在那一次 TP 里要放的所有方块</li>
 * </ul>
 *
 * <p><b>为什么是「一个站位 + 一批方块」</b>：这是源的性能设计 ——
 * 一次 TP 要发几十个移动包，如果每个方块单独 TP 一次，
 * 包量会爆。所以 {@code findAndSetTpVec} 会找一个
 * 「能同时够到列表里每一个方块」的站位。
 */
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
