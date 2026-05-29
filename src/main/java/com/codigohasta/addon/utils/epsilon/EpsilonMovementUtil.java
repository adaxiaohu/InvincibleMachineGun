package com.codigohasta.addon.utils.epsilon;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

import java.lang.reflect.Field;

/**
 * 移植自 Epsilon 的 SendPositionEvent / MoveInputEvent 功能。
 * PlayerMoveC2SPacket 在 1.21.11 的 Yarn 映射中所有字段（x/y/z/yaw/pitch/onGround）
 * 都声明在父类 PlayerMoveC2SPacket 中（protected final），子类不自带字段。
 */
public class EpsilonMovementUtil {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    // 缓存父类 PlayerMoveC2SPacket 的字段引用（所有子类共用同一份字段）
    private static Field yField;
    private static Field onGroundField;

    static {
        try {
            for (Field f : PlayerMoveC2SPacket.class.getDeclaredFields()) {
                f.setAccessible(true);
                String name = f.getName();
                if (f.getType() == double.class && name.equals("y")) {
                    yField = f;
                } else if (f.getType() == boolean.class && name.equals("onGround")) {
                    onGroundField = f;
                }
            }
        } catch (Exception ignored) {}
    }

    // --- SendPositionEvent 等价方法 ---

    /** 等价于 Epsilon SendPositionEvent.setOnGround(boolean) */
    public static void setOnGround(PlayerMoveC2SPacket packet, boolean onGround) {
        if (onGroundField != null) {
            try { onGroundField.setBoolean(packet, onGround); } catch (Exception ignored) {}
        }
    }

    /** 等价于 Epsilon SendPositionEvent.getY() */
    public static double getY(PlayerMoveC2SPacket packet) {
        if (yField != null) {
            try { return yField.getDouble(packet); } catch (Exception ignored) {}
        }
        return 0.0;
    }

    /** 等价于 Epsilon SendPositionEvent.setY(double) */
    public static void setY(PlayerMoveC2SPacket packet, double y) {
        if (yField != null) {
            try { yField.setDouble(packet, y); } catch (Exception ignored) {}
        }
    }

    /** 等价于 Epsilon SendPositionEvent.setY(getY() + delta) */
    public static void shiftY(PlayerMoveC2SPacket packet, double delta) {
        setY(packet, getY(packet) + delta);
    }

    /** 检查包是否包含位置数据 */
    public static boolean hasPosition(PlayerMoveC2SPacket packet) {
        return packet instanceof PlayerMoveC2SPacket.PositionAndOnGround
            || packet instanceof PlayerMoveC2SPacket.Full;
    }

    /** 构造一个 Y+delta、onGround=true 的位置包，用于绕过 Grim */
    public static PlayerMoveC2SPacket createGrimPositionPacket(double delta) {
        assert mc.player != null;
        return new PlayerMoveC2SPacket.PositionAndOnGround(
            mc.player.getX(), mc.player.getY() + delta, mc.player.getZ(),
            true, mc.player.horizontalCollision);
    }

    // --- MoveInputEvent 等价方法 ---

    public static void setJump(boolean jump) {
        if (mc.player != null) {
            mc.options.jumpKey.setPressed(jump);
        }
    }
}
