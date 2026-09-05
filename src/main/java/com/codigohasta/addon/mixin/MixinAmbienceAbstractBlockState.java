package com.codigohasta.addon.mixin;

import com.codigohasta.addon.modules.Ambience;
import net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockStateBase.class)
public class MixinAmbienceAbstractBlockState {
    @Inject(method = "getLightEmission", at = @At("HEAD"), cancellable = true)
    public void getLuminanceHook(CallbackInfoReturnable<Integer> cir) {
        if (Minecraft.getInstance().level != null && Minecraft.getInstance().player != null) {
            if (Ambience.INSTANCE != null && Ambience.INSTANCE.isActive() && Ambience.INSTANCE.customLuminance.get()) {
                cir.setReturnValue(Ambience.INSTANCE.luminance.get());
            }
        }
    }
}
