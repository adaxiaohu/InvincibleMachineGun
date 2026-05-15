package com.codigohasta.addon.mixin;

import com.codigohasta.addon.utils.alien.AlienRotationUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Temporarily set the player's yaw to sprintYaw during jump() so getYaw()
// returns the movement direction for the sprint boost calculation.
// Only modifies yaw (setYaw), NOT bodyYaw/headYaw — no rendering thread conflict.
@Mixin(LivingEntity.class)
public class MixinLivingEntitySprint {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    @Inject(method = "jump", at = @At("HEAD"))
    private void onJumpPre(CallbackInfo ci) {
        if (mc == null || mc.player == null || (Object) this != mc.player) return;
        if (!AlienRotationUtil.shouldRotate) return;

        AlienRotationUtil.preYaw = mc.player.getYaw();
        mc.player.setYaw(AlienRotationUtil.sprintYaw);
    }

    @Inject(method = "jump", at = @At("RETURN"))
    private void onJumpPost(CallbackInfo ci) {
        if (mc == null || mc.player == null || (Object) this != mc.player) return;
        if (!AlienRotationUtil.shouldRotate) return;

        mc.player.setYaw(AlienRotationUtil.preYaw);
    }
}
