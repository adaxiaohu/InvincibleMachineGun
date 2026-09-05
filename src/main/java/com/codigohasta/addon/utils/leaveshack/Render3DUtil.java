package com.codigohasta.addon.utils.leaveshack;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import com.mojang.math.Axis;
import net.minecraft.world.phys.Vec3;
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

    private static void drawWithShadow(PoseStack matrices, String info, float x, float y, int color) {
        var immediate = mc.renderBuffers().bufferSource();
        mc.font.drawInBatch(info, x, y, color, true, matrices.last().pose(), immediate, Font.DisplayMode.SEE_THROUGH, 0, 0xf000f0);
        immediate.endBatch();
    }

    public static void renderText3D(String info, Vec3 targetPos, int color) {
        Camera camera = mc.gameRenderer.getMainCamera();
        GL11.glDepthFunc(GL11.GL_ALWAYS);
        PoseStack matrixStack = new PoseStack();
        double x = targetPos.x();
        double y = targetPos.y();
        double z = targetPos.z();
        int width = mc.font.width(info);
        float hwidth = width / 2.0f;
        renderInfo(info, hwidth, x, y, z, camera, matrixStack, color);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
    }

    public static void renderInfo(String info, float width, double x, double y, double z, Camera camera, PoseStack matrices, int color) {
        final Vec3 pos = camera.position();
        float scale = (float) (-0.025f + (pos.distanceToSqr(x, y, z) > (6 * 6) ? (Math.sqrt(pos.distanceToSqr(x, y, z)) - 6) * -0.0025f : 0));
        matrices.pushPose();
        matrices.mulPose(Axis.XP.rotationDegrees(camera.xRot()));
        matrices.mulPose(Axis.YP.rotationDegrees(camera.yRot() + 180.0f));
        matrices.translate(x - pos.x(),
                y - pos.y() + (scale / -0.025f - 1) / 4,
                z - pos.z());
        matrices.mulPose(Axis.YP.rotationDegrees(-camera.yRot()));
        matrices.mulPose(Axis.XP.rotationDegrees(camera.xRot()));

        matrices.scale(scale, scale, -1.0f);

        drawWithShadow(matrices, info, -width, 0.0f, color);

        matrices.popPose();
    }

    public static Vec3 worldSpaceToScreenSpace(Vec3 pos) {
        Camera camera = mc.getEntityRenderDispatcher().camera;
        int displayHeight = mc.getWindow().getHeight();
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        Vector3f target = new Vector3f();

        double deltaX = pos.x - camera.position().x;
        double deltaY = pos.y - camera.position().y;
        double deltaZ = pos.z - camera.position().z;

        Vector4f transformedCoordinates = new Vector4f((float) deltaX, (float) deltaY, (float) deltaZ, 1.f).mul(lastWorldSpaceMatrix);
        Matrix4f matrixProj = new Matrix4f(lastProjMat);
        Matrix4f matrixModel = new Matrix4f(lastModMat);
        matrixProj.mul(matrixModel).project(transformedCoordinates.x(), transformedCoordinates.y(), transformedCoordinates.z(), viewport, target);
        return new Vec3(target.x / mc.getWindow().getGuiScale(), (displayHeight - target.y) / mc.getWindow().getGuiScale(), target.z);
    }

    public static void drawTargetBox2D(GuiGraphicsExtractor context, Entity entity, Color color) {
        if (entity == null) return;

        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();

        var box = entity.getBoundingBox().move(
                x - entity.getX(),
                y - entity.getY(),
                z - entity.getZ()
        );

        Vec3[] points = new Vec3[]{
                new Vec3(box.minX, box.minY, box.minZ),
                new Vec3(box.minX, box.maxY, box.minZ),
                new Vec3(box.maxX, box.minY, box.minZ),
                new Vec3(box.maxX, box.maxY, box.minZ),
                new Vec3(box.minX, box.minY, box.maxZ),
                new Vec3(box.minX, box.maxY, box.maxZ),
                new Vec3(box.maxX, box.minY, box.maxZ),
                new Vec3(box.maxX, box.maxY, box.maxZ)
        };

        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -1;
        float maxY = -1;

        for (Vec3 point : points) {
            Vec3 screen = worldSpaceToScreenSpace(point);

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

    public static void drawRectOutline(GuiGraphicsExtractor context, float x1, float y1, float x2, float y2, int color) {
        context.horizontalLine((int) x1, (int) x2, (int) y1, color);
        context.horizontalLine((int) x1, (int) x2, (int) y2, color);
        context.verticalLine((int) x1, (int) y1, (int) y2, color);
        context.verticalLine((int) x2, (int) y1, (int) y2, color);
    }

    public static PoseStack matrixFrom(double x, double y, double z) {
        PoseStack matrices = new PoseStack();

        Camera camera = mc.gameRenderer.getMainCamera();
        matrices.mulPose(Axis.XP.rotationDegrees(camera.xRot()));
        matrices.mulPose(Axis.YP.rotationDegrees(camera.yRot() + 180.0F));
        matrices.translate(x - camera.position().x, y - camera.position().y, z - camera.position().z);

        return matrices;
    }

    public static void drawText3D(String text, Vec3 vec3d, Color color) {
        drawText3D(Component.literal(text), vec3d.x, vec3d.y, vec3d.z, 0, 0, 1, color.getRGB());
    }

    public static void drawText3D(String text, Vec3 vec3d, int color) {
        drawText3D(Component.literal(text), vec3d.x, vec3d.y, vec3d.z, 0, 0, 1, color);
    }

    public static void drawText3D(Component text, Vec3 vec3d, double offX, double offY, double scale, Color color) {
        drawText3D(text, vec3d.x, vec3d.y, vec3d.z, offX, offY, scale, color.getRGB());
    }

    public static void drawText3D(Component text, double x, double y, double z, double offX, double offY, double scale, int color) {
        PoseStack matrices = matrixFrom(x, y, z);

        Camera camera = mc.gameRenderer.getMainCamera();
        matrices.mulPose(Axis.YP.rotationDegrees(-camera.yRot()));
        matrices.mulPose(Axis.XP.rotationDegrees(camera.xRot()));

        matrices.translate(offX, offY, 0);
        matrices.scale(-0.025f * (float) scale, -0.025f * (float) scale, 1);

        int halfWidth = mc.font.width(text) / 2;

        MultiBufferSource.BufferSource immediate = MultiBufferSource.immediate(new ByteBufferBuilder(1536));

        mc.font.drawInBatch(text.getString(), -halfWidth, 0f, color, true, matrices.last().pose(), immediate, Font.DisplayMode.SEE_THROUGH, 0, 0xf000f0);
        immediate.endBatch();
    }

    // Stubbed - old Blaze3D rendering pipeline was removed in MC 1.21.11
    public static void drawFill(PoseStack matrixStack, AABB bb, Color fillColor) {}
    public static void drawBox(PoseStack matrixStack, AABB bb, Color outlineColor) {}
    public static void drawBox(PoseStack matrixStack, AABB bb, Color outlineColor, float lineWidth) {}
    public static void draw3DBox(PoseStack matrixStack, AABB box, Color fillColor, Color outlineColor) {}
    public static void draw3DBox(PoseStack matrixStack, AABB box, Color fillColor, Color outlineColor, boolean outline, boolean fill) {}
    public static void draw3DBox(PoseStack matrixStack, AABB box, Color fillColor, Color outlineColor, boolean outline, boolean fill, float lineWidth) {}
    public static void drawFadeFill(PoseStack stack, AABB box, Color c, Color c1) {}
    public static void drawLine(Vec3 start, Vec3 end, Color color) {}
    public static void drawLine(double x1, double y1, double z1, double x2, double y2, double z2, Color color, float width) {}
    public static void drawTargetEsp(PoseStack stack, Entity target, Color color) {}
    public static void drawLineToTop3D(Entity entity, Color color) {}

    public static Vector3f getNormal(float x1, float y1, float z1, float x2, float y2, float z2) {
        float xNormal = x2 - x1;
        float yNormal = y2 - y1;
        float zNormal = z2 - z1;
        float normalSqrt = Mth.sqrt(xNormal * xNormal + yNormal * yNormal + zNormal * zNormal);

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
