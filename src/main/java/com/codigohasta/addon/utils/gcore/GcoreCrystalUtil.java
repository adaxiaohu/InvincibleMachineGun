package com.codigohasta.addon.utils.gcore;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * 水晶基础工具（放置判定 / 底座判定 / 找要打的水晶）。
 *
 * <p>移植自 {@code dev.godden.core.Utils.CrystalUtil}（GCore，源 600 行）。
 *
 * <p><b>裁剪说明</b>：源有 6 个公开方法，TP 水晶路径**只用 3 个**：
 * <ul>
 *     <li>{@link #canPlaceCheck} —— 这一格能否放水晶</li>
 *     <li>{@link #isCrystaBase} —— 这是不是合法底座</li>
 *     <li>{@link #getAttackCrystalFromTarget} —— 找伤害最高的水晶</li>
 * </ul>
 * 其余（{@code getPlaceObsidianDataFromTarget} / {@code getPlaceCrystalPosFromTarget} /
 * {@code getCauraAttackCrystalFromTarget}）只有**非 TP** 的 {@code ModuleGCrystalAura} 用，
 * 按「按需裁剪」原则不移植。
 *
 * <p><b>★ 别与本项目已有的 {@link GcoreCrystalUtils} 混淆</b>：
 * <ul>
 *     <li>{@code GcoreCrystalUtils} ← 源 {@code dev.mumy.utils.crystal.CrystalUtils}，
 *         <b>只有 {@code getExplosion()}</b>（36 行，给伤害计算提供水晶爆炸实例）</li>
 *     <li>{@code GcoreCrystalUtil} ← 源 {@code dev.godden.core.Utils.CrystalUtil}，
 *         <b>本类</b>（600 行的放置判定逻辑）</li>
 * </ul>
 * 两个类名字只差一个 {@code s}，源里也确实是两个不同的类。
 */
public final class GcoreCrystalUtil {

    private GcoreCrystalUtil() {
    }

    // ─────────────────────────── canPlaceCheck ───────────────────────────

    /** 见 {@link #canPlaceCheck(GcoreEnumType.CrystalPlaceVersion, BlockPos, boolean, boolean, List)}，全默认。 */
    public static boolean canPlaceCheck(GcoreEnumType.CrystalPlaceVersion version, BlockPos placePos,
                                        boolean ignoreCrystal) {
        return canPlaceCheck(version, placePos, ignoreCrystal, false,
            GcoreTickUpdate.getWorldEntitiesList());
    }

    /**
     * 这一格能否放水晶。
     *
     * <p>逐行对应源的 {@code CrystalUtil.canPlaceCheck}（源 32-69 行）。
     *
     * <pre>
     * if (world.isEmptyBlock(placePos.above())) {                  // ① 底座上方必须"空"
     *     if (version == MC1_12 && !world.isEmptyBlock(placePos.above(2)))
     *         return false;                                        // ② 1.12 还要再空一格
     *     if ((ignoreBase || isCrystaBase(placePos))               // ③ 底座合法
     *         && !EntityUtil.checkAnyEntitiesCollie(               // ④ 1×2×1 空间内无实体
     *                 new Box(placePos.x, placePos.y+1, placePos.z,
     *                         placePos.x+1, placePos.y+3, placePos.z+1),
     *                 ignoreCrystal, list))
     *         return true;
     * }
     * return false;
     * </pre>
     *
     * <p><b>★ 底座上方判定用 {@code world.isAir(pos)}</b>：
     * 源的 {@code method_22347} 在项目实际使用的 Yarn 映射
     * （{@code 1.21.11+build.4}，见 {@code gradle/libs.versions.toml}）里
     * 就是 <b>{@code isAir}</b> —— 已用编译探针实测确认
     * （{@code world.isEmptyBlock(...)} 在本映射下**不存在**，编译不过）。
     *
     * <p>⚠️ 排查提示：{@code E:\.gradle\...\loom.mappings.1_21_11.layered+...\mappings-base.tiny}
     * 是**另一套映射**（含 Mojang 名），它把同一个 {@code method_22347}
     * 标成 {@code isEmptyBlock}。**不要用那个文件查 Yarn 名** ——
     * 必须用 {@code net.fabricmc.yarn.1_21_11.1.21.11+build.4-v2}。
     *
     * <p><b>★ 盒子是 1×2×1（不是 1×1×1）</b>：
     * 从 {@code placePos.y + 1} 到 {@code + 3}，即底座上方 **2 格**
     * （水晶本身占 2 格高）。参数里的 {@code +3} 容易抄成 {@code +2}。
     *
     * @param ignoreCrystal 是否忽略水晶实体（{@code true} 时不算水晶挡位）
     * @param ignoreBase    是否忽略底座检查（{@code true} 时任何方块都算底座）。
     *                      源默认 {@code false}；TP 水晶的调用点传 {@code true}
     * @param list          实体列表（源默认取 {@code TickUpdate.getWorldEntitiesList()}）
     */
    public static boolean canPlaceCheck(GcoreEnumType.CrystalPlaceVersion version, BlockPos placePos,
                                        boolean ignoreCrystal, boolean ignoreBase, List<Entity> list) {
        World world = MinecraftClient.getInstance().world;

        // ① 底座上方必须「空」
        if (world.isAir(placePos.up())) {
            // ② MC1_12 的水晶是 2 格高，要求上方 2 格都空
            if (version == GcoreEnumType.CrystalPlaceVersion.MC1_12) {
                if (!world.isAir(placePos.up(2))) {
                    return false;
                }
            }

            // ③ 底座合法 + ④ 1×2×1 空间内没有实体
            if ((ignoreBase || isCrystaBase(placePos))
                && !GcoreEntityUtil.checkAnyEntitiesCollie(
                    new Box(
                        placePos.getX() + 0.0, placePos.getY() + 1.0, placePos.getZ() + 0.0,
                        placePos.getX() + 1.0, placePos.getY() + 3.0, placePos.getZ() + 1.0),
                    ignoreCrystal,
                    list)) {
                return true;
            }
        }

        return false;
    }

    // ─────────────────────────── isCrystaBase ───────────────────────────

    /**
     * 这一格是不是合法底座（黑曜石 / 基岩）。
     *
     * <p>逐行对应源的 {@code CrystalUtil.isCrystaBase}（源 71-76 行）。
     *
     * <p><b>★ 源的方法名拼错了</b>（{@code isCrystaBase}，少一个 {@code l}）——
     * 保留原名以对应源，不"修正"成 {@code isCrystalBase}。
     *
     * <p><b>★ 只认两种方块</b>，源就是这么写的：
     * <pre>
     * return block == Blocks.OBSIDIAN || block == Blocks.BEDROCK;
     * </pre>
     * 注意**没有** {@code CRYING_OBSIDIAN} —— 尽管它也能放水晶，
     * 源不接受。照抄不改。
     */
    public static boolean isCrystaBase(BlockPos placePos) {
        World world = MinecraftClient.getInstance().world;
        var block = world.getBlockState(placePos).getBlock();
        return block == Blocks.OBSIDIAN || block == Blocks.BEDROCK;
    }

    // ─────────────────────────── getAttackCrystalFromTarget ───────────────────────────

    /** 见 {@link #getAttackCrystalFromTarget(Entity, Vec3d, Entity, Vec3d, float, float, float, Predicate)}，全默认。 */
    public static Entity getAttackCrystalFromTarget(Entity target, Vec3d targetVec,
                                                    Entity attacker, Vec3d attackerVec) {
        return getAttackCrystalFromTarget(target, targetVec, attacker, attackerVec,
            0.0f, 1.0f, 12.0f, crystal -> true);
    }

    /**
     * 找「最值得打」的末影水晶。
     *
     * <p>逐行对应源的 {@code CrystalUtil.getAttackCrystalFromTarget}（源 517-567 行）。
     *
     * <pre>
     * sqExpDis = expSizeDistance²
     * for (entity : TickUpdate.getWorldEntitiesList()) {
     *     if (entity instanceof EndCrystalEntity
     *         &amp;&amp; target.squaredDistanceTo(entity) &lt; sqExpDis          // ★ 搜索半径（对目标，不是对自己）
     *         &amp;&amp; filterFunction.test(entity)) {
     *         damageToTarget = getExpDamage(crystal.pos, target, targetVec);
     *         if (damageToTarget &gt;= minDamageToTarget) {
     *             damageToSelf = (maxDamageToSelf &gt;= 1.0f)
     *                 ? -999                                        // ★ 跳过自伤计算
     *                 : getExpDamage(crystal.pos, attacker, attackerVec);
     *             if (!(damageToSelf &gt; maxDamageToSelf))
     *                 crystalList.add(new CrystalVecAndDamage(null, damageToTarget, entity));
     *         }
     *     }
     * }
     * return maxWithOrNull(crystalList, comparingDouble(d -&gt; d.getDamage()));   // ★ 取伤害最大
     * </pre>
     *
     * <p><b>★ 三个容易抄错的点（都已用字节码核实）</b>：
     * <ol>
     *     <li><b>{@code maxDamageToSelf >= 1.0f} 时自伤取常量 {@code -999}</b>
     *         —— 这是「跳过自伤计算」的短路技巧，不是「-999 伤害」。
     *         {@code -999 <= maxDamageToSelf} 恒真，所以任何水晶都能通过自伤这一关。
     *         模块传的正是 {@code 999.0f}，即**完全跳过**自伤过滤。</li>
     *     <li><b>自伤判据是 {@code !(damageToSelf > maxDamageToSelf)}</b>，
     *         即 {@code <=}（含等于）。不是严格 {@code <}。</li>
     *     <li><b>搜索半径是「目标到水晶」的距离</b>，不是「玩家到水晶」。
     *         因为玩家可以 TP 过去，远近无所谓；关键是水晶得炸到目标。</li>
     * </ol>
     *
     * <p><b>排序比较器</b>（从 jar 里找回的 {@code getAttackCrystalFromTarget$best$1}）：
     * <pre>
     * CrystalVecAndDamage::getDamage    →   maxWithOrNull   →   取 damage 最大者
     * </pre>
     *
     * <p><b>为什么返回 {@code Entity} 而不是 {@code EndCrystalEntity}</b>：
     * 源里返回类型是 {@code class_1511}（= {@code EndCrystalEntity}）。
     * Java 侧保持具体类型更贴近源，调用方需要时可直接用。
     *
     * @param minDamageToTarget 对目标的最小伤害（低于此不算）
     * @param maxDamageToSelf   自伤上限；{@code >= 1.0f} 时**跳过**自伤计算
     * @param expSizeDistance   搜索半径（对目标）。模块传 12.0
     * @param filterFunction    额外过滤（源默认 {@code { true }}）
     */
    public static net.minecraft.entity.Entity getAttackCrystalFromTarget(
        Entity target, Vec3d targetVec,
        Entity attacker, Vec3d attackerVec,
        float minDamageToTarget, float maxDamageToSelf, float expSizeDistance,
        Predicate<net.minecraft.entity.Entity> filterFunction) {

        ArrayList<GcoreCrystalVecAndDamage> crystalList = new ArrayList<>();
        float sqExpDis = expSizeDistance * expSizeDistance;

        for (Entity entity : GcoreTickUpdate.getWorldEntitiesList()) {
            if (entity instanceof net.minecraft.entity.decoration.EndCrystalEntity
                && target.squaredDistanceTo(entity) < (double) sqExpDis
                && filterFunction.test(entity)) {

                Vec3d crystalPos = entity.getEntityPos();
                float damageToTarget = GcoreExplosionUtils.getExpDamage(
                    crystalPos, target, targetVec, null, GcoreCrystalUtils.getExplosion());

                if (!(damageToTarget < minDamageToTarget)) {
                    float damageToSelf;
                    if (maxDamageToSelf >= 1.0f) {
                        damageToSelf = -999.0f;
                    } else {
                        damageToSelf = GcoreExplosionUtils.getExpDamage(
                            crystalPos, attacker, attackerVec, null, GcoreCrystalUtils.getExplosion());
                    }

                    if (!(damageToSelf > maxDamageToSelf)) {
                        crystalList.add(new GcoreCrystalVecAndDamage(null, damageToTarget, entity));
                    }
                }
            }
        }

        // 源：maxWithOrNull(crystalList, comparingDouble(CrystalVecAndDamage::getDamage))
        GcoreCrystalVecAndDamage best = crystalList.stream()
            .max(Comparator.comparingDouble(GcoreCrystalVecAndDamage::getDamage))
            .orElse(null);

        return (best == null) ? null : best.getCrystalEntity();
    }
}
