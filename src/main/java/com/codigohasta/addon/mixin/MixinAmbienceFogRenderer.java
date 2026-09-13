package com.codigohasta.addon.mixin;

import com.codigohasta.addon.modules.Ambience;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public class MixinAmbienceFogRenderer {
    // Override fog/dimension color by intercepting the return value of getFogColor
    @Inject(method = "getFogColor(Lnet/minecraft/client/render/Camera;FLnet/minecraft/client/world/ClientWorld;IF)Lorg/joml/Vector4f;", at = @At("RETURN"), cancellable = true)
    private void onGetFogColor(Camera camera, float tickProgress, ClientWorld world, int viewDistance, float skyDarkness, CallbackInfoReturnable<Vector4f> cir) {
        if (Modules.get() == null) return;
        Ambience ambience = Modules.get().get(Ambience.class);
        if (ambience == null || !ambience.isActive()) return;

        if (ambience.fogEnabled.get()) {
            cir.setReturnValue(ambience.fogColor.get().getVec4f());
        } else if (ambience.dimensionColorEnabled.get()) {
            cir.setReturnValue(ambience.dimensionColor.get().getVec4f());
        }
    }

    // Override fog start distance (fogDistance)
    @ModifyVariable(method = "applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V", at = @At("HEAD"), argsOnly = true, index = 4)
    private float modifyFogStart(float start) {
        if (Modules.get() == null) return start;
        Ambience ambience = Modules.get().get(Ambience.class);
        if (ambience != null && ambience.isActive() && ambience.fogDistance.get()) {
            return ambience.fogStart.get().floatValue();
        }
        return start;
    }

    // Override fog end distance (fogDistance)
    @ModifyVariable(method = "applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V", at = @At("HEAD"), argsOnly = true, index = 5)
    private float modifyFogEnd(float end) {
        if (Modules.get() == null) return end;
        Ambience ambience = Modules.get().get(Ambience.class);
        if (ambience != null && ambience.isActive() && ambience.fogDistance.get()) {
            return ambience.fogEnd.get().floatValue();
        }
        return end;
    }
}
