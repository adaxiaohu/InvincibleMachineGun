package com.codigohasta.addon.utils.render;

import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.util.math.MathHelper;

/**
 * GUI 渲染工具 — 基于 HudRenderer，实现圆角/渐变/阴影
 * 圆角采用 scanline 像素级精确算法（参考 cyemer 的 fillRoundedRectPixels）
 */
public class GuiRenderUtil {

    // ─── 颜色工具 ─────────────────────────────────────────

    public static int toARGB(int r, int g, int b, int a) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int toARGB(int r, int g, int b, float a) {
        return toARGB(r, g, b, (int)(a * 255));
    }

    public static Color toColor(int argb) {
        return new Color(
            (argb >> 16) & 0xFF,
            (argb >> 8) & 0xFF,
            argb & 0xFF,
            (argb >> 24) & 0xFF
        );
    }

    // ─── 圆角矩形 ─────────────────────────────────────────

    /**
     * 绘制实心圆角矩形 — scanline 像素级精确算法
     */
    public static void drawRound(HudRenderer r, double x, double y, double w, double h, double radius, Color color) {
        radius = clamp(radius, w, h);
        if (w <= 0 || h <= 0 || color.a == 0) return;

        // 身体：3 段水平条
        r.quad(x + radius, y, w - 2 * radius, radius, color);                // 上条
        r.quad(x, y + radius, w, h - 2 * radius, color);                     // 中条（全宽）
        r.quad(x + radius, y + h - radius, w - 2 * radius, radius, color);   // 下条

        // 四个角用 scanline 精确绘制
        int ri = (int) Math.ceil(radius);
        for (int iy = 0; iy < ri; iy++) {
            double dy = radius - iy - 0.5;
            double dx = Math.sqrt(Math.max(0.0, radius * radius - dy * dy));
            double inset = Math.max(0.0, radius - dx);

            // 左上
            r.quad(x, y + iy, inset, 1, color);
            // 右上
            r.quad(x + w - inset, y + iy, inset, 1, color);
            // 左下
            r.quad(x, y + h - iy - 1, inset, 1, color);
            // 右下
            r.quad(x + w - inset, y + h - iy - 1, inset, 1, color);
        }
    }

    // ─── 渐变圆角矩形 ─────────────────────────────────────

    /**
     * 用 4 色渐变填充圆角矩形 — scanline 算法
     */
    public static void drawRoundGradient(HudRenderer r, double x, double y, double w, double h, double radius,
                                          Color cTL, Color cTR, Color cBR, Color cBL) {
        radius = clamp(radius, w, h);
        if (w <= 0 || h <= 0) return;

        // 中条（全宽渐变）
        r.quad(x, y + radius, w, h - 2 * radius, cTL, cTR, cBR, cBL);
        // 上条
        r.quad(x + radius, y, w - 2 * radius, radius, cTL, cTR, cBR, cBL);
        // 下条
        r.quad(x + radius, y + h - radius, w - 2 * radius, radius, cTL, cTR, cBR, cBL);

        // 四个角用 scanline 绘制，角用就近颜色
        int ri = (int) Math.ceil(radius);
        for (int iy = 0; iy < ri; iy++) {
            double dy = radius - iy - 0.5;
            double dx = Math.sqrt(Math.max(0.0, radius * radius - dy * dy));
            double inset = Math.max(0.0, radius - dx);

            // 左上（cTL）
            r.quad(x, y + iy, inset, 1, cTL);
            // 右上（cTR）
            r.quad(x + w - inset, y + iy, inset, 1, cTR);
            // 左下（cBL）
            r.quad(x, y + h - iy - 1, inset, 1, cBL);
            // 右下（cBR）
            r.quad(x + w - inset, y + h - iy - 1, inset, 1, cBR);
        }
    }

    // ─── 阴影 ─────────────────────────────────────────────

    /**
     * 多层半透明圆角模拟阴影
     */
    public static void drawShadow(HudRenderer r, double x, double y, double w, double h, double radius, Color color) {
        int layers = 6;
        for (int i = layers; i > 0; i--) {
            double sp = i * 1.2;
            float a = color.a / 255f * (1f / (layers * 2) * (layers - i + 1));
            Color c = new Color(color.r, color.g, color.b, (int)(a * 255));
            drawRound(r, x - sp, y - sp, w + sp * 2, h + sp * 2, radius + sp, c);
        }
    }

    // ─── 内部 ─────────────────────────────────────────────

    private static double clamp(double r, double w, double h) {
        return MathHelper.clamp(r, 0, Math.min(w, h) / 2);
    }
}
