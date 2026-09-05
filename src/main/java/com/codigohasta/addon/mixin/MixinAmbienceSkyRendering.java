package com.codigohasta.addon.mixin;

import com.codigohasta.addon.modules.Ambience;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public class MixinAmbienceSkyRendering {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onUpdateRenderState(ClientLevel world, float tickProgress, Camera camera, SkyRenderState state, CallbackInfo ci) {
        if (Modules.get() == null) return;
        Ambience ambience = Modules.get().get(Ambience.class);
        if (ambience == null || !ambience.isActive()) return;

        if (ambience.skyEnabled.get()) {
            state.skyColor = ambience.skyColor.get().getPacked();
        }

        if (ambience.forceOverworld.get()) {
            state.skybox = DimensionType.Skybox.OVERWORLD;
        }
    }
}
