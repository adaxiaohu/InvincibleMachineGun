package com.codigohasta.addon.mixin;

import com.codigohasta.addon.modules.AlienSprint;
import com.codigohasta.addon.utils.alien.AlienMovementUtil;
import com.codigohasta.addon.utils.alien.AlienRotationUtil;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public class MixinClientPlayerEntitySprint {

    @Inject(method = "sendPosition", at = @At("HEAD"))
    private void onSendMovementPacketsHead(CallbackInfo ci) {
        if (AlienRotationUtil.shouldRotate) {
            LocalPlayer player = (LocalPlayer) (Object) this;
            AlienRotationUtil.preYaw = player.getYRot();
            AlienRotationUtil.preBodyYaw = player.yBodyRot;
            AlienRotationUtil.preHeadYaw = player.yHeadRot;
            player.setYRot(AlienRotationUtil.sprintYaw);
            player.yBodyRot = AlienRotationUtil.sprintYaw;
            player.yHeadRot = AlienRotationUtil.sprintYaw;
            AlienRotationUtil.rotationYaw = AlienRotationUtil.sprintYaw;
            AlienRotationUtil.rotationPitch = player.getXRot();
        }
    }

    @Inject(method = "sendPosition", at = @At("TAIL"))
    private void onSendMovementPacketsTail(CallbackInfo ci) {
        if (AlienRotationUtil.shouldRotate) {
            LocalPlayer player = (LocalPlayer) (Object) this;
            player.setYRot(AlienRotationUtil.preYaw);
        }
    }

    // Prevent vanilla tickMovement from un-sprinting us when strafing (A/D) in Rotation mode
    @WrapWithCondition(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;setSprinting(Z)V", ordinal = 3))
    private boolean wrapStopSprinting(LocalPlayer instance, boolean b) {
        if (AlienSprint.INSTANCE != null && AlienSprint.INSTANCE.isActive()
            && AlienSprint.INSTANCE.mode.get() == AlienSprint.Mode.Rotation
            && AlienMovementUtil.isMoving()) {
            return false; // prevent setSprinting(false) — keep sprinting
        }
        return true; // allow normally
    }
}
