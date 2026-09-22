package com.codigohasta.addon.mixin.slimefunhelper;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.codigohasta.addon.modules.slimefunhelper.ElytraExtra;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BipedEntityRenderer.class)
public abstract class EntityRenderStateMixin {
    @ModifyExpressionValue(
            method = "updateBipedRenderState",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;isGliding()Z"))
    private static boolean updateBipedRenderState(boolean original, @Local(argsOnly = true) LivingEntity livingEntity) {
        if (com.codigohasta.addon.utils.slimefunhelper.SlimefunRuntime.extraActive() && livingEntity.isGliding() && livingEntity == MinecraftClient.getInstance().player) {
            if (ElytraExtra.INSTANCE.renderFix.get() && ElytraExtra.INSTANCE.isCurrentArmorGliding()) {
                return false;
            }
        }
        return original;
    }
}
