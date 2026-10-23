package com.codigohasta.addon.utils.gcore;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

import java.util.HashMap;
import java.util.Map;

/**
 * 战斗状态判断（图腾 / 受击无敌帧）。
 *
 * <p><b>来源与依据</b>（全部用 Paper 服务端 jar 官方名 + 本项目 yarn 映射核实，
 * 见 {@code docs/gcore/无敌帧-最终定论.md}）：
 *
 * <ul>
 *   <li><b>图腾消耗广播</b>：服务端 {@code LivingEntity.tryUseDeathProtector} 在
 *       消耗图腾后调用 {@code sendEntityStatus(this, (byte) 35)}；
 *       yarn 侧该常量的具名形式就是
 *       {@link EntityStatuses#USE_TOTEM_OF_UNDYING}。
 *       客户端收到 {@code EntityStatusS2CPacket} 即可知道「谁刚用了一个图腾」。</li>
 *
 *   <li><b>图腾只查双手</b>：{@code tryUseDeathProtector} 用 {@code Hand.values()}
 *       遍历主手/副手，<b>不查背包</b>。
 *       → 所以「目标手上还有没有图腾」可用 {@code getStackInHand} 直接读；
 *         「背包里还剩几个」则无从得知。</li>
 *
 *   <li><b>无敌帧可精确读</b>：服务端判定用的是
 *       {@code LivingEntity.timeUntilRegen}（yarn）= {@code Entity.invulnerableTime}（官方名），
 *       它是 <b>{@code public int}</b>，客户端对<b>任意实体</b>都能读。
 *       受伤时置 20，每 tick 递减（{@code ServerPlayerEntity} 在自己的 tick 里递减，
 *       通用的 {@code LivingEntity} tick 会跳过它以免重复扣减）。</li>
 * </ul>
 *
 * <p>调用方（模块）需要在 {@code PacketEvent.Receive} 里把
 * {@code EntityStatusS2CPacket} 喂给 {@link #onEntityStatus}。
 */
public final class GcoreCombatUtil {

    private GcoreCombatUtil() {
    }

    /** 图腾消耗后，把「该实体有图腾」标记维持多久（tick）。 */
    private static final int TOTEM_MEMORY_TICKS = 100;

    /** 无敌帧阈值：{@code timeUntilRegen <= 10} 时服务端会给全额伤害。 */
    public static final int FULL_DAMAGE_REGEN_THRESHOLD = 10;

    /** entityId -> 剩余记忆 tick 数。 */
    private static final Map<Integer, Integer> totemMemory = new HashMap<>();

    /**
     * 模块每 tick 调用一次，用于推进「图腾记忆」的倒计时并清理过期项。
     *
     * <p>过期即移除，所以 Map 里同时最多只有「最近 100 tick 内用过图腾」的实体，
     * 不会无限增长。
     */
    public static void tick() {
        if (totemMemory.isEmpty()) {
            return;
        }
        totemMemory.entrySet().removeIf(e -> {
            int left = e.getValue() - 1;
            if (left <= 0) {
                return true;
            }
            e.setValue(left);
            return false;
        });
    }

    /** 模块卸载/关闭时清空，避免状态跨会话残留。 */
    public static void clear() {
        totemMemory.clear();
    }

    /**
     * 收到 {@code EntityStatusS2CPacket} 时调用。
     *
     * @param entity 包里的实体（可能为 null）
     * @param status 状态码
     */
    public static void onEntityStatus(Entity entity, byte status) {
        if (entity == null) {
            return;
        }
        if (status == EntityStatuses.USE_TOTEM_OF_UNDYING) {
            totemMemory.put(entity.getId(), TOTEM_MEMORY_TICKS);
        }
    }

    /** 该实体最近是否消耗过图腾。 */
    public static boolean recentlyUsedTotem(Entity entity) {
        return entity != null && totemMemory.containsKey(entity.getId());
    }

    /**
     * 目标手上（主手或副手）是否持有图腾。
     *
     * <p>依据：图腾判定只查 {@code Hand.values()}（不查背包），
     * 所以「手上没有」≈「下次死亡不会再被图腾救」。
     */
    public static boolean hasTotemInHand(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        return isTotem(entity.getStackInHand(Hand.MAIN_HAND))
            || isTotem(entity.getStackInHand(Hand.OFF_HAND));
    }

    /** 兼容 1.21 的组件化物品判定（{@code Items.TOTEM_OF_UNDYING}）。 */
    private static boolean isTotem(net.minecraft.item.ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.TOTEM_OF_UNDYING;
    }

    /**
     * ★ 是否应该对该目标启用「图腾绕过」（连续递增攻击）。
     *
     * <p>原实现是「开关打开就对所有目标连击」，对没有图腾的目标纯属浪费
     * （多发好几套伪造包，还更容易被反作弊盯上）。
     *
     * <p>判据（任一成立才连击）：
     * <ol>
     *     <li>目标<b>手上</b>持有图腾 → 需要打穿</li>
     *     <li>目标<b>刚刚</b>消耗过图腾 → 它可能还会补，且此时血只剩 1</li>
     * </ol>
     */
    public static boolean shouldCompatTotem(LivingEntity target) {
        if (target == null) {
            return false;
        }
        return hasTotemInHand(target) || recentlyUsedTotem(target);
    }

    /**
     * 目标是否处于「无敌帧且不会给全额伤害」的状态。
     *
     * @return 剩余无敌 tick；{@code <= 0} 表示没有无敌
     */
    public static int invulnerableTicks(Entity entity) {
        return entity == null ? 0 : entity.timeUntilRegen;
    }

    /**
     * ★ 智能无敌帧：这一击现在打，能不能吃到<b>全额</b>伤害？
     *
     * <p>服务端 {@code LivingEntity.damage} 的规则（字节码已核）：
     * <pre>
     * if (timeUntilRegen &gt; 10 &amp;&amp; !BYPASSES_COOLDOWN) {
     *     if (damage &lt;= lastDamageTaken) return false;   // 整击丢弃
     *     applyDamage(damage - lastDamageTaken);         // 只结算差额
     * }
     * </pre>
     * 因此 {@code timeUntilRegen <= 10} 时必定给全额。
     *
     * @return 若现在打能拿全额则 true
     */
    public static boolean canDealFullDamage(Entity entity) {
        return invulnerableTicks(entity) <= FULL_DAMAGE_REGEN_THRESHOLD;
    }

    /**
     * ★ 智能无敌帧：建议等待的 tick 数（等到能拿全额伤害）。
     *
     * @param margin 额外余量，抵消 tick 边界与网络延迟
     * @return 0 表示不必等
     */
    public static int suggestWaitTicks(Entity entity, int margin) {
        int regen = invulnerableTicks(entity);
        if (regen <= FULL_DAMAGE_REGEN_THRESHOLD) {
            return 0;
        }
        return (regen - FULL_DAMAGE_REGEN_THRESHOLD) + Math.max(0, margin);
    }

    /**
     * 目标是否在举盾格挡（此时攻击基本无效，acid 的 MaceKill 也做这个判断）。
     *
     * <p>注意只有 {@link PlayerEntity} 有格挡语义，其它生物恒为 false。
     */
    public static boolean isBlocking(Entity entity) {
        if (entity instanceof LivingEntity living) {
            return living.isBlocking();
        }
        return false;
    }
}
