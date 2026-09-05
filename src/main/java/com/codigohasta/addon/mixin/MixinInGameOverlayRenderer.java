package com.codigohasta.addon.mixin;

import com.codigohasta.addon.modules.PearlPhase;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource; // 注意这个新导入
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public class MixinInGameOverlayRenderer {

    /**
     * 适配 1.21.11 最终版
     * 根据崩溃日志，该方法的参数列表必须是：
     * 1. TextureAtlasSprite (class_1058)
     * 2. PoseStack (class_4587)
     * 3. MultiBufferSource (class_4597)
     * 这样才能完美匹配 Expected (Lnet/minecraft/class_1058;Lnet/minecraft/class_4587;Lnet/minecraft/class_4597;...)
     */
    @Inject(method = "renderTex", at = @At("HEAD"), cancellable = true)
    private static void onRenderInWallOverlay(
        TextureAtlasSprite sprite, 
        PoseStack matrices, 
        MultiBufferSource vertexConsumers, 
        CallbackInfo ci
    ) {
        // 获取模块
        PearlPhase module = Modules.get().get(PearlPhase.class);
        
        // 核心逻辑：开启模块且开启移除遮挡时，直接取消该渲染
        if (module != null && module.isActive() && module.removeOverlay.get()) {
            ci.cancel();
        }
    }
}