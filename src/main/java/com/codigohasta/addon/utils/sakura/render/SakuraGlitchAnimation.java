package com.codigohasta.addon.utils.sakura.render;

/**
 * 「信号故障」风格的入场 / 出场动画控制器。
 *
 * <p>驱动一个 0~1 的「存在感」{@code presence} 与一个 0~1 的「故障强度」{@code glitch}：
 * <ul>
 *     <li><b>入场</b>：presence 0→1，故障强度 {@code 1-presence}，即一开始强烈干扰、
 *         逐渐稳定锁定 —— 类似显示器捕获到信号的过程</li>
 *     <li><b>出场</b>：presence 1→0，故障强度同样是 {@code 1-presence}，即画面在
 *         干扰越来越强的情况下溶解消失 —— 类似信号丢失</li>
 *     <li><b>待机</b>：目标稳定显示时，按随机间隔触发短促的干扰脉冲</li>
 * </ul>
 *
 * <p>这样做的好处是入场与出场共用同一条曲线，代码简单且观感连贯。
 *
 * <p>本类不涉及任何 GL 调用，纯数值状态机，可安全地在渲染线程每帧更新。
 */
public final class SakuraGlitchAnimation {

    /** 0~1，面板/标记的存在感，用作透明度与缩放。 */
    private float presence = 0f;
    /** 0~1，入场/出场包络产生的故障强度。 */
    private float envelopeGlitch = 0f;
    /** 0~1，待机随机脉冲产生的故障强度。 */
    private float idleGlitch = 0f;

    /** 待机脉冲剩余时间（秒）。 */
    private float idlePulseLeft = 0f;
    /** 距离下一次待机脉冲的剩余时间（秒）。 */
    private float idleCooldown = 0f;

    /** 累计运行时间（秒），传给着色器驱动随机行。 */
    private float time = 0f;

    private boolean targetPresent = false;

    /** 入场 / 出场动画时长（秒）。 */
    private float entryDuration = 0.35f;
    private float exitDuration = 0.30f;
    /** 待机脉冲的持续时间（秒）。 */
    private float idlePulseDuration = 0.14f;

    /**
     * 每帧更新。
     *
     * @param hasTarget  当前是否有目标
     * @param deltaTime  距上一帧的秒数
     */
    public void update(boolean hasTarget, float deltaTime) {
        float dt = Math.max(0f, Math.min(0.1f, deltaTime));
        time += dt;

        if (hasTarget && !targetPresent) {
            // 刚获得目标：从头播放入场
            presence = 0f;
        }
        targetPresent = hasTarget;

        float duration = hasTarget ? entryDuration : exitDuration;
        if (duration <= 0.0001f) {
            presence = hasTarget ? 1f : 0f;
        } else {
            float step = dt / duration;
            presence = hasTarget
                ? Math.min(1f, presence + step)
                : Math.max(0f, presence - step);
        }

        // 入场/出场共用的包络：presence 越小，干扰越强
        envelopeGlitch = 1f - easeOutCubic(presence);

        updateIdleGlitch(dt);
    }

    /** 待机脉冲：按随机间隔触发短促干扰。 */
    private void updateIdleGlitch(float dt) {
        if (idlePulseLeft > 0f) {
            idlePulseLeft -= dt;
            // 脉冲内部也做一次 0→1→0 的包络，避免生硬
            float t = 1f - Math.max(0f, idlePulseLeft) / idlePulseDuration;
            idleGlitch = (float) Math.sin(t * Math.PI);
            if (idlePulseLeft <= 0f) {
                idleGlitch = 0f;
                idleCooldown = nextIdleDelay();
            }
            return;
        }

        idleGlitch = 0f;

        if (!targetPresent || presence < 1f) {
            // 入场/出场期间不叠加待机脉冲，避免画面过乱
            idleCooldown = 0f;
            return;
        }

        idleCooldown -= dt;
        if (idleCooldown <= 0f) {
            idlePulseLeft = idlePulseDuration;
        }
    }

    /** 下一次待机脉冲的间隔（秒），带随机抖动。 */
    private float nextIdleDelay() {
        return idleInterval * (0.6f + (float) Math.random() * 0.8f);
    }

    private float idleInterval = 2.5f;

    private static float easeOutCubic(float t) {
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    // ─── 对外读取 ─────────────────────────────────────────

    /** 存在感 0~1：用于透明度与缩放。 */
    public float presence() {
        return presence;
    }

    /**
     * 「跳入」用的缩放系数，带轻微回弹过冲（0 → 略大于 1 → 1）。
     *
     * <p>透视标记用它做出现动画：先冲过头再落回，观感比线性放大更利落。
     * 出场时曲线反向，自然形成「跳出」。
     *
     * @param overshoot 过冲幅度，0 表示无过冲（等价于线性）
     */
    public float jumpScale(float overshoot) {
        float t = presence;
        if (t >= 1f) return 1f;
        if (t <= 0f) return 0f;

        // back-out 缓动
        float s = Math.max(0f, overshoot) * 1.7f;
        float inv = t - 1f;
        return 1f + (s + 1f) * inv * inv * inv + s * inv * inv;
    }

    /** 当前总故障强度 0~1（取包络与待机脉冲的较大者）。 */
    public float glitch() {
        return Math.max(envelopeGlitch, idleGlitch);
    }

    /** 累计时间（秒），驱动着色器里的随机行。 */
    public float time() {
        return time;
    }

    /** 是否还需要绘制（完全消失后可以跳过整个渲染流程）。 */
    public boolean isVisible() {
        return presence > 0.001f;
    }

    /** 立即复位（模块关闭时调用）。 */
    public void reset() {
        presence = 0f;
        envelopeGlitch = 0f;
        idleGlitch = 0f;
        idlePulseLeft = 0f;
        idleCooldown = 0f;
        targetPresent = false;
    }

    // ─── 参数设置（由模块的设置项驱动） ────────────────────

    public void configure(float entryDuration, float exitDuration,
                          float idleInterval, float idlePulseDuration) {
        this.entryDuration = Math.max(0f, entryDuration);
        this.exitDuration = Math.max(0f, exitDuration);
        this.idleInterval = Math.max(0.2f, idleInterval);
        this.idlePulseDuration = Math.max(0.02f, idlePulseDuration);
    }
}
