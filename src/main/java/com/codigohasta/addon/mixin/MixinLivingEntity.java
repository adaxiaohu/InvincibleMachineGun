package com.codigohasta.addon.mixin;

import com.codigohasta.addon.modules.FireworkElytraFly;
import com.codigohasta.addon.modules.GlobalSetting;
import com.codigohasta.addon.utils.leaveshack.Rotation;
import com.codigohasta.addon.utils.leaveshack.events.ElytraUpdateEvent;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(LivingEntity.class)
public class MixinLivingEntity {
    @WrapOperation(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isFallFlying()Z")
    )
    private boolean wrapIsGliding(LivingEntity instance, Operation<Boolean> original) {
        if (instance == mc.player) {
            ElytraUpdateEvent elytraTransformEvent = new ElytraUpdateEvent(instance);
            MeteorClient.EVENT_BUS.post(elytraTransformEvent);
            FireworkElytraFly.INSTANCE.isFallFlying = original.call(instance);
            if (elytraTransformEvent.isCancelled()) {
                return false;
            }
        }
        return original.call(instance);
    }

    @WrapOperation(method = "jumpFromGround", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F"))
    private float wrapGetYaw(LivingEntity instance, Operation<Float> original) {
        if (GlobalSetting.INSTANCE.moveFix.get()) {
            if (Rotation.rotation) {
                return Rotation.targetYaw;
            }
        }
        return original.call(instance);
    }
}
