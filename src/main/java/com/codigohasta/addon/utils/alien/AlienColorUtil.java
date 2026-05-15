package com.codigohasta.addon.utils.alien;

import java.awt.Color;

public class AlienColorUtil {
    public static Color injectAlpha(Color color, int alpha) {
        alpha = Math.max(Math.min(255, alpha), 0);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    public static int injectAlpha(int color, int alpha) {
        return color & 0xFFFFFF | alpha << 24;
    }

    public static Color fadeColor(Color startColor, Color endColor, double progress) {
        progress = Math.min(Math.max(progress, 0.0), 1.0);
        int sR = startColor.getRed();
        int sG = startColor.getGreen();
        int sB = startColor.getBlue();
        int sA = startColor.getAlpha();
        int eR = endColor.getRed();
        int eG = endColor.getGreen();
        int eB = endColor.getBlue();
        int eA = endColor.getAlpha();
        return new Color(
            Math.min((int) (sR + (eR - sR) * progress), 255),
            Math.min((int) (sG + (eG - sG) * progress), 255),
            Math.min((int) (sB + (eB - sB) * progress), 255),
            Math.min((int) (sA + (eA - sA) * progress), 255)
        );
    }
}
