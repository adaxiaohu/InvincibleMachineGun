package com.codigohasta.addon.utils.sakura.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 「准星锁定」解析器：带锥形捕获 + 粘滞保持 + 停留窗口的目标选择，<b>支持多目标</b>。
 *
 * <p>解决的问题：直接读 `crosshairTarget` 时，只有准星**精确压**在实体上才有目标，
 * 稍微移开就立刻丢失。这里改成三段式：
 *
 * <ol>
 *     <li><b>锥形捕获</b>：在准星周围一个可调角度（{@code coneAngle}）的圆锥内搜索，
 *         只要射线方向与「相机→实体」方向夹角在范围内即可命中，无需精确瞄准；</li>
 *     <li><b>粘滞保持</b>：一旦锁定某个目标，即使准星偏出锥体也继续显示，
 *         直到超出更大的 {@code stickiness} 角度上限；</li>
 *     <li><b>停留窗口</b>：目标完全脱离视野后，面板与锁定标记再保留 {@code lingerTime} 秒
 *         才播出场动画，不会立刻消失。</li>
 * </ol>
 *
 * <p><b>多目标是怎么做的</b>：不再只维护一个 {@code locked}，而是维护一张
 * {@link Tracked} 列表（每个条目带自己的停留倒计时），一次返回前 {@code maxTargets} 个。
 * 排序时给「已锁定的目标」一个分数折扣 {@code (1 - switchMargin)}，于是
 * 「新候选要明显更优才会顶掉旧目标」这一粘滞语义被完整保留 ——
 * 单目标时（{@code maxTargets = 1}）的行为与旧实现完全一致。
 *
 * <p>本类不涉及渲染，只做目标选择。
 */
public final class SakuraCrosshairLock {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    /** 一个被跟踪的目标及其停留倒计时。 */
    private static final class Tracked {
        final LivingEntity entity;
        float lingerLeft;

        Tracked(LivingEntity entity, float lingerLeft) {
            this.entity = entity;
            this.lingerLeft = lingerLeft;
        }
    }

    /** 当前锁定的目标（按优先级排序，第一个是主目标）。 */
    private final List<Tracked> tracked = new ArrayList<>();

    // ─── 可调参数 ─────────────────────────────────────────

    /** 捕获圆锥半角（度）。准星周围这个范围内都能锁定。 */
    private float coneAngle = 12f;
    /** 保持锁定允许的最大偏角（度）。应大于 coneAngle。 */
    private float stickiness = 25f;
    /** 最大锁定距离（格）。 */
    private float range = 32f;
    /** 目标脱离视野后仍保持显示的秒数。 */
    private float lingerTime = 2.0f;
    /** 切换到新目标所需的「优度」优势（0~1）。越大越不愿意切换。 */
    private float switchMargin = 0.25f;
    /** 是否只锁定玩家。 */
    private boolean playersOnly = false;
    /** 是否忽略旁观/创造模式玩家。 */
    private boolean ignoreSpectators = true;
    /** 是否要求目标可见（无方块遮挡）。 */
    private boolean requireVisible = false;

    // ─── 参数设置 ─────────────────────────────────────────

    public void configure(float coneAngle, float stickiness, float range, float lingerTime,
                          float switchMargin, boolean playersOnly,
                          boolean ignoreSpectators, boolean requireVisible) {
        this.coneAngle = Math.max(0f, coneAngle);
        // 保持角不得小于捕获角，否则一锁定就会立刻掉
        this.stickiness = Math.max(this.coneAngle, stickiness);
        this.range = Math.max(0.5f, range);
        this.lingerTime = Math.max(0f, lingerTime);
        this.switchMargin = Math.max(0f, Math.min(1f, switchMargin));
        this.playersOnly = playersOnly;
        this.ignoreSpectators = ignoreSpectators;
        this.requireVisible = requireVisible;
    }

    /**
     * 每帧调用，返回当前应显示的目标列表（可能来自停留窗口）。
     *
     * @param deltaTime  距上一帧的秒数
     * @param maxTargets 最多返回几个；{@code <= 0} 时按 1 处理
     * @return 按优先级排序的目标列表（可能为空）
     */
    public List<LivingEntity> update(float deltaTime, int maxTargets) {
        List<LivingEntity> result = new ArrayList<>();
        int limit = Math.max(1, maxTargets);

        if (mc.player == null || mc.world == null) {
            clear();
            return result;
        }

        float dt = Math.max(0f, Math.min(0.25f, deltaTime));

        // 1) 丢弃已失效的（死亡 / 被移除 / 超出距离）
        tracked.removeIf(t -> !isStillValid(t.entity));

        // 记录「本帧之前就已经锁定」的目标集合。粘滞折扣只给它们，
        // 本帧新加入的候选不享受折扣，否则新候选会立刻被自己打折而无法顶掉旧目标。
        List<LivingEntity> previouslyLocked = new ArrayList<>(tracked.size());
        for (Tracked t : tracked) previouslyLocked.add(t.entity);

        // 2) 视野（圆锥）内的候选，按分数升序（越小越好）
        List<LivingEntity> candidates = findCandidatesInCone();

        // 3) 已锁定的目标若仍在候选里 → 刷新停留；否则进入停留倒计时
        for (Tracked t : tracked) {
            if (contains(candidates, t.entity)) {
                t.lingerLeft = lingerTime;
            } else {
                t.lingerLeft -= dt;
            }
        }
        tracked.removeIf(t -> t.lingerLeft <= 0f);

        // 4) 把所有候选都纳入竞争（即使列表已满）。
        //    必须这样做：若已锁定目标只是脱离视野、仍在停留窗口内，而列表已满就不再
        //    纳入新候选，就会出现「视野里明明有更好的目标却永远切不过去」的回归。
        //    先全部纳入、再按分数裁到 limit，粘滞语义由折扣体现。
        for (LivingEntity candidate : candidates) {
            if (!containsEntity(tracked, candidate)) {
                tracked.add(new Tracked(candidate, lingerTime));
            }
        }

        // 5) 排序：帧前已锁定的目标享受 (1 - switchMargin) 折扣，从而「新候选要明显更优才顶得掉」。
        //    升序排列即优先级从高到低；单目标时等价于旧的 shouldSwitch 判定。
        tracked.sort(Comparator.comparingDouble(t -> effectiveScore(t.entity, previouslyLocked)));

        // 6) 裁到 limit：多出来的直接丢弃（它们会在下一帧重新作为候选竞争）
        while (tracked.size() > limit) {
            tracked.remove(tracked.size() - 1);
        }

        for (Tracked t : tracked) {
            result.add(t.entity);
        }
        return result;
    }

    /**
     * 兼容入口：单目标（{@code maxTargets = 1}）。
     *
     * <p>等价于旧版签名，方便只想取主目标时调用。
     */
    public LivingEntity update(float deltaTime) {
        List<LivingEntity> all = update(deltaTime, 1);
        return all.isEmpty() ? null : all.get(0);
    }

    /** 清除锁定并重置停留计时。 */
    public void clear() {
        tracked.clear();
    }

    /** 当前是否处于「已脱离视野、仍在停留显示」的阶段。 */
    public boolean isLingering() {
        for (Tracked t : tracked) {
            if (t.lingerLeft > 0f && t.lingerLeft < lingerTime) return true;
        }
        return false;
    }

    // ─── 内部实现 ─────────────────────────────────────────

    private boolean isStillValid(LivingEntity entity) {
        return entity.isAlive()
            && !entity.isRemoved()
            && entity != mc.player
            && mc.player.squaredDistanceTo(entity) <= (double) range * range;
    }

    private static boolean contains(List<LivingEntity> list, LivingEntity entity) {
        for (LivingEntity e : list) {
            if (e == entity) return true;
        }
        return false;
    }

    private static boolean containsEntity(List<Tracked> list, LivingEntity entity) {
        for (Tracked t : list) {
            if (t.entity == entity) return true;
        }
        return false;
    }

    /**
     * 在捕获圆锥内找出所有候选，按分数升序排列（越靠前越优先）。
     *
     * <p>评分综合「与准星的夹角」和「距离」：夹角越小越优先，同夹角下更近的优先。
     * 已锁定的目标放宽到 {@code stickiness}，其余用 {@code coneAngle}。
     */
    private List<LivingEntity> findCandidatesInCone() {
        List<LivingEntity> candidates = new ArrayList<>();
        if (mc.player == null || mc.world == null) return candidates;

        // 相机位置与朝向：第三人称下 getCameraPos 会落在相机上，比玩家眼睛更准确
        var camera = mc.gameRenderer.getCamera();
        Vec3d eye = camera.getCameraPos();
        Vec3d look = Vec3d.fromPolar(camera.getPitch(), camera.getYaw()).normalize();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (!passesFilter(living)) continue;

            // 用实体包围盒中心判断，比脚底位置更稳定
            Box box = living.getBoundingBox();
            Vec3d toEntity = box.getCenter().subtract(eye);

            double distance = toEntity.length();
            if (distance < 0.01 || distance > range) continue;

            double cos = toEntity.normalize().dotProduct(look);
            // 数值安全：避免浮点误差导致 acos 得到 NaN
            cos = Math.max(-1.0, Math.min(1.0, cos));
            double angle = Math.toDegrees(Math.acos(cos));

            // 已锁定的目标放宽到 stickiness，其余用 coneAngle
            double allowed = isTracked(living) ? stickiness : coneAngle;
            if (angle > allowed) continue;

            if (requireVisible && !mc.player.canSee(living)) continue;

            candidates.add(living);
        }

        candidates.sort(Comparator.comparingDouble(this::score));
        return candidates;
    }

    private boolean isTracked(LivingEntity entity) {
        return containsEntity(tracked, entity);
    }

    /**
     * 排序用分数：帧前已锁定的目标乘 {@code (1 - switchMargin)} 折扣，从而更难被顶掉。
     *
     * <p>与旧的 {@code shouldSwitch} 判定等价：只有当
     * {@code candidate < current * (1 - switchMargin)} 时，新候选才会排到前面。
     *
     * <p>折扣只给 {@code previouslyLocked} 里的目标：若对本帧刚加入的候选也打折，
     * 它们会自己把自己压低，反而顶不掉旧目标。
     */
    private double effectiveScore(LivingEntity entity, List<LivingEntity> previouslyLocked) {
        double s = score(entity);
        return contains(previouslyLocked, entity) ? s * (1.0 - switchMargin) : s;
    }

    /** 单个目标的评分（越小越好）：夹角为主，距离为辅。 */
    private double score(LivingEntity entity) {
        var camera = mc.gameRenderer.getCamera();
        Vec3d eye = camera.getCameraPos();
        Vec3d look = Vec3d.fromPolar(camera.getPitch(), camera.getYaw()).normalize();

        Vec3d toEntity = entity.getBoundingBox().getCenter().subtract(eye);
        double distance = toEntity.length();
        if (distance < 0.01) return 0;

        double cos = Math.max(-1.0, Math.min(1.0, toEntity.normalize().dotProduct(look)));
        double angle = Math.toDegrees(Math.acos(cos));

        return angle / Math.max(1.0, coneAngle) + (distance / range) * 0.35;
    }

    /** 目标过滤：排除自己、死亡、友军以及按设置应排除的类型。 */
    private boolean passesFilter(LivingEntity entity) {
        if (entity == mc.player) return false;
        if (!entity.isAlive() || entity.isRemoved()) return false;
        if (entity.isInvisibleTo(mc.player)) return false;

        if (playersOnly && !(entity instanceof PlayerEntity)) return false;

        if (ignoreSpectators && entity instanceof PlayerEntity player && player.isSpectator()) {
            return false;
        }

        return true;
    }
}
