package com.codigohasta.addon.mixin;

import com.codigohasta.addon.modules.BMWSprint;
import com.codigohasta.addon.utils.bmw.BMWPlayerUtil;
import com.codigohasta.addon.utils.bmw.BMWRotationUtil;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BMW Sprint 专属 Mixin —— sendMovementPackets 时的 yaw 旋转 + 阻止原版取消疾跑。
 */
@Mixin(LocalPlayer.class)
public class MixinBMWClientPlayerEntity {
    private static final Minecraft mc = Minecraft.getInstance();

    @Inject(method = "sendPosition", at = @At("HEAD"))
    private void onSendMovementPacketsHead(CallbackInfo ci) {
        if (BMWRotationUtil.shouldRotate) {
            LocalPlayer player = (LocalPlayer) (Object) this;
            BMWRotationUtil.preYaw = player.getYRot();
            BMWRotationUtil.preBodyYaw = player.yBodyRot;
            BMWRotationUtil.preHeadYaw = player.yHeadRot;
            player.setYRot(BMWRotationUtil.sprintYaw);
            player.yBodyRot = BMWRotationUtil.sprintYaw;
            player.yHeadRot = BMWRotationUtil.sprintYaw;
        }
    }

    @Inject(method = "sendPosition", at = @At("TAIL"))
    private void onSendMovementPacketsTail(CallbackInfo ci) {
        if (BMWRotationUtil.shouldRotate) {
            LocalPlayer player = (LocalPlayer) (Object) this;
            player.setYRot(BMWRotationUtil.preYaw);
        }
    }

    @WrapWithCondition(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;setSprinting(Z)V", ordinal = 3))
    private boolean wrapStopSprinting(LocalPlayer instance, boolean b) {
        if (BMWSprint.INSTANCE != null && BMWSprint.INSTANCE.isActive()
            && (BMWSprint.INSTANCE.sprintMode.get() == BMWSprint.Mode.OMNIROTATIONAL
                || BMWSprint.INSTANCE.sprintMode.get() == BMWSprint.Mode.OMNIDIRECTIONAL)
            && BMWPlayerUtil.isMoving()) {
            return false;
        }
        return true;
    }
}
