package com.codigohasta.addon.utils.gcore;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/**
 * 实体位置扩展。
 *
 * <p>移植自 {@code dev.godden.core.Utils.EntityUtilKt}（GCore）。
 * 源是 Kotlin 扩展函数（{@code entity.getPrevPos(prev)}），Java 里改为静态方法，
 * 第一个参数是原来的接收者。
 *
 * <p><b>TP 锚的关键用法</b>：{@link #getPrevPos} 用来算「目标上一 tick 的位置」，
 * 抵消网络延迟 —— 服务端看到的目标位置比客户端晚，用它做攻击基准更准。
 */
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

    /**
     * 按速度外推实体位置。
     *
     * <p>{@code prev} 是外推系数：0 等于当前位置，1 等于上一 tick 位置。
     * 公式与源一致：
     * <pre>
     * pos + (pos - prevPos) * prev
     * </pre>
     */
    public static Vec3d getPrevPos(Entity entity, float prev) {
        return new Vec3d(
            entity.getX() + (entity.getX() - entity.lastX) * (double) prev,
            entity.getY() + (entity.getY() - entity.lastY) * (double) prev,
            entity.getZ() + (entity.getZ() - entity.lastZ) * (double) prev);
    }
}
