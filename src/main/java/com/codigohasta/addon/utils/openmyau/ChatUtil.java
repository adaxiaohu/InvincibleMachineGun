package com.codigohasta.addon.utils.openmyau;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class ChatUtil {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static void send(Text text) {
        if (mc.player != null) {
            mc.player.sendMessage(text, false);
        }
    }

    public static void sendFormatted(String string) {
        send(Text.literal(ChatColors.formatColor(string)));
    }

    public static void sendRaw(String string) {
        send(Text.literal(string));
    }

    public static void sendMessage(String string) {
        if (mc.player != null) {
            mc.player.networkHandler.sendChatMessage(string);
        }
    }
}
