package com.codigohasta.addon.utils.gcore;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.friends.Friends;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public final class GcoreTargetUtil {

    private GcoreTargetUtil() {
    }

    public enum Priority {
        Distance("距离最近"),
        Hurttime("刚受伤"),
        SelfFov("视角最近"),
        Health("血量最少");

        private final String choiceName;

        Priority(String choiceName) {
            this.choiceName = choiceName;
        }

        public String getChoiceName() {
            return this.choiceName;
        }

        @Override
        public String toString() {
            return this.choiceName;
        }
    }

    public static boolean isSelectTarget(Entity entity, boolean isPlayer, boolean isMob,
                                         boolean isAnimal, boolean isInvisible, boolean isFriend) {
        // ★ 排除自己（源第 77 行：if (Intrinsics.areEqual(entity, clientPlayerEntity)) return false）
        //   漏掉这一行会导致模块把自己当目标 —— 实机已踩过。
        if (entity == MeteorClient.mc.player) {
            return false;
        }

        if (!(entity instanceof LivingEntity living)) {
            return false;
        }
        if (!living.isAlive()) {
            return false;
        }
        if (living.isInvisible() && !isInvisible) {
            return false;
        }

        if (isPlayer && entity instanceof PlayerEntity player) {
            if (Friends.get().shouldAttack(player)) {
                return true;
            }
            if (isFriend) {
                return true;
            }
        }

        if (isAnimal && entity instanceof AnimalEntity) {
            return true;
        }

        if (!isMob) {
            return false;
        }

        // ★★★ 此处是修复点：原为 `if (!(entity instanceof MobEntity)) return false;`
        //     改为「真怪物」判据（SpawnGroup.MONSTER）。
        if (!isMonster(entity)) {
            return false;
        }
        if (entity instanceof AnimalEntity) {
            return false;
        }
        return true;
    }

    public static boolean isMonster(Entity entity) {
        try {
            EntityType<?> type = entity.getType();
            if (type != null && type.getSpawnGroup() != null) {
                return type.getSpawnGroup() == SpawnGroup.MONSTER;
            }
        } catch (Throwable ignored) {
            // 退回旧判据
        }
        return entity instanceof MobEntity;
    }

    /**
     * 目标筛选：**优先用「生物列表」，列表为空时退回旧的三开关逻辑**。
     *
     * <p>这是 <b>Q1 的迁移方案 ③</b>：新增 {@code 生物列表} 设置（默认空 = 不生效），
     * 旧设置 {@code 目标玩家/目标怪物/目标动物} 全部保留。
     * 这样：
     * <ul>
     *     <li>用户既有配置**零丢失**（旧开关不被替换、不改名）</li>
     *     <li>想用新功能时，往「生物列表」里勾选（或直接输入 {@code monster} 一键勾全组）</li>
     * </ul>
     *
     * @param entityTypes 生物列表；{@code null} 或空 = 不使用列表
     */
    public static boolean isSelectTargetWithList(Entity entity,
                                                 Collection<EntityType<?>> entityTypes,
                                                 boolean isPlayer, boolean isMob,
                                                 boolean isAnimal, boolean isInvisible,
                                                 boolean isFriend) {
        if (entityTypes != null && !entityTypes.isEmpty()) {
            // 使用列表：完全以列表为准（但仍然排除自己、死亡、隐身规则）
            if (entity == MeteorClient.mc.player) {
                return false;
            }
            if (!(entity instanceof LivingEntity living)) {
                return false;
            }
            if (!living.isAlive()) {
                return false;
            }
            if (living.isInvisible() && !isInvisible) {
                return false;
            }
            if (!entityTypes.contains(entity.getType())) {
                return false;
            }
            // 玩家仍受好友规则约束
            if (entity instanceof PlayerEntity player) {
                if (Friends.get().shouldAttack(player)) {
                    return true;
                }
                return isFriend;
            }
            return true;
        }

        // 列表为空 → 旧逻辑
        return isSelectTarget(entity, isPlayer, isMob, isAnimal, isInvisible, isFriend);
    }

    /** 选一个目标（源 {@code getTarget(TpSelectTargetsConfigurable)}）。 */
    public static Entity getTarget(GcoreTpSelectTargetsConfig option) {
        List<Entity> list = getTargets(option);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 选一个目标。
     *
     * <p>源：{@code CollectionsKt.firstOrNull(getTargets(...))} ——
     * 即「排序后的第一个」，没有则 null。
     */
    public static Entity getTarget(Priority priority, float range, boolean isPlayer, boolean isMob,
                                   boolean isAnimal, boolean isInvisible, boolean isFriend) {
        List<Entity> list = getTargets(priority, range, isPlayer, isMob, isAnimal, isInvisible, isFriend);
        return list.isEmpty() ? null : list.get(0);
    }

    /** 见 {@link #getTarget(GcoreTpSelectTargetsConfig)}。 */
    public static List<Entity> getTargets(GcoreTpSelectTargetsConfig option) {
        return getTargets(option.getPriority(), option.getRange(),
            option.getTargetPlayer(), option.getTargetMob(), option.getTargetAnimal(),
            option.getTargetInvisible(), option.getTargetFriend(), option.getEntityTypes());
    }

    /**
     * 筛选 + 排序所有可选目标。
     *
     * <p>逐行对应源 133-167：
     * <pre>
     * entities = TickUpdate.getWorldEntitiesList()
     * filter: isSelectTarget(it, ...) &amp;&amp; player.squaredDistanceTo(it) &lt;= range*range
     * return sortedWith(targetSortComparator(priority))
     * </pre>
     */
    public static List<Entity> getTargets(Priority priority, float range, boolean isPlayer,
                                          boolean isMob, boolean isAnimal, boolean isInvisible,
                                          boolean isFriend) {
        return getTargets(priority, range, isPlayer, isMob, isAnimal, isInvisible, isFriend, null);
    }

    /**
     * 筛选 + 排序所有可选目标（★ 支持「生物列表」）。
     *
     * @param entityTypes 生物列表；{@code null} 或空 = 退回旧的三开关逻辑
     */
    public static List<Entity> getTargets(Priority priority, float range, boolean isPlayer,
                                          boolean isMob, boolean isAnimal, boolean isInvisible,
                                          boolean isFriend, Collection<EntityType<?>> entityTypes) {
        var player = MeteorClient.mc.player;
        List<Entity> result = new ArrayList<>();

        for (Entity it : GcoreTickUpdate.getWorldEntitiesList()) {
            if (!isSelectTargetWithList(it, entityTypes, isPlayer, isMob, isAnimal, isInvisible, isFriend)) {
                continue;
            }
            if (player != null && player.squaredDistanceTo(it) <= (double) (range * range)) {
                result.add(it);
            }
        }

        result.sort(targetSortComparator(priority));
        return result;
    }

    public static Comparator<Entity> targetSortComparator(Priority priority) {
        return switch (priority) {
            case Hurttime -> Comparator.comparingInt(e ->
                e instanceof LivingEntity living ? -living.hurtTime : 0);
            case Health -> Comparator.comparingDouble(e ->
                e instanceof LivingEntity living ? (double) living.getHealth() : 0.0);
            case Distance -> Comparator.comparingDouble(e -> {
                var player = MeteorClient.mc.player;
                return player != null ? (double) player.distanceTo(e) : 0.0;
            });
            case SelfFov -> Comparator.comparingDouble(GcoreTargetUtil::rotationDifference);
        };
    }

    private static double rotationDifference(Entity entity) {
        var player = MeteorClient.mc.player;
        if (player == null) {
            return 0.0;
        }

        Vec3d eyePos = player.getEyePos();
        Vec3d toEntity = entity.getEntityPos()
            .add(0.0, entity.getStandingEyeHeight() * 0.5, 0.0)
            .subtract(eyePos);
        double len = toEntity.length();
        if (len == 0.0) {
            return 0.0;
        }

        Vec3d look = player.getRotationVec(1.0f);
        double dot = look.dotProduct(toEntity) / len;
        // 数值误差可能让 dot 略微超出 [-1,1]
        dot = MathHelper.clamp(dot, -1.0, 1.0);
        return Math.toDegrees(Math.acos(dot));
    }
}
