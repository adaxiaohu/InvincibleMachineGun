package com.codigohasta.addon.utils;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Multiplies every vertex colour by a fixed tint before handing it to the
 * delegate. Every method returns {@code this} so the chained calls MC makes
 * ({@code addVertex(..).setColor(..).setUv(..)}) stay inside the wrapper.
 */
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
    public VertexConsumer addVertex(float x, float y, float z) {
        delegate.addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        delegate.setColor(
            Math.min(255, (int) (red * tintR)),
            Math.min(255, (int) (green * tintG)),
            Math.min(255, (int) (blue * tintB)),
            Math.min(255, (int) (alpha * tintA))
        );
        return this;
    }

    @Override
    public VertexConsumer setColor(int argb) {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return setColor(r, g, b, a);
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        delegate.setUv(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        delegate.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        delegate.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        delegate.setNormal(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
        delegate.setLineWidth(width);
        return this;
    }
}
