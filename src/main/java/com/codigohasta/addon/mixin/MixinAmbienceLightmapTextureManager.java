package com.codigohasta.addon.mixin;

import com.codigohasta.addon.modules.Ambience;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.entry.RegistryEntry;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LightmapTextureManager.class)
public class MixinAmbienceLightmapTextureManager {
    @Redirect(
        method = "update",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;hasStatusEffect(Lnet/minecraft/registry/entry/RegistryEntry;)Z",
            ordinal = 0,
            remap = false
        ),
        require = 0
    )
    private boolean nightVisionHook(ClientPlayerEntity instance, RegistryEntry<StatusEffect> registryEntry) {
        if (Modules.get() == null) return instance.hasStatusEffect(registryEntry);
        Ambience ambience = Modules.get().get(Ambience.class);
        return (ambience != null && ambience.isActive() && ambience.fullBright.get())
            ? true : instance.hasStatusEffect(registryEntry);
    }

    // Override world lightmap color (intercept the sky light color written to the lightmap UBO)
    @ModifyArg(
        method = "update",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putVec3(Lorg/joml/Vector3fc;)Lcom/mojang/blaze3d/buffers/Std140Builder;", ordinal = 0),
        index = 0,
        require = 0
    )
    private Vector3fc modifyWorldColor(Vector3fc skyLightColor) {
        if (Modules.get() == null) return skyLightColor;
        Ambience ambience = Modules.get().get(Ambience.class);
        if (ambience != null && ambience.isActive() && ambience.worldColorEnabled.get()) {
            var c = ambience.worldColor.get();
            return new Vector3f(c.r / 255f, c.g / 255f, c.b / 255f);
        }
        return skyLightColor;
    }
}
