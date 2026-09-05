package com.codigohasta.addon.mixin;

import com.codigohasta.addon.modules.Ambience;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FogRenderer.class)
public class MixinAmbienceFogRenderer {
    // Override fog/dimension color. In 26.1.2 computeFogColor no longer returns the
    // colour: it writes into the Vector4f handed to it, so the override mutates that.
    @Inject(method = "computeFogColor(Lnet/minecraft/client/Camera;FLnet/minecraft/client/multiplayer/ClientLevel;IFLorg/joml/Vector4f;)V", at = @At("RETURN"))
    private void onComputeFogColor(Camera camera, float tickProgress, ClientLevel world, int viewDistance, float skyDarkness, Vector4f out, CallbackInfo ci) {
        if (Modules.get() == null) return;
        Ambience ambience = Modules.get().get(Ambience.class);
        if (ambience == null || !ambience.isActive()) return;

        if (ambience.fogEnabled.get()) {
            out.set(ambience.fogColor.get().getVec4f());
        } else if (ambience.dimensionColorEnabled.get()) {
            out.set(ambience.dimensionColor.get().getVec4f());
        }
    }

    // Override fog start distance (fogDistance)
    @ModifyVariable(method = "updateBuffer(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V", at = @At("HEAD"), argsOnly = true, index = 4)
    private float modifyFogStart(float start) {
        if (Modules.get() == null) return start;
        Ambience ambience = Modules.get().get(Ambience.class);
        if (ambience != null && ambience.isActive() && ambience.fogDistance.get()) {
            return ambience.fogStart.get().floatValue();
        }
        return start;
    }

    // Override fog end distance (fogDistance)
    @ModifyVariable(method = "updateBuffer(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V", at = @At("HEAD"), argsOnly = true, index = 5)
    private float modifyFogEnd(float end) {
        if (Modules.get() == null) return end;
        Ambience ambience = Modules.get().get(Ambience.class);
        if (ambience != null && ambience.isActive() && ambience.fogDistance.get()) {
            return ambience.fogEnd.get().floatValue();
        }
        return end;
    }
}
