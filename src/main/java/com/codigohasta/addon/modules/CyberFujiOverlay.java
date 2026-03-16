package com.codigohasta.addon.modules;

import com.mojang.blaze3d.systems.RenderSystem;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
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

public class CyberFujiOverlay extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> gridSpeed = sgGeneral.add(new DoubleSetting.Builder().name("grid-speed").defaultValue(2.0).min(0).sliderMax(10).build());
    private final Setting<Double> sunSpeed = sgGeneral.add(new DoubleSetting.Builder().name("sun-speed").defaultValue(0.2).min(0).sliderMax(2.0).build());
    
    // 呼吸灯速度设置
    private final Setting<Double> pulseSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("pulse-speed")
        .description("Speed of the grid breathing light effect.")
        .defaultValue(2.0)
        .min(0.0)
        .sliderMax(10.0)
        .build()
    );

    private final Setting<Double> scanSpeed = sgGeneral.add(new DoubleSetting.Builder().name("scan-speed").defaultValue(1.0).min(0.1).sliderMax(5.0).build());
    private final Setting<Boolean> loopScan = sgGeneral.add(new BoolSetting.Builder().name("loop-scan").defaultValue(false).build());
    private final Setting<Double> scanDuration = sgGeneral.add(new DoubleSetting.Builder().name("scan-duration").defaultValue(3.0).min(1.0).sliderMax(10.0).visible(loopScan::get).build());

    public CyberFujiOverlay() {
        super(com.codigohasta.addon.AddonTemplate.CATEGORY, "cyber-fuji", "Synthwave Radar Shader (With Pulse Control)");
    }

    private int programId = -1;
    private int vao = -1, vbo = -1;
    private long startTime;

    @Override
    public void onActivate() {
        startTime = System.currentTimeMillis();
        loadShader();
        initVao();
    }

    @EventHandler(priority = -200)
    private void onRender3D(Render3DEvent event) {
        if (programId == -1 || vao == -1) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getFramebuffer() == null) return;

        // 状态保护与物理视口修复
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        RenderSystem.viewport(0, 0, mc.getWindow().getFramebufferWidth(), mc.getWindow().getFramebufferHeight());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        GL20.glUseProgram(programId);

        // 纹理绑定
        GL20.glActiveTexture(GL20.GL_TEXTURE0);
        GL20.glBindTexture(GL20.GL_TEXTURE_2D, mc.getFramebuffer().getDepthAttachment());
        GL20.glUniform1i(GL20.glGetUniformLocation(programId, "MainDepthSampler"), 0);

        GL20.glActiveTexture(GL20.GL_TEXTURE1);
        GL20.glBindTexture(GL20.GL_TEXTURE_2D, mc.getFramebuffer().getColorAttachment());
        GL20.glUniform1i(GL20.glGetUniformLocation(programId, "MainColorSampler"), 1);

        // 传递尺寸：分离视口屏幕尺寸与FrameBuffer真实纹理尺寸
        GL20.glUniform2f(GL20.glGetUniformLocation(programId, "ScreenSize"), (float)mc.getWindow().getFramebufferWidth(), (float)mc.getWindow().getFramebufferHeight());
        GL20.glUniform2f(GL20.glGetUniformLocation(programId, "FboSize"), (float)mc.getFramebuffer().textureWidth, (float)mc.getFramebuffer().textureHeight);

        // 传递 Uniforms
        GL20.glUniform1f(GL20.glGetUniformLocation(programId, "U_GridSpeed"), gridSpeed.get().floatValue());
        GL20.glUniform1f(GL20.glGetUniformLocation(programId, "U_SunSpeed"), sunSpeed.get().floatValue());
        GL20.glUniform1f(GL20.glGetUniformLocation(programId, "U_PulseSpeed"), pulseSpeed.get().floatValue()); 
        GL20.glUniform1f(GL20.glGetUniformLocation(programId, "U_ScanSpeed"), scanSpeed.get().floatValue());
        GL20.glUniform1f(GL20.glGetUniformLocation(programId, "U_LoopEnabled"), loopScan.get() ? 1.0f : 0.0f);
        GL20.glUniform1f(GL20.glGetUniformLocation(programId, "U_ScanDuration"), scanDuration.get().floatValue());
        GL20.glUniform1f(GL20.glGetUniformLocation(programId, "U_GameTime"), (System.currentTimeMillis() - startTime) / 1000.0f);

        if (mc.gameRenderer.getCamera() != null) {
            GL20.glUniform3f(GL20.glGetUniformLocation(programId, "U_CameraPosition"),
                (float)mc.gameRenderer.getCamera().getPos().x, (float)mc.gameRenderer.getCamera().getPos().y, (float)mc.gameRenderer.getCamera().getPos().z);
        }

        // 核心修复：1.21.x获取准确矩阵的方法，避免视角旋转导致的网格偏移
        Matrix4f projMat = new Matrix4f(RenderSystem.getProjectionMatrix());
        Matrix4f viewMat = new Matrix4f(event.matrices.peek().getPositionMatrix());
        
        Matrix4f invProj = projMat.invert();
        Matrix4f invView = viewMat.invert();
        
        uploadMatrix(programId, "U_InverseProjectionMatrix", invProj);
        uploadMatrix(programId, "U_InverseViewMatrix", invView);

        // 绘图
        GL30.glBindVertexArray(vao);
        GL20.glDrawArrays(GL20.GL_TRIANGLES, 0, 6);
        GL30.glBindVertexArray(0);

        // 彻底清理状态：防止手持物品红块，防止透明方块渲染错误
        GL20.glActiveTexture(GL20.GL_TEXTURE1);
        GL20.glBindTexture(GL20.GL_TEXTURE_2D, 0);
        GL20.glActiveTexture(GL20.GL_TEXTURE0);
        GL20.glBindTexture(GL20.GL_TEXTURE_2D, 0);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        GL20.glUseProgram(0);
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
        // 全屏 Quad 顶点
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
            int v = createShader("synthwave.vsh", GL20.GL_VERTEX_SHADER);
            int f = createShader("synthwave.fsh", GL20.GL_FRAGMENT_SHADER);
            programId = GL20.glCreateProgram();
            GL20.glAttachShader(programId, v);
            GL20.glAttachShader(programId, f);
            GL20.glLinkProgram(programId);
            GL20.glDeleteShader(v); GL20.glDeleteShader(f);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private int createShader(String f, int t) throws IOException {
        int s = GL20.glCreateShader(t);
        Identifier id = Identifier.of("template", "shaders/" + f);
        InputStream is = MinecraftClient.getInstance().getResourceManager().getResource(id).get().getInputStream();
        GL20.glShaderSource(s, IOUtils.toString(is, StandardCharsets.UTF_8));
        GL20.glCompileShader(s);
        return s;
    }
}