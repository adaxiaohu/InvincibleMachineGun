package com.codigohasta.addon.utils.notify;

import com.codigohasta.addon.utils.sakura.render.SakuraTextRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;

public final class ArmorAlert {

    public static final ArmorAlert INSTANCE = new ArmorAlert();

    private volatile boolean active = false;
    private boolean wasActive = false;
    public volatile String line1 = "";
    public volatile String line2 = "";

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
