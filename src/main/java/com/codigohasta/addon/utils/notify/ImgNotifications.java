package com.codigohasta.addon.utils.notify;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 通知队列（全局单例）。
 *
 * <p>对应 CatLean {@code sg} 的数据部分：
 * <pre>
 *   private static final List o;      // 队列
 *   private static boolean w;         // 开关
 *   private static int O;             // 上限
 * </pre>
 * 以及 {@code sg.C(Render2DEvent)} 里的：
 * <pre>
 *   if (o.size() > 上限) o.removeFirst();        // 超出丢最旧
 *   for (l1 l : 副本(o)) { M(l, ...); }          // 逐条绘制
 *   o.removeIf(sg::K);                            // 移除过期
 * </pre>
 *
 * <p><b>为什么队列放 utils/</b>：命令（{@code img-notify}）与模块都要访问它；
 * 且 {@code check_i18n} 会把 {@code modules/} 下每个 .java 当模块要求翻译条目。
 *
 * <p>本类<b>只管数据</b>（入队/推进/清理），绘制由
 * {@link com.codigohasta.addon.modules.NotificationsWidget} 负责 ——
 * 与该模块的设置（位置/缩放等）解耦。
 */
public final class ImgNotifications {

    /** 全局单例。 */
    public static final ImgNotifications INSTANCE = new ImgNotifications();

    /** 队列（照源码用「先进先出」，超出上限丢最旧）。 */
    private final Deque<ImgNotification> queue = new ArrayDeque<>();

    /** 队列上限（超过则丢最旧）。 */
    private static final int MAX = 20;

    private ImgNotifications() {}

    /**
     * 发一条通知。
     *
     * <p><b>音效</b>（✅ 照 CatLean {@code sg.q} + {@code sw.java} 的映射表）：
     * 只有 {@code ENABLED} / {@code DISABLED} 两种类型会自动播音效
     * （{@code enable.ogg} / {@code disable.ogg}）——
     * 其余类型（SUCCESS/INFO/WARNING/ERROR）在源码里<b>没有</b>音效分支，
     * 由各自的功能点自己播（如低甲播 {@code armor-alert}）。
     *
     * @param title   标题（可空）
     * @param content 内容
     * @param type    类型
     * @param icon    图标物品（可空）
     * @param seconds 显示秒数
     */
    public synchronized void post(String title, String content, ImgNotificationType type,
                                  net.minecraft.item.ItemStack icon, int seconds) {
        post(title, content, type, icon, null, seconds);
    }

    /**
     * 发一条通知（带 sprite 图标）。
     *
     * <p>用户 2026-10-07：「获得/失去药水的时候，图标能不能弄成和药水 HUD 一样
     * 的那种对应的药水的图标材质」—— 走 {@code spritePath}（GUI 图集路径）。
     */
    public synchronized void post(String title, String content, ImgNotificationType type,
                                  net.minecraft.item.ItemStack icon, String spritePath,
                                  int seconds) {
        queue.addLast(new ImgNotification(title, content, type, icon, spritePath, seconds));
        while (queue.size() > MAX) queue.removeFirst();

        // 照 CatLean：只有开关类通知有音效
        NotifySounds.playForType(type, soundVolume);
    }

    /** 音效音量（0~1）；由 NotificationsWidget 同步。 */
    private volatile float soundVolume = 1.0f;

    /** 设置音效音量（供模块同步设置值）。 */
    public void setSoundVolume(float v) {
        soundVolume = Math.max(0f, Math.min(1f, v));
    }

    /**
     * 默认驻留秒数（由 NotificationsWidget 的设置同步）。
     *
     * <p>用户 2026-10-07：「模块开关的面板持续驻留时间要可调，现在显示的时间有点长」。
     */
    public volatile int defaultSeconds = 3;

    /** 便捷重载：无图标。 */
    public void post(String title, String content, ImgNotificationType type, int seconds) {
        post(title, content, type, null, null, seconds);
    }

    /**
     * 取当前队列的一个<b>快照</b>用于绘制。
     *
     * <p>照源码 {@code Lists.newArrayList(o)} —— 绘制期间可能有过期移除，
     * 用快照避免并发修改。
     */
    public synchronized List<ImgNotification> snapshot() {
        return new ArrayList<>(queue);
    }

    /**
     * 每帧推进所有条目的动画，并移除已结束的。
     *
     * @param dt 本帧时长（秒）
     */
    public synchronized void update(float dt) {
        for (ImgNotification n : queue) n.update(dt);
        queue.removeIf(ImgNotification::isDead);
    }

    /** 清空（例如模块关闭时）。 */
    public synchronized void clear() {
        queue.clear();
    }

    /** 当前条数。 */
    public synchronized int size() {
        return queue.size();
    }

    /** 是否为空。 */
    public synchronized boolean isEmpty() {
        return queue.isEmpty();
    }
}
