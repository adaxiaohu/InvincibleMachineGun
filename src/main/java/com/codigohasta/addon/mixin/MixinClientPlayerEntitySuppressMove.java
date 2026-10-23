package com.codigohasta.addon.mixin;

import com.codigohasta.addon.utils.gcore.MoveSequenceGate;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPlayerEntity.class)
public abstract class MixinClientPlayerEntitySuppressMove {

    /**
     * 见类注释。
     *
     * @param original 原版 {@code isCamera()} 的返回值
     * @return {@code false} = 本次不发原版移动包；否则透传原值
     */
    @ModifyExpressionValue(
        method = "sendMovementPackets",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/client/network/ClientPlayerEntity;isCamera()Z")
    )
    private boolean sfimg$suppressMovementPackets(boolean original) {
        if (MoveSequenceGate.consumeSuppress()) {
            return false;
        }
        return original;
    }
}
