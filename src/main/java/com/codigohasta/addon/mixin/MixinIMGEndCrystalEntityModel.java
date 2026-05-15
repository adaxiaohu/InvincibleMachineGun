package com.codigohasta.addon.mixin;

import com.codigohasta.addon.modules.IMGChams;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.render.entity.EndCrystalEntityRenderer;
import net.minecraft.client.render.entity.model.EndCrystalEntityModel;
import net.minecraft.client.render.entity.state.EndCrystalEntityRenderState;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EndCrystalEntityModel.class)
public abstract class MixinIMGEndCrystalEntityModel {

    @Unique
    private IMGChams chams;

    @Unique
    private IMGChams getChams() {
        if (chams == null) chams = Modules.get().get(IMGChams.class);
        return chams;
    }

    // Chams - Crystal Bounce
    @ModifyExpressionValue(
        method = "setAngles(Lnet/minecraft/client/render/entity/state/EndCrystalEntityRenderState;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/EndCrystalEntityRenderer;getYOffset(F)F")
    )
    private float onBounce(float original, EndCrystalEntityRenderState state) {
        IMGChams c = getChams();
        if (c != null && c.isActive() && c.crystalEnabled.get()) {
            float g = MathHelper.sin(state.age * 0.2F * c.bounceSpeed.get().floatValue()) / 2.0F + 0.5F;
            g = (g * g + g) * 0.4F * c.bounceHeight.get().floatValue();
            return g - 1.4F + c.yOffset.get().floatValue();
        }
        return original;
    }

    // Chams - Crystal Rotation Speed
    @ModifyExpressionValue(
        method = "setAngles(Lnet/minecraft/client/render/entity/state/EndCrystalEntityRenderState;)V",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/entity/state/EndCrystalEntityRenderState;age:F", ordinal = 0)
    )
    private float onRotationSpeed(float original) {
        IMGChams c = getChams();
        if (c != null && c.isActive() && c.crystalEnabled.get()) {
            return original * c.spinSpeed.get().floatValue();
        }
        return original;
    }
}
