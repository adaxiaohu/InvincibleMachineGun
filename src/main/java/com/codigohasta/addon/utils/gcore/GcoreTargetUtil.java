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

/**
 * 目标选取。
 *
 * <p>移植自 {@code dev.godden.core.Utils.TargetUtil}（GCore）。
 * 源是 Kotlin `object`，Java 里改为静态方法。
 *
 * <p><b>裁剪说明</b>：源类 362 行，TP 锚只走
 * {@code getTarget(GcoreTpSelectTargetsConfig)} → {@code getTargets(...)}
 * → {@code isSelectTarget(...)} + {@code targetSortComparator(...)} 这条链。
 * 因此只移植这四个方法 + {@link Priority} 枚举；
 * {@code getAimTargets} / {@code AimSelectTarget} 相关（给瞄准模块用）不移植。
 *
 * <p><b>★ 环境适配</b>：
 * <ul>
 *     <li>好友判断：源用 LiquidBounce 的 {@code FriendManager}，
 *         这里换成 Meteor 的 {@code Friends.get().shouldAttack(player)}
 *         —— 语义等价（「是不是可以打的目标」）</li>
 *     <li>{@code SelfFov} 排序：源用 LiquidBounce 的
 *         {@code RotationManager.rotationDifference(entity)}，
 *         这里用「玩家视线方向与指向实体方向的夹角」重算（见
 *         {@link #rotationDifference}），语义等价</li>
 * </ul>
 */
public final class GcoreTargetUtil {

    private GcoreTargetUtil() {
    }

    /**
     * 目标优先级。
     *
     * <p>枚举值与源一致，且 {@code Distance} 是默认值。
     */
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

    /**
     * 该实体算不算可选目标（**原版逻辑，保留**）。
     *
     * <p>逐行对应源 69-96：
     * <pre>
     * if (!(entity instanceof LivingEntity)) return false;
     * if (!living.isAlive()) return false;
     * if (living.isInvisible() &amp;&amp; !isInvisible) return false;
     * if (isPlayer &amp;&amp; entity instanceof PlayerEntity) {
     *     if (!isFriend(entity)) return true;      // 非好友 → 可选
     *     if (isFriend) return true;               // 是好友且允许打好友 → 可选
     * }
     * if (isAnimal &amp;&amp; entity instanceof AnimalEntity) return true;
     * if (!isMob) return false;
     * if (!(entity instanceof MobEntity)) return false;
     * if (entity instanceof AnimalEntity) return false;   // 动物已在上面处理过
     * return true;
     * </pre>
     *
     * <p><b>⚠️ 已知缺陷（本项目已确认，源同样存在）</b>：
     * 「怪物」用的是 {@code instanceof MobEntity}，而 {@code MobEntity} 是
     * **「有 AI 的生物」**的基类，不等于「怪物」——
     * 村民（{@code MerchantEntity}）、鱼（{@code FishEntity}）、
     * 铁傀儡（{@code GolemEntity}）、悦灵、蝙蝠都继承 {@code MobEntity}，
     * 因此都会被当成「怪物」选中。
     *
     * <p>源的判据是 {@code class_1308}（= {@code MobEntity}），
     * 与这里逐字一致 —— 即**源本身的缺陷**。
     *
     * <p><b>修法</b>：改用 {@code EntityType.getSpawnGroup() == SpawnGroup.MONSTER}
     * （MC 自己的分类：村民=MISC、鱼=WATER_AMBIENT、铁傀儡=MISC，均不再是「怪物」）。
     * 见 {@link #isMonster(Entity)}。
     */
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
            // 环境适配：源的 FriendManager.isFriend(entity) → Meteor 的 shouldAttack
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

    /**
     * ★ 真正的「怪物」判据。
     *
     * <p>用 MC 自己的分类 {@code SpawnGroup.MONSTER}
     * （{@code EntityType.getSpawnGroup()}）—— 这正是 Meteor 的
     * {@code EntityTypeListSetting} 里 {@code monster} 预设所用的判据。
     *
     * <p>已核实（javap 逐个实体取 SpawnGroup）：
     * <pre>
     * MONSTER  : 僵尸/骷髅/苦力怕/末影人/史莱姆/恶魂/凋灵/末影龙/监守者/掠夺者/幻翼/猪灵/疣猪兽…
     * MISC     : 村民、流浪商人(?)、铁傀儡、雪傀儡、悦灵、蝙蝠   ← 不再是「怪物」
     * CREATURE : 流浪商人、悦灵、绵羊、牛、猪…                  ← 动物
     * WATER_AMBIENT / WATER_CREATURE : 鱼、鱿鱼、海豚            ← 不再是「怪物」
     * </pre>
     *
     * <p>取不到 {@code EntityType} 时**退回**原来的 {@code MobEntity} 判据，
     * 保证不会因为异常而漏掉目标。
     */
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

    /**
     * 按优先级构造排序比较器。
     *
     * <p><b>四个排序键从 jar 字节码逐条找回</b>
     * （{@code TargetUtil$targetSortComparator$1..4}）：
     * <pre>
     * case 1 Hurttime → comparingInt(e -&gt; e instanceof LivingEntity ? -hurtTime : 0)
     * case 2 Health   → comparingDouble(e -&gt; e.getHealth())        // class_1309.method_6032
     * case 3 Distance → comparingDouble(e -&gt; player.distanceTo(e)) // class_746.method_5739
     * case 4 SelfFov  → comparingDouble(e -&gt; rotationDifference(e))
     * </pre>
     * 全部**升序**（{@code comparingX} 默认升序）——
     * 受伤时间取负号，所以「刚受伤的排前面」。
     *
     * <p>{@code WhenMappings} 的 case 号对应关系（从字节码读出）：
     * Hurttime=1、Health=2、Distance=3、SelfFov=4。
     */
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

    /**
     * 实体方向与玩家视线的夹角（度）。
     *
     * <p><b>环境适配</b>：源用 LiquidBounce 的
     * {@code RotationManager.rotationDifference(entity)}（LiquidBounce 专有）。
     * 这里按同样语义重算 —— 「要瞄准该实体需要转多少度」，
     * 即玩家当前视线方向与「玩家眼→实体」方向的夹角。
     *
     * <p>用两个方向向量的点积求夹角（比分别算 yaw/pitch 差值更稳，
     * 且不受 yaw 绕圈影响）。
     */
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
