package com.codigohasta.addon.utils.notify;

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
