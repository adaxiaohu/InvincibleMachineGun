package com.codigohasta.addon.mixin;

import com.codigohasta.addon.modules.IMGChams;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.1.2 no longer keeps a mutable static RenderLayer (END_CRYSTAL) on the renderer: render()
 * became submit(), and the render type is derived from the Identifier passed to submitModel.
 * The texture swap therefore happens inside the wrapped submitModel call instead of in a
 * HEAD inject, which also removes the need to shadow END_CRYSTAL / TEXTURE.
 */
@Mixin(EndCrystalRenderer.class)
public class MixinIMGEndCrystalRenderer {

    @Unique
    private static final Identifier BLANK = Identifier.fromNamespaceAndPath("minecraft", "textures/blank.png");

    @Unique
    private IMGChams imgChams;

    @Unique
    private IMGChams img$chams() {
        if (imgChams == null && Modules.get() != null) imgChams = Modules.get().get(IMGChams.class);
        return imgChams;
    }

    // Apply additional scale alongside the built-in scale(2.0, 2.0, 2.0)
    @Inject(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V")
    )
    private void onScale(EndCrystalRenderState state, PoseStack matrixStack, SubmitNodeCollector commandQueue, CameraRenderState cameraState, CallbackInfo ci) {
        IMGChams chams = img$chams();
        if (chams != null && chams.customCrystal() && chams.scale.get() != 1.0) {
            float s = chams.scale.get().floatValue();
            matrixStack.scale(s, s, s);
        }
    }

    // Intercept submitModel to apply custom color/texture. When active, call the 10-param
    // overload with our color packed as the main vertex color (not the outline color).
    @WrapWithCondition(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/resources/Identifier;IIILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V")
    )
    private <S> boolean onColor(SubmitNodeCollector instance, Model<? super S> model, S state, PoseStack matrixStack, Identifier texture, int light, int overlay, int outlineColor, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        IMGChams chams = img$chams();
        if (chams != null && chams.customCrystal()) {
            instance.submitModel(
                model, state, matrixStack,
                RenderTypes.entityTranslucent(chams.textureEnabled.get() ? texture : BLANK),
                light, overlay,
                chams.crystalColor.get().getPacked(),
                null,
                outlineColor,
                crumblingOverlay
            );
            return false;
        }
        return true;
    }
}
