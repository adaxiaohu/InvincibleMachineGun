package com.codigohasta.addon.utils.leaveshack;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

import java.awt.*;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class Render3DUtil {
    public static final Matrix4f lastProjMat = new Matrix4f();
    public static final Matrix4f lastModMat = new Matrix4f();
    public static final Matrix4f lastWorldSpaceMatrix = new Matrix4f();
    public static long initTime = System.currentTimeMillis();

    private static void drawWithShadow(MatrixStack matrices, String info, float x, float y, int color) {
        var immediate = mc.getBufferBuilders().getEntityVertexConsumers();
        mc.textRenderer.draw(info, x, y, color, true, matrices.peek().getPositionMatrix(), immediate, TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xf000f0);
        immediate.draw();
    }

    public static void renderText3D(String info, Vec3d targetPos, int color) {
        Camera camera = mc.gameRenderer.getCamera();
        GL11.glDepthFunc(GL11.GL_ALWAYS);
        MatrixStack matrixStack = new MatrixStack();
        double x = targetPos.getX();
        double y = targetPos.getY();
        double z = targetPos.getZ();
        int width = mc.textRenderer.getWidth(info);
        float hwidth = width / 2.0f;
        renderInfo(info, hwidth, x, y, z, camera, matrixStack, color);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
    }

    public static void renderInfo(String info, float width, double x, double y, double z, Camera camera, MatrixStack matrices, int color) {
        final Vec3d pos = camera.getCameraPos();
        float scale = (float) (-0.025f + (pos.squaredDistanceTo(x, y, z) > (6 * 6) ? (Math.sqrt(pos.squaredDistanceTo(x, y, z)) - 6) * -0.0025f : 0));
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0f));
        matrices.translate(x - pos.getX(),
                y - pos.getY() + (scale / -0.025f - 1) / 4,
                z - pos.getZ());
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));

        matrices.scale(scale, scale, -1.0f);

        drawWithShadow(matrices, info, -width, 0.0f, color);

        matrices.pop();
    }

    public static Vec3d worldSpaceToScreenSpace(Vec3d pos) {
        Camera camera = mc.getEntityRenderDispatcher().camera;
        int displayHeight = mc.getWindow().getHeight();
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        Vector3f target = new Vector3f();

        double deltaX = pos.x - camera.getCameraPos().x;
        double deltaY = pos.y - camera.getCameraPos().y;
        double deltaZ = pos.z - camera.getCameraPos().z;

        Vector4f transformedCoordinates = new Vector4f((float) deltaX, (float) deltaY, (float) deltaZ, 1.f).mul(lastWorldSpaceMatrix);
        Matrix4f matrixProj = new Matrix4f(lastProjMat);
        Matrix4f matrixModel = new Matrix4f(lastModMat);
        matrixProj.mul(matrixModel).project(transformedCoordinates.x(), transformedCoordinates.y(), transformedCoordinates.z(), viewport, target);
        return new Vec3d(target.x / mc.getWindow().getScaleFactor(), (displayHeight - target.y) / mc.getWindow().getScaleFactor(), target.z);
    }

    public static void drawTargetBox2D(DrawContext context, Entity entity, Color color) {
        if (entity == null) return;

        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();

        var box = entity.getBoundingBox().offset(
                x - entity.getX(),
                y - entity.getY(),
                z - entity.getZ()
        );

        Vec3d[] points = new Vec3d[]{
                new Vec3d(box.minX, box.minY, box.minZ),
                new Vec3d(box.minX, box.maxY, box.minZ),
                new Vec3d(box.maxX, box.minY, box.minZ),
                new Vec3d(box.maxX, box.maxY, box.minZ),
                new Vec3d(box.minX, box.minY, box.maxZ),
                new Vec3d(box.minX, box.maxY, box.maxZ),
                new Vec3d(box.maxX, box.minY, box.maxZ),
                new Vec3d(box.maxX, box.maxY, box.maxZ)
        };

        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -1;
        float maxY = -1;

        for (Vec3d point : points) {
            Vec3d screen = worldSpaceToScreenSpace(point);

            if (screen.z > 0 && screen.z < 1) {
                minX = Math.min(minX, (float) screen.x);
                minY = Math.min(minY, (float) screen.y);
                maxX = Math.max(maxX, (float) screen.x);
                maxY = Math.max(maxY, (float) screen.y);
            }
        }

        if (maxX <= minX || maxY <= minY) return;

        drawRectOutline(context, minX, minY, maxX, maxY, color.getRGB());
    }

    public static void drawRectOutline(DrawContext context, float x1, float y1, float x2, float y2, int color) {
        context.drawHorizontalLine((int) x1, (int) x2, (int) y1, color);
        context.drawHorizontalLine((int) x1, (int) x2, (int) y2, color);
        context.drawVerticalLine((int) x1, (int) y1, (int) y2, color);
        context.drawVerticalLine((int) x2, (int) y1, (int) y2, color);
    }

    public static MatrixStack matrixFrom(double x, double y, double z) {
        MatrixStack matrices = new MatrixStack();

        Camera camera = mc.gameRenderer.getCamera();
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0F));
        matrices.translate(x - camera.getCameraPos().x, y - camera.getCameraPos().y, z - camera.getCameraPos().z);

        return matrices;
    }

    public static void drawText3D(String text, Vec3d vec3d, Color color) {
        drawText3D(Text.of(text), vec3d.x, vec3d.y, vec3d.z, 0, 0, 1, color.getRGB());
    }

    public static void drawText3D(String text, Vec3d vec3d, int color) {
        drawText3D(Text.of(text), vec3d.x, vec3d.y, vec3d.z, 0, 0, 1, color);
    }

    public static void drawText3D(Text text, Vec3d vec3d, double offX, double offY, double scale, Color color) {
        drawText3D(text, vec3d.x, vec3d.y, vec3d.z, offX, offY, scale, color.getRGB());
    }

    public static void drawText3D(Text text, double x, double y, double z, double offX, double offY, double scale, int color) {
        MatrixStack matrices = matrixFrom(x, y, z);

        Camera camera = mc.gameRenderer.getCamera();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));

        matrices.translate(offX, offY, 0);
        matrices.scale(-0.025f * (float) scale, -0.025f * (float) scale, 1);

        int halfWidth = mc.textRenderer.getWidth(text) / 2;

        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(new BufferAllocator(1536));

        mc.textRenderer.draw(text.getString(), -halfWidth, 0f, color, true, matrices.peek().getPositionMatrix(), immediate, TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xf000f0);
        immediate.draw();
    }

    // Stubbed - old Blaze3D rendering pipeline was removed in MC 1.21.11
    public static void drawFill(MatrixStack matrixStack, Box bb, Color fillColor) {}
    public static void drawBox(MatrixStack matrixStack, Box bb, Color outlineColor) {}
    public static void drawBox(MatrixStack matrixStack, Box bb, Color outlineColor, float lineWidth) {}
    public static void draw3DBox(MatrixStack matrixStack, Box box, Color fillColor, Color outlineColor) {}
    public static void draw3DBox(MatrixStack matrixStack, Box box, Color fillColor, Color outlineColor, boolean outline, boolean fill) {}
    public static void draw3DBox(MatrixStack matrixStack, Box box, Color fillColor, Color outlineColor, boolean outline, boolean fill, float lineWidth) {}
    public static void drawFadeFill(MatrixStack stack, Box box, Color c, Color c1) {}
    public static void drawLine(Vec3d start, Vec3d end, Color color) {}
    public static void drawLine(double x1, double y1, double z1, double x2, double y2, double z2, Color color, float width) {}
    public static void drawTargetEsp(MatrixStack stack, Entity target, Color color) {}
    public static void drawLineToTop3D(Entity entity, Color color) {}

    public static Vector3f getNormal(float x1, float y1, float z1, float x2, float y2, float z2) {
        float xNormal = x2 - x1;
        float yNormal = y2 - y1;
        float zNormal = z2 - z1;
        float normalSqrt = MathHelper.sqrt(xNormal * xNormal + yNormal * yNormal + zNormal * zNormal);

        return new Vector3f(xNormal / normalSqrt, yNormal / normalSqrt, zNormal / normalSqrt);
    }

    public static Color injectAlpha(Color color, int alpha) {
        alpha = Math.max(Math.min(255, alpha), 0);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    public static Color pulseColor(Color color, double index, int count, double speed) {
        float[] hsb = new float[3];
        Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), hsb);
        double brightness = Math.abs((System.currentTimeMillis() * speed % ((long) 1230675006 ^ 0x495A9BEEL) / Float.intBitsToFloat(Float.floatToIntBits(0.0013786979f) ^ 0x7ECEB56D) + index / (float) count * Float.intBitsToFloat(Float.floatToIntBits(0.09192204f) ^ 0x7DBC419F)) % Float.intBitsToFloat(Float.floatToIntBits(0.7858098f) ^ 0x7F492AD5) - Float.intBitsToFloat(Float.floatToIntBits(6.46708f) ^ 0x7F4EF252));
        brightness = Float.intBitsToFloat(Float.floatToIntBits(18.996923f) ^ 0x7E97F9B3) + Float.intBitsToFloat(Float.floatToIntBits(2.7958195f) ^ 0x7F32EEB5) * brightness;
        hsb[2] = (float) (brightness % Float.intBitsToFloat(Float.floatToIntBits(0.8992331f) ^ 0x7F663424));
        return new Color(Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]));
    }
}
