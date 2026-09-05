package com.codigohasta.addon.mixin;

import com.codigohasta.addon.modules.Ambience;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ambience fullBright. The 1.21.11 addon forced LocalPlayer#hasStatusEffect(NIGHT_VISION)
 * to return true inside the lightmap update. That cannot be ported verbatim: in 26.1.2 the
 * lightmap is fed from a render state, and GameRenderer#getNightVisionScale calls
 * getEffect(NIGHT_VISION) and then endsWithin(200) on the result with no null check, so a
 * forced true would NPE for a player that has no night vision. The extracted intensity is
 * therefore written directly instead, which is what full night vision produces anyway.
 */
@Mixin(LightmapRenderStateExtractor.class)
public class MixinAmbienceLightmapExtractor {
    @Inject(
        method = "extract(Lnet/minecraft/client/renderer/state/LightmapRenderState;F)V",
        at = @At("RETURN"),
        require = 0
    )
    private void onExtract(LightmapRenderState state, float tickProgress, CallbackInfo ci) {
        if (Modules.get() == null) return;
        Ambience ambience = Modules.get().get(Ambience.class);
        if (ambience != null && ambience.isActive() && ambience.fullBright.get()) {
            state.nightVisionEffectIntensity = 1.0f;
        }
    }
}
