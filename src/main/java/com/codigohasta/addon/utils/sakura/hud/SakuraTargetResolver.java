package com.codigohasta.addon.utils.sakura.hud;

import com.codigohasta.addon.mixin.sakura.AttackRenderTargetAccessor;
import com.codigohasta.addon.mixin.sakura.AttractAuraTargetAccessor;
import com.codigohasta.addon.mixin.sakura.AutoCrystalTargetAccessor;
import com.codigohasta.addon.mixin.sakura.CrossbowAuraTargetAccessor;
import com.codigohasta.addon.mixin.sakura.ElytraBotTargetAccessor;
import com.codigohasta.addon.mixin.sakura.ElytraFollowerTargetAccessor;
import com.codigohasta.addon.mixin.sakura.GLightTpAnchorAuraTargetAccessor;
import com.codigohasta.addon.mixin.sakura.KillAuraTargetsAccessor;
import com.codigohasta.addon.mixin.sakura.MaceAuraTargetAccessor;
import com.codigohasta.addon.mixin.sakura.MaceDMGPlusTargetAccessor;
import com.codigohasta.addon.mixin.sakura.PistonCrystalTargetAccessor;
import com.codigohasta.addon.mixin.sakura.SpearKillTargetAccessor;
import com.codigohasta.addon.mixin.sakura.TargetStrafeTargetAccessor;
import com.codigohasta.addon.mixin.sakura.TpAnchorAuraTargetAccessor;
import com.codigohasta.addon.mixin.sakura.TpAuraTargetAccessor;
import com.codigohasta.addon.mixin.sakura.XTpauraTargetAccessor;
import com.codigohasta.addon.modules.AttractAura;
import com.codigohasta.addon.modules.AutoAnchor;
import com.codigohasta.addon.modules.AutoCrystal;
import com.codigohasta.addon.modules.CrossbowAura;
import com.codigohasta.addon.modules.ElytraFollower;
import com.codigohasta.addon.modules.GLightTpAnchorAura;
import com.codigohasta.addon.modules.MaceAura;
import com.codigohasta.addon.modules.MaceDMGPlus;
import com.codigohasta.addon.modules.PistonCrystal;
import com.codigohasta.addon.modules.SpearKill;
import com.codigohasta.addon.modules.TargetStrafe;
import com.codigohasta.addon.modules.TpAnchorAura;
import com.codigohasta.addon.modules.TpAura;
import com.codigohasta.addon.modules.XTpaura;
import com.codigohasta.addon.modules.slimefunhelper.Attack;
import com.codigohasta.addon.modules.slimefunhelper.ElytraBot;
import com.codigohasta.addon.utils.slimefunhelper.hacks.CombatTasks;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 目标解析器：按优先级汇总「谁才是当前目标」，支持<b>多目标</b>。
 *
 * <p>Sakura 原版 TargetHud 的顺序是：
 * <pre>
 *   KillAura.getCurrentTarget()  →  CrystalAura.getCurrentTarget()  →  null
 * </pre>
 * 本项目没有这两个模块，因此这里按同样的「aura 优先、准星兜底」原则重建：
 * <ol>
 *     <li>Meteor 自带 KillAura —— 它支持 {@code max-targets} 多目标，因此能一次给出多个</li>
 *     <li>sfIMG 的 {@code Attack}（「a攻击辅助」）—— 取它自己的<b>渲染目标</b>
 *         {@code lastTickTarget}，与屏幕上画框的那个目标一致（只贡献 1 个）</li>
 *     <li>本项目的 aura 模块，按 {@link #SOURCES} 的顺序取第一个非空且仍存活的目标</li>
 *     <li>准星指向的实体（等价于原版「没有 aura 目标时显示准星目标」的直觉行为）</li>
 * </ol>
 *
 * <p><b>多目标是怎么来的</b>：只有真正「同时攻击多个目标」的来源才会贡献多于一个结果。
 * 目前只有 KillAura（{@code targets} 列表）具备这个能力；其余模块内部只维护单个 target，
 * 因此最多贡献 1 个。调用方用 {@link #resolveAll} 拿到列表后用 {@code maxTargets}
 * 设置自行截断。
 *
 * <p>每个来源都要求模块处于激活状态；多数 aura 模块在 {@code onDeactivate()} 里会把目标
 * 置空，但仍然显式检查 {@code isActive()}，避免读取到残留值。
 */
public final class SakuraTargetResolver {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    /** 单个来源：返回模块实例（未注册时为 null）。 */
    private record Source(Class<? extends Module> type, Supplier<Entity> reader) {}

    /**
     * 有目标字段、且能通过 accessor mixin 读取的模块。
     *
     * <p>注意 {@code CrystalMacro} 没有任何目标字段，无法参与；{@code AutoAnchor} 的
     * {@code target} 是 public 字段，直接用方法引用读；其余走 accessor。
     */
    private static final List<Source> SOURCES = new ArrayList<>();

    static {
        SOURCES.add(new Source(MaceAura.class,
                () -> ((MaceAuraTargetAccessor) Modules.get().get(MaceAura.class)).img$currentTarget()));
        SOURCES.add(new Source(CrossbowAura.class, () -> {
            CrossbowAuraTargetAccessor accessor = (CrossbowAuraTargetAccessor) Modules.get().get(CrossbowAura.class);
            Entity locked = accessor.img$lockedTarget();
            return locked != null ? locked : accessor.img$currentTarget();
        }));
        SOURCES.add(new Source(TpAura.class,
                () -> ((TpAuraTargetAccessor) Modules.get().get(TpAura.class)).img$currentTarget()));
        // 两个 TP 锚模块（完整版 / Light 版）与上面的 TpAura 同源，都是 gcore 系 TP 锚打击。
        // 读的是模块「记忆中的目标」：updateTarget() 里 bvr 置位时目标会保留不清空，
        // 所以这里可能读到已不在范围内、但模块仍在记忆的目标 —— 这正是要的语义
        // （与模块自身行为一致），因此【不】额外加范围过滤。
        SOURCES.add(new Source(TpAnchorAura.class,
                () -> ((TpAnchorAuraTargetAccessor) Modules.get().get(TpAnchorAura.class)).img$target()));
        SOURCES.add(new Source(GLightTpAnchorAura.class,
                () -> ((GLightTpAnchorAuraTargetAccessor) Modules.get().get(GLightTpAnchorAura.class)).img$target()));
        SOURCES.add(new Source(XTpaura.class,
                () -> ((XTpauraTargetAccessor) Modules.get().get(XTpaura.class)).img$target()));
        SOURCES.add(new Source(SpearKill.class,
                () -> ((SpearKillTargetAccessor) Modules.get().get(SpearKill.class)).img$killTarget()));
        SOURCES.add(new Source(MaceDMGPlus.class,
                () -> ((MaceDMGPlusTargetAccessor) Modules.get().get(MaceDMGPlus.class)).img$currentTarget()));
        SOURCES.add(new Source(AutoCrystal.class,
                () -> ((AutoCrystalTargetAccessor) Modules.get().get(AutoCrystal.class)).img$target()));
        SOURCES.add(new Source(PistonCrystal.class,
                () -> ((PistonCrystalTargetAccessor) Modules.get().get(PistonCrystal.class)).img$target()));
        SOURCES.add(new Source(AttractAura.class,
                () -> ((AttractAuraTargetAccessor) Modules.get().get(AttractAura.class)).img$target()));
        SOURCES.add(new Source(TargetStrafe.class,
                () -> ((TargetStrafeTargetAccessor) Modules.get().get(TargetStrafe.class)).img$target()));
        SOURCES.add(new Source(ElytraFollower.class,
                () -> ((ElytraFollowerTargetAccessor) Modules.get().get(ElytraFollower.class)).img$target()));
        // sfIMG 的「鞘翅追人」是 ElytraBot（与上面的 ElytraFollower 是两个模块）
        SOURCES.add(new Source(ElytraBot.class,
                () -> ((ElytraBotTargetAccessor) Modules.get().get(ElytraBot.class)).img$target()));
        // AutoAnchor 的目标字段本身是 public，不需要 accessor
        SOURCES.add(new Source(AutoAnchor.class, () -> {
            Module module = Modules.get().get(AutoAnchor.class);
            return module instanceof AutoAnchor anchor ? anchor.target : null;
        }));
    }

    private SakuraTargetResolver() {}

    /** 准星锁定状态（锥形捕获 + 粘滞 + 停留窗口）。 */
    private static final SakuraCrosshairLock CROSSHAIR_LOCK = new SakuraCrosshairLock();

    /** 由模块每帧同步锁定参数。 */
    public static void configureCrosshair(boolean enabled,
                                          float coneAngle, float stickiness, float range,
                                          float lingerTime, float switchMargin,
                                          boolean playersOnly, boolean ignoreSpectators,
                                          boolean requireVisible) {
        crosshairEnabled = enabled;
        if (!enabled) CROSSHAIR_LOCK.clear();
        CROSSHAIR_LOCK.configure(coneAngle, stickiness, range, lingerTime,
            switchMargin, playersOnly, ignoreSpectators, requireVisible);
    }

    private static boolean crosshairEnabled = true;

    /**
     * 取当前应显示的目标（单个，等价于 {@link #resolveAll} 的第一个）。
     *
     * @param deltaTime 距上一帧的秒数（驱动停留窗口倒计时）
     * @return 存活的 {@link LivingEntity}，没有目标时返回 {@code null}
     */
    public static LivingEntity resolve(float deltaTime) {
        List<LivingEntity> all = resolveAll(deltaTime, 1);
        return all.isEmpty() ? null : all.get(0);
    }

    /**
     * 取当前应显示的<b>全部</b>目标，按优先级排序且已去重。
     *
     * <p>去重是必要的：同一个实体可能同时是 KillAura 和某个 aura 模块的目标，
     * 不去重会在面板上重复显示同一个人。
     *
     * @param deltaTime 距上一帧的秒数（驱动停留窗口倒计时）
     * @param maxTargets 最多返回几个；{@code <= 0} 时按 1 处理
     */
    public static List<LivingEntity> resolveAll(float deltaTime, int maxTargets) {
        List<LivingEntity> result = new ArrayList<>();
        int limit = Math.max(1, maxTargets);

        // 本帧重新统计「哪些目标被 aura 模块锁定」
        auraLockedIds.clear();

        if (mc.player == null || mc.world == null) {
            CROSSHAIR_LOCK.clear();
            return result;
        }

        // 用 LinkedHashMap 保序去重：键是实体 id
        Map<Integer, LivingEntity> unique = new LinkedHashMap<>();

        // 1) Meteor 自带 KillAura —— 支持 max-targets 多目标
        KillAura killAura = Modules.get().get(KillAura.class);
        if (killAura != null && killAura.isActive()) {
            try {
                List<Entity> targets = ((KillAuraTargetsAccessor) killAura).img$targets();
                if (targets != null) {
                    for (Entity entity : targets) addUnique(unique, entity);
                }
            } catch (Throwable ignored) {
                // accessor 不可用时退回单目标接口，保证 HUD 不崩
                addUnique(unique, killAura.getTarget());
            }
        }

        // 2) sfIMG 的「a攻击辅助」(Attack) —— 以它自己的【渲染目标】为准。
        //
        //    不能再用 getCurrentRangeEntities()：它返回范围内"所有"可攻击实体且【不排序】，
        //    取第一个只是碰巧，与模块真正锁定、并在屏幕上画框的目标不是同一个。
        //    渲染目标由 onRenderTarget() 每 tick 缓存进 lastTickTarget，
        //    来源是 searchAttackEntity(...)，内部是「准星优先 → 射线命中 → 权重排序取最优」，
        //    这才是用户看到的那个目标。
        //
        //    只贡献 1 个：渲染目标本身就只有单个，多目标名额留给 KillAura / 其它 aura / 准星锁定。
        //
        //    该字段只在 enable && renderAttackTarget && 手持合适武器时更新，
        //    所以拿不到值时【本来源不贡献目标】（不回退到范围列表），避免显示一个
        //    屏幕上根本没有框的实体。
        if (unique.size() < limit) {
            try {
                Attack attack = CombatTasks.getAttack();
                if (attack != null && attack.isActive()) {
                    addUnique(unique,
                        ((AttackRenderTargetAccessor) attack).img$lastTickTarget());
                }
            } catch (Throwable ignored) {
                // sfIMG 未加载 / accessor 不可用时静默跳过，别让 HUD 崩
            }
        }

        // 3) 本项目的 aura 模块（各自最多贡献 1 个）
        if (unique.size() < limit) {
            for (Source source : SOURCES) {
                if (unique.size() >= limit) break;
                Module module = Modules.get().get(source.type());
                if (module == null || !module.isActive()) continue;

                try {
                    // 记录「这个目标是被某个 aura 模块锁定的」——供透视标记的动态旋转
                    // 判断「模块是否正在工作」。准星锁定不算（那只是准星指向，不是模块在打）。
                    addUniqueTracked(unique, source.reader().get(), true);
                } catch (Throwable ignored) {
                    // accessor 与目标类不匹配（例如模块被移除/改名）时不要让 HUD 崩掉
                }
            }
        }

        // 4) 准星锁定：锥形捕获 + 粘滞保持 + 停留窗口
        //    它自己就能贡献多个目标（锥内所有候选），因此要把剩余名额都给它
        if (unique.size() < limit && crosshairEnabled) {
            List<LivingEntity> locked = CROSSHAIR_LOCK.update(deltaTime, limit - unique.size());
            for (LivingEntity entity : locked) {
                addUnique(unique, entity);
            }
        }

        for (LivingEntity entity : unique.values()) {
            result.add(entity);
            if (result.size() >= limit) break;
        }
        return result;
    }

    /** 过滤存活实体并去重加入。 */
    private static void addUnique(Map<Integer, LivingEntity> unique, Entity entity) {
        addUniqueTracked(unique, entity, false);
    }

    /**
     * 去重加入，并可标记「来自 aura 模块」。
     *
     * <p>被 aura 模块锁定的目标 id 会记进 {@link #auraLockedIds}，
     * 供透视标记判断「模块是否正在工作」而不必再遍历一次模块表。
     */
    private static void addUniqueTracked(Map<Integer, LivingEntity> unique, Entity entity,
                                         boolean fromAura) {
        LivingEntity living = asLiving(entity);
        if (living == null) return;
        unique.putIfAbsent(living.getId(), living);
        if (fromAura) auraLockedIds.add(living.getId());
    }

    /**
     * 本帧被 aura 模块锁定的目标 id（用于透视标记的动态旋转）。
     *
     * <p>每帧在 {@link #resolveAll} 开头清空、解析时填充。
     */
    private static final java.util.Set<Integer> auraLockedIds = new java.util.HashSet<>();

    /** 某个实体本帧是否被 aura 模块锁定。 */
    public static boolean isAuraLocked(Entity entity) {
        return entity != null && auraLockedIds.contains(entity.getId());
    }

    /**
     * 兼容入口：不带时间步进（等价于 deltaTime = 0）。
     *
     * <p>注意这会让停留窗口永远不倒计时，只应在无法取得帧间隔时使用。
     */
    public static LivingEntity resolve() {
        return resolve(0f);
    }

    /** 当前是否处于「准星已移开但仍在停留显示」的阶段。 */
    public static boolean isLingering() {
        return crosshairEnabled && CROSSHAIR_LOCK.isLingering();
    }

    /** 模块关闭时清理锁定状态。 */
    public static void clearCrosshairLock() {
        CROSSHAIR_LOCK.clear();
    }

    /** 过滤掉已死亡/已移除的实体，并统一成 LivingEntity。 */
    private static LivingEntity asLiving(Entity entity) {
        if (entity instanceof LivingEntity living && living.isAlive() && !living.isRemoved()) {
            return living;
        }
        return null;
    }
}
