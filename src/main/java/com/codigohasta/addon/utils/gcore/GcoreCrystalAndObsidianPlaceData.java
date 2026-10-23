package com.codigohasta.addon.utils.gcore;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * 一条「水晶放置方案」。
 *
 * <p>移植自 {@code dev.godden.core.Utils.ClassUtil.CrystalAndObsidianPlaceData}
 * （GCore，源 34 行）。
 *
 * <p>字段与源一一对应：
 * <ul>
 *     <li>{@code type} —— {@link Type#PlaceCrystal}（底座已存在，只放水晶）
 *         / {@link Type#PlaceObsidian}（要自己放黑曜石再放水晶）</li>
 *     <li>{@code placeData} —— 怎么放（承载面 + 朝向）。<b>可变</b> ——
 *         {@code findAndSetTpVec} 会为它挑一个可达的放置面后 set 回来</li>
 *     <li>{@code expVec} —— 爆炸中心（= 底座上方 1 格的中心）</li>
 *     <li>{@code damage} —— 对目标的伤害</li>
 *     <li>{@code obsidianPos} —— 底座（黑曜石）所在格</li>
 *     <li>{@code placeVec} —— 关联的站位（可为 null；源默认参数为 null）</li>
 * </ul>
 *
 * <p><b>默认值</b>：源里 {@code placeVec} 的默认参数是 {@code null}
 * （见合成构造器的 {@code n &amp; 0x20} 分支）。用重载表达。
 */
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

    /** 见主构造器，{@code placeVec} 取源的默认值 {@code null}。 */
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

    /** ★ 源里这是 {@code var}（可变）—— {@code findAndSetTpVec} 会写回挑好的放置面。 */
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
