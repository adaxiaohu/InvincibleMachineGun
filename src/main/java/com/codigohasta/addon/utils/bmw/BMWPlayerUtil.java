package com.codigohasta.addon.utils.bmw;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;

/**
 * BMWClient-nextgen 移植的玩家工具 —— 完全独立。
 * 对应 LiquidBounce 中 EntityExtensions.kt 的功能。
 */
public class BMWPlayerUtil {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    /**
     * 使用 DirectionalInput 判断玩家是否在移动。
     */
    public static boolean isMoving() {
        return BMWDirectionalInput.fromPlayer().isMoving();
    }

    /**
     * 对应 LB 的 getMovementDirectionOfInput(facingYaw, DirectionalInput)。
     *
     * 根据玩家的 facingYaw 和 WASD 输入，计算移动方向对应的 yaw。
     * 精确匹配 LB EntityExtensions.kt:194-218 的逻辑：
     *
     *   W → yaw+0°,      W+A → yaw-45°,     W+D → yaw+45°
     *   S → yaw+180°,    S+A → yaw+135°,    S+D → yaw-135°
     *   A → yaw-90°,     D → yaw+90°
     */
    public static float getMovementDirectionOfInput(float facingYaw, BMWDirectionalInput input) {
        boolean forwards = input.forwards && !input.backwards;
        boolean backwards = input.backwards && !input.forwards;
        boolean left = input.left && !input.right;
        boolean right = input.right && !input.left;

        float actualYaw = facingYaw;
        float fwd = 1.0F;

        if (backwards) {
            actualYaw += 180.0F;
            fwd = -0.5F;
        } else if (forwards) {
            fwd = 0.5F;
        }

        if (left) {
            actualYaw -= 90.0F * fwd;
        }
        if (right) {
            actualYaw += 90.0F * fwd;
        }

        return MathHelper.wrapDegrees(actualYaw);
    }

    /**
     * 重载：自动从当前玩家创建 DirectionalInput。
     */
    public static float getMovementDirectionOfInput(float facingYaw) {
        return getMovementDirectionOfInput(facingYaw, BMWDirectionalInput.fromPlayer());
    }
}
