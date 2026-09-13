package com.codigohasta.addon.utils.bmw;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * BMWClient-nextgen 移植：RotationManager（简化版）。
 *
 * 对应 LB 的 RotationManager.kt，功能：
 * 1. 持有 targetRotation（由模块调用 setRotationTarget 设置）
 * 2. update() 每 tick 调用，在 CHANGE_LOOK 模式下直接修改 player.yaw/pitch
 * 3. SILENT 模式下仅记录目标，由 MixinBMWClientPlayerEntity 在 sendMovementPackets 中应用
 *
 * 对应 LB 的 ClientPlayerEntity.setRotation(rotation) 扩展函数。
 */
public class BMWRotationManager {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    /** 当前旋转目标 */
    public static BMWRotation targetRotation = null;

    /** SILENT=false, CHANGE_LOOK=true */
    public static boolean changeLook = false;

    /**
     * 对应 LB: RotationManager.setRotationTarget()
     */
    public static void setRotationTarget(BMWRotation rotation, boolean changeLookMode) {
        targetRotation = rotation;
        changeLook = changeLookMode;
    }

    /**
     * 清除旋转目标
     */
    public static void clearTarget() {
        targetRotation = null;
        changeLook = false;
    }

    /**
     * 对应 LB: RotationManager.update()
     *
     * 每 tick 调用（由 BMWSprint 在 TickEvent.Pre 驱动）。
     * CHANGE_LOOK 时直接修改 player.yaw/pitch。
     */
    public static void update() {
        if (targetRotation == null) return;
        if (mc.player == null) return;

        if (changeLook) {
            // 对应 LB: player.setRotation(rotation)
            setPlayerRotation(mc.player, targetRotation);
        }
        // SILENT: 只记录目标，由 MixinBMWClientPlayerEntity 在 sendMovementPackets 中应用
    }

    /**
     * 对应 LB ClientPlayerEntity.setRotation(rotation) 扩展函数。
     *
     * 直接修改 player.yaw、player.pitch，并同步 prev/render/lastRender yaw。
     */
    public static void setPlayerRotation(ClientPlayerEntity player, BMWRotation rotation) {
        BMWRotation normalized = rotation.normalize();
        player.setYaw(normalized.yaw);
        player.setPitch(normalized.pitch);
    }
}
