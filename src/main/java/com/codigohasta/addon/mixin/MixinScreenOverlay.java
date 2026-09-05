package com.codigohasta.addon.mixin;

import com.codigohasta.addon.modules.IMGTips;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class MixinScreenOverlay {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onRender(GuiGraphicsExtractor context, int mouseX, int mouseY, float tickDelta, CallbackInfo ci) {
        IMGTips.renderOnScreen(context);
    }
}
