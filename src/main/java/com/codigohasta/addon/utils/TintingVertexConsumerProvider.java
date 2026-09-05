package com.codigohasta.addon.utils;

import net.minecraft.client.renderer.rendertype.RenderType;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;

public class TintingVertexConsumerProvider implements MultiBufferSource {
    private final MultiBufferSource delegate;
    private final float r, g, b, a;

    public TintingVertexConsumerProvider(MultiBufferSource delegate, float r, float g, float b, float a) {
        this.delegate = delegate;
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
    }

    @Override
    public VertexConsumer getBuffer(RenderType layer) {
        return new TintingVertexConsumer(delegate.getBuffer(layer), r, g, b, a);
    }
}
