package com.codigohasta.addon.utils.gcore;

import net.minecraft.world.explosion.Explosion;

/**
 * 末影水晶的爆炸实例提供者。
 *
 * <p>移植自 {@code dev.mumy.utils.crystal.CrystalUtils}（GCore）。
 *
 * <p><b>裁剪说明</b>：源类有 282 行（含 {@code canPlaceAt} / {@code isBase} /
 * {@code placementBox} / {@code placementPosition} 等），但 TP 锚路径
 * **只用 {@link #getExplosion()}** —— 它是 {@code getExpDamage} 里
 * 「不指定 explosion 时」的默认值（源 {@code getExpDamage$default} 的
 * 位掩码 {@code 0x20} 分支）。其余方法供水晶模块使用，按「按需裁剪」不移植。
 */
public final class GcoreCrystalUtils {

    /** ★ 末影水晶的爆炸威力（与源一致）。 */
    public static final float CRYSTAL_POWER = 6.0f;

    private GcoreCrystalUtils() {
    }

    /**
     * 构造一个「末影水晶」的爆炸实例。
     *
     * <p>源（1.20.4）直接 {@code new Explosion(world, null, 0,0,0, 6.0f, true,
     * DestructionType.DESTROY_WITH_DECAY)}。
     *
     * <p><b>版本适配</b>：同 {@link GcoreAnchorUtils} —— 1.21.11 的
     * {@code Explosion} 是接口，用 {@link GcoreFakeExplosion} 承载威力值。
     */
    public static Explosion getExplosion() {
        return new GcoreFakeExplosion(CRYSTAL_POWER);
    }
}
