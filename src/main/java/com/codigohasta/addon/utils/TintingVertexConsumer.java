package com.codigohasta.addon.utils;

import net.minecraft.client.render.VertexConsumer;

public class TintingVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final float tintR, tintG, tintB, tintA;

    public TintingVertexConsumer(VertexConsumer delegate, float r, float g, float b, float a) {
        this.delegate = delegate;
        this.tintR = r;
        this.tintG = g;
        this.tintB = b;
        this.tintA = a;
    }

    @Override
    public VertexConsumer vertex(float x, float y, float z) {
        return delegate.vertex(x, y, z);
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        return delegate.color(
            Math.min(255, (int)(red * tintR)),
            Math.min(255, (int)(green * tintG)),
            Math.min(255, (int)(blue * tintB)),
            Math.min(255, (int)(alpha * tintA))
        );
    }

    @Override
    public VertexConsumer color(int argb) {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        int newR = Math.min(255, (int)(r * tintR));
        int newG = Math.min(255, (int)(g * tintG));
        int newB = Math.min(255, (int)(b * tintB));
        int newA = Math.min(255, (int)(a * tintA));
        return delegate.color(newA << 24 | newR << 16 | newG << 8 | newB);
    }

    @Override
    public VertexConsumer texture(float u, float v) {
        delegate.texture(u, v);
        return this;
    }

    @Override
    public VertexConsumer overlay(int u, int v) {
        delegate.overlay(u, v);
        return this;
    }

    @Override
    public VertexConsumer light(int u, int v) {
        delegate.light(u, v);
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        delegate.normal(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer lineWidth(float width) {
        delegate.lineWidth(width);
        return this;
    }
}
