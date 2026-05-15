package com.codigohasta.addon.mixin;

import com.codigohasta.addon.modules.IMGChams;
import com.codigohasta.addon.utils.TintingVertexConsumerProvider;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ItemRenderer.class)
public abstract class MixinIMGItemRenderer {
    private static boolean rendering = false;

    @Inject(
        method = "renderItem(Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II[ILjava/util/List;Lnet/minecraft/client/render/RenderLayer;Lnet/minecraft/client/render/item/ItemRenderState$Glint;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void onRenderItem(
        ItemDisplayContext context,
        MatrixStack matrices,
        VertexConsumerProvider provider,
        int light,
        int overlay,
        int[] tints,
        List<BakedQuad> quads,
        RenderLayer layer,
        ItemRenderState.Glint glint,
        CallbackInfo ci
    ) {
        if (rendering) return;

        IMGChams c = Modules.get().get(IMGChams.class);
        if (c != null && c.isActive() && c.handEnabled.get()
            && (context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND)) {
            rendering = true;
            try {
                int packed = c.handColor.get().getPacked();
                float r = ((packed >> 16) & 0xFF) / 255.0f;
                float g = ((packed >> 8) & 0xFF) / 255.0f;
                float b = (packed & 0xFF) / 255.0f;
                float a = ((packed >> 24) & 0xFF) / 255.0f;

                VertexConsumerProvider wrapped = new TintingVertexConsumerProvider(provider, r, g, b, a);
                ItemRenderer.renderItem(context, matrices, wrapped, light, overlay, tints, quads, layer, glint);
                ci.cancel();
            } finally {
                rendering = false;
            }
        }
    }
}
