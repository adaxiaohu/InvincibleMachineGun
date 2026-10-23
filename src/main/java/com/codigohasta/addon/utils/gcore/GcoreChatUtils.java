package com.codigohasta.addon.utils.gcore;

import meteordevelopment.meteorclient.utils.player.ChatUtils;

public final class GcoreChatUtils {

    private GcoreChatUtils() {
    }

    public static void chat(String message) {
        ChatUtils.info(message);
    }
}
