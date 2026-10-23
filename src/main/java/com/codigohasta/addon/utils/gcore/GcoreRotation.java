package com.codigohasta.addon.utils.gcore;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * 视角朝向（yaw / pitch），替代 LiquidBounce 的 {@code utils.aiming.Rotation}。
 *
 * <p>GCore 的 {@code MathUtil.getStraightVec(Vec3d, Rotation, double)} 用到它。
 * 源类来自 LiquidBounce，字段与 {@code getRotationVec()} 的算法与 MC 原版
 * {@code Entity.getRotationVec(float)} 完全一致 —— 这里按 MC 原版公式重写，
 * 保证与源行为等价。
 *
 * <p>TP 锚当前不调用这个方法，但移植进来保持闭包完整（将来弓/水晶模块会用）。
 */
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
