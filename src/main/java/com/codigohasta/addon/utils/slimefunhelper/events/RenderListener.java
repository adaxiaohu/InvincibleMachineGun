package com.codigohasta.addon.utils.slimefunhelper.events;

import net.minecraft.client.util.math.MatrixStack;
public final class RenderListener {
    private static final Channel<net.minecraft.client.gui.DrawContext> render2d=new Channel<>();
    public static Channel<net.minecraft.client.gui.DrawContext> getRender2DEvent(){return render2d;}
    private static final Channel<net.minecraft.util.Identifier> modelOverride=new Channel<>();
    public static Channel<net.minecraft.util.Identifier> getCustomModelOverride(){return modelOverride;}
    private static final Channel<MatrixStack> render=new Channel<>();
    public static Channel<MatrixStack> getRender3DEvent(){return render;}
}
