package com.codigohasta.addon.mixin;

import com.codigohasta.addon.utils.TerminatorModelScan;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Model.class)
public abstract class MixinTerminatorModelRender {
    @Shadow
    public abstract ModelPart getRootPart();

    @Inject(
        method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void img$renderStructuralScanPart(MatrixStack matrices, VertexConsumer vertices,
                                              int light, int overlay, int color,
                                              CallbackInfo info) {
        if (TerminatorModelScan.renderSelectedPart(getRootPart(), matrices, vertices, light, overlay, color)) {
            info.cancel();
        }
    }
}
