package com.codigohasta.addon.utils.alien;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

public class AlienMovementUtil {
    private static final Minecraft mc = Minecraft.getInstance();

    public static boolean isMoving() {
        if (mc.player == null) return false;
        return mc.player.input.keyPresses.forward()
            || mc.player.input.keyPresses.backward()
            || mc.player.input.keyPresses.left()
            || mc.player.input.keyPresses.right();
    }

    public static double getMotionX() {
        return mc.player.getDeltaMovement().x;
    }

    public static void setMotionX(double x) {
        mc.player.setDeltaMovement(x, mc.player.getDeltaMovement().y, mc.player.getDeltaMovement().z);
    }

    public static double getMotionY() {
        return mc.player.getDeltaMovement().y;
    }

    public static void setMotionY(double y) {
        mc.player.setDeltaMovement(mc.player.getDeltaMovement().x, y, mc.player.getDeltaMovement().z);
    }

    public static double getMotionZ() {
        return mc.player.getDeltaMovement().z;
    }

    public static void setMotionZ(double z) {
        mc.player.setDeltaMovement(mc.player.getDeltaMovement().x, mc.player.getDeltaMovement().y, z);
    }

    public static double[] directionSpeed(double speed) {
        if (mc.player == null) return new double[]{0.0, 0.0};

        float forward = mc.player.input.keyPresses.forward() ? 1.0f
            : (mc.player.input.keyPresses.backward() ? -1.0f : 0.0f);
        float side = mc.player.input.keyPresses.left() ? 1.0f
            : (mc.player.input.keyPresses.right() ? -1.0f : 0.0f);
        float yaw = mc.player.getYRot();

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
