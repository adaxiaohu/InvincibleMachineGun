package com.codigohasta.addon.mixin;

import com.codigohasta.addon.modules.Ambience;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.render.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(CloudRenderer.class)
public class MixinAmbienceCloudRenderer {
    @ModifyVariable(method = "renderClouds", at = @At("HEAD"), argsOnly = true, index = 1)
    private int modifyCloudColor(int color) {
        if (Modules.get() == null) return color;
        Ambience ambience = Modules.get().get(Ambience.class);
        if (ambience != null && ambience.isActive() && ambience.cloudEnabled.get()) {
            return ambience.cloudColor.get().getPacked();
        }
        return color;
    }
}
