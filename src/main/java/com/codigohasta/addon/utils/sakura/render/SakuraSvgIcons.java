package com.codigohasta.addon.utils.sakura.render;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.github.weisj.jsvg.view.ViewBox;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * SVG 图标库：把 {@code assets/invincible_machine_gun/vector/*.svg} 栅格化成 MC 贴图并绘制。
 *
 * <p>管线与 CatLean 的 {@code bj.java} 一致：
 * JSVG {@link SVGLoader} → {@link SVGDocument#render} 到 {@link BufferedImage} →
 * {@link NativeImageBackedTexture} 注册 → 走本项目的 SDF 贴图管线绘制。
 *
 * <p>图标按需加载并缓存；加载失败的图标记入失败表，不再每帧重试。
 */
public final class SakuraSvgIcons {
    private SakuraSvgIcons() {
    }

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    /** 资源命名空间。 */
    private static final String PREFIX = "invincible_machine_gun";
    /** 图标资源目录（assets/{PREFIX}/vector/*.svg）。 */
    private static final String DIR = "vector";

    /** 已栅格化的图标缓存：{@code 图标名@像素尺寸} → GL 信息。 */
    private static final Map<String, IconInfo> CACHE = new HashMap<>();
    /** 已知加载失败的图标名（避免每帧重试刷日志）。 */
    private static final Set<String> FAILED = new HashSet<>();

    /**
     * 一张已栅格化图标的 GL 信息。
     *
     * <p>{@code glId} 供<b>裸 GL</b> 通路使用（{@code rect_textured.fsh}）。
     * <p>{@code texture} 是注册到 {@code TextureManager} 的那张贴图对象，
     * 供 <b>MeshBuilder 通路</b>使用 —— 它能给出 {@code getGlTextureView()} 与
     * {@code getSampler()}（这两个方法的返回类型在本项目源码里写不出名字，
     * 与 {@code SakuraTextRenderer} 提交文字时的写法完全一致）。
     */
    /**
     * 一张已栅格化图标的 GL 信息。
     *
     * <p>{@code glId} 供<b>裸 GL</b> 通路使用（{@code rect_textured.fsh}）。
     * <p>{@code owned} 是注册进 {@code TextureManager} 的贴图对象（具体类型
     * {@code NativeImageBackedTexture}，可直接写出类名）——
     * 供 <b>MeshBuilder 通路</b> 取 {@code getGlTextureView()} / {@code getSampler()}，
     * 与 {@code SakuraTextRenderer} 提交文字时的写法一致。
     */
    private record IconInfo(int glId, int width, int height,
                            net.minecraft.client.texture.NativeImageBackedTexture owned) {
    }

    /**
     * 绘制一个 SVG 图标。
     *
     * <p>图标未就绪时安静跳过（下一帧再试），绝不抛异常。
     *
     * <p><b>为什么按显示尺寸栅格化</b>：绘制走的是 {@code rect_textured.fsh}，
     * 它用 {@code texelFetch} 逐纹素取样，<b>完全忽略纹理过滤</b>（这正是它「像素精确
     * 不糊」的原因）。所以若固定 64×64 栅格再缩到 10px 显示，会每 6.4 个纹素才取 1 个，
     * 细笔画（小人的手臂/腿）直接断裂或消失。CatLean 的 {@code bj.java:142} 也是
     * {@code new BufferedImage(ceil(w), ceil(h))} —— 按目标尺寸栅格化。
     * 因此这里也按显示尺寸（乘 GUI 缩放）栅格化，并按尺寸分档缓存。
     *
     * @param name 图标名（不带 {@code .svg}，如 {@code "player-list-widget"}）
     * @param x    左上角 X（逻辑像素）
     * @param y    左上角 Y
     * @param size 边长（逻辑像素）
     * @param tint 染色（含 alpha）。<b>用白色</b>才会保留 SVG 自身的颜色；
     *             传深色会把整张图标乘黑（着色器是 {@code tex.rgb * uColor.rgb}）
     * @return 是否真的画了
     */
    public static boolean draw(String name, float x, float y, float size,
                               meteordevelopment.meteorclient.utils.render.color.Color tint) {
        return draw(name, x, y, size, size, tint);
    }

    /**
     * 绘制一个 SVG 图标（<b>可分离「栅格化尺寸」与「显示尺寸」</b>）。
     *
     * <h3>⚠️ 为什么需要这个重载（性能修复，2026-10-07）</h3>
     *
     * <p>缓存键含栅格化像素尺寸（见 {@link #icon(String, float)} 的 {@code name@px}）。
     * 如果调用方把<b>逐帧变化的尺寸</b>（例如弹性入场动画系数
     * {@code TYPE_ICON * scale * k2}，k2 在 0~1.35 间振荡）直接传进来，
     * 就会<b>每帧都缓存未命中</b>：
     * <pre>
     *   rasterize() -> 解析 SVG -> 渲染 BufferedImage -> registerTexture() 新建 GL 纹理
     * </pre>
     * 而 {@code CACHE} 只增不减、没有 {@code close()}，
     * —— <b>每一帧泄漏若干张 GL 纹理</b>（已由性能审计确证）。
     *
     * <p>正解：<b>栅格化用稳定尺寸</b>（{@code rasterSize}，命中缓存），
     * <b>显示仍按动画尺寸</b>（{@code drawSize}）。两者不同是安全的 ——
     * 因为下面的 {@code unit = drawSize / info.width()} 会自动算对纹素映射
     * （{@code rect_textured.fsh} 的 {@code texelPos} 推导依赖这个比值）。
     *
     * <p><b>视觉影响</b>：栅格化尺寸固定后，缩小时走的是「同一张贴图按比例取样」，
     * 与「按缩小尺寸重新栅格化」在像素层面略有差异（前者由着色器取样，后者精度更高）。
     * 由于 HUD 图标最大也就 16px、动画只持续几百毫秒，肉眼不可辨。
     *
     * @param rasterSize 栅格化尺寸（应<b>稳定</b>，如该图标的最大显示尺寸）
     * @param drawSize   本次显示的边长（可逐帧变化）
     */
    public static boolean draw(String name, float x, float y, float drawSize,
                               float rasterSize,
                               meteordevelopment.meteorclient.utils.render.color.Color tint) {
        return drawRotated(name, x, y, drawSize, rasterSize, 0f, tint);
    }

    /**
     * 绘制 SVG 图标，<b>支持旋转</b>（用于指南针箭头这类随角度转的图标）。
     *
     * <p><b>旋转在着色器里做</b>（{@code rect_textured.fsh} 的 {@code uRotation}），
     * 不是在栅格化时做。好处：
     * <ul>
     *   <li>本项目绘制走「全屏四边形 + 片元着色器按 {@code gl_FragCoord} 裁剪」，
     *       Java 侧矩阵旋转<b>无效</b>；而 {@code face} 正是矩形内坐标，正好用来转采样；</li>
     *   <li>SVG 只需栅格化<b>一次</b>，任意角度复用同一张纹理
     *       —— 不会像「按角度重新栅格化」那样每帧新建纹理（本项目坑 30）。</li>
     * </ul>
     *
     * @param angleDeg 旋转角度（度，顺时针）；内部转成弧度
     */
    public static boolean drawRotated(String name, float x, float y, float drawSize,
                                      float rasterSize, float angleDeg,
                                      meteordevelopment.meteorclient.utils.render.color.Color tint) {
        IconInfo info = icon(name, rasterSize);
        if (info == null) return false;
        float unit = drawSize / (float) info.width();
        Shader2DUtil.drawTexturedRoundedRectRotated(x, y, drawSize, drawSize, 0f,
            info.glId(), info.width(), info.height(),
            0f, 0f, unit, (float) Math.toRadians(angleDeg), tint);
        return true;
    }

    // ═══════════════════════════════════════════════════════════════════
    // ★ 2026-10-18 新增：走 MeshBuilder + UI_TEXTURED 的图标绘制
    //
    // 背景：用户报「按 F1 后整屏闪烁 + 图标位置出现另一个游戏画面」。
    // 根因是图标走 Shader2DUtil 的【裸 GL】路径，它会直接改
    // framebuffer / viewport / program / VAO / 纹理绑定，再靠
    // GlStateSnapshot 强行翻正缓存 —— 而按 F1 时 Minecraft 的渲染路径
    // 变化，这份手写快照没能覆盖全部状态，就造成整屏错位闪烁。
    //
    // 而【文字】走的是 Meteor 的 MeshBuilder + MeshRenderer，全程由 MC
    // 自己管理状态，从未出过这类问题（已由大量 HUD 长期验证）。
    //
    // 所以这里给图标补一条「和文字完全同一套」的绘制通路：
    //   用 MeshBuilder 写 4 个顶点（位置 + UV + 颜色），
    //   再交给 MeshRenderer 用 UI_TEXTURED 管线提交。
    // 这样图标不再触碰任何裸 GL 状态。
    // ═══════════════════════════════════════════════════════════════════

    /** 图标专用 MeshBuilder（与文字分开，避免同一帧的 mesh 生命周期冲突）。 */
    private static meteordevelopment.meteorclient.renderer.MeshBuilder iconMesh;

    /**
     * 用 MeshBuilder 提交一个图标（不碰裸 GL）。
     *
     * <p>坐标约定与 {@link com.codigohasta.addon.utils.sakura.render.SakuraTextRenderer} 一致：
     * 传入的是<b>逻辑像素、左上角原点</b>，内部乘 GUI 缩放换成物理像素，
     * 并且 y 需要按 UI_TEXTURED 的约定处理（见方法内注释）。
     *
     * @param name 图标名
     * @param x    左上角 X（逻辑像素）
     * @param y    左上角 Y（逻辑像素，向下为正）
     * @param size 边长（逻辑像素）
     * @param tint 染色
     * @return 是否真的提交了
     */
    public static boolean drawViaMesh(String name, float x, float y, float size,
                                      meteordevelopment.meteorclient.utils.render.color.Color tint) {
        IconInfo info = icon(name, size);
        if (info == null || info.owned() == null) return false;

        if (iconMesh == null) {
            iconMesh = new meteordevelopment.meteorclient.renderer.MeshBuilder(
                meteordevelopment.meteorclient.renderer.MeteorRenderPipelines.UI_TEXTURED);
        }

        float sf = (float) mc.getWindow().getScaleFactor();
        float px = x * sf;
        float py = y * sf;
        float ps = size * sf;

        // 与文字一致：UI_TEXTURED 的顶点是物理像素，y 向下为正（与 MeshRenderer 的
        // unscaledProjection 一致，文字就是这么画的且显示正常）。
        float x0 = px, x1 = px + ps;
        float y0 = py, y1 = py + ps;

        var color = new meteordevelopment.meteorclient.utils.render.color.Color(
            tint.r, tint.g, tint.b, tint.a);

        iconMesh.begin();
        iconMesh.ensureQuadCapacity();
        iconMesh.quad(
            iconMesh.vec2(x0, y0).vec2(0f, 0f).color(color).next(),
            iconMesh.vec2(x0, y1).vec2(0f, 1f).color(color).next(),
            iconMesh.vec2(x1, y1).vec2(1f, 1f).color(color).next(),
            iconMesh.vec2(x1, y0).vec2(1f, 0f).color(color).next()
        );
        iconMesh.end();

        meteordevelopment.meteorclient.renderer.MeshRenderer.begin()
            .attachments(mc.getFramebuffer())
            .pipeline(meteordevelopment.meteorclient.renderer.MeteorRenderPipelines.UI_TEXTURED)
            .mesh(iconMesh)
            // 与 SakuraTextRenderer.java:286 完全同一写法
            .sampler("u_Texture", info.owned().getGlTextureView(), info.owned().getSampler())
            .end();
        return true;
    }

    /** 取图标（懒加载 + 按尺寸缓存；未就绪返回 null）。 */
    private static IconInfo icon(String name, float size) {
        // 栅格化尺寸：显示逻辑像素 × GUI 缩放，至少 8px、上限 128px（防超大贴图）
        float sf = mc.getWindow() != null ? (float) mc.getWindow().getScaleFactor() : 1f;
        int px = Math.max(8, Math.min(128, Math.round(size * sf)));
        String key = name + "@" + px;

        IconInfo cached = CACHE.get(key);
        if (cached != null) return cached;
        if (FAILED.contains(key)) return null;

        IconInfo info = rasterize(name, px);
        if (info == null) {
            FAILED.add(key);
            return null;
        }
        CACHE.put(key, info);
        return info;
    }

    /**
     * 把 SVG 栅格化成 {@link NativeImageBackedTexture}。
     *
     * <p>与 CatLean {@code bj.java:138-166} 相同的三步：
     * SVGLoader.load → SVGDocument.render(Graphics2D) → 注册贴图。
     *
     * @param px 栅格化边长（像素），由调用方按显示尺寸算好
     */
    private static IconInfo rasterize(String name, int px) {
        try {
            // JSVG 2.0 只接受 URL（CatLean 用的旧版能吃 InputStream）
            String path = "/assets/" + PREFIX + "/" + DIR + "/" + name + ".svg";
            java.net.URL url = SakuraSvgIcons.class.getResource(path);
            if (url == null) return null;

            SVGDocument doc = new SVGLoader().load(url);
            if (doc == null) return null;

            BufferedImage img = new BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB_PRE);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            doc.render(null, g, new ViewBox(px, px));
            g.dispose();

            // 1.21.11：先建空贴图，再 setImage + upload（没有单参 NativeImage 构造器）
            NativeImageBackedTexture texture = new NativeImageBackedTexture(
                "img-svg-" + name + "-" + px, px, px, true);
            NativeImage nativeImage = texture.getImage();
            for (int py = 0; py < px; py++) {
                for (int pxx = 0; pxx < px; pxx++) {
                    nativeImage.setColorArgb(pxx, py, img.getRGB(pxx, py));
                }
            }
            texture.upload();

            Identifier id = Identifier.of(PREFIX, DIR + "/dynamic_" + name + "_" + px);
            mc.getTextureManager().registerTexture(id, texture);

            AbstractTexture abstractTexture = mc.getTextureManager().getTexture(id);
            if (abstractTexture == null
                || !(abstractTexture.getGlTexture() instanceof GlTexture glTexture)) return null;

            return new IconInfo(glTexture.getGlId(), px, px, texture);
        } catch (Throwable t) {
            MeteorClient.LOG.warn("[IMG/SvgIcons] 图标加载失败: " + name, t);
            return null;
        }
    }
}
