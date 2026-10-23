package com.codigohasta.addon.utils.gcore;

import meteordevelopment.meteorclient.utils.player.ChatUtils;

/**
 * 聊天提示。
 *
 * <p>对应 GCore 里 {@code net.ccbluex.liquidbounce.utils.client.ClientUtilsKt.chat(...)}
 * 的调用点（目前只有 TP 包数超限提示一处）。
 *
 * <p>适配为 Meteor 的 {@link ChatUtils}，并保留源里相同的文案与颜色码。
 */
public final class GcoreChatUtils {

    private GcoreChatUtils() {
    }

    /** 发送一条聊天消息（保留源里的 {@code §7} 灰色前缀）。 */
    public static void chat(String message) {
        ChatUtils.info(message);
    }
}
