package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlBackend;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;

abstract class FullscreenShaderModule extends Module {
    private final SettingGroup area = settings.createGroup("作用范围");
    private final Setting<Boolean> renderSky = area.add(new BoolSetting.Builder()
        .name("天空着色").description("对天空范围应用着色器效果。")
        .defaultValue(true).build());
    private final Setting<Boolean> renderGround = area.add(new BoolSetting.Builder()
        .name("地面着色").description("对地面、方块和实体等非天空范围应用着色器效果。")
        .defaultValue(true).build());

    private final Identifier vertexShader;
    private final Identifier fragmentShader;
    private final String textureLabel;

    private int program = -1;
    private int vao = -1;
    private int vbo = -1;
    private GpuTexture sceneTexture;
    private int sceneTextureWidth = -1;
    private int sceneTextureHeight = -1;
    private long startedAtNanos;
    private boolean initializationPending;

    protected FullscreenShaderModule(String name, String description, String shaderName) {
        super(AddonTemplate.CATEGORY, name, description);
        vertexShader = Identifier.of("shader", shaderName + ".vsh");
        fragmentShader = Identifier.of("shader", shaderName + ".fsh");
        textureLabel = name + " scene copy";
    }

    @Override
    public final void onActivate() {
        startedAtNanos = System.nanoTime();
        initializationPending = true;
    }

    @Override
    public final void onDeactivate() {
        initializationPending = false;
        if (RenderSystem.isOnRenderThread()) destroyGlObjects();
        else RenderSystem.queueFencedTask(() -> {
            if (!isActive()) destroyGlObjects();
        });
    }

    @EventHandler(priority = -200)
    protected final void onRender(Render3DEvent event) {
        if (initializationPending) {
            initializationPending = false;
            try {
                destroyGlObjects();
                program = createProgram();
                createFullscreenQuad();
                info("着色器已启用。");
            } catch (RuntimeException e) {
                error("着色器加载失败：%s", e.getMessage());
                destroyGlObjects();
                toggle();
            }
            return;
        }

        if (program == -1 || vao == -1) return;

        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer framebuffer = client.getFramebuffer();
        if (!(framebuffer.getDepthAttachment() instanceof GlTexture depthTexture)
            || !(framebuffer.getColorAttachment() instanceof GlTexture colorTexture)
            || !(RenderSystem.getDevice() instanceof GlBackend backend)) return;

        RenderSystem.assertOnRenderThread();

        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int previousTexture0 = textureBinding(GL13.GL_TEXTURE0);
        int previousTexture1 = textureBinding(GL13.GL_TEXTURE1);
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] previousViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, previousViewport);

        try {
            int width = framebuffer.textureWidth;
            int height = framebuffer.textureHeight;
            copySceneColor(colorTexture, width, height);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,
                colorTexture.getOrCreateFramebuffer(backend.getBufferManager(), null));
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glViewport(0, 0, width, height);

            GL20.glUseProgram(program);
            bindTexture("MainDepthSampler", GL13.GL_TEXTURE0, depthTexture.getGlId());
            bindTexture("MainColorSampler", GL13.GL_TEXTURE1, ((GlTexture) sceneTexture).getGlId());

            float elapsedSeconds = (System.nanoTime() - startedAtNanos) / 1_000_000_000.0f;
            uniform2f("ScreenSize", width, height);
            uniform1f("U_GameTime", elapsedSeconds);
            uniform1f("U_SkyEnabled", renderSky.get() ? 1.0f : 0.0f);
            uniform1f("U_GroundEnabled", renderGround.get() ? 1.0f : 0.0f);

            var camera = client.gameRenderer.getCamera();
            var cameraPos = camera.getCameraPos();
            uniform3f("U_CameraPosition", (float) cameraPos.x, (float) cameraPos.y, (float) cameraPos.z);
            uniformMatrix4f("U_InverseProjectionMatrix", new Matrix4f(RenderUtils.projection).invert());
            uniformMatrix4f("U_InverseViewMatrix", new Matrix4f().rotation(camera.getRotation()));
            configureUniforms(event, elapsedSeconds);

            GL30.glBindVertexArray(vao);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        } finally {
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture1);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture0);
            GL13.glActiveTexture(previousActiveTexture);
            GL30.glBindVertexArray(previousVao);
            GL20.glUseProgram(previousProgram);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            GL11.glViewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3]);
            setEnabled(GL11.GL_BLEND, blend);
            setEnabled(GL11.GL_DEPTH_TEST, depthTest);
            setEnabled(GL11.GL_SCISSOR_TEST, scissor);
            GL11.glDepthMask(depthMask);
        }
    }

    protected abstract void configureUniforms(Render3DEvent event, float elapsedSeconds);

    protected final void uniform1f(String name, float value) {
        int location = GL20.glGetUniformLocation(program, name);
        if (location >= 0) GL20.glUniform1f(location, value);
    }

    protected final void uniform3f(String name, float x, float y, float z) {
        int location = GL20.glGetUniformLocation(program, name);
        if (location >= 0) GL20.glUniform3f(location, x, y, z);
    }

    protected final void uniformColor(String name, SettingColor color) {
        uniform3f(name, color.r / 255.0f, color.g / 255.0f, color.b / 255.0f);
    }

    private void copySceneColor(GpuTexture source, int width, int height) {
        if (sceneTexture == null || width != sceneTextureWidth || height != sceneTextureHeight) {
            if (sceneTexture != null) sceneTexture.close();
            sceneTexture = RenderSystem.getDevice().createTexture(
                textureLabel,
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                source.getFormat(), width, height, 1, 1);
            sceneTextureWidth = width;
            sceneTextureHeight = height;
        }

        RenderSystem.getDevice().createCommandEncoder()
            .copyTextureToTexture(source, sceneTexture, 0, 0, 0, 0, 0, width, height);
    }

    private int textureBinding(int unit) {
        int old = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(unit);
        int binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL13.glActiveTexture(old);
        return binding;
    }

    private void setEnabled(int capability, boolean enabled) {
        if (enabled) GL11.glEnable(capability);
        else GL11.glDisable(capability);
    }

    private void bindTexture(String uniform, int unit, int texture) {
        GL13.glActiveTexture(unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        int location = GL20.glGetUniformLocation(program, uniform);
        if (location >= 0) GL20.glUniform1i(location, unit - GL13.GL_TEXTURE0);
    }

    protected final void uniform2f(String name, float x, float y) {
        int location = GL20.glGetUniformLocation(program, name);
        if (location >= 0) GL20.glUniform2f(location, x, y);
    }

    private void uniformMatrix4f(String name, Matrix4f matrix) {
        int location = GL20.glGetUniformLocation(program, name);
        if (location < 0) return;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(16);
            matrix.get(buffer);
            GL20.glUniformMatrix4fv(location, false, buffer);
        }
    }

    private int createProgram() {
        int vertex = compileShader(vertexShader, GL20.GL_VERTEX_SHADER);
        int fragment = compileShader(fragmentShader, GL20.GL_FRAGMENT_SHADER);
        int newProgram = GL20.glCreateProgram();
        try {
            GL20.glAttachShader(newProgram, vertex);
            GL20.glAttachShader(newProgram, fragment);
            GL20.glBindAttribLocation(newProgram, 0, "Position");
            GL20.glLinkProgram(newProgram);
            if (GL20.glGetProgrami(newProgram, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                throw new IllegalStateException("程序链接失败：" + GL20.glGetProgramInfoLog(newProgram));
            }
            return newProgram;
        } catch (RuntimeException e) {
            GL20.glDeleteProgram(newProgram);
            throw e;
        } finally {
            GL20.glDeleteShader(vertex);
            GL20.glDeleteShader(fragment);
        }
    }

    private int compileShader(Identifier id, int type) {
        String source;
        Resource resource = mc.getResourceManager().getResource(id)
            .orElseThrow(() -> new IllegalStateException("找不到资源 " + id));
        try (InputStream stream = resource.getInputStream()) {
            source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }

        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException(id + " 编译失败：" + log);
        }
        return shader;
    }

    private void createFullscreenQuad() {
        float[] vertices = {
            -1f, -1f, 0f, 1f, -1f, 0f, -1f, 1f, 0f,
            -1f, 1f, 0f, 1f, -1f, 0f, 1f, 1f, 0f
        };
        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STATIC_DRAW);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 0, 0L);
        GL20.glEnableVertexAttribArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
    }

    private void destroyGlObjects() {
        if (vbo != -1) GL15.glDeleteBuffers(vbo);
        if (vao != -1) GL30.glDeleteVertexArrays(vao);
        if (program != -1) GL20.glDeleteProgram(program);
        if (sceneTexture != null) sceneTexture.close();
        vbo = vao = program = -1;
        sceneTexture = null;
        sceneTextureWidth = sceneTextureHeight = -1;
    }
}
