package com.codigohasta.addon.utils.bmw;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;

/**
 * BMWClient-nextgen 移植：Rotation 数据类
 *
 * 对应 LB 的 Rotation(yaw, pitch) 数据类。
 */
public class BMWRotation {

    public static final BMWRotation ZERO = new BMWRotation(0.0F, 0.0F);

    public final float yaw;
    public final float pitch;

    public BMWRotation(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public BMWRotation normalize() {
        return new BMWRotation(MathHelper.wrapDegrees(yaw), MathHelper.wrapDegrees(pitch));
    }

    public static BMWRotation ofPlayer() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return ZERO;
        return new BMWRotation(mc.player.getYaw(), mc.player.getPitch());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BMWRotation other)) return false;
        return Float.compare(other.yaw, yaw) == 0 && Float.compare(other.pitch, pitch) == 0;
    }

    @Override
    public int hashCode() {
        return 31 * Float.hashCode(yaw) + Float.hashCode(pitch);
    }

    @Override
    public String toString() {
        return "BMWRotation{yaw=" + yaw + ", pitch=" + pitch + "}";
    }
}
