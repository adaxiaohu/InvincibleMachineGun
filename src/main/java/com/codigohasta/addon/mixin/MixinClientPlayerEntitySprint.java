package com.codigohasta.addon.mixin;

import com.codigohasta.addon.modules.AlienSprint;
import com.codigohasta.addon.utils.alien.AlienMovementUtil;
import com.codigohasta.addon.utils.alien.AlienRotationUtil;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public class MixinClientPlayerEntitySprint {

    @Inject(method = "sendMovementPackets", at = @At("HEAD"))
    private void onSendMovementPacketsHead(CallbackInfo ci) {
        if (AlienRotationUtil.shouldRotate) {
            ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
            AlienRotationUtil.preYaw = player.getYaw();
            AlienRotationUtil.preBodyYaw = player.bodyYaw;
            AlienRotationUtil.preHeadYaw = player.headYaw;
            player.setYaw(AlienRotationUtil.sprintYaw);
            player.bodyYaw = AlienRotationUtil.sprintYaw;
            player.headYaw = AlienRotationUtil.sprintYaw;
            AlienRotationUtil.rotationYaw = AlienRotationUtil.sprintYaw;
            AlienRotationUtil.rotationPitch = player.getPitch();
        }
    }

    @Inject(method = "sendMovementPackets", at = @At("TAIL"))
    private void onSendMovementPacketsTail(CallbackInfo ci) {
        if (AlienRotationUtil.shouldRotate) {
            ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
            player.setYaw(AlienRotationUtil.preYaw);
        }
    }

    // Prevent vanilla tickMovement from un-sprinting us when strafing (A/D) in Rotation mode
    @WrapWithCondition(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;setSprinting(Z)V", ordinal = 3))
    private boolean wrapStopSprinting(ClientPlayerEntity instance, boolean b) {
        if (AlienSprint.INSTANCE != null && AlienSprint.INSTANCE.isActive()
            && AlienSprint.INSTANCE.mode.get() == AlienSprint.Mode.Rotation
            && AlienMovementUtil.isMoving()) {
            return false; // prevent setSprinting(false) — keep sprinting
        }
        return true; // allow normally
    }
}
