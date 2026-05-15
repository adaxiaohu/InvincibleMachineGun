package com.codigohasta.addon.utils;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;

public class TintingVertexConsumerProvider implements VertexConsumerProvider {
    private final VertexConsumerProvider delegate;
    private final float r, g, b, a;

    public TintingVertexConsumerProvider(VertexConsumerProvider delegate, float r, float g, float b, float a) {
        this.delegate = delegate;
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
    }

    @Override
    public VertexConsumer getBuffer(RenderLayer layer) {
        return new TintingVertexConsumer(delegate.getBuffer(layer), r, g, b, a);
    }
}
