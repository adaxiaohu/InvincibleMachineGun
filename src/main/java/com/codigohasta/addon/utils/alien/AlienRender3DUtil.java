package com.codigohasta.addon.utils.alien;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

public class AlienRender3DUtil {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    // ── Text rendering (deferred, projected to 2D) ──
    // In 1.21.11, 3D world-space text rendering via VertexConsumerProvider.Immediate
    // no longer works reliably because RenderSystem.disableDepthTest() etc. were removed
    // and the rendering pipeline controls state. Instead we queue text during Render3DEvent
    // and render it projected to 2D during Render2DEvent, matching meteor-client's own approach.

    private static final List<TextRequest> textQueue = new ArrayList<>();
    private static final BufferAllocator textBuffer = new BufferAllocator(2048);
    private static final VertexConsumerProvider.Immediate textImmediate = VertexConsumerProvider.immediate(textBuffer);
    private static final Matrix4f identityMatrix = new Matrix4f();
    private static final Vector3d projPos = new Vector3d();

    public static void drawText3D(String text, Vec3d vec3d, int color) {
        drawText3D(Text.of(text), vec3d, 0, 0, 1, color, 2.0, 0.5, Double.MAX_VALUE);
    }

    public static void drawText3D(String text, Vec3d vec3d, double baseScale, double distanceFactor, double maxScale, int color) {
        drawText3D(Text.of(text), vec3d, 0, 0, 1, color, baseScale, distanceFactor, maxScale);
    }

    public static void drawText3D(Text text, Vec3d vec3d, double offX, double offY, double scale, int color) {
        drawText3D(text, vec3d, offX, offY, scale, color, 2.0, 0.5, Double.MAX_VALUE);
    }

    public static void drawText3D(Text text, Vec3d vec3d, double offX, double offY, double scale, int color, double maxScale) {
        drawText3D(text, vec3d, offX, offY, scale, color, 2.0, 0.5, maxScale);
    }

    public static void drawText3D(Text text, Vec3d vec3d, double offX, double offY, double scale, int color, double baseScale, double distanceFactor, double maxScale) {
        textQueue.add(new TextRequest(text, vec3d.add(offX, offY, 0), scale, color, baseScale, distanceFactor, maxScale));
    }

    /** Call from a Render2DEvent handler to flush deferred 3D text. */
    public static void renderDeferred() {
        if (textQueue.isEmpty()) return;

        for (TextRequest req : textQueue) {
            projPos.set(req.position.x, req.position.y, req.position.z);

            // Use distanceScaling=false — NametagUtils's built-in formula (1 - dist*0.01)
            // makes text smaller when close, which is the opposite of what we want.
            // We handle scaling ourselves below with a distance-proportional formula.
            if (!NametagUtils.to2D(projPos, 2.0, false)) continue;

            // Override the scale: base + distance factor, capped at max.
            double dist = Math.sqrt(mc.gameRenderer.getCamera().getCameraPos().squaredDistanceTo(req.position));
            double scale = Math.min(req.baseScale + dist * req.distanceFactor, req.maxScale);

            // Set the stored scale BEFORE calling begin()
            NametagUtils.scale = scale;
            NametagUtils.begin(projPos);

            // Position must be divided by scale because begin() applies
            // scale to the model-view stack (matching VanillaTextRenderer).
            int halfWidth = mc.textRenderer.getWidth(req.text) / 2;
            float scaledX = -halfWidth / (float) scale;
            mc.textRenderer.draw(
                req.text,
                scaledX, 0,
                req.color,
                true,
                identityMatrix,
                textImmediate,
                TextRenderer.TextLayerType.NORMAL,
                0,
                15728880
            );
            textImmediate.draw();

            NametagUtils.end();
        }

        textQueue.clear();
    }

    // ── Deferred box rendering (via event.renderer) ──

    public static void drawFill(Render3DEvent event, Box bb, java.awt.Color fillColor) {
        if (fillColor == null) return;
        Color meteorColor = new Color(fillColor.getRed(), fillColor.getGreen(), fillColor.getBlue(), fillColor.getAlpha());
        event.renderer.box(bb, meteorColor, meteorColor, ShapeMode.Sides, 0);
    }

    public static void drawBox(Render3DEvent event, Box bb, java.awt.Color outlineColor) {
        if (outlineColor == null) return;
        Color meteorColor = new Color(outlineColor.getRed(), outlineColor.getGreen(), outlineColor.getBlue(), outlineColor.getAlpha());
        event.renderer.box(bb, meteorColor, meteorColor, ShapeMode.Lines, 0);
    }

    public static void draw3DBox(Render3DEvent event, Box box, java.awt.Color fillColor, java.awt.Color outlineColor, boolean outline, boolean fill) {
        if (fill && fillColor != null) drawFill(event, box, fillColor);
        if (outline && outlineColor != null) drawBox(event, box, outlineColor);
    }

    // ── Utilities ──

    public static MatrixStack matrixFrom(double x, double y, double z) {
        MatrixStack matrices = new MatrixStack();
        Camera camera = mc.gameRenderer.getCamera();
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0F));
        Vec3d camPos = camera.getCameraPos();
        matrices.translate(x - camPos.x, y - camPos.y, z - camPos.z);
        return matrices;
    }

    // ── Internal ──

    private record TextRequest(Text text, Vec3d position, double scale, int color, double baseScale, double distanceFactor, double maxScale) {}
}
