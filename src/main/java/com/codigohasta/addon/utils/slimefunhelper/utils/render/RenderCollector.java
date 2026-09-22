package com.codigohasta.addon.utils.slimefunhelper.utils.render;
public class RenderCollector<T> {
private record Entry(Object shape,int rgb){}private final java.util.List<Entry> entries=new java.util.ArrayList<>();private final boolean outline;
public RenderCollector(boolean outline){this.outline=outline;}
public void submit(T shape,int rgb){entries.add(new Entry(shape,rgb));}public void clear(){entries.clear();}
public void render3D(net.minecraft.client.util.math.MatrixStack matrices){for(var entry:entries){var box=(net.minecraft.util.math.Box)entry.shape;var color=new java.awt.Color(entry.rgb,true);if(outline)com.codigohasta.addon.utils.slimefunhelper.utils.RenderUtils.drawOutlinedBox(matrices,box.getMinPos(),box.getMaxPos(),color);else com.codigohasta.addon.utils.slimefunhelper.utils.RenderUtils.drawSolidBox(matrices,box.getMinPos(),box.getMaxPos(),color);}}
}