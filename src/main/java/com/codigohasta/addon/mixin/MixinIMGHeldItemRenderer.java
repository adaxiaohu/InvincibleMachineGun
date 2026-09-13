package com.codigohasta.addon.mixin;

import net.minecraft.client.render.item.HeldItemRenderer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(HeldItemRenderer.class)
public abstract class MixinIMGHeldItemRenderer {
    // Held item tinting now handled in MixinIMGItemRenderer via VertexConsumer wrapping
}
