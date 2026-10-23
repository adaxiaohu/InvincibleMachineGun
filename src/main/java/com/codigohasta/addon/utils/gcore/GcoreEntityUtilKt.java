package com.codigohasta.addon.utils.gcore;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

public final class GcoreEntityUtilKt {

    private GcoreEntityUtilKt() {
    }

    /**
     * 实体当前 tick 相对上一 tick 的位移长度。
     *
     * <p>版本差异：1.20.4 的 {@code prevX/prevY/prevZ} 在 1.21.11 改名为
     * {@code lastX/lastY/lastZ}（{@code field_6014/6036/5969}），语义相同
     * —— 都是「上一 tick 的位置」。
     */
    public static double getPosSpeed(Entity entity) {
        return Math.sqrt(
            Math.pow(entity.lastX - entity.getX(), 2)
                + Math.pow(entity.lastY - entity.getY(), 2)
                + Math.pow(entity.lastZ - entity.getZ(), 2));
    }

    public static Vec3d getPrevPos(Entity entity, float prev) {
        return new Vec3d(
            entity.getX() + (entity.getX() - entity.lastX) * (double) prev,
            entity.getY() + (entity.getY() - entity.lastY) * (double) prev,
            entity.getZ() + (entity.getZ() - entity.lastZ) * (double) prev);
    }
}
