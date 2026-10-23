package com.codigohasta.addon.utils.alien;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;

public class AlienMovementUtil {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static boolean isMoving() {
        if (mc.player == null) return false;
        return mc.player.input.playerInput.forward()
            || mc.player.input.playerInput.backward()
            || mc.player.input.playerInput.left()
            || mc.player.input.playerInput.right();
    }

    /**
     * ★ 读**真实按键**，不读 {@code player.input.playerInput}。
     *
     * <p>为什么需要它：{@code KeyboardInput.tick()} 在 {@code aiStep()} 里才刷新
     * {@code playerInput}，而 {@code TickEvent.Pre} 在 {@code MinecraftClient.tick()}
     * 的 HEAD（更早）就发布了。所以在 {@code Pre} 里读 {@code playerInput}
     * 拿到的是<b>上一 tick</b> 的值 —— 表现为：
     * <ul>
     *   <li>按键松开的那一 tick 仍判「在动」⇒ 多发一个 offset 的 yaw 包</li>
     *   <li>按下那一 tick 判「没动」⇒ 漏发</li>
     * </ul>
     * 两者都会让相邻包之间的 yaw 跳变（realYaw ↔ fixYaw 交替），
     * 既让移动方向错乱，也会触发 Grim {@code AimModulo360}
     * （它判 {@code |deltaYaw| > 320}，见 {@code AimModulo360.java:31}）。
     *
     * <p>{@link #isMoving()} 保留原语义给 ElytraFly 等模块用，不改动它们的行为。
     */
    public static boolean isMovingRaw() {
        if (mc.player == null) return false;
        return mc.options.forwardKey.isPressed()
            || mc.options.backKey.isPressed()
            || mc.options.leftKey.isPressed()
            || mc.options.rightKey.isPressed();
    }

    public static double getMotionX() {
        return mc.player.getVelocity().x;
    }

    public static void setMotionX(double x) {
        mc.player.setVelocity(x, mc.player.getVelocity().y, mc.player.getVelocity().z);
    }

    public static double getMotionY() {
        return mc.player.getVelocity().y;
    }

    public static void setMotionY(double y) {
        mc.player.setVelocity(mc.player.getVelocity().x, y, mc.player.getVelocity().z);
    }

    public static double getMotionZ() {
        return mc.player.getVelocity().z;
    }

    public static void setMotionZ(double z) {
        mc.player.setVelocity(mc.player.getVelocity().x, mc.player.getVelocity().y, z);
    }

    public static double[] directionSpeed(double speed) {
        if (mc.player == null) return new double[]{0.0, 0.0};

        float forward = mc.player.input.playerInput.forward() ? 1.0f
            : (mc.player.input.playerInput.backward() ? -1.0f : 0.0f);
        float side = mc.player.input.playerInput.left() ? 1.0f
            : (mc.player.input.playerInput.right() ? -1.0f : 0.0f);
        float yaw = mc.player.getYaw();

        if (forward == 0.0f && side == 0.0f) return new double[]{0.0, 0.0};

        if (forward != 0.0f) {
            if (side > 0.0f) yaw += (forward > 0.0f ? -45 : 45);
            else if (side < 0.0f) yaw += (forward > 0.0f ? 45 : -45);
            side = 0.0f;
            if (forward > 0.0f) forward = 1.0f;
            else if (forward < 0.0f) forward = -1.0f;
        }

        double sin = Math.sin(Math.toRadians(yaw + 90.0f));
        double cos = Math.cos(Math.toRadians(yaw + 90.0f));
        double posX = forward * speed * cos + side * speed * sin;
        double posZ = forward * speed * sin - side * speed * cos;
        return new double[]{posX, posZ};
    }
}
