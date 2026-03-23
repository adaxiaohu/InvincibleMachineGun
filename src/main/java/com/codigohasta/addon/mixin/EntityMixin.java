package com.codigohasta.addon.mixin;

import com.codigohasta.addon.utils.CamUtils;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void onChangeLookDirection(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if ((Object) this == MeteorClient.mc.player) {
            // 当找到目标进入“自动驾驶”状态时，强制解耦身体与视野
            if (CamUtils.isUsing()) {
                // method_31044 = getPerspective
                double modifier = MeteorClient.mc.options.getPerspective() == Perspective.THIRD_PERSON_FRONT ? -1.0 : 1.0;
                CamUtils.changeLookDirection(cursorDeltaX * 0.15, cursorDeltaY * 0.15 * modifier);
                ci.cancel(); 
            } else {
                CamUtils.yaw = MeteorClient.mc.player.getYaw();
                CamUtils.pitch = MeteorClient.mc.player.getPitch();
                CamUtils.prevYaw = CamUtils.yaw;
                CamUtils.prevPitch = CamUtils.pitch;
            }
        }
    }
}