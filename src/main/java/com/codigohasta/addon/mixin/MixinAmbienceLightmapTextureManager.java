package com.codigohasta.addon.mixin;

import com.codigohasta.addon.modules.Ambience;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.renderer.Lightmap;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Lightmap.class)
public class MixinAmbienceLightmapTextureManager {
    // Override world lightmap color (intercept the sky light color written to the lightmap UBO).
    // 26.1.2 renamed update -> render and now writes blockLightTint first, so the sky light
    // colour is putVec3 ordinal 1 (order: blockLightTint, skyLightColor, ambientColor,
    // nightVisionColor).
    @ModifyArg(
        method = "render",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putVec3(Lorg/joml/Vector3fc;)Lcom/mojang/blaze3d/buffers/Std140Builder;", ordinal = 1),
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
