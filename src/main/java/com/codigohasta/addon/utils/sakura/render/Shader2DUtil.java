package com.codigohasta.addon.utils.sakura.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlBackend;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class Shader2DUtil {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private static final Identifier GLITCH_VS =
        Identifier.of("invincible_machine_gun", "shaders/glitch.vsh");
    private static final Identifier GLITCH_FS =
        Identifier.of("invincible_machine_gun", "shaders/glitch.fsh");
    private static final Identifier BLUR_VS =
        Identifier.of("invincible_machine_gun", "shaders/blur_rect.vsh");
    private static final Identifier BLUR_FS =
        Identifier.of("invincible_machine_gun", "shaders/blur_rect.fsh");

    private static final Identifier GLASS_VS =
        Identifier.of("invincible_machine_gun", "shaders/glass_rect.vsh");
    private static final Identifier GLASS_FS =
        Identifier.of("invincible_machine_gun", "shaders/glass_rect.fsh");

    /**
     * SDF 圆角矩形着色器，用来替代 NanoVG 的 {@code nvgRoundedRect}。
     *
     * <p>几何上只画一个全屏四边形，圆角由片元着色器按到边界的距离（SDF）算出来并做抗锯齿。
     * 好处是顶点数恒定、边缘永远平滑，且描边天然支持。
     */
    private static final Identifier RECT_VS =
        Identifier.of("invincible_machine_gun", "shaders/rect.vsh");
    private static final Identifier RECT_FS =
        Identifier.of("invincible_machine_gun", "shaders/rect.fsh");
    /** 贴图版 SDF 圆角，用于头像的圆角裁剪（替代 NanoVG 的 image pattern）。 */
    private static final Identifier RECT_TEX_FS =
        Identifier.of("invincible_machine_gun", "shaders/rect_textured.fsh");
    /** 圆角矩形的羽化阴影（与 {@link #RECT_VS} 配对，共用同一套坐标约定）。 */
    private static final Identifier SHADOW_FS =
        Identifier.of("invincible_machine_gun", "shaders/shadow_rect.fsh");
    /** 圆角矩形的双色渐变（血条的流动渐变）。 */
    private static final Identifier RECT_GRADIENT_FS =
        Identifier.of("invincible_machine_gun", "shaders/rect_gradient.fsh");

    /** 已编译的程序：贴图 -> GL program id；-1 表示尚未编译。 */
    private static final Map<ProgramKey, Integer> PROGRAMS = new HashMap<>();
    /** 编译失败的程序（避免每帧重试刷日志）。 */
    private static final Map<ProgramKey, Boolean> FAILED = new HashMap<>();
    /** program -> (uniform 名 -> location)。 */
    private static final Map<Integer, Map<String, Integer>> UNIFORM_CACHE = new HashMap<>();

    private static int vao = -1;
    private static int vbo = -1;

    /** 主帧缓冲的颜色拷贝，作为两个效果共同的输入。 */
    private static GpuTexture sceneTexture;
    private static int sceneTextureWidth = -1;
    private static int sceneTextureHeight = -1;

    private record ProgramKey(Identifier vertex, Identifier fragment) {}

    private Shader2DUtil() {}

    // ═══════════════════════════════════════════════════════
    //  对外接口
    // ═══════════════════════════════════════════════════════

    /**
     * 绘制圆角矩形背景模糊（默认<b>按帧缓存</b>场景拷贝）。
     *
     * <p><b>2026-10-07 性能修复</b>：本重载原本默认 {@code false}
     * （= 每个面板各自做一次整屏拷贝，约 8.3MB）。审计确证全开时
     * 约 42 次/帧 ≈ 350MB/帧 ≈ 21GB/s 纯浪费。
     *
     * <p>现在中央有了 {@link SakuraFrameTicker}（挂在 {@code Render2DEvent}
     * 的最高优先级上，每帧推进一次帧号），所以默认改为 {@code true}
     * —— 一帧内所有面板<b>共用一次</b>场景拷贝。
     * 前提是调用方都走 {@code Render2DEvent}（项目内所有 HUD 模块都是）。
     *
     * @param c1           叠加色
     * @param blurStrength 模糊强度
     * @param blurOpacity  不透明度 0~1
     */
    public static void drawRoundedBlur(float x, float y, float width, float height, float radius,
                                       Color c1, float blurStrength, float blurOpacity) {
        drawRoundedBlur(x, y, width, height, radius, c1, blurStrength, blurOpacity, true);
    }

    /**
     * 圆角模糊（可选择<b>按帧缓存</b>场景拷贝）。
     *
     * <p><b>为什么需要这个重载</b>：场景拷贝是整屏纹理复制（1920×1080 ≈ 8MB）。
     * 一帧里画 30 个面板就是 30 次拷贝 ≈ 256MB/帧（60fps 下约 15GB/s），
     * 单这一项就能吃掉整个帧预算。而同一帧里所有面板采的本来就是<b>同一张</b>
     * 「面板之前的画面」，缓存是安全的。
     *
     * <p><b>调用方必须遵守的约定</b>：传 {@code cacheSceneForFrame = true} 时，
     * 必须<b>每帧调用一次</b> {@link #beginFrame()}。否则帧号不推进，
     * 缓存会永远命中第一次的画面 —— 表现为<b>模糊冻结不更新</b>。
     * 这也是本方法默认 {@code false}（如实拷贝）的原因：没推进帧号的调用方
     * 保持旧行为，不会被这个缓存坑到。
     */
    public static void drawRoundedBlur(float x, float y, float width, float height, float radius,
                                       Color c1, float blurStrength, float blurOpacity,
                                       boolean cacheSceneForFrame) {
        float opacity = Math.max(0f, Math.min(1f, blurOpacity));
        int program = ensureProgram(new ProgramKey(BLUR_VS, BLUR_FS));
        if (program == -1) return;
        if (!copySceneColor(cacheSceneForFrame)) return;

        Framebuffer framebuffer = mc.getFramebuffer();
        float scale = (float) mc.getWindow().getScaleFactor();

        // 与 Sakura 原实现一致：像素坐标 + gl_FragCoord 原点在左下角
        final float locX = x * scale;
        final float locY = -y * scale + framebuffer.textureHeight - height * scale;
        final float sizeX = width * scale;
        final float sizeY = height * scale;

        // ★ viewport 余量：blur_rect.fsh 的采样半径是 Quality（像素），
        //   每个片元会采到半径外的纹理；虽然输出像素仍只在矩形内（discard 掉外侧），
        //   但为保险起见（SDF 抗锯齿 + 边界半个像素）多给 2px。
        final float blurMargin = blurStrength + 2f;

        drawFullscreen(program, framebuffer, () -> {
            bindSceneTexture(program, "InputSampler");
            uniform2f(program, "InputResolution", framebuffer.textureWidth, framebuffer.textureHeight);
            uniform2f(program, "uLocation", locX, locY);
            uniform2f(program, "uSize", sizeX, sizeY);
            uniform1f(program, "radius", radius * scale);
            uniform1f(program, "Brightness", opacity);
            uniform1f(program, "Quality", blurStrength);
            uniform4f(program, "color1",
                c1.getRed() / 255f, c1.getGreen() / 255f, c1.getBlue() / 255f, c1.getAlpha() / 255f);
        }, boundsPx(locX, locY, sizeX, sizeY), blurMargin);
    }

    public static void drawGlassRect(float x, float y, float width, float height, float radius,
                                     meteordevelopment.meteorclient.utils.render.color.Color color,
                                     float mix, float border,
                                     meteordevelopment.meteorclient.utils.render.color.Color borderColor) {
        drawGlassRect(x, y, width, height, radius, color, mix, border, borderColor, 6f);
    }

    public static void drawGlassRect(float x, float y, float width, float height, float radius,
                                     meteordevelopment.meteorclient.utils.render.color.Color color,
                                     float mix, float border,
                                     meteordevelopment.meteorclient.utils.render.color.Color borderColor,
                                     float blurRadius) {
        if (width <= 0f || height <= 0f || color == null || color.a <= 0) return;

        int program = ensureProgram(new ProgramKey(GLASS_VS, GLASS_FS));
        if (program == -1) return;
        // 毛玻璃需要当前屏幕内容作为模糊源。
        // 这里用**按帧缓存**：一帧里可能画十几个玻璃面板，每次都全屏拷贝太浪费；
        // 而所有面板采的本来就是「同一时刻的画面」，缓存是安全的。
        if (!copySceneColor(true)) return;

        Framebuffer framebuffer = mc.getFramebuffer();
        float scale = (float) mc.getWindow().getScaleFactor();

        // 与 blur_rect 同一套坐标约定：像素坐标 + gl_FragCoord 原点在左下角
        final float locX = x * scale;
        final float locY = -y * scale + framebuffer.textureHeight - height * scale;
        final float sizeX = width * scale;
        final float sizeY = height * scale;
        final float r = Math.max(0f, Math.min(radius, Math.min(width, height) / 2f)) * scale;
        final float bw = Math.max(0f, border) * scale;
        final float m = Math.max(0f, Math.min(1f, mix));
        // lambda 只能捕获 effectively final 的变量，参数本身不是，先转存
        final meteordevelopment.meteorclient.utils.render.color.Color fill = color;
        final meteordevelopment.meteorclient.utils.render.color.Color stroke = borderColor;

        drawFullscreen(program, framebuffer, () -> {
            bindSceneTexture(program, "InputSampler");
            uniform2f(program, "InputResolution",
                framebuffer.textureWidth, framebuffer.textureHeight);
            uniform2f(program, "uLocation", locX, locY);
            uniform2f(program, "uSize", sizeX, sizeY);
            uniform1f(program, "radius", r);
            uniform4f(program, "uColor",
                fill.r / 255f, fill.g / 255f, fill.b / 255f, fill.a / 255f);
            uniform1f(program, "uBorderWidth", bw);
            if (stroke != null) {
                uniform4f(program, "uBorderColor",
                    stroke.r / 255f, stroke.g / 255f,
                    stroke.b / 255f, stroke.a / 255f);
            } else {
                uniform4f(program, "uBorderColor", 0f, 0f, 0f, 0f);
            }
            uniform1f(program, "uMix", m);
            uniform1f(program, "uBlurRadius", blurRadius * scale);
        }, boundsPx(locX, locY, sizeX, sizeY), blurRadius * scale + 2f);
    }

    /**
     * 绘制纯色 SDF 圆角矩形（替代 NanoVG 的 {@code nvgRoundedRect + nvgFill}）。
     *
     * <p>与 {@link #drawRoundedBlur} 不同，这里<b>不需要拷贝场景</b>：它是直接往当前
     * 帧缓冲上叠加的几何，而不是对已有画面的后处理。因此开销更小，也不需要每帧做
     * 一次全屏 texture copy。
     *
     * <p>坐标用 MC 的逻辑（GUI 缩放）坐标，与本项目其它 2D 绘制一致；
     * 内部按 {@code scaleFactor} 换算成帧缓冲像素，与着色器的 {@code gl_FragCoord} 对齐。
     *
     * @param color  填充色（含 alpha）
     * @param border 描边宽度（逻辑像素）；{@code <= 0} 表示不描边
     */
    public static void drawRoundedRect(float x, float y, float width, float height, float radius,
                                       meteordevelopment.meteorclient.utils.render.color.Color color,
                                       float border,
                                       meteordevelopment.meteorclient.utils.render.color.Color borderColor) {
        if (width <= 0f || height <= 0f) return;
        // ⚠️ 填充透明【但】有描边时不能跳过：
        //    这正是一个「环」的画法（例如准星的圆环样式 = 透明填充 + 实心描边）。
        //    rect.fsh 在到达边界带时会把颜色 mix 成 uBorderColor，所以填充 alpha=0
        //    完全合法。只有「既没填充也没描边」才真的什么都不用画。
        boolean hasBorder = border > 0f && borderColor != null && borderColor.a > 0;
        if (color.a <= 0 && !hasBorder) return;

        int program = ensureProgram(new ProgramKey(RECT_VS, RECT_FS));
        if (program == -1) return;

        Framebuffer framebuffer = mc.getFramebuffer();
        float scale = (float) mc.getWindow().getScaleFactor();

        // 与 blur_rect.fsh 同一套约定：gl_FragCoord 原点在左下角
        final float locX = x * scale;
        final float locY = -y * scale + framebuffer.textureHeight - height * scale;

        // 圆角半径不能超过短边的一半，否则 SDF 会退化成怪异形状
        final float r = Math.max(0f, Math.min(radius, Math.min(width, height) / 2f)) * scale;

        final float bw = Math.max(0f, border) * scale;

        drawFullscreen(program, framebuffer, () -> {
            uniform2f(program, "uLocation", locX, locY);
            uniform2f(program, "uSize", width * scale, height * scale);
            uniform1f(program, "radius", r);
            uniform4f(program, "uColor",
                color.r / 255f, color.g / 255f, color.b / 255f, color.a / 255f);
            uniform1f(program, "uBorderWidth", hasBorder ? bw : 0f);
            uniform4f(program, "uBorderColor",
                hasBorder ? borderColor.r / 255f : 0f,
                hasBorder ? borderColor.g / 255f : 0f,
                hasBorder ? borderColor.b / 255f : 0f,
                hasBorder ? borderColor.a / 255f : 0f);
        }, boundsPx(locX, locY, width * scale, height * scale), 2f);
    }

    /**
     * 绘制<b>双色渐变</b>的圆角矩形（用于血条的流动渐变）。
     *
     * <p>与 {@link #drawRoundedRect} 的关系：那个只收一个颜色、画纯色填充；
     * 这个在矩形内按 x 从左端色混到右端色，并可让色带随时间流动。
     *
     * <p>为什么新开着色器而不是给 {@code rect.fsh} 加 uniform：现有三个 SDF 着色器
     * （rect / rect_textured / shadow_rect）都有各自的调用点，改动会波及全部；
     * 新增 {@code rect_gradient.fsh} 对它们是零影响。
     *
     * @param flow  流动相位（0~1）。{@code <= 0} 时是静态渐变，不做流动
     * @param bands 色带数量；{@code > 1} 时在整条内重复多段渐变（越密越像波纹）
     */
    public static void drawRoundedRectGradient(float x, float y, float width, float height,
                                               float radius,
                                               meteordevelopment.meteorclient.utils.render.color.Color color1,
                                               meteordevelopment.meteorclient.utils.render.color.Color color2,
                                               float flow, float bands) {
        if (width <= 0f || height <= 0f) return;
        if (color1 == null || color2 == null) return;
        if (color1.a <= 0 && color2.a <= 0) return;

        int program = ensureProgram(new ProgramKey(RECT_VS, RECT_GRADIENT_FS));
        if (program == -1) return;

        Framebuffer framebuffer = mc.getFramebuffer();
        float scale = (float) mc.getWindow().getScaleFactor();

        // 与 rect.fsh 同一套约定：gl_FragCoord 原点在左下角
        final float locX = x * scale;
        final float locY = -y * scale + framebuffer.textureHeight - height * scale;
        final float r = Math.max(0f, Math.min(radius, Math.min(width, height) / 2f)) * scale;

        drawFullscreen(program, framebuffer, () -> {
            uniform2f(program, "uLocation", locX, locY);
            uniform2f(program, "uSize", width * scale, height * scale);
            uniform1f(program, "radius", r);
            uniform4f(program, "uColor1",
                color1.r / 255f, color1.g / 255f, color1.b / 255f, color1.a / 255f);
            uniform4f(program, "uColor2",
                color2.r / 255f, color2.g / 255f, color2.b / 255f, color2.a / 255f);
            uniform1f(program, "uFlow", Math.max(0f, flow));
            uniform1f(program, "uBands", Math.max(1f, bands));
        }, boundsPx(locX, locY, width * scale, height * scale), 2f);
    }

    public static void drawRoundedShadow(float x, float y, float width, float height, float radius,
                                         meteordevelopment.meteorclient.utils.render.color.Color color,
                                         float feather, float offsetX, float offsetY) {
        if (width <= 0f || height <= 0f || color == null || color.a <= 0) return;
        if (feather <= 0f) return;

        int program = ensureProgram(new ProgramKey(RECT_VS, SHADOW_FS));
        if (program == -1) return;

        Framebuffer framebuffer = mc.getFramebuffer();
        float scale = (float) mc.getWindow().getScaleFactor();

        // 与 rect.fsh 同一套约定：gl_FragCoord 原点在左下角
        final float locX = x * scale;
        final float locY = -y * scale + framebuffer.textureHeight - height * scale;

        final float r = Math.max(0f, Math.min(radius, Math.min(width, height) / 2f)) * scale;

        // 着色器里的 gl_FragCoord.y 向上，而面板坐标 y 向下，所以偏移要取反
        final float offX = offsetX * scale;
        final float offY = -offsetY * scale;

        // ★ viewport 余量：阴影会向四外羽化 uFeather，并整体偏移 uOffset。
        //   所以包围盒要往外扩 (feather + |offset|)，否则阴影边缘会被裁掉。
        final float shadowMargin =
            feather * scale + Math.max(Math.abs(offX), Math.abs(offY)) + 1f;

        drawFullscreen(program, framebuffer, () -> {
            uniform2f(program, "uLocation", locX, locY);
            uniform2f(program, "uSize", width * scale, height * scale);
            uniform1f(program, "radius", r);
            uniform2f(program, "uOffset", offX, offY);
            uniform1f(program, "uFeather", feather * scale);
            uniform4f(program, "uColor",
                color.r / 255f, color.g / 255f, color.b / 255f, color.a / 255f);
        }, boundsPx(locX, locY, width * scale, height * scale), shadowMargin);
    }

    /**
     * 绘制贴图 SDF 圆角矩形 —— 用于头像（皮肤 / 生物贴图）的圆角裁剪。
     *
     * <p>替代 NanoVG 的 {@code nvgImagePattern + nvgRoundedRect + nvgFill}。
     * 采样用 {@code texelFetch}，直接取单个纹素，因此：
     * <ul>
     *     <li>不受贴图自身 LINEAR 过滤 / mipmap 影响 —— 像素画不会被插值糊掉
     *         （这正是「头像模糊」的根因）</li>
     *     <li>不受 1.21.9+ sampler object 覆盖贴图参数的影响</li>
     * </ul>
     *
     * <p>UV 语义与 NanoVG 的 image pattern 一致：{@code uv} 是贴图上的起始像素，
     * {@code unit} 是「头像像素 / 贴图像素」的 cover 缩放系数（取两个方向较大者，
     * 保证短边也铺满，长边溢出部分被圆角裁掉）。
     *
     * @param uv   贴图上的起始像素（通常是头部区域的左上角）
     * @param unit cover 缩放系数
     */
    public static void drawTexturedRoundedRect(float x, float y, float width, float height, float radius,
                                               int glTextureId, float texWidth, float texHeight,
                                               float uvX, float uvY, float unit,
                                               meteordevelopment.meteorclient.utils.render.color.Color tint) {
        drawTexturedRoundedRectRotated(x, y, width, height, radius, glTextureId,
            texWidth, texHeight, uvX, uvY, unit, 0f, tint);
    }

    /**
     * 绘制贴图圆角矩形，<b>支持在矩形内旋转</b>（用于指南针箭头等随角度转的图标）。
     *
     * <p><b>为什么旋转必须在着色器里做</b>：本项目所有 2D 绘制都走
     * 「全屏四边形 + 片元着色器按 {@code gl_FragCoord} 裁剪」，顶点固定是 NDC ±1
     * —— 在 Java 侧做矩阵旋转<b>完全无效</b>（见 {@code ensureQuad}）。
     * 而 {@code rect_textured.fsh} 里的 {@code face} 正是「矩形内坐标」，
     * 在那里绕中心反向旋转采样坐标，视觉上就是把图像正向旋转。
     *
     * <p><b>额外好处</b>：SVG 只需栅格化<b>一次</b>，任意角度复用同一张纹理
     * —— 不会像「按角度重新栅格化」那样每帧新建纹理（本项目坑 30）。
     *
     * @param rotationRad 旋转角（弧度，顺时针为正）；0 = 不旋转
     */
    public static void drawTexturedRoundedRectRotated(float x, float y, float width, float height,
                                                      float radius,
                                                      int glTextureId, float texWidth, float texHeight,
                                                      float uvX, float uvY, float unit,
                                                      float rotationRad,
                                                      meteordevelopment.meteorclient.utils.render.color.Color tint) {
        if (width <= 0f || height <= 0f || glTextureId <= 0) return;
        if (texWidth <= 0f || texHeight <= 0f || unit <= 0f) return;

        int program = ensureProgram(new ProgramKey(RECT_VS, RECT_TEX_FS));
        if (program == -1) return;

        Framebuffer framebuffer = mc.getFramebuffer();
        float scale = (float) mc.getWindow().getScaleFactor();

        final float locX = x * scale;
        final float locY = -y * scale + framebuffer.textureHeight - height * scale;
        final float r = Math.max(0f, Math.min(radius, Math.min(width, height) / 2f)) * scale;
        final float rot = rotationRad;

        drawFullscreen(program, framebuffer, () -> {
            uniform2f(program, "uLocation", locX, locY);
            uniform2f(program, "uSize", width * scale, height * scale);
            uniform1f(program, "radius", r);
            uniform2f(program, "uTexSize", texWidth, texHeight);
            uniform2f(program, "uUvOrigin", uvX, uvY);
            // unit 是「逻辑像素 / 贴图像素」，而着色器里的 face 是帧缓冲像素，
            // 所以这里必须乘上 scaleFactor 才能对齐（否则头像会被放大 scale 倍）。
            uniform1f(program, "uUnit", unit * scale);
            uniform1f(program, "uRotation", rot);
            uniform4f(program, "uColor",
                tint.r / 255f, tint.g / 255f, tint.b / 255f, tint.a / 255f);
            // ★ 用【单元 1】而不是 0（用户 2026-10-18 修复「图标里出现游戏画面」）：
            //   bindSceneTexture（模糊/毛玻璃/故障）把场景纹理绑在 GL_TEXTURE0，
            //   若图标也用单元 0，两者会互相覆盖 -> 图标采样到游戏画面、整屏闪烁。
            //   换到单元 1 后互不干扰。着色器里 u_Texture 的 sampler 值由
            //   bindTexture 自动写成 (unit - GL_TEXTURE0)，无需改 .fsh。
            bindTexture(program, "u_Texture", GL13.GL_TEXTURE1, glTextureId);
        }, boundsPx(locX, locY, width * scale, height * scale), 2f);
    }

    /**
     * 绘制信号故障 / RGB 色散效果。
     *
     * <p>必须在面板已经画完之后调用——它会拷贝当前帧（含面板），再以通道错位 + 行撕裂 +
     * 扫描线的方式重绘到同一块圆角区域内。
     *
     * @param strength 总强度 0~1，为 0 时直接返回
     * @param rgbSplit 色散像素（相对 scaleFactor 缩放）
     * @param scanline 扫描线强度 0~1
     * @param tear     撕裂强度 0~1
     * @param time     动画时间（秒），用于驱动随机行
     */
    public static void drawGlitch(float x, float y, float width, float height, float radius,
                                  float strength, float rgbSplit, float scanline, float tear,
                                  float time) {
        if (strength <= 0.001f) return;

        int program = ensureProgram(new ProgramKey(GLITCH_VS, GLITCH_FS));
        if (program == -1) return;
        if (!copySceneColor()) return;

        Framebuffer framebuffer = mc.getFramebuffer();
        float scale = (float) mc.getWindow().getScaleFactor();

        final float locX = x * scale;
        final float locY = -y * scale + framebuffer.textureHeight - height * scale;
        final float sizeX = width * scale;
        final float sizeY = height * scale;

        drawFullscreen(program, framebuffer, () -> {
            bindSceneTexture(program, "InputSampler");
            uniform2f(program, "InputResolution", framebuffer.textureWidth, framebuffer.textureHeight);
            uniform2f(program, "uLocation", locX, locY);
            uniform2f(program, "uSize", sizeX, sizeY);
            uniform1f(program, "radius", radius * scale);
            uniform1f(program, "uTime", time);
            uniform1f(program, "uGlitch", Math.max(0f, Math.min(1f, strength)));
            uniform1f(program, "uRgbSplit", rgbSplit * scale);
            uniform1f(program, "uScanline", Math.max(0f, Math.min(1f, scanline)));
            uniform1f(program, "uTear", Math.max(0f, Math.min(1f, tear)));
        }, boundsPx(locX, locY, sizeX, sizeY), 2f);
    }

    /** 释放 GL 资源（关闭模块时调用）。 */
    public static void destroy() {
        if (vbo != -1) GL15.glDeleteBuffers(vbo);
        if (vao != -1) GL30.glDeleteVertexArrays(vao);
        for (int program : PROGRAMS.values()) {
            if (program != -1) GL20.glDeleteProgram(program);
        }
        if (sceneTexture != null) sceneTexture.close();

        vbo = vao = -1;
        PROGRAMS.clear();
        FAILED.clear();
        UNIFORM_CACHE.clear();
        sceneTexture = null;
        sceneTextureWidth = sceneTextureHeight = -1;
    }

    // ═══════════════════════════════════════════════════════
    //  内部实现
    // ═══════════════════════════════════════════════════════

    /** 编译着色器程序；失败时记录一次并返回 -1。 */
    private static int ensureProgram(ProgramKey key) {
        Integer existing = PROGRAMS.get(key);
        if (existing != null) return existing;
        if (Boolean.TRUE.equals(FAILED.get(key))) return -1;

        // Minecraft 的 GL 对象只能在渲染线程创建
        if (!RenderSystem.isOnRenderThread()) return -1;

        try {
            int vertex = compileShader(key.vertex(), GL20.GL_VERTEX_SHADER);
            int fragment = compileShader(key.fragment(), GL20.GL_FRAGMENT_SHADER);
            int program = GL20.glCreateProgram();
            try {
                GL20.glAttachShader(program, vertex);
                GL20.glAttachShader(program, fragment);
                GL20.glBindAttribLocation(program, 0, "Position");
                GL20.glLinkProgram(program);
                if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                    throw new IllegalStateException("程序链接失败：" + GL20.glGetProgramInfoLog(program));
                }
                PROGRAMS.put(key, program);
            } catch (RuntimeException e) {
                GL20.glDeleteProgram(program);
                throw e;
            } finally {
                GL20.glDeleteShader(vertex);
                GL20.glDeleteShader(fragment);
            }

            ensureQuad();
            return PROGRAMS.get(key);
        } catch (RuntimeException e) {
            FAILED.put(key, true);
            MeteorClient.LOG.error("[IMG] 2D 着色器初始化失败（{}），已禁用该效果", key.fragment(), e);
            return -1;
        }
    }

    /**
     * 把主帧缓冲的颜色内容拷贝到我们自己的纹理。
     *
     * <p>替代 Sakura 原版的 {@code GL30.glBlitFramebuffer}：1.21.11 把帧缓冲操作
     * 抽象到了 {@code CommandEncoder} 上。
     *
     * <p><b>不能跨效果复用这次拷贝</b>：背景模糊需要「面板之前」的画面，
     * 而故障效果需要「面板之后」的画面 —— 两者是同一帧里的不同时间点。
     * 曾经想按帧缓存以省掉多目标的重复拷贝，但那会让故障效果采到面板之前的画面，
     * 所以这里始终如实拷贝当前帧。
     */
    private static boolean copySceneColor() {
        return copySceneColor(false);
    }

    /**
     * 拷贝场景，可选择**按帧缓存**。
     *
     * @param cacheForFrame {@code true} 时同一帧内只真正拷贝一次
     */
    private static boolean copySceneColor(boolean cacheForFrame) {
        if (cacheForFrame && sceneCopyFrame == currentFrame()) {
            return sceneTexture != null;
        }

        Framebuffer framebuffer = mc.getFramebuffer();
        int width = framebuffer.textureWidth;
        int height = framebuffer.textureHeight;
        if (width <= 0 || height <= 0) return false;

        if (!(framebuffer.getColorAttachment() instanceof GlTexture source)) return false;

        if (sceneTexture == null || width != sceneTextureWidth || height != sceneTextureHeight) {
            if (sceneTexture != null) sceneTexture.close();
            sceneTexture = RenderSystem.getDevice().createTexture(
                "IMG Sakura 2D post-process source",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                source.getFormat(), width, height, 1, 1);
            sceneTextureWidth = width;
            sceneTextureHeight = height;
        }

        RenderSystem.getDevice().createCommandEncoder()
            .copyTextureToTexture(source, sceneTexture, 0, 0, 0, 0, 0, width, height);

        if (cacheForFrame) sceneCopyFrame = currentFrame();
        return true;
    }

    /** 已做过「按帧缓存」拷贝的帧号。 */
    private static long sceneCopyFrame = -1;

    /** 由调用方在每帧开始时推进的帧号。 */
    private static long frameCounter = 0;

    public static void beginFrame() {
        frameCounter++;
    }

    private static long currentFrame() {
        return frameCounter;
    }

    /**
     * 保存 GL 状态 → 绑定主帧缓冲与程序 → 画四边形 → 恢复 GL 状态。
     *
     * <p>显式绑定主帧缓冲的 FBO（而不是裸的 0）：1.21.11 把渲染目标抽象成了 {@code RenderPass}，
     * 不能假定当前绑定的一定是主画面。
     *
     * <p><b>不在这里绑定场景纹理</b>：只有模糊/故障这类后处理着色器才需要它，
     * 而圆角着色器是直接叠加的几何、根本不做场景拷贝（此时 {@code sceneTexture} 可能为 null）。
     * 因此取样器由各着色器自己的 {@code setUniforms} 负责绑定。
     */
    private static void drawFullscreen(int program, Framebuffer framebuffer, Runnable setUniforms) {
        drawFullscreen(program, framebuffer, setUniforms, null, 0f);
    }

    /**
     * 同 {@link #drawFullscreen(int, Framebuffer, Runnable)}，但<b>把 viewport 限制到元素包围盒</b>。
     *
     * <h2>★ 性能修复（2026-10-07，见性能审计 P0）</h2>
     *
     * <p>本渲染层的所有绘制都实现成「铺满 NDC 的四边形 + 片元着色器按 SDF 裁剪」
     * （见 {@link #ensureQuad()} 的顶点是 ±1）。问题是：
     * <b>{@code discard} 只省纹理采样，不省光栅化与片元着色器调用</b> ——
     * 每个元素都按<b>整屏</b>（1920×1080 ≈ 207 万片元）计费，
     * 全开约 241 个元素 ≈ <b>每帧 5 亿片元</b>。这是显卡 70℃ 的主因。
     *
     * <h2>为什么缩 viewport 就够（不用改几何、不用改着色器）</h2>
     *
     * <p>所有着色器都用<b>绝对坐标</b> {@code gl_FragCoord} 定位元素：
     * <pre>
     *   rect.fsh:31         gl_FragCoord.xy - uLocation - halfSize
     *   blur_rect.fsh:30    uv = gl_FragCoord.xy / InputResolution.xy
     *   glass_rect.fsh:64   gl_FragCoord.xy - uLocation - halfSize
     *   shadow_rect.fsh:49  gl_FragCoord.xy - center
     *   glitch.fsh:34       vec2 frag = gl_FragCoord.xy
     * </pre>
     * 而 viewport <b>只决定「生成哪些片元」</b>，不影响 {@code gl_FragCoord} 的取值。
     * 所以把 viewport 设成元素包围盒后：
     * <ul>
     *   <li>片元只在元素范围内生成 → 开销从「整屏」降到「元素面积」；</li>
     *   <li>{@code gl_FragCoord} 仍是绝对坐标 → SDF、圆角、描边、模糊<b>全部照旧</b>；</li>
     *   <li><b>着色器一行都不用改</b>；</li>
     *   <li>{@link GlStateSnapshot} 已保存/恢复 viewport（{@code capture()} 里
     *       {@code glGetIntegerv(GL_VIEWPORT)}、{@code restore()} 里
     *       {@code GlStateManager._viewport(...)}）→ 不会污染后续绘制。</li>
     * </ul>
     *
     * <h2>⚠️ 余量（margin）必须给够</h2>
     *
     * <p>着色器的<b>输出可能超出矩形本身</b>，若 viewport 卡到精确包围盒会把它们裁掉：
     * <ul>
     *   <li>{@code blur_rect} —— 采样半径 {@code Quality} 像素（向外扩散）；</li>
     *   <li>{@code shadow_rect} —— {@code uFeather + uOffset}（阴影向外羽化 + 偏移）；</li>
     *   <li>{@code glitch} —— 撕裂水平位移最多 36px；</li>
     *   <li>其余 —— SDF 抗锯齿约 1~2px + 描边宽度。</li>
     * </ul>
     * 调用方按各自着色器传对应的 {@code marginPx}。
     *
     * @param bounds   元素包围盒（<b>像素坐标</b>，x 向右、y 向上原点在左下角，
     *                 与 {@code gl_FragCoord} 同系）；null = 整屏（旧行为）
     * @param marginPx 四周额外余量（像素）
     */
    private static void drawFullscreen(int program, Framebuffer framebuffer,
                                       Runnable setUniforms,
                                       float[] bounds, float marginPx) {
        // ★ 先记下【进入时真实绑定的 DRAW_FRAMEBUFFER】——这才是我们要画的目标。
        //   见下方修复说明：不能用 mainFramebufferId() 自己推算（它给出的是另一个 FBO id）。
        int[] fboProbe = new int[1];
        GL11.glGetIntegerv(GL30.GL_DRAW_FRAMEBUFFER_BINDING, fboProbe);
        int drawFboAtEntry = fboProbe[0];

        // 先取快照：裸 GL 会绕过 GlStateManager 的状态缓存，画完必须把状态与缓存一并校正，
        // 否则 Minecraft 之后会「以为状态已设好」而跳过 GL 调用，导致文字/贴图错乱。
        GlStateSnapshot snapshot = GlStateSnapshot.capture();

        try {
            // ═══ ★ 根因修复（用户 2026-10-18，已实测确证）═══════════════════
            //
            // 【实测证据】日志 [IMG FBO]（17:28:15）：
            //     drawFbo=27  readFbo=27   |  目标FBO=29
            //   当前真实绑定的是 27，而 mainFramebufferId() 算出来的是 29 —— 不是同一个！
            //
            // 【后果】这里把 DRAW_FRAMEBUFFER 从 27 改绑到 29 后，
            //   GlStateSnapshot 又不还原 FBO（现已加了，但根本问题是绑错了）。
            //   于是后续 MC 的绘制继续按「27」的假设走，真实 GL 却停在 29 ——
            //   画面在两张纹理之间错位 => 用户看到的「整屏闪烁」，
            //   以及「图标位置出现另一个游戏画面」（采到另一张的颜色）。
            //
            // 【为什么平时看着正常】27 与 29 很可能指向同一张颜色纹理，
            //   只是 FBO 对象不同；一旦按 F1 等操作改变了缓冲/重绘路径，
            //   差异就暴露成闪烁。
            //
            // 【修法】不要自己用 getOrCreateFramebuffer 推算 id，
            //   直接沿用【进入时已绑定的那个 DRAW_FRAMEBUFFER】——
            //   我们本来就是要往当前正在渲染的目标上画。只有当前绑定为 0
            //   （即默认 FBO）时才回退到 mainFramebufferId()。
            int curDrawFbo = drawFboAtEntry;
            if (curDrawFbo == 0) curDrawFbo = mainFramebufferId(framebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, curDrawFbo);

            // ★ viewport 限制：只在该元素的包围盒（+余量）内生成片元。
            //   已裁剪到帧缓冲范围内，避免负值/越界（GL 会静默丢弃越界的 viewport）。
            if (bounds == null) {
                GL11.glViewport(0, 0, framebuffer.textureWidth, framebuffer.textureHeight);
            } else {
                int vx = (int) Math.floor(bounds[0] - marginPx);
                int vy = (int) Math.floor(bounds[1] - marginPx);
                int vw = (int) Math.ceil(bounds[2] + marginPx * 2f);
                int vh = (int) Math.ceil(bounds[3] + marginPx * 2f);

                // 裁剪到帧缓冲（GL 规范要求 viewport 完全在缓冲内才有定义行为）
                if (vx < 0) { vw += vx; vx = 0; }
                if (vy < 0) { vh += vy; vy = 0; }
                if (vx + vw > framebuffer.textureWidth) vw = framebuffer.textureWidth - vx;
                if (vy + vh > framebuffer.textureHeight) vh = framebuffer.textureHeight - vy;

                if (vw <= 0 || vh <= 0) return;   // 完全在屏幕外 -> 什么都不用画
                GL11.glViewport(vx, vy, vw, vh);
            }

            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            GL20.glUseProgram(program);

            setUniforms.run();

            GL30.glBindVertexArray(vao);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        } finally {
            snapshot.restore();
        }
    }

    /**
     * 构造像素空间包围盒（供 {@link #drawFullscreen} 限制 viewport 用）。
     *
     * @return {@code [x, y, w, h]}，原点在<b>左下角</b>（与 {@code gl_FragCoord} 同系）
     */
    private static float[] boundsPx(float locX, float locY, float sizeX, float sizeY) {
        return new float[]{locX, locY, sizeX, sizeY};
    }

    /**
     * 把场景拷贝纹理绑定到指定取样器（仅后处理着色器使用）。
     */
    private static void bindSceneTexture(int program, String uniform) {
        if (sceneTexture == null) return;
        bindTexture(program, uniform, GL13.GL_TEXTURE0, ((GlTexture) sceneTexture).getGlId());
    }

    /** 取主帧缓冲对应的 GL FBO id（1.21.11 需要经由 GlTexture 获取）。 */
    private static int mainFramebufferId(Framebuffer framebuffer) {
        if (framebuffer.getColorAttachment() instanceof GlTexture colorTexture
                && RenderSystem.getDevice() instanceof GlBackend backend) {
            return colorTexture.getOrCreateFramebuffer(backend.getBufferManager(), null);
        }
        return 0;
    }

    private static void ensureQuad() {
        if (vao != -1) return;

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

    private static int compileShader(Identifier id, int type) {
        String source;
        Resource resource = mc.getResourceManager().getResource(id)
            .orElseThrow(() -> new IllegalStateException("找不到资源 " + id));
        try (InputStream stream = resource.getInputStream()) {
            source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取着色器失败 " + id, e);
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

    private static int uniformLocation(int program, String name) {
        return UNIFORM_CACHE
            .computeIfAbsent(program, p -> new HashMap<>())
            .computeIfAbsent(name, n -> GL20.glGetUniformLocation(program, n));
    }

    private static void uniform1f(int program, String name, float value) {
        int location = uniformLocation(program, name);
        if (location >= 0) GL20.glUniform1f(location, value);
    }

    private static void uniform2f(int program, String name, float x, float y) {
        int location = uniformLocation(program, name);
        if (location >= 0) GL20.glUniform2f(location, x, y);
    }

    private static void uniform4f(int program, String name, float x, float y, float z, float w) {
        int location = uniformLocation(program, name);
        if (location >= 0) GL20.glUniform4f(location, x, y, z, w);
    }

    private static void bindTexture(int program, String uniform, int unit, int texture) {
        GL13.glActiveTexture(unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        int location = uniformLocation(program, uniform);
        if (location >= 0) GL20.glUniform1i(location, unit - GL13.GL_TEXTURE0);
    }
}
