package com.codigohasta.addon.mixin;

import com.codigohasta.addon.modules.IMGChams;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AvatarRenderer.class)
public abstract class MixinIMGPlayerEntityRenderer {

    @Unique
    private IMGChams chams;

    @Unique
    private IMGChams getChams() {
        if (chams == null) chams = Modules.get().get(IMGChams.class);
        return chams;
    }

    @Unique
    private static final Identifier BLANK = Identifier.fromNamespaceAndPath("minecraft", "textures/blank.png");

    // Chams - Hand Texture (swap to blank when texture disabled)
    @ModifyExpressionValue(
        method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Lnet/minecraft/client/model/geom/ModelPart;Z)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;entityTranslucent(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;")
    )
    private RenderType onRenderArmTexture(RenderType original, PoseStack matrixStack, SubmitNodeCollector commandQueue, int light, Identifier skinTexture, ModelPart modelPart, boolean sleeveVisible) {
        IMGChams c = getChams();
        if (c != null && c.isActive() && c.handEnabled.get()) {
            Identifier tex = c.handTexture.get() ? skinTexture : BLANK;
            return RenderTypes.entityTranslucent(tex);
        }
        return original;
    }

    // Chams - Hand Color
    @WrapWithCondition(
        method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Lnet/minecraft/client/model/geom/ModelPart;Z)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModelPart(Lnet/minecraft/client/model/geom/ModelPart;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IILnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V")
    )
    private boolean onRenderArmColor(SubmitNodeCollector instance, ModelPart modelPart, PoseStack matrixStack, RenderType renderLayer, int light, int overlay, TextureAtlasSprite sprite) {
        IMGChams c = getChams();
        if (c != null && c.isActive() && c.handEnabled.get()) {
            instance.submitModelPart(modelPart, matrixStack, renderLayer, light, overlay, null, c.handColor.get().getPacked(), null);
            return false;
        }
        return true;
    }
}
