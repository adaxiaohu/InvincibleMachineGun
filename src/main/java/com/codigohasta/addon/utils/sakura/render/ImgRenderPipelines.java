package com.codigohasta.addon.utils.sakura.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.renderer.MeshBuilder;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.gl.UniformType;
import net.minecraft.util.Identifier;

/**
 * 本项目自定义的渲染管线。
 *
 * <p>为什么需要它：Sakura 的 TargetHud 在 3D 事件里用
 * {@code Tessellator + VertexFormats.POSITION_TEXTURE_COLOR} 画一个带贴图的世界空间四边形。
 * 1.21.11 里这套 API 换成了 {@code RenderPipeline}，而 Meteor 只提供了：
 * <ul>
 *     <li>{@code UI_TEXTURED}：有贴图，但顶点是 2D 位置（屏幕空间）</li>
 *     <li>{@code WORLD_COLORED}：世界空间顶点，但没有贴图（顶点格式里没有 UV）</li>
 * </ul>
 * 两者都不能直接满足需求，因此这里按 Meteor {@code MeteorRenderPipelines} 的写法自建一条
 * 「世界空间 + 贴图 + 顶点色」管线，着色器放在本 addon 的资源目录下。
 *
 * <p>uniform {@code MeshData}（u_Proj / u_ModelView）由 Meteor 的 {@code MeshRenderer} 统一写入，
 * 所以这里只需声明，不需要自己维护 UBO。
 */
public final class ImgRenderPipelines {

    public static final VertexFormat POS3_TEXTURE_COLOR = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)
        .add("Texture", VertexFormatElement.UV)
        .add("Color", VertexFormatElement.COLOR)
        .build();

    /**
     * 世界空间带贴图四边形。
     *
     * <p>深度测试用 {@code NO_DEPTH_TEST} + 关闭深度写入，与原版 TargetHud 的
     * {@code disableDepthTest()} 行为一致（透视标记要能穿过方块看到）。
     *
     * <p><b>着色器 Identifier 的写法很关键</b>：引擎用
     * {@code ShaderType.idConverter()}（即 {@code ResourceFinder("shaders", ".vsh"/".fsh")}）
     * 把 Identifier 解析成资源路径，规则是
     * {@code "<ns>:<path>" -> "assets/<ns>/shaders/<path>.vsh"}。
     * 所以这里必须写 {@code core/pos_tex_color_3d}（不含 {@code shaders/} 前缀、不含扩展名），
     * 对应文件 {@code assets/invincible_machine_gun/shaders/core/pos_tex_color_3d.vsh}。
     * 这样与 Minecraft 自带核心着色器的写法（{@code minecraft:core/screenquad}）一致。
     */
    public static final RenderPipeline WORLD_TEXTURED = RenderPipeline.builder()
        .withLocation(Identifier.of("invincible_machine_gun", "pipeline/world_textured"))
        .withVertexFormat(POS3_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
        .withVertexShader(Identifier.of("invincible_machine_gun", "core/pos_tex_color_3d"))
        .withFragmentShader(Identifier.of("invincible_machine_gun", "core/pos_tex_color_3d"))
        .withSampler("u_Texture")
        .withUniform("MeshData", UniformType.UNIFORM_BUFFER)
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .withDepthWrite(false)
        .withBlend(BlendFunction.TRANSLUCENT)
        .withCull(false)
        .build();

    private ImgRenderPipelines() {}

    /**
     * 预编译并判断 WORLD_TEXTURED 是否可用。
     *
     * <p>引擎在着色器缺失或编译失败时不会抛异常，而是返回 {@code ShaderProgram.INVALID}
     * 并把错误写进日志；因此这里检查返回的 program 是否为 INVALID。
     *
     * @return 管线可用返回 {@code true}
     */
    public static boolean isWorldTexturedAvailable() {
        if (!RenderSystem.isOnRenderThread()) return false;

        try {
            var compiled = RenderSystem.getDevice().precompilePipeline(WORLD_TEXTURED);
            if (compiled instanceof net.minecraft.client.gl.CompiledShaderPipeline pipeline) {
                return pipeline.program() != null
                    && pipeline.program() != net.minecraft.client.gl.ShaderProgram.INVALID;
            }
            return false;
        } catch (Throwable t) {
            MeteorClient.LOG.error("[IMG] 世界空间贴图管线预编译失败", t);
            return false;
        }
    }

    /**
     * 按世界空间顶点构建一个带贴图的四边形网格。
     *
     * <p><b>必须在写顶点前调用 {@code ensureQuadCapacity()}</b>：{@code MeshBuilder} 的
     * 顶点缓冲由 {@code ensureCapacity()} 惰性分配，直接写会向 null 指针写入
     * （native 层 {@code EXCEPTION_ACCESS_VIOLATION}，整个 JVM 崩溃）。
     *
     * @param x1,z1 / x2,z2 水平两个角；y1 下边，y2 上边
     */
    public static MeshBuilder buildWorldQuad(double x1, double z1, double x2, double z2,
                                             double y1, double y2,
                                             Color c1, Color c2, Color c3, Color c4) {
        MeshBuilder mesh = new MeshBuilder(WORLD_TEXTURED);
        mesh.begin();
        mesh.ensureQuadCapacity();

        // UV 与原版一致：左上(0,0) 左下(0,1) 右下(1,1) 右上(1,0)
        mesh.quad(
            mesh.vec3(x1, y2, z1).vec2(0f, 0f).color(c1).next(),
            mesh.vec3(x1, y1, z1).vec2(0f, 1f).color(c2).next(),
            mesh.vec3(x2, y1, z2).vec2(1f, 1f).color(c3).next(),
            mesh.vec3(x2, y2, z2).vec2(1f, 0f).color(c4).next()
        );

        mesh.end();
        return mesh;
    }
}
