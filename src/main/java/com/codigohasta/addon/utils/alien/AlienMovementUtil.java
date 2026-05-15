package com.codigohasta.addon.utils.alien;

import net.minecraft.client.MinecraftClient;

public class AlienMovementUtil {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static boolean isMoving() {
        if (mc.player == null) return false;
        return mc.player.input.playerInput.forward()
            || mc.player.input.playerInput.backward()
            || mc.player.input.playerInput.left()
            || mc.player.input.playerInput.right();
    }
}
