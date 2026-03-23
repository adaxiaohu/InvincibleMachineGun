package com.codigohasta.addon.utils;

import java.util.ArrayList;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.util.math.MathHelper;

public class CamUtils {
    public static final ArrayList<Module> inUse = new ArrayList<>();
    public static float yaw = 0.0F;
    public static float pitch = 0.0F;
    public static float prevYaw = 0.0F;
    public static float prevPitch = 0.0F;

    public static void changeLookDirection(double deltaX, double deltaY) {
        prevYaw = yaw;
        prevPitch = pitch;
        yaw = (float) ((double) yaw + deltaX);
        pitch = (float) ((double) pitch + deltaY);
        pitch = MathHelper.clamp(pitch, -90.0F, 90.0F);
    }

    public static void add(Module m) {
        if (!inUse.contains(m)) inUse.add(m);
    }

    public static void rem(Module m) {
        inUse.remove(m);
    }

    public static boolean isUsing() {
        return !inUse.isEmpty();
    }

    public static double getYaw(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevYaw, yaw);
    }

    public static double getPitch(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevPitch, pitch);
    }

    public static float pitch() {
        return pitch;
    }
}