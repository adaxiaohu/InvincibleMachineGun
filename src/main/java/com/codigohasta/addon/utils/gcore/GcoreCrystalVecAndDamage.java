package com.codigohasta.addon.utils.gcore;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/**
 * 「一个位置 + 它的伤害值」。
 *
 * <p>移植自 {@code dev.godden.core.Utils.ClassUtil.CrystalVecAndDamage}（GCore，源 13 行）。
 *
 * <p>字段与源一一对应：
 * <ul>
 *     <li>{@code vec} —— 位置（可为 null）</li>
 *     <li>{@code damage} —— 伤害值</li>
 *     <li>{@code crystalEntity} —— 关联的末影水晶实体（可为 null）</li>
 * </ul>
 *
 * <p><b>两个用途</b>（源里同一个类服务两处）：
 * <ol>
 *     <li>{@code getSafePosToAttackCrystal} —— 装「站位 + 自伤」，
 *         按自伤升序取最小（此时 {@code crystalEntity} 为 null）</li>
 *     <li>{@code getAttackCrystalFromTarget} —— 装「水晶位置 + 对目标伤害 + 水晶实体」，
 *         按伤害降序取最大（此时 {@code vec} 为 null）</li>
 * </ol>
 *
 * <p><b>★ 三个字段都可空，且源没有任何 null 检查</b> ——
 * 排序比较器只读 {@code damage}，取用时按用途读对应字段。
 * 照抄源的宽松语义，不额外加检查。
 */
public final class GcoreCrystalVecAndDamage {

    private final Vec3d vec;
    private final float damage;
    private final Entity crystalEntity;

    public GcoreCrystalVecAndDamage(Vec3d vec, float damage, Entity crystalEntity) {
        this.vec = vec;
        this.damage = damage;
        this.crystalEntity = crystalEntity;
    }

    public Vec3d getVec() {
        return this.vec;
    }

    public float getDamage() {
        return this.damage;
    }

    public Entity getCrystalEntity() {
        return this.crystalEntity;
    }

    @Override
    public String toString() {
        return "CrystalVecAndDamage(vec=" + this.vec + ", damage=" + this.damage
            + ", crystal=" + this.crystalEntity + ")";
    }
}
