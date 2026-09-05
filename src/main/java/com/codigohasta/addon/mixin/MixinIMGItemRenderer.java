package com.codigohasta.addon.mixin;

import com.codigohasta.addon.modules.IMGChams;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * First-person hand tinting for {@link IMGChams}.
 *
 * 26.1.2 moved item rendering to the deferred submit pipeline, so there is no
 * longer a MultiBufferSource to wrap on the way in. The per-quad tint colour is
 * now the single hook: ItemFeatureRenderer#renderItem pushes the model's tint
 * into QuadInstance#setColor(int) right before each quad is emitted, so
 * overriding that argument recolours the held item.
 */
@Mixin(ItemFeatureRenderer.class)
public class MixinIMGItemRenderer {
    @Unique
    private boolean imgTintHeldItem;

    @Inject(method = "renderItem", at = @At("HEAD"))
    private void imgCaptureDisplayContext(
        MultiBufferSource.BufferSource bufferSource,
        OutlineBufferSource outlineBufferSource,
        SubmitNodeStorage.ItemSubmit submit,
        CallbackInfo ci
    ) {
        ItemDisplayContext context = submit.displayContext();
        IMGChams chams = Modules.get().get(IMGChams.class);

        imgTintHeldItem = chams != null
            && chams.isActive()
            && chams.handEnabled.get()
            && (context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND);
    }

    @ModifyArg(
        method = "renderItem",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/QuadInstance;setColor(I)V"),
        index = 0
    )
    private int imgTintQuadColour(int color) {
        if (!imgTintHeldItem) return color;

        IMGChams chams = Modules.get().get(IMGChams.class);
        if (chams == null) return color;

        SettingColor tint = chams.handColor.get();
        return (tint.a & 0xFF) << 24 | (tint.r & 0xFF) << 16 | (tint.g & 0xFF) << 8 | (tint.b & 0xFF);
    }
}
