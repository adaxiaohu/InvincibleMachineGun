package com.codigohasta.addon.mixin;

import net.minecraft.client.renderer.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ItemInHandRenderer.class)
public abstract class MixinIMGHeldItemRenderer {
    // Held item tinting now handled in MixinIMGItemRenderer via VertexConsumer wrapping
}
