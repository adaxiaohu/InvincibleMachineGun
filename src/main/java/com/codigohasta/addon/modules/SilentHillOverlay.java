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
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;

public class SilentHillOverlay extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // 模式选择枚举
    public enum Style {
        FogWorld,   // 表世界 (普通雾气)
        Otherworld  // 里世界 (红色血锈)
    }

    private final Setting<Style> worldStyle = sgGeneral.add(new EnumSetting.Builder<Style>()
        .name("world-style")
        .description("Choose between Fog World (classic gray) or Otherworld (hellish red).")
        .defaultValue(Style.FogWorld)
        .build()
    );

    private final Setting<Double> fogDensity = sgGeneral.add(new DoubleSetting.Builder().name("fog-density").defaultValue(0.12).min(0.01).sliderMax(0.5).build());
    private final Setting<Double> grainIntensity = sgGeneral.add(new DoubleSetting.Builder().name("grain-intensity").defaultValue(0.10).min(0).sliderMax(0.5).build());
    private final Setting<Double> scanSpeed = sgGeneral.add(new DoubleSetting.Builder().name("reveal-speed").defaultValue(1.0).min(0.1).sliderMax(5.0).build());
    private final Setting<Boolean> loopScan = sgGeneral.add(new BoolSetting.Builder().name("loop-reveal").defaultValue(false).build());

    public SilentHillOverlay() {
        super(com.codigohasta.addon.AddonTemplate.CATEGORY, "silent-hill", "PS2 Horror Atmosphere Shader");
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

    @EventHandler(priority = -100)
    private void onRender3D(Render3DEvent event) {
        if (programId == -1 || vao == -1) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getFramebuffer() == null) return;

        GL20.glUseProgram(programId);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        GL20.glActiveTexture(GL20.GL_TEXTURE0);
        GL20.glBindTexture(GL20.GL_TEXTURE_2D, mc.getFramebuffer().getDepthAttachment());
        GL20.glUniform1i(GL20.glGetUniformLocation(programId, "MainDepthSampler"), 0);

        GL20.glActiveTexture(GL20.GL_TEXTURE1);
        GL20.glBindTexture(GL20.GL_TEXTURE_2D, mc.getFramebuffer().getColorAttachment());
        GL20.glUniform1i(GL20.glGetUniformLocation(programId, "MainColorSampler"), 1);

        // 参数传递
        GL20.glUniform1f(GL20.glGetUniformLocation(programId, "U_FogDensity"), fogDensity.get().floatValue());
        GL20.glUniform1f(GL20.glGetUniformLocation(programId, "U_GrainIntensity"), grainIntensity.get().floatValue());
        GL20.glUniform1f(GL20.glGetUniformLocation(programId, "U_ScanSpeed"), scanSpeed.get().floatValue());
        GL20.glUniform1f(GL20.glGetUniformLocation(programId, "U_LoopEnabled"), loopScan.get() ? 1.0f : 0.0f);
        GL20.glUniform1f(GL20.glGetUniformLocation(programId, "U_ScanDuration"), 5.0f);
        
        // 传递模式：FogWorld=0, Otherworld=1
        float modeVal = (worldStyle.get() == Style.Otherworld) ? 1.0f : 0.0f;
        GL20.glUniform1f(GL20.glGetUniformLocation(programId, "U_StyleMode"), modeVal);

        GL20.glUniform1f(GL20.glGetUniformLocation(programId, "U_GameTime"), (System.currentTimeMillis() - startTime) / 1000.0f);
        GL20.glUniform2f(GL20.glGetUniformLocation(programId, "ScreenSize"), (float)mc.getWindow().getFramebufferWidth(), (float)mc.getWindow().getFramebufferHeight());

        uploadMatrix(programId, "U_InverseProjectionMatrix", new Matrix4f(RenderSystem.getProjectionMatrix()).invert());
        uploadMatrix(programId, "U_InverseViewMatrix", new Matrix4f(RenderSystem.getModelViewMatrix()).invert());

        GL30.glBindVertexArray(vao);
        GL20.glDrawArrays(GL20.GL_TRIANGLES, 0, 6);
        GL30.glBindVertexArray(0);

        // 状态清理
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
            int v = createShader("silenthill.vsh", GL20.GL_VERTEX_SHADER);
            int f = createShader("silenthill.fsh", GL20.GL_FRAGMENT_SHADER);
            programId = GL20.glCreateProgram();
            GL20.glAttachShader(programId, v);
            GL20.glAttachShader(programId, f);
            GL20.glLinkProgram(programId);
            GL20.glDeleteShader(v); GL20.glDeleteShader(f);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private int createShader(String f, int t) throws IOException {
        int s = GL20.glCreateShader(t);
        InputStream is = MinecraftClient.getInstance().getResourceManager().getResource(Identifier.of("template", "shaders/"+f)).get().getInputStream();
        GL20.glShaderSource(s, IOUtils.toString(is, StandardCharsets.UTF_8));
        GL20.glCompileShader(s);
        return s;
    }
}