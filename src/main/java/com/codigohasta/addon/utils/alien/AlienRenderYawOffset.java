package com.codigohasta.addon.utils.alien;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;

/**
 * 渲染朝向平滑器 —— 逐行等价翻译 AlienV4
 * {@code RotationManager.getRenderYawOffset()}（{@code RotationManager.java:361-393}）。
 *
 * <h2>为什么需要它（这就是「生硬」的根因）</h2>
 * 直接把 {@code bodyYaw} 设成服务端 yaw，身体会<b>瞬间跳到目标角度</b>，
 * 看起来像瞬移/抽搐。
 *
 * <p>真实人体是「头先转，身体慢慢跟上」——Alien 用三段数学实现：
 * <pre>
 * // ① 身体朝向只跟随 30%（一阶低通滤波，这是「平滑」的核心）
 * result = offsetIn + wrapDegrees(result - offsetIn) * 0.3F;
 *
 * // ② 头与身体的最大夹角限制在 ±75°
 * float offset = wrapDegrees(yaw - result);
 * if (offset &lt; -75.0F) offset = -75.0F;
 * else if (offset &gt;= 75.0F) offset = 75.0F;
 *
 * // ③ 身体 = 目标 yaw - 头部偏移；夹角超过 50° 时身体再补 20%
 * result = yaw - offset;
 * if (offset * offset &gt; 2500.0F) result += offset * 0.2F;
 * </pre>
 *
 * <p>另外还有移动方向自动对齐（Alien:363-373）：
 * 当玩家在水平移动时，身体朝向会倾向「移动方向」而不是「视线方向」，
 * 且移动方向与视线夹角在 (95°, 265°) 之间时反向（倒着走）。
 */
public final class AlienRenderYawOffset {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    /** 上一帧的身体朝向（对应 Alien 的 {@code prevRenderYawOffset}）。 */
    private static float prevOffset = 0.0F;

    private AlienRenderYawOffset() {
    }

    /**
     * 计算本帧应该用的身体朝向。
     *
     * @param yaw      目标朝向（= 服务端 yaw = {@code fixYaw}）
     * @param offsetIn 上一帧的身体朝向
     */
    public static float compute(float yaw, float offsetIn) {
        if (mc.player == null) return yaw;

        float result = offsetIn;

        // ── Alien:363-373  移动方向优先 ──
        // ⚠️ 1.21.11 把 prevX/prevZ 改名为 lastX/lastZ（mappings class_1297 实测）
        double xDif = mc.player.getX() - mc.player.lastX;
        double zDif = mc.player.getZ() - mc.player.lastZ;
        if (xDif * xDif + zDif * zDif > 0.0025000002F) {
            // 移动方向角
            float offset = (float) MathHelper.atan2(zDif, xDif) * (180.0F / (float) Math.PI) - 90.0F;
            float wrap = MathHelper.abs(MathHelper.wrapDegrees(yaw) - offset);
            // 夹角在 (95°, 265°) 之间 ⇒ 玩家在「倒着走」，身体应反向
            if (95.0F < wrap && wrap < 265.0F) {
                result = offset - 180.0F;
            } else {
                result = offset;
            }
        }

        // ── Alien:375-377  挥手时身体直接对齐 ──
        if (mc.player.handSwingProgress > 0.0F) {
            result = yaw;
        }

        // ── Alien:379  ★ 核心：一阶低通，身体只跟 30% ──
        result = offsetIn + MathHelper.wrapDegrees(result - offsetIn) * 0.3F;

        // ── Alien:380-385  头与身体夹角限制 ±75° ──
        float offset = MathHelper.wrapDegrees(yaw - result);
        if (offset < -75.0F) {
            offset = -75.0F;
        } else if (offset >= 75.0F) {
            offset = 75.0F;
        }

        // ── Alien:387  身体 = 目标 - 头部偏移 ──
        result = yaw - offset;

        // ── Alien:388-390  夹角 > 50°（offset² > 2500）时身体再补 20% ──
        if (offset * offset > 2500.0F) {
            result += offset * 0.2F;
        }

        return result;
    }

    /** 取上一帧保存的身体朝向（首次返回 0）。 */
    public static float prevOffset() {
        return prevOffset;
    }

    /** 记录本帧的身体朝向，供下一帧的 {@code offsetIn} 使用。 */
    public static void save(float offset) {
        prevOffset = offset;
    }

    /** 复位（模块关闭时调用）。 */
    public static void reset() {
        prevOffset = 0.0F;
    }
}
