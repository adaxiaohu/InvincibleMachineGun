package com.codigohasta.addon.utils.bmw;

/**
 * BMWClient-nextgen 移植的旋转工具 —— 完全独立于 AlienRotationUtil。
 * 由 BMWSprint 的 Omnirotational 模式写入，由 MixinBMWClientPlayerEntity 消费。
 */
public class BMWRotationUtil {
    public static boolean shouldRotate = false;
    public static float sprintYaw = 0.0F;
    public static float preYaw = 0.0F;
    public static float preBodyYaw = 0.0F;
    public static float preHeadYaw = 0.0F;
}
