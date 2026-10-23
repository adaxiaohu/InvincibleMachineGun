package com.codigohasta.addon.utils.sakura.render;

/**
 * 极简缓动动画（0→1），供面板出入场用。
 *
 * <p> CatLean 的弹出动画（{@code CatAnimation / CatEasing}，legacy 备份里有一套）：
 * 那套支持矩阵缩放绕中心；本项目的分批渲染器不支持逐项矩阵变换，
 * 所以这里用「进度直接驱动尺寸偏移 + alpha」近似同样的观感（见 CooldownsHud）。
 *
 * <p><b>用法</b>：持有实例 → 每帧 {@code update(dt)} 推进 → 读 {@code value()}。
 */
public final class CatProgress {

    /** 当前值 0~1。 */
    private float value;
    /** 目标值 0~1。 */
    private float target;
    /** 每秒变化速率（1/时长秒）。 */
    private final float speed;

    /** 剩余延迟（秒）：>0 时 update 只递减它、不推进 value。 */
    private float delay;

    public CatProgress(float durationMs) {
        this(durationMs, 0f);
    }

    /**
     * @param durationMs 走完 0→1 所需时长
     * @param delayMs    启动延迟。用于「逐个划入」的错开 ——
     *                   <b>必须用延迟，不能用「从最终进度里扣减」</b>：
     *                   扣减的话进度会永远停在扣掉后的值上（实测卡在 0.55 / 0.10）。
     */
    public CatProgress(float durationMs, float delayMs) {
        this.speed = durationMs <= 0f ? Float.MAX_VALUE : 1000f / durationMs;
        this.delay = Math.max(0f, delayMs / 1000f);
    }

    /** 推进动画。 */
    public void update(float deltaSeconds) {
        float dt = (deltaSeconds > 0f && deltaSeconds < 0.5f) ? deltaSeconds : 1f / 60f;
        if (delay > 0f) {
            delay -= dt;
            return;   // 延迟期间不推进进度
        }
        if (value < target) value = Math.min(target, value + dt * speed);
        else if (value > target) value = Math.max(target, value - dt * speed);
    }

    /** 设定目标（1 = 展开，0 = 收起）。 */
    public void animateTo(float t) {
        target = Math.max(0f, Math.min(1f, t));
    }

    /**
     * 重设启动延迟（秒）。
     *
     * <p>用于「入场正序、出场反序」的逐个划入/收回：同一行在入场时按
     * {@code i × 间隔} 延迟、出场时按 {@code (n−1−i) × 间隔} 延迟。
     */
    public void setDelay(float delaySeconds) {
        this.delay = Math.max(0f, delaySeconds);
    }

    /** 当前进度 0~1。 */
    public float value() {
        return value;
    }

    /** 是否已完全到位。 */
    public boolean settled() {
        return value == target;
    }

    /**
     * OUT_BACK 缓动：带过冲的「弹出」感。
     *
     * <p>CatLean 的 {@code fd(_s.OUT_BACK, ...)} 就是这一族
     * （{@code l1.java:90} 确证）。
     *
     * @param overshoot 过冲强度；标准公式取 {@code 1.70158}。
     *                  用户 2026-10-07 要求「更灵动、有活力」，
     *                  所以通知用 <b>2.6</b>（过冲峰值约 1.16，比标准更明显）。
     */
    public static float outBack(float t, float overshoot) {
        float x = Math.max(0f, Math.min(1f, t));
        final float c1 = overshoot;
        final float c3 = c1 + 1f;
        float xm1 = x - 1f;
        return 1f + c3 * xm1 * xm1 * xm1 + c1 * xm1 * xm1;
    }

    /** OUT_BACK（标准强度，照 CatLean 默认）。 */
    public static float outBack(float t) {
        return outBack(t, 1.70158f);
    }

    /** OUT_QUINT 缓动：快速展开后减速停下（划入用）。 */
    public static float outQuint(float t) {
        float x = Math.max(0f, Math.min(1f, t));
        float xm1 = x - 1f;
        return 1f + xm1 * xm1 * xm1 * xm1 * xm1;
    }

    /**
     * OUT_ELASTIC 缓动：带「弹几下」的收尾 —— 最适合「有活力」的弹出。
     *
     * <p>公式取自 easings.net 的标准实现：
     * <pre>
     *   f(x) = 2^(-10x) · sin((x·10 − 0.75)·(2π/3)) + 1
     * </pre>
     * 峰值可达 <b>1.25</b>（比 OUT_BACK 更弹），随后几次衰减振荡收敛到 1。
     *
     * <p>用在通知入场：先猛地弹大、再回弹两下稳住，观感最「活」。
     *
     * @param amplitude 振荡幅度（1 = 标准；越小越收敛）
     */
    public static float outElastic(float t, float amplitude) {
        float x = Math.max(0f, Math.min(1f, t));
        if (x <= 0f) return 0f;
        if (x >= 1f) return 1f;
        final float c4 = (float) (2.0 * Math.PI / 3.0);
        return (float) (Math.pow(2.0, -10.0 * x) * amplitude
            * Math.sin((x * 10.0 - 0.75) * c4) + 1.0);
    }

    /** OUT_ELASTIC（标准幅度）。 */
    public static float outElastic(float t) {
        return outElastic(t, 1.0f);
    }

    /**
     * IN_BACK 缓动：收回时带「吸进去」的预期感（出场用）。
     *
     * <p>与 OUT_BACK 镜像：先反向蓄力再快速缩回中心，
     * 比单纯线性缩小「有力」得多。
     */
    public static float inBack(float t, float overshoot) {
        float x = Math.max(0f, Math.min(1f, t));
        final float c1 = overshoot;
        final float c3 = c1 + 1f;
        return c3 * x * x * x - c1 * x * x;
    }

    /** IN_BACK（标准强度）。 */
    public static float inBack(float t) {
        return inBack(t, 1.70158f);
    }
}
