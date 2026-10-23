package com.codigohasta.addon.utils.gcore;

import net.minecraft.world.explosion.Explosion;

/**
 * 重生锚的爆炸实例提供者。
 *
 * <p>移植自 {@code dev.mumy.utils.anchor.AnchorUtils}（GCore）。
 *
 * <p><b>裁剪说明</b>：源类有 236 行（含 {@code isExplodable} / {@code canPlaceAt} /
 * {@code calculateExplosionDamage} 等），但 TP 锚路径**只用
 * {@link #getExplosion()}** 一个方法 —— 它是
 * {@code GcoreExplosionUtils.getExpDamage(...)} 的 {@code explosion} 参数来源。
 * 其余方法供别的模块使用，按「按需裁剪」不移植。
 */
public final class GcoreAnchorUtils {

    /** ★ 重生锚的爆炸威力（与源一致）。 */
    public static final float ANCHOR_POWER = 5.0f;

    private GcoreAnchorUtils() {
    }

    /**
     * 构造一个「重生锚」的爆炸实例。
     *
     * <p>源（1.20.4）直接 {@code new Explosion(world, null, 0,0,0, 5.0f, true,
     * DestructionType.DESTROY_WITH_DECAY)}。
     *
     * <p><b>版本适配</b>：1.21.11 把 {@code Explosion} 改成了接口，
     * 客户端无法构造真实的 {@code ExplosionImpl}（需要 {@code ServerWorld}）。
     * 因为伤害计算只用到 {@code getPower()}（而 {@code isImmuneToExplosion}
     * 在 1.21.11 基类里恒返回 false、不读该参数），所以用
     * {@link GcoreFakeExplosion} 承载威力值 —— **行为与源等价**。
     */
    public static Explosion getExplosion() {
        return new GcoreFakeExplosion(ANCHOR_POWER);
    }
}
