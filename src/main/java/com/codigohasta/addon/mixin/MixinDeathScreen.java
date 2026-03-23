package com.codigohasta.addon.mixin;

import com.codigohasta.addon.utils.CamUtils;
import net.minecraft.client.gui.screen.DeathScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DeathScreen.class)
public class MixinDeathScreen {
    @Inject(method = "init", at = @At("TAIL"))
    private void onInitClear(CallbackInfo ci) {
       CamUtils.inUse.clear();
    }
}