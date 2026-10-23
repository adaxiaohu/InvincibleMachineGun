package com.codigohasta.addon.utils.gcore;

import net.minecraft.util.math.BlockPos;

/**
 * 保护方块数据：放在哪、怎么放、自己会吃多少伤害。
 *
 * <p>移植自 {@code dev.godden.core.Utils.ClassUtil.AnchorProtectData}（GCore）。
 *
 * <p>「保护方块」是放在锚旁边用来挡爆炸的方块（黑曜石/萤石），
 * 目的是让自己少挨炸。
 */
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
