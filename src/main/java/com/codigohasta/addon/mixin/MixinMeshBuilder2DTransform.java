package com.codigohasta.addon.mixin;

import com.codigohasta.addon.utils.ScreenVertexTransform;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import meteordevelopment.meteorclient.renderer.MeshBuilder;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = MeshBuilder.class, remap = false)
public abstract class MixinMeshBuilder2DTransform {
    @WrapMethod(method = "vec2")
    private MeshBuilder img$transform2DVertex(double x, double y, Operation<MeshBuilder> original) {
        // CustomTextRenderer writes position and UV through the same vec2 method. Transforming
        // the UV as screen coordinates makes the glyph sample outside its font atlas.
        if (!ScreenVertexTransform.shouldTransformNextVec2()) return original.call(x, y);

        double transformedX = ScreenVertexTransform.transformX(x, y);
        double transformedY = ScreenVertexTransform.transformY(x, y);
        return original.call(transformedX, transformedY);
    }
}
