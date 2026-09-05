package com.codigohasta.addon.mixin;

import com.codigohasta.addon.modules.IMGChams;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.model.object.crystal.EndCrystalModel;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EndCrystalModel.class)
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
        method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EndCrystalRenderer;getY(F)F")
    )
    private float onBounce(float original, EndCrystalRenderState state) {
        IMGChams c = getChams();
        if (c != null && c.isActive() && c.crystalEnabled.get()) {
            float g = Mth.sin(state.ageInTicks * 0.2F * c.bounceSpeed.get().floatValue()) / 2.0F + 0.5F;
            g = (g * g + g) * 0.4F * c.bounceHeight.get().floatValue();
            return g - 1.4F + c.yOffset.get().floatValue();
        }
        return original;
    }

    // Chams - Crystal Rotation Speed
    @ModifyExpressionValue(
        method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;)V",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;ageInTicks:F", ordinal = 0)
    )
    private float onRotationSpeed(float original) {
        IMGChams c = getChams();
        if (c != null && c.isActive() && c.crystalEnabled.get()) {
            return original * c.spinSpeed.get().floatValue();
        }
        return original;
    }
}
