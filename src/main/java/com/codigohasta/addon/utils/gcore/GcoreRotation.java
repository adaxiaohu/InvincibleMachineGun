package com.codigohasta.addon.utils.gcore;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class GcoreRotation {

    public final float yaw;
    public final float pitch;

    public GcoreRotation(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    /**
     * 由 yaw / pitch 算出单位方向向量。
     *
     * <p>与 MC 原版 {@code Entity.getRotationVec} 相同（Y 取负是因为 MC 的
     * pitch 正值朝下）。
     */
    public Vec3d getRotationVec() {
        float f = this.pitch * 0.017453292F;
        float g = -this.yaw * 0.017453292F;
        float h = MathHelper.cos(g);
        float i = MathHelper.sin(g);
        float j = MathHelper.cos(f);
        float k = MathHelper.sin(f);
        return new Vec3d(i * j, -k, h * j);
    }

    @Override
    public String toString() {
        return "Rotation(" + this.yaw + ", " + this.pitch + ")";
    }
}
