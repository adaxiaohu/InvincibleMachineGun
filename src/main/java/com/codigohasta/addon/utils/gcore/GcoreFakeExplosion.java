package com.codigohasta.addon.utils.gcore;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.explosion.Explosion;

/**
 * 一个最小的 {@link Explosion} 实现，**只用来做伤害计算**。
 *
 * <p><b>为什么需要它</b>：GCore（MC 1.20.4）里 {@code Explosion} 是个**类**，
 * 可以直接 {@code new Explosion(world, null, 0,0,0, power, true, DestructionType)}
 * 造一个「虚拟爆炸」来算伤害。
 *
 * <p>但 **1.21.11 把 {@code Explosion} 改成了接口**，唯一实现
 * {@code ExplosionImpl} 的构造器要求 {@code ServerWorld} + 8 个参数
 * （{@code (ServerWorld, Entity, DamageSource, ExplosionBehavior, Vec3d, float, boolean, DestructionType)}），
 * **客户端根本造不出来**。
 *
 * <p><b>解法</b>：GCore 的伤害计算只用到 {@code Explosion} 的两个能力：
 * <ol>
 *     <li>{@link #getPower()} —— 主公式里的 {@code size = power * 2}</li>
 *     <li>{@code entity.isImmuneToExplosion(explosion)} —— 但这个在 1.21.11 的
 *         {@code Entity} 基类里**恒返回 false**，且**完全不读 explosion 参数**
 *         （已用 javap 核实字节码：{@code iconst_0; ireturn}）</li>
 * </ol>
 * 所以只要提供一个正确的 {@code getPower()}，其余方法给安全默认值即可 ——
 * 行为与源**完全等价**。
 *
 * <p>爆炸威力取值与源一致：重生锚 {@code 5.0f}（{@link GcoreAnchorUtils}）、
 * 水晶 {@code 6.0f}（{@link GcoreCrystalUtils}）。
 */
public final class GcoreFakeExplosion implements Explosion {

    private final float power;

    public GcoreFakeExplosion(float power) {
        this.power = power;
    }

    /** ★ 唯一被真正使用的方法。 */
    @Override
    public float getPower() {
        return this.power;
    }

    // ── 以下方法在伤害计算路径上不会被读，给安全默认值 ──

    @Override
    public ServerWorld getWorld() {
        return null;
    }

    @Override
    public Vec3d getPosition() {
        return Vec3d.ZERO;
    }

    @Override
    public DestructionType getDestructionType() {
        // 源用的是 DESTROY_WITH_DECAY（1.20.4 的枚举）
        return DestructionType.DESTROY_WITH_DECAY;
    }

    @Override
    public LivingEntity getCausingEntity() {
        return null;
    }

    @Override
    public Entity getEntity() {
        return null;
    }

    @Override
    public boolean canTriggerBlocks() {
        return true;
    }

    @Override
    public boolean preservesDecorativeEntities() {
        return false;
    }
}
