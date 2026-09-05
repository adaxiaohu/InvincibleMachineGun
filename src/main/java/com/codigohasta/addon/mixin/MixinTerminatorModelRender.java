package com.codigohasta.addon.mixin;

import com.codigohasta.addon.utils.TerminatorModelScan;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Model.class)
public abstract class MixinTerminatorModelRender {
    @Shadow
    @Final
    protected ModelPart root;

    @Inject(
        method = "renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void img$renderStructuralScanPart(PoseStack matrices, VertexConsumer vertices,
                                              int light, int overlay, int color,
                                              CallbackInfo info) {
        if (TerminatorModelScan.renderSelectedPart(this.root, matrices, vertices, light, overlay, color)) {
            info.cancel();
        }
    }
}
