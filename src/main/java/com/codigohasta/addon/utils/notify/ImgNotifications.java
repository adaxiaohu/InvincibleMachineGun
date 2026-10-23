package com.codigohasta.addon.utils.notify;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class ImgNotifications {

    /** 全局单例。 */
    public static final ImgNotifications INSTANCE = new ImgNotifications();

    private final Deque<ImgNotification> queue = new ArrayDeque<>();

    /** 队列上限（超过则丢最旧）。 */
    private static final int MAX = 20;

    private ImgNotifications() {}

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
