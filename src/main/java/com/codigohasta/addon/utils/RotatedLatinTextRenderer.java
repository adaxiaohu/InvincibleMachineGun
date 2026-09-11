package com.codigohasta.addon.utils;

import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.TextureFormat;
import meteordevelopment.meteorclient.renderer.MeshBuilder;
import meteordevelopment.meteorclient.renderer.MeshRenderer;
import meteordevelopment.meteorclient.renderer.MeteorRenderPipelines;
import meteordevelopment.meteorclient.renderer.Texture;
import meteordevelopment.meteorclient.renderer.text.FontFace;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTPackContext;
import org.lwjgl.stb.STBTTPackRange;
import org.lwjgl.stb.STBTTPackedchar;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Dedicated clockwise-90-degree renderer for Latin runs used by VerticalModuleList.
 *
 * <p>This deliberately does not rotate Meteor's already-built screen-space mesh. Instead it
 * reads the exact active {@link FontFace}, builds a small Latin atlas once, and writes every
 * glyph quad in its final rotated screen coordinates. That makes positioning independent from
 * Meteor's internal CustomTextRenderer mesh origin and avoids the clipping/left-edge artifacts
 * caused by post-transforming absolute UI vertices.</p>
 */
public final class RotatedLatinTextRenderer implements AutoCloseable {
    private static final int ATLAS_SIZE = 2048;
    private static final int FONT_HEIGHT = 54;

    private final MeshBuilder mesh = new MeshBuilder(MeteorRenderPipelines.UI_TEXT);
    private final Map<Integer, Glyph> glyphs = new HashMap<>();

    private FontFace loadedFace;
    private Texture texture;
    private float fontScale;
    private float ascent;
    private boolean building;

    public boolean begin(FontFace face) {
        if (building) throw new IllegalStateException("RotatedLatinTextRenderer.begin() called twice");
        if (face == null) return false;

        try {
            ensureFace(face);
        } catch (IOException | RuntimeException e) {
            return false;
        }

        if (texture == null || glyphs.isEmpty()) return false;
        mesh.begin();
        building = true;
        return true;
    }

    public boolean isBuilding() {
        return building;
    }

    /**
     * Draws one normal horizontal word rotated clockwise as a whole.
     * targetX/targetY are the top-left of the final rotated line box.
     */
    public void renderClockwise(String text, double targetX, double targetY, double lineWidth, Color color, boolean shadow) {
        if (!building || text == null || text.isEmpty() || lineWidth <= 0.0) return;

        double scale = lineWidth / FONT_HEIGHT;
        if (shadow) {
            int alpha = Math.max(0, Math.min(255, (int) Math.round(color.a * (180.0 / 255.0))));
            Color shadowColor = new Color(60, 60, 60, alpha);
            double offset = Math.max(0.55, scale);
            renderPass(text, targetX + offset, targetY + offset, scale, shadowColor);
        }
        renderPass(text, targetX, targetY, scale, color);
    }

    private void renderPass(String text, double targetX, double targetY, double scale, Color color) {
        double penX = 0.0;
        double baseline = ascent * fontScale;
        int glyphCount = text.codePointCount(0, text.length());
        mesh.ensureCapacity(Math.max(1, glyphCount) * 4, Math.max(1, glyphCount) * 6);

        for (int offset = 0; offset < text.length();) {
            int cp = text.codePointAt(offset);
            offset += Character.charCount(cp);

            Glyph g = glyphs.get(cp);
            if (g == null) g = glyphs.get((int) '?');
            if (g == null) g = glyphs.get(32);
            if (g == null) continue;

            // Normal horizontal glyph corners in native font pixels.
            double x0 = penX + g.x0;
            double x1 = penX + g.x1;
            double y0 = baseline + g.y0;
            double y1 = baseline + g.y1;

            // Clockwise 90 degrees: (x, y) -> (-y, x). Translate the complete line-box so
            // y=FONT_HEIGHT maps to x=targetX and x=0 maps to y=targetY. This preserves normal
            // word/letter spacing while making the top of each Latin glyph point right.
            double rx0y0 = targetX + (FONT_HEIGHT - y0) * scale;
            double ry0x0 = targetY + x0 * scale;
            double rx0y1 = targetX + (FONT_HEIGHT - y1) * scale;
            double ry1x0 = targetY + x0 * scale;
            double rx1y1 = targetX + (FONT_HEIGHT - y1) * scale;
            double ry1x1 = targetY + x1 * scale;
            double rx1y0 = targetX + (FONT_HEIGHT - y0) * scale;
            double ry0x1 = targetY + x1 * scale;

            int i1 = mesh.vec2(rx0y0, ry0x0).vec2(g.u0, g.v0).color(color).next();
            int i2 = mesh.vec2(rx0y1, ry1x0).vec2(g.u0, g.v1).color(color).next();
            int i3 = mesh.vec2(rx1y1, ry1x1).vec2(g.u1, g.v1).color(color).next();
            int i4 = mesh.vec2(rx1y0, ry0x1).vec2(g.u1, g.v0).color(color).next();
            mesh.quad(i1, i2, i3, i4);

            penX += g.xAdvance;
        }
    }

    public void end() {
        if (!building) return;
        mesh.end();
        building = false;

        MeshRenderer.begin()
            .attachments(MinecraftClient.getInstance().getFramebuffer())
            .pipeline(MeteorRenderPipelines.UI_TEXT)
            .mesh(mesh)
            .sampler("u_Texture", texture.getGlTextureView(), texture.getSampler())
            .end();
    }

    private void ensureFace(FontFace face) throws IOException {
        if (face == loadedFace && texture != null && !glyphs.isEmpty()) return;
        releaseFace();

        ByteBuffer buffer = face.readToDirectByteBuffer();
        STBTTFontinfo info = STBTTFontinfo.create();
        if (!STBTruetype.stbtt_InitFont(info, buffer)) {
            throw new IOException("Failed to initialize font " + face);
        }

        ByteBuffer bitmap = BufferUtils.createByteBuffer(ATLAS_SIZE * ATLAS_SIZE);
        STBTTPackedchar.Buffer[] data = {
            STBTTPackedchar.create(95),   // Basic Latin: 32..126
            STBTTPackedchar.create(96),   // Latin-1: 160..255
            STBTTPackedchar.create(144),  // Greek/Coptic: 880..1023
            STBTTPackedchar.create(256)   // Cyrillic: 1024..1279
        };
        int[] starts = {32, 160, 880, 1024};

        STBTTPackContext context = STBTTPackContext.create();
        if (!STBTruetype.stbtt_PackBegin(context, bitmap, ATLAS_SIZE, ATLAS_SIZE, 0, 1)) {
            throw new IOException("Failed to create font atlas for " + face);
        }

        try {
            STBTTPackRange.Buffer ranges = STBTTPackRange.create(data.length);
            for (int i = 0; i < data.length; i++) {
                ranges.put(STBTTPackRange.create().set(FONT_HEIGHT, starts[i], null, data[i].capacity(), data[i], (byte) 2, (byte) 2));
            }
            ranges.flip();

            if (!STBTruetype.stbtt_PackFontRanges(context, buffer, 0, ranges)) {
                throw new IOException("Failed to pack font atlas for " + face);
            }
        } finally {
            STBTruetype.stbtt_PackEnd(context);
        }

        fontScale = STBTruetype.stbtt_ScaleForPixelHeight(info, FONT_HEIGHT);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer ascentBuffer = stack.mallocInt(1);
            STBTruetype.stbtt_GetFontVMetrics(info, ascentBuffer, null, null);
            ascent = ascentBuffer.get(0);
        }

        float inv = 1.0f / ATLAS_SIZE;
        for (int i = 0; i < data.length; i++) {
            STBTTPackedchar.Buffer chars = data[i];
            int start = starts[i];
            for (int j = 0; j < chars.capacity(); j++) {
                STBTTPackedchar c = chars.get(j);
                glyphs.put(start + j, new Glyph(
                    c.xoff(), c.yoff(), c.xoff2(), c.yoff2(),
                    c.x0() * inv, c.y0() * inv, c.x1() * inv, c.y1() * inv,
                    c.xadvance()
                ));
            }
        }

        texture = new Texture(ATLAS_SIZE, ATLAS_SIZE, TextureFormat.RED8, FilterMode.LINEAR, FilterMode.LINEAR);
        texture.upload(bitmap);
        loadedFace = face;
    }

    private void releaseFace() {
        glyphs.clear();
        loadedFace = null;
        if (texture != null) {
            texture.close();
            texture = null;
        }
    }

    @Override
    public void close() {
        if (building) {
            // A module deactivation can only happen on the client thread; finish a pending batch
            // before destroying the atlas to keep the renderer state valid.
            end();
        }
        releaseFace();
    }

    private record Glyph(
        float x0, float y0, float x1, float y1,
        float u0, float v0, float u1, float v1,
        float xAdvance
    ) {}
}
