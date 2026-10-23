package com.codigohasta.addon.utils.notify;

import net.minecraft.item.ItemStack;

public final class ImgNotification {

    /** 标题（可空）。 */
    public final String title;
    /** 内容。 */
    public final String content;
    /** 类型（决定图标与配色）。 */
    public final ImgNotificationType type;
    /** 图标物品；null = 用 sprite 或类型图标。 */
    public final ItemStack icon;

    /**
     * 图标 sprite 路径（GUI 图集内的路径，如 {@code mob_effect/speed}）。
     *
     * <p>用户 2026-10-07：「获得/失去药水的时候，图标能不能弄成和药水 HUD 一样
     * 的那种对应的药水的图标材质」——
     * 药水效果在 GUI 图集里的图标是 {@code mob_effect/<id>}（不是 ItemStack），
     * 所以通知需要支持这一路图标（照 {@code PotionsHud} 的做法）。
     */
    public final String spritePath;

    /** 总时长（毫秒）。 */
    public final long durationMs;
    /** 创建时刻（毫秒，系统时钟）。 */
    public final long createdAt;

    /**
     * 弹出/收回动画（0 = 完全收起，1 = 完全展开）。
     *
     * <p>入场用 OUT_BACK（带过冲的「弹」），出场同理收回。
     */
    public final com.codigohasta.addon.utils.sakura.render.CatProgress anim;

    public ImgNotification(String title, String content, ImgNotificationType type,
                           ItemStack icon, int seconds) {
        this(title, content, type, icon, null, seconds);
    }

    public ImgNotification(String title, String content, ImgNotificationType type,
                           ItemStack icon, String spritePath, int seconds) {
        this.title = title == null ? "" : title;
        this.content = content == null ? "" : content;
        this.type = type == null ? ImgNotificationType.INFO : type;
        this.icon = (icon == null || icon.isEmpty()) ? null : icon;
        this.spritePath = spritePath;
        this.durationMs = Math.max(1, seconds) * 1000L;
        this.createdAt = System.currentTimeMillis();
        // 入场时长。
        //   用户 2026-10-07 要求「更灵动、有活力」且「动画速度要可自定义可调」——
        //   时长由 NotificationsWidget 的设置写进静态字段（见 ANIM_MS）。
        this.anim = new com.codigohasta.addon.utils.sakura.render.CatProgress(ANIM_MS);
    }

    /**
     * 入场动画时长（毫秒）——<b>由 NotificationsWidget 的设置每帧同步</b>。
     *
     * <p>用户 2026-10-07：「动画速度要可自定义可调」。
     * 放静态字段是因为通知在 {@code post()} 时就要构造动画对象，
     * 那一刻拿不到模块设置（且每条通知各自持有进度）。
     */
    public static volatile float ANIM_MS = 340f;

    /**
     * 出场动画时长（毫秒）——同上，由设置同步。
     */
    public static volatile float EXIT_MS = 260f;

    public float exitProgress() {
        long remain = durationMs - ageMs();
        float ex = EXIT_MS <= 0f ? 1f : EXIT_MS;
        if (remain >= ex) return 1f;
        return Math.max(0f, remain / ex);
    }

    /** 已存活时长（毫秒）。 */
    public long ageMs() {
        return System.currentTimeMillis() - createdAt;
    }

    /** 剩余比例 1→0；未过期为 1。 */
    public float remainingRatio() {
        float r = 1f - (float) ageMs() / (float) durationMs;
        return Math.max(0f, Math.min(1f, r));
    }

    /** 是否应当移除（已过期且收回动画结束）。 */
    public boolean isDead() {
        return remainingRatio() <= 0f && anim.value() <= 0.001f;
    }

    /** 每帧推进动画：入场到 1；剩余时间不足时开始收回。 */
    public void update(float dt) {
        boolean alive = remainingRatio() > 0f;
        anim.animateTo(alive ? 1f : 0f);
        anim.update(dt);
    }
}
