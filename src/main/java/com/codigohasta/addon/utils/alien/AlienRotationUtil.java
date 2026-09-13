package com.codigohasta.addon.utils.alien;

public class AlienRotationUtil {
    public static float rotationYaw = 0.0F;
    public static float rotationPitch = 0.0F;

    // Sprint rotation — set by AlienSprint, consumed by sendMovementPackets mixin
    public static boolean shouldRotate = false;
    public static float sprintYaw = 0.0F;
    public static float preYaw = 0.0F;
    public static float preBodyYaw = 0.0F;
    public static float preHeadYaw = 0.0F;

}
