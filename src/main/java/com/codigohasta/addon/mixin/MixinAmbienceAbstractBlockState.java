package com.codigohasta.addon.mixin;

import com.codigohasta.addon.modules.Ambience;
import net.minecraft.block.AbstractBlock.AbstractBlockState;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlockState.class)
public class MixinAmbienceAbstractBlockState {
    @Inject(method = "getLuminance", at = @At("HEAD"), cancellable = true)
    public void getLuminanceHook(CallbackInfoReturnable<Integer> cir) {
        if (MinecraftClient.getInstance().world != null && MinecraftClient.getInstance().player != null) {
            if (Ambience.INSTANCE != null && Ambience.INSTANCE.isActive() && Ambience.INSTANCE.customLuminance.get()) {
                cir.setReturnValue(Ambience.INSTANCE.luminance.get());
            }
        }
    }
}
