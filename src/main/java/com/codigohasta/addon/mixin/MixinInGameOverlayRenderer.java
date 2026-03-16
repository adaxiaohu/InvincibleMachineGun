package com.codigohasta.addon.mixins;

import com.codigohasta.addon.modules.PearlPhase;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameOverlayRenderer.class)
public class MixinInGameOverlayRenderer {

    @Inject(method = "renderOverlays", at = @At("HEAD"), cancellable = true)
    private static void onRenderOverlays(
        MinecraftClient client, 
        MatrixStack matrices, 
        VertexConsumerProvider vertexConsumers, 
        CallbackInfo ci
    ) {
        PearlPhase module = Modules.get().get(PearlPhase.class);
        
        // 只有开启模块且开启移除遮挡时，才取消渲染
        if (module != null && module.isActive() && module.removeOverlay.get()) {
            ci.cancel();
        }
    }
}