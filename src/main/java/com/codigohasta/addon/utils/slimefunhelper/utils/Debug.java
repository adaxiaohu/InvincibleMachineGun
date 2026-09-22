package com.codigohasta.addon.utils.slimefunhelper.utils;

import java.util.Arrays;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
public final class Debug {
    public static void sendPlayer(Text text) {
        var mc = MinecraftClient.getInstance();
        var player = mc.player;
        if (player == null) return;
        // Chat layout can upload glyph textures. It must not run on a packet thread.
        Runnable deliver = () -> {
            if (mc.player == player && mc.inGameHud != null) mc.inGameHud.getChatHud().addMessage(text);
        };
        if (mc.isOnThread()) deliver.run();
        else mc.execute(deliver);
    }
    public static void chat(Object... message) {
        var text = Text.empty();
        for (int i = 0; i < message.length; i++) {
            if (i > 0) text.append(" ");
            Text part = message[i] instanceof Text rich ? rich : Text.literal(String.valueOf(message[i]));
            text.append(com.codigohasta.addon.utils.slimefunhelper.SfTranslations.chatText(part));
        }
        sendPlayer(text);
    }
    public static void info(Object...message){com.codigohasta.addon.AddonTemplate.LOG.debug(Arrays.toString(message));}
    public static void debug(Object...message){info(message);}
public static void stackTrace(){new Exception("SlimefunHelper diagnostic").printStackTrace();}
}
