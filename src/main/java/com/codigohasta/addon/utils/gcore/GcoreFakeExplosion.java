package com.codigohasta.addon.utils.gcore;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.explosion.Explosion;

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
