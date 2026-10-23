package com.codigohasta.addon.utils.notify;

import com.codigohasta.addon.utils.sakura.render.SakuraTextRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;

/**
 * 低耐久护甲警报（<b>常驻横幅</b>，不是普通通知）。
 *
 * <h2>✅ 源码确证（CatLean {@code Notifier.O(DrawContext, bj, bj, ...)}）</h2>
 *
 * <p>用户 2026-10-07 指出「源 catlean 是一个单独的字的提示 + 一个盾牌图标
 * 在屏幕顶端驻留显示（不是我们现在这样的普通通知面板）」——
 * 源码确实是<b>独立绘制</b>：
 * <pre>
 *   f11 = max(行1宽, 行2宽);
 *   f10 = 屏幕宽/2 - f11/2 - 17.0f;                    // ★ 水平居中（左侧给图标留 17）
 *   n11 = (int)(System.currentTimeMillis() / 3.0 % N); // ★ 闪烁相位
 *   color3 = jl.y.C(RED, seed, YELLOW, n11 / 360.0f);   // ★ 红↔黄 循环插值
 *   b8.v(l14).e(drawContext, 行1, seed, f10,  8.0f, color3);   // 第 1 行 y=8
 *   b8.p(l12).e(drawContext, 行2, seed, f10, 20.0f, color3);   // 第 2 行 y=20
 *   bj4 = n11 > 阈值 ? 图标A : 图标B;                   // ★ 图标两态切换（= 感叹号闪烁）
 *   bj.p(bj4, mat, f10 + 5 + f11, 5.0, ...);            // 图标在文字右侧
 * </pre>
 *
 * <p><b>关键差异</b>：它<b>不进通知队列</b>、<b>不随通知滚动</b>，
 * 而是「条件满足时每帧画在屏幕顶端」→ 天然「驻留」。
 * 音效只在<b>刚跨过阈值</b>时播一次（对照用户要求）。
 *
 * <p>本类承载「是否警报中」的状态 + 闪烁相位计算，
 * 绘制由 {@link com.codigohasta.addon.modules.Notifier} 完成
 * （它才知道当前设置与玩家装备）。
 */
public final class ArmorAlert {

    public static final ArmorAlert INSTANCE = new ArmorAlert();

    /** 当前是否处于警报状态（由 Notifier 每 tick 更新）。 */
    private volatile boolean active = false;
    /** 上一次是否在警报（用于「只在跨过阈值时播一次音效」）。 */
    private boolean wasActive = false;

    /** 第 1 行文案（照源码：标题）。 */
    public volatile String line1 = "";
    /** 第 2 行文案。 */
    public volatile String line2 = "";

    /**
     * 横幅自身动画进度 0→1（入场弹出）/ 1→0（出场缩回）。
     *
     * <p>用户 2026-10-07：「Notifier 一并优化（动画）」——
     * 旧的横幅是「一出现就全尺寸」，很生硬；现在也做中心弹出/缩回。
     */
    private final com.codigohasta.addon.utils.sakura.render.CatProgress anim =
        new com.codigohasta.addon.utils.sakura.render.CatProgress(300f);

    private ArmorAlert() {}

    /** 推进横幅动画。 */
    public void updateAnim(float dt) {
        anim.animateTo(active ? 1f : 0f);
        anim.update(dt);
    }

    /** 当前横幅动画进度 0~1。 */
    public float anim() {
        return anim.value();
    }

    /**
     * 每 tick 由 Notifier 调用，更新警报状态。
     *
     * <p><b>⚠️ 离开警报时【不清空文案】</b>：出场动画（缩回中心）需要继续画文字；
     * 清空了就只能看到「凭空消失」。文字由 {@link #clearText()} 在动画结束后清。
     *
     * @param active 是否低于阈值
     * @param l1     第 1 行
     * @param l2     第 2 行
     * @return <b>是否刚刚进入警报</b>（true = 该播音效，且只播这一次）
     */
    public boolean update(boolean active, String l1, String l2) {
        this.active = active;
        if (active) {
            // 只在警报中才更新文案，出场期间保留最后一份
            this.line1 = l1 == null ? "" : l1;
            this.line2 = l2 == null ? "" : l2;
        }

        boolean justEntered = active && !wasActive;
        wasActive = active;
        return justEntered;
    }

    /** 清空文案（出场动画结束后调用）。 */
    public void clearText() {
        this.line1 = "";
        this.line2 = "";
    }

    /** 出场动画是否已结束（可清文案）。 */
    public boolean exitFinished() {
        return !active && anim.value() <= 0.001f;
    }

    public boolean isActive() {
        return active;
    }

    /** 复位（模块关闭时）。 */
    public void reset() {
        active = false;
        wasActive = false;
    }

    /**
     * 闪烁相位 0~1（照源码 {@code (ms / 3.0) % N} 的循环）。
     *
     * <p>源码用 {@code n11 / 360.0f} 作为两色插值参数。
     *
     * <p>用户 2026-10-07：「闪烁的慢一些」+「颜色要可自定义」——
     * 所以周期与两端颜色都改为设置项（这里只负责按给定周期算相位）。
     *
     * @param periodMs 一个来回的毫秒数（越大越慢）
     */
    public float blinkPhase(double periodMs) {
        double p = periodMs <= 0 ? 1200.0 : periodMs;
        double t = (System.currentTimeMillis() % p) / p;
        // 三角波：0→1→0（往返，不是锯齿）
        return (float) (t < 0.5 ? t * 2.0 : (1.0 - t) * 2.0);
    }

    /** 闪烁是否处于前半（用于图标两态切换）。 */
    public boolean blinkOn(double periodMs) {
        return blinkPhase(periodMs) < 0.5f;
    }

    /** 某件装备的耐久百分比（0~100）；不可损坏返回 100。 */
    public static int durabilityPercent(ItemStack st) {
        if (st == null || st.isEmpty() || !st.isDamageable()) return 100;
        int max = st.getMaxDamage();
        if (max <= 0) return 100;
        return Math.max(0, (max - st.getDamage()) * 100 / max);
    }

    /** 便捷：给绘制方用的 MC 实例。 */
    public static MinecraftClient mc() {
        return MinecraftClient.getInstance();
    }

    /** 画一段文字（封装，避免调用方到处引 SakuraTextRenderer）。 */
    public static void drawText(String text, float x, float y, float size, int argb) {
        SakuraTextRenderer.draw(text, x, y, size, argb);
    }

    /** 量宽。 */
    public static float textWidth(String text, float size) {
        return (float) SakuraTextRenderer.getWidth(text, size);
    }
}
