package com.codigohasta.addon.utils.notify;

/**
 * 通知输出方式（对应 CatLean 的 {@code cm} 枚举）。
 *
 * <p>✅ evidence 确证的常量（{@code su.catlean.cm}）：
 * <pre>
 *   cm[] cmArray = new cm[]{ OFF, CHAT, NOTIFICATION, BOTH };
 * </pre>
 *
 * <p>用户 2026-10-07 截图里的三态控件（{@code Off / Chat / Both}）
 * 就是这一套 —— 另有独立的 {@code Notification} 开关，
 * 在源码里对应 {@code NOTIFICATION} 这个值。
 *
 * <p>本项目用 Meteor 的 {@code EnumSetting} 承载
 * （用户确认：「先用 EnumSetting」，外观与 CatLean 的三按钮不同但功能等价）。
 */
public enum ChatMode {
    /** 不提示。 */
    OFF("关闭"),
    /** 只在聊天栏提示。 */
    CHAT("聊天"),
    /** 只用通知 HUD 提示。 */
    NOTIFICATION("通知"),
    /** 两者都提示。 */
    BOTH("两者");

    /** 中文显示名。 */
    public final String label;

    ChatMode(String label) {
        this.label = label;
    }

    /** 是否要发通知 HUD。 */
    public boolean wantsNotification() {
        return this == NOTIFICATION || this == BOTH;
    }

    /** 是否要发聊天栏消息。 */
    public boolean wantsChat() {
        return this == CHAT || this == BOTH;
    }

    @Override
    public String toString() {
        return label;
    }
}
