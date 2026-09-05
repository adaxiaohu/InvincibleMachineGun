package com.codigohasta.addon.utils.openmyau;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class ChatUtil {
    private static final Minecraft mc = Minecraft.getInstance();

    public static void send(Component text) {
        if (mc.player != null) {
            mc.player.sendSystemMessage(text);
        }
    }

    public static void sendFormatted(String string) {
        send(Component.literal(ChatColors.formatColor(string)));
    }

    public static void sendRaw(String string) {
        send(Component.literal(string));
    }

    public static void sendMessage(String string) {
        if (mc.player != null) {
            mc.player.connection.sendChat(string);
        }
    }
}
