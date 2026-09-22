package com.codigohasta.addon.utils.slimefunhelper.utils;
public final class ColorUtils {
    public static java.awt.Color withAlpha(java.awt.Color c,float a){return new java.awt.Color(c.getRed(),c.getGreen(),c.getBlue(),Math.round(255*Math.clamp(a,0,1)));}
    public static int withAlphaInt(int color,int alpha){return (color & 0x00FFFFFF) | (alpha << 24);}
}
