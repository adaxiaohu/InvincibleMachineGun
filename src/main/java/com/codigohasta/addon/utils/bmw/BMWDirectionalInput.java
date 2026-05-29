package com.codigohasta.addon.utils.bmw;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.PlayerInput;

/**
 * BMWClient-nextgen 移植：DirectionalInput.kt
 *
 * 封装玩家 WASD 输入，提供 isMoving 判断和预置方向常量。
 */
public class BMWDirectionalInput {

    public static final BMWDirectionalInput NONE = new BMWDirectionalInput(false, false, false, false);
    public static final BMWDirectionalInput FORWARDS = new BMWDirectionalInput(true, false, false, false);
    public static final BMWDirectionalInput BACKWARDS = new BMWDirectionalInput(false, true, false, false);
    public static final BMWDirectionalInput LEFT = new BMWDirectionalInput(false, false, true, false);
    public static final BMWDirectionalInput RIGHT = new BMWDirectionalInput(false, false, false, true);

    public final boolean forwards;
    public final boolean backwards;
    public final boolean left;
    public final boolean right;

    public BMWDirectionalInput(boolean forwards, boolean backwards, boolean left, boolean right) {
        this.forwards = forwards;
        this.backwards = backwards;
        this.left = left;
        this.right = right;
    }

    public BMWDirectionalInput(PlayerInput input) {
        this(input.forward(), input.backward(), input.left(), input.right());
    }

    public boolean isMoving() {
        return (forwards && !backwards)
            || (backwards && !forwards)
            || (left && !right)
            || (right && !left);
    }

    public static BMWDirectionalInput fromPlayer() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return NONE;
        return new BMWDirectionalInput(mc.player.input.playerInput);
    }
}
