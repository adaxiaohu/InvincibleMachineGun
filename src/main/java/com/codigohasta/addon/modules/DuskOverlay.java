package com.codigohasta.addon.modules;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.util.Identifier;
import org.apache.commons.io.IOUtils;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;

public class DuskOverlay extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");

    private final Setting<Double> moodIntensity = sgGeneral.add(new DoubleSetting.Builder().name("mood-intensity").defaultValue(0.85).min(0).sliderMax(1.0).build());
    private final Setting<Double> wetness = sgGeneral.add(new DoubleSetting.Builder().name("wet-ground").defaultValue(0.3).min(0).sliderMax(1.0).build());
    private final Setting<SettingColor> skyTopColor = sgColors.add(new ColorSetting.Builder().name("sky-top").defaultValue(new SettingColor(13, 26, 51)).build());
    private final Setting<SettingColor> skyBottomColor = sgColors.add(new ColorSetting.Builder().name("sky-bottom").defaultValue(new SettingColor(51, 64, 89)).build());
    private final Setting<SettingColor> moodTint = sgColors.add(new ColorSetting.Builder().name("mood-tint").defaultValue(new SettingColor(153, 191, 230)).build());
    private final Setting<SettingColor> fogColor = sgColors.add(new ColorSetting.Builder().name("fog-color").defaultValue(new SettingColor(38, 46, 56)).build());

    private int programId = -1;
    private int vao = -1, vbo = -1;
    private long startTime;

    public DuskOverlay() {
        super(com.codigohasta.addon.AddonTemplate.CATEGORY, "dusk-overlay", "Moody Dusk (1.21.4 Fix)");
    }

    @Override
    public void onActivate() {
        startTime = System.currentTimeMillis();
        loadShader();
        initVao();
    }

    @EventHandler(priority = -500)
    private void onRender3D(Render3DEvent event) {
        if (programId == -1 || vao == -1) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        Framebuffer fb = mc.getFramebuffer();
        if (fb == null) return;

        // 1. 获取当前物理像素
        int fbWidth = fb.textureWidth;
        int fbHeight = fb.textureHeight;

        // 2. 核心状态修复 (解决窗口缩放错位和花屏格子)
        GlStateManager._disableScissorTest(); // 强制关闭剪裁，防止 Sodium 的渲染污染
        RenderSystem.viewport(0, 0, fbWidth, fbHeight);
        
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        GL20.glUseProgram(programId);

        // 3. 深度探测逻辑 (使用十六进制常量，防止映射名变动导致报错)
        int depthTextureId = -1;
        // 36006 = GL_FRAMEBUFFER_BINDING
        int currentFBO = GL30.glGetInteger(36006); 
        if (currentFBO != 0) {
            // 36160 = GL_FRAMEBUFFER, 36096 = GL_DEPTH_ATTACHMENT, 36048 = GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE
            int type = GL30.glGetFramebufferAttachmentParameteri(36160, 36096, 36048);
            if (type == 5890) { // GL_TEXTURE
                // 36049 = GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME
                depthTextureId = GL30.glGetFramebufferAttachmentParameteri(36160, 36096, 36049);
            }
        }
        if (depthTextureId <= 0) depthTextureId = fb.getDepthAttachment();

        // 4. 绑定纹理
        GL20.glActiveTexture(GL20.GL_TEXTURE0);
        GL20.glBindTexture(GL20.GL_TEXTURE_2D, depthTextureId);
        GL20.glUniform1i(GL20.glGetUniformLocation(programId, "MainDepthSampler"), 0);

        GL20.glActiveTexture(GL20.GL_TEXTURE1);
        GL20.glBindTexture(GL20.GL_TEXTURE_2D, fb.getColorAttachment());
        GL20.glUniform1i(GL20.glGetUniformLocation(programId, "MainColorSampler"), 1);

        // 5. 矩阵处理 (获取当前的投影矩阵并实时求逆)
        Matrix4f proj = new Matrix4f(RenderSystem.getProjectionMatrix());
        Matrix4f view = new Matrix4f(RenderSystem.getModelViewMatrix());
        uploadMatrix(programId, "U_InverseProjectionMatrix", proj.invert());
        uploadMatrix(programId, "U_InverseViewMatrix", view.invert());

        // 6. 传递其他 Uniforms
        GL20.glUniform2f(GL20.glGetUniformLocation(programId, "ScreenSize"), (float)fbWidth, (float)fbHeight);
        GL20.glUniform1f(GL20.glGetUniformLocation(programId, "U_GameTime"), (System.currentTimeMillis() - startTime) / 1000.0f);
        
        uploadColor(programId, "U_SkyTop", skyTopColor.get());
        uploadColor(programId, "U_SkyBottom", skyBottomColor.get());
        uploadColor(programId, "U_MoodColor", moodTint.get());
        uploadColor(programId, "U_FogColor", fogColor.get());
        GL20.glUniform1f(GL20.glGetUniformLocation(programId, "U_MoodIntensity"), moodIntensity.get().floatValue());
        GL20.glUniform1f(GL20.glGetUniformLocation(programId, "U_Wetness"), wetness.get().floatValue());

        if (mc.gameRenderer.getCamera() != null) {
            GL20.glUniform3f(GL20.glGetUniformLocation(programId, "U_CameraPosition"),
                (float)mc.gameRenderer.getCamera().getPos().x, 
                (float)mc.gameRenderer.getCamera().getPos().y, 
                (float)mc.gameRenderer.getCamera().getPos().z);
        }

        // 7. 绘制 (直接绘制 NDC 空间的 Quad)
        GL30.glBindVertexArray(vao);
        GL20.glDrawArrays(GL20.GL_TRIANGLES, 0, 6);
        GL30.glBindVertexArray(0);

        // 8. 彻底清理
        GL20.glActiveTexture(GL20.GL_TEXTURE1);
        GL20.glBindTexture(GL20.GL_TEXTURE_2D, 0);
        GL20.glActiveTexture(GL20.GL_TEXTURE0);
        GL20.glBindTexture(GL20.GL_TEXTURE_2D, 0);
        GL20.glUseProgram(0);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private void uploadColor(int program, String name, SettingColor color) {
        int loc = GL20.glGetUniformLocation(program, name);
        if (loc != -1) GL20.glUniform3f(loc, color.r / 255.0f, color.g / 255.0f, color.b / 255.0f);
    }

    private void uploadMatrix(int program, String name, Matrix4f mat) {
        int loc = GL20.glGetUniformLocation(program, name);
        if (loc != -1) {
            FloatBuffer buffer = MemoryUtil.memAllocFloat(16);
            mat.get(buffer);
            GL20.glUniformMatrix4fv(loc, false, buffer);
            MemoryUtil.memFree(buffer);
        }
    }

    private void initVao() {
        float[] vertices = { -1f, -1f, 0f, 1f, -1f, 0f, -1f, 1f, 0f, -1f, 1f, 0f, 1f, -1f, 0f, 1f, 1f, 0f };
        vao = GL30.glGenVertexArrays();
        vbo = GL20.glGenBuffers();
        GL30.glBindVertexArray(vao);
        GL20.glBindBuffer(GL20.GL_ARRAY_BUFFER, vbo);
        FloatBuffer buffer = MemoryUtil.memAllocFloat(vertices.length);
        buffer.put(vertices).flip();
        GL20.glBufferData(GL20.GL_ARRAY_BUFFER, buffer, GL20.GL_STATIC_DRAW);
        GL20.glVertexAttribPointer(0, 3, GL20.GL_FLOAT, false, 0, 0);
        GL20.glEnableVertexAttribArray(0);
        MemoryUtil.memFree(buffer);
        GL30.glBindVertexArray(0);
    }

    private void loadShader() {
        try {
            int v = createShader("dusk.vsh", GL20.GL_VERTEX_SHADER);
            int f = createShader("dusk.fsh", GL20.GL_FRAGMENT_SHADER);
            programId = GL20.glCreateProgram();
            GL20.glAttachShader(programId, v);
            GL20.glAttachShader(programId, f);
            GL20.glLinkProgram(programId);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private int createShader(String fileName, int type) throws IOException {
        int shaderId = GL20.glCreateShader(type);
        Identifier id = Identifier.of("template", "shaders/" + fileName);
        InputStream is = MinecraftClient.getInstance().getResourceManager().getResource(id).get().getInputStream();
        GL20.glShaderSource(shaderId, IOUtils.toString(is, StandardCharsets.UTF_8));
        GL20.glCompileShader(shaderId);
        return shaderId;
    }
}