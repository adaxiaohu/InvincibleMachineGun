package com.codigohasta.addon.utils.slimefunhelper.chatcraft;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
public final class ChatCraftKeys {
    private static boolean down(int key){return InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow(),key);}
    public static boolean ctrl(){return down(GLFW.GLFW_KEY_LEFT_CONTROL)||down(GLFW.GLFW_KEY_RIGHT_CONTROL);}
    public static boolean shift(){return down(GLFW.GLFW_KEY_LEFT_SHIFT)||down(GLFW.GLFW_KEY_RIGHT_SHIFT);}
}
