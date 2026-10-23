package com.codigohasta.addon.utils.sakura.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.TextureFormat;
import meteordevelopment.meteorclient.renderer.MeshBuilder;
import meteordevelopment.meteorclient.renderer.MeshRenderer;
import meteordevelopment.meteorclient.renderer.MeteorRenderPipelines;
import meteordevelopment.meteorclient.renderer.Texture;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

public final class SakuraTextRenderer {
    /**
     * 默认字体集。
     *
     * <p>历史调用方（{@code SakuraTargetHud}）不传字体集，走的就是这一套，
     * 字体路径与图集参数与改造前逐字一致。</p>
     */
    public static final CatFontSet DEFAULT_FONT_SET = CatFontSet.SAKURA;

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    /**
     * 字形 mesh。
     *
     * <p><b>为什么 mesh 与 {@code building} 是全局而不是每个字体集一份：</b>
     * 一次 {@link #begin()}/{@link #end()} 之间只应该产生<b>一个</b> draw call。
     * 两个 HUD（Sakura 与 CatHud）可能在同一帧先后绘制，若各自持一个 MeshBuilder，
     * 就会出现「第二次 begin() 被嵌套跳过、而它写的顶点落在第一个 mesh 里、
     * 采样到的却是另一个字体集的纹理」的串图。共用一个 mesh、
     * 每次 begin 记录本段用的是哪个字体集，{@link #end()} 时按该字体集上传/绑定纹理即可。</p>
     */
    private static final MeshBuilder mesh = new MeshBuilder(MeteorRenderPipelines.UI_TEXT);

    /** 每个字体集一份状态；键用引用相等（IdentityHashMap），避免 record 的 equals 造成误合并。 */
    private static final Map<CatFontSet, FontSetState> SETS = new IdentityHashMap<>();

    /** 当前帧正在收集的字体集（{@link #begin()} 时确定，{@link #end()} 时消费）。 */
    private static FontSetState active;
    /** 全局的「正在收集顶点」标志，避免嵌套 begin/end 破坏 mesh。 */
    private static boolean building;

    private SakuraTextRenderer() {}

    // ═══════════════════════════════════════════════════════
    //  对外接口
    // ═══════════════════════════════════════════════════════

    /**
     * 一帧开始时调用一次，使用默认字体集（{@link CatFontSet#SAKURA}）。
     *
     * <p>必须在渲染线程调用；不在渲染线程（或字体加载失败）时本帧所有绘制都会静默跳过。</p>
     */
    public static void begin() {
        begin(DEFAULT_FONT_SET);
    }

    /**
     * 一帧开始时调用一次，并指定本段文字用哪个字体集。
     *
     * <p>同一个 {@link #begin()}/{@link #end()} 区间内必须只用同一个字体集
     * （纹理是在 {@link #end()} 时才绑定的，中途换集会串图）。需要画两套字体时，
     * 就配对地调用两次 begin/end —— 这也是 SakuraTargetHud 与 CatHud 在同一帧
     * 各自绘制时的正常情形。</p>
     *
     * @param fontSet 字体集；传 {@code null} 等价于默认字体集
     */
    public static void begin(CatFontSet fontSet) {
        if (building) return;

        // 纹理创建/上传只能在渲染线程；其它线程直接放弃本帧，避免 GL 崩溃。
        if (!RenderSystem.isOnRenderThread()) return;

        FontSetState state = setFor(fontSet);
        if (!state.ensureInit()) {
            // ★ 诊断（照 img-diagnose-loop 的静默失败铁律）：
            //   这里 return 会让 building 保持 false -> 随后所有 draw() 直接丢弃
            //   -> 表现为「文字不显示，但走独立 GL 路径的图标正常」。
            //   这个失败路径以前完全静默，是最难查的一类。
            diagOnce("begin FAILED ensureInit set=" + fontSet);
            return;
        }

        // 上一帧把图集写满了：现在（帧边界，mesh 里还没有任何 UV）整张重建。
        if (state.atlasFull) state.resetAtlas();

        mesh.begin();
        building = true;
        active = state;
    }

    /** 一次性诊断（避免刷屏）。 */
    private static boolean diagDone;

    private static void diagOnce(String msg) {
        if (diagDone) return;
        diagDone = true;
        com.codigohasta.addon.AddonTemplate.LOG.warn("[SakuraText diag] {}", msg);
    }

    /**
     * 限流诊断：最多每秒 1 条。
     *
     * <p>保留给将来排查「文字不显示」这类静默失败 —— 插一条就能看到中间值，
     * 不必反复读代码猜（教训见 {@code img-diagnose-loop}）。
     */
    private static long diagLastMs;

    private static void diagThrottled(String msg) {
        long now = System.currentTimeMillis();
        if (now - diagLastMs < 1000) return;
        diagLastMs = now;
        com.codigohasta.addon.AddonTemplate.LOG.warn("[SakuraText commit] {}", msg);
    }

    /**
     * 一帧结束时调用一次：提交 mesh 并把这一帧的所有文字一次性渲染出去。
     *
     * <p>没有实际绘制任何字形时不会产生 draw call。</p>
     */
    public static void end() {
        if (!building) return;
        mesh.end();
        building = false;

        FontSetState state = active;
        active = null;
        if (state == null) return;

        // 有新字形才重新上传整张图集。中英混排的名字在预热之后就基本不再变化，
        // 稳态下这里通常是零开销。
        if (state.atlasDirty) {
            state.texture.upload(state.atlas);
            state.atlasDirty = false;
        }

        if (mesh.getIndicesCount() == 0) return;

        MeshRenderer.begin()
            .attachments(mc.getFramebuffer())
            .pipeline(MeteorRenderPipelines.UI_TEXT)
            .mesh(mesh)
            .sampler("u_Texture", state.texture.getGlTextureView(), state.texture.getSampler())
            .end();
    }

    /**
     * 文本的像素宽度（逻辑像素），按默认字体集测量。
     *
     * <p>只累加字形的 advance，不触碰图集，所以可以在 {@link #begin()} 之前安全调用
     * （用于右对齐、截断判断等布局计算）。</p>
     *
     * @param size 逻辑字号
     * @return 逻辑像素宽度；{@code text} 为空或 {@code size <= 0} 时返回 0
     */
    public static double getWidth(String text, float size) {
        return getWidth(text, size, DEFAULT_FONT_SET);
    }

    /**
     * 文本的像素宽度（逻辑像素），按指定字体集测量。
     *
     * <p>宽度是与图集无关的纯度量，所以这里可以显式指定字体集 ——
     * CatHud 的布局（面板宽度、右对齐、截断）必须用它自己的字体来算，
     * 否则文字与面板会对不上。</p>
     *
     * @param fontSet 字体集；传 {@code null} 等价于默认字体集
     */
    public static double getWidth(String text, float size, CatFontSet fontSet) {
        if (text == null || text.isEmpty() || size <= 0f) return 0.0;

        FontSetState state = setFor(fontSet);
        if (!state.ensureInit()) return 0.0;

        int rasterHeight = rasterHeight(size);
        SizeMetrics m = state.metricsFor(rasterHeight);
        double scaleFactor = mc.getWindow().getScaleFactor();

        double width = 0.0;
        int length = text.length();
        for (int offset = 0; offset < length; ) {
            int cp = text.codePointAt(offset);
            offset += Character.charCount(cp);
            width += state.advanceOf(cp, m);
        }

        // advance 是在物理像素空间算的，换回逻辑像素交给调用方。
        return width / scaleFactor;
    }

    /**
     * 行高（逻辑像素）。
     *
     * <p>返回的就是传入的 {@code size}：本类用 {@code stbtt_ScaleForPixelHeight(info, h)}
     * 做缩放，该函数保证 {@code (ascent - descent) * scale == h}，
     * 所以字体的 ascent~descent 跨度在逻辑空间里恰好等于字号。
     * 这与现状「NanoVG 按逻辑字号渲染、字符高度大致等于字号」一致。
     * 与字体集无关，因此不需要重载。</p>
     */
    public static double getHeight(float size) {
        return size <= 0f ? 0.0 : size;
    }

    /**
     * 在 (x, y) 画一行文字，用当前 {@link #begin()} 激活的字体集。
     *
     * <p>{@code (x, y)} 是这一行文字的<b>左上角</b>（不是基线），
     * 且是<b>逻辑（GUI 缩放）坐标</b>。</p>
     *
     * @param size 逻辑字号（面板用到 14 / 11 / 9，允许小数）
     * @param argb 颜色，{@code 0xAARRGGBB}
     */
    public static void draw(String text, float x, float y, float size, int argb) {
        if (!building || text == null || text.isEmpty() || size <= 0f) {
            // ★ 诊断：把【被丢弃的原因】打出来（不是打「进来了」）。
            //   以前这里静默 return，导致「文字不显示」没有任何线索可查。
            if (text != null && !text.isEmpty()) {
                diagOnce("draw DROPPED building=" + building + " size=" + size
                    + " text=" + text);
            }
            return;
        }
        activeSet().drawInternal(text, x, y, size, argb,
            Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY);
    }

    /**
     * 在 (x, y) 画一行文字，并把超出 {@code [clipLeft, clipRight]} 的部分裁掉。
     *
     * <p><b>为什么用 CPU 裁剪而不是 GL scissor</b>：scissor 是裸 GL 状态，
     * 在 1.21.11 的 {@code GlStateManager} 缓存化状态机下必须配 {@code GlStateSnapshot}
     * 才能安全还原（这正是本类想要摆脱的负担）。而字形本来就是一个个四边形，
     * 直接在写顶点时把 x 夹到裁剪区间、并按比例调整 u 即可 —— 效果等价（半个字形
     * 也能正确显示），却不碰任何 GL 状态。
     *
     * <p>长名称滚动播放（marquee）就是靠它实现的：把整行文字按负偏移绘制，
     * 再用裁剪窗口露出中间一段。
     *
     * @param clipLeft  裁剪左边界（逻辑坐标）
     * @param clipRight 裁剪右边界（逻辑坐标）
     */
    public static void drawClipped(String text, float x, float y, float size, int argb,
                                   float clipLeft, float clipRight) {
        if (!building || text == null || text.isEmpty() || size <= 0f) return;
        if (clipRight <= clipLeft) return;
        activeSet().drawInternal(text, x, y, size, argb, clipLeft, clipRight,
            Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY);
    }

    /**
     * 在 (x, y) 画一行文字，并同时做<b>水平与垂直</b>裁剪。
     *
     * <p>垂直裁剪是给「血量数字上下滚动」（里程表效果）用的：数字往上/下滚出时，
     * 若只裁水平方向，滑出去的字形会露到相邻行上（上面是名字行、下面是血条）。
     * 裁剪同样在 CPU 侧按 UV 比例完成，因此半个字形也能正确显示，且不碰任何 GL 状态。
     *
     * @param clipTop    裁剪上边界（逻辑坐标）
     * @param clipBottom 裁剪下边界（逻辑坐标）
     */
    public static void drawClippedRect(String text, float x, float y, float size, int argb,
                                       float clipLeft, float clipRight,
                                       float clipTop, float clipBottom) {
        if (!building || text == null || text.isEmpty() || size <= 0f) return;
        if (clipRight <= clipLeft || clipBottom <= clipTop) return;
        activeSet().drawInternal(text, x, y, size, argb, clipLeft, clipRight,
            clipTop, clipBottom);
    }

    /**
     * 以 {@code centerX} 水平居中画一行文字。
     *
     * <p>{@code y} 同样是这一行文字的<b>左上角</b>，{@code centerX} 是逻辑坐标的中心线。</p>
     */
    public static void drawCentered(String text, float centerX, float y, float size, int argb) {
        if (text == null || text.isEmpty() || size <= 0f) return;
        double half = getWidth(text, size, activeSet().fontSet) * 0.5;
        draw(text, (float) (centerX - half), y, size, argb);
    }

    /**
     * 画一行<b>水平渐变</b>文字：左端 {@code argbLeft} 渐到右端 {@code argbRight}。
     *
     * <p><b>为什么是「逐顶点插值」而不是「逐字符上色」</b>：本类的 mesh 每个顶点各自带颜色
     * （见 {@code drawInternal} 里 4 次 {@code .color(...)}），GPU 会在四边形内部做线性插值。
     * 因此只要把每个字形的左右顶点分别设成该 x 位置的颜色，整行就是<b>数学上连续</b>的渐变，
     * 不会出现色阶。
     *
     * <p>逐字符上色（每个字符取一个纯色）也能做，但颜色在字符边界跳变：
     * 实测「Invincible Machine Gun」@13px 共 140.4px / 22 字符 = 6.4px 一个色带，
     * 红→黄这种高对比配色每步跳变约 4.8%，肉眼能看出条纹；流动动画时条纹会扫过文字，
     * 更容易注意到。所以这里用逐顶点方案。
     *
     * <p>不透明度也一起插值，因此可以让文字一端淡出。
     *
     * @param text     文本
     * @param x        这一行文字的<b>左上角</b>（逻辑坐标）
     * @param y        同上（不是基线）
     * @param size     逻辑字号
     * @param argbLeft  左端颜色 {@code 0xAARRGGBB}
     * @param argbRight 右端颜色 {@code 0xAARRGGBB}
     */
    public static void drawGradient(String text, float x, float y, float size,
                                    int argbLeft, int argbRight) {
        if (!building || text == null || text.isEmpty() || size <= 0f) return;
        activeSet().drawGradientInternal(text, x, y, size, argbLeft, argbRight, 0f,
            Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY);
    }

    /**
     * 画一行<b>流动</b>渐变文字：两色循环的渐变带随时间在文字上滚动。
     *
     * <p>与 {@link #drawGradient} 的区别：那个是「一次性」的 左→右 渐变（左端一色、右端另一色）；
     * 这个是<b>重复</b>的渐变带，并按 {@code phase} 平移，视觉上就是颜色在文字上流动。
     *
     * <p><b>为什么不直接平移相位再用取模</b>：{@code t = frac(x + phase)} 在环绕处会从
     * 右端色硬跳回左端色，出现一条可见的接缝。这里用<b>三角波</b>
     * （{@code 1 - |2*frac(u) - 1|}）让颜色沿 左端色→右端色→左端色 来回过渡，
     * 天然无缝、且速度均匀。
     *
     * @param phase 相位（0~1）；随时间递增即产生流动。调用方负责按速度累加。
     */
    public static void drawGradientFlow(String text, float x, float y, float size,
                                        int argbLeft, int argbRight, float phase) {
        if (!building || text == null || text.isEmpty() || size <= 0f) return;
        activeSet().drawGradientInternal(text, x, y, size, argbLeft, argbRight, phase,
            Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY);
    }

    /**
     * 逐顶点取色的回调：由调用方决定文字上任意一点是什么颜色。
     *
     * <p>有了它，调用方可以实现任意一维/二维着色 —— 斜向流动色带、彩虹、
     * 按位置渐变等 —— 而不需要渲染器为每种效果各加一个方法。
     * 颜色在字形四边形内部由 GPU 线性插值，所以只要回调本身连续，结果就没有色阶。
     */
    @FunctionalInterface
    public interface VertexColor {
        /**
         * @param lx 顶点相对<b>文字左上角</b>的 X（逻辑像素）
         * @param ly 顶点相对<b>文字左上角</b>的 Y（逻辑像素）
         * @return 该顶点的颜色 {@code 0xAARRGGBB}
         */
        int colorAt(float lx, float ly);
    }

    /**
     * 画一行文字，每个顶点的颜色由 {@code shader} 按位置决定。
     *
     * <p><b>为什么用它来实现「斜向流动」和「彩虹」</b>：
     * 斜向流动需要同时用到 x 和 y（把点投影到旋转后的轴），
     * 而彩虹需要直接按色相算色 —— 两者都不是「左端色→右端色」这种一维线性关系，
     * 传两个端点色无法表达。
     *
     * <p>回调的坐标是<b>相对文字左上角的逻辑像素</b>，与 {@code x}/{@code y} 同一套单位，
     * 所以调用方可以直接拿面板/文字的几何来算，不必关心 {@code scaleFactor}。
     *
     * @param shader 逐顶点取色函数；为 {@code null} 时什么都不画
     */
    public static void drawShaded(String text, float x, float y, float size, VertexColor shader) {
        if (!building || text == null || text.isEmpty() || size <= 0f || shader == null) return;
        activeSet().drawShadedInternal(text, x, y, size, shader,
            Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY);
    }

    /**
     * 释放<b>全部</b>字体集的图集纹理与字体缓存。
     *
     * <p>保持「一关模块就把显存还回去」的原有语义：调用方（如 SakuraTargetHud）
     * 只知道自己用了哪套字体，却无法知道同一帧里别人有没有用别的字体集，
     * 所以这里统一释放。代价只是下次使用时的重新懒加载。</p>
     *
     * <p>只能在渲染线程调用；若正处在一帧中间会先把这一帧提交掉，避免留下半截 mesh。</p>
     */
    public static void destroy() {
        if (building) end();
        for (FontSetState state : SETS.values()) state.destroy();
    }

    // ═══════════════════════════════════════════════════════
    //  字体集状态
    // ═══════════════════════════════════════════════════════

    /** 取（必要时创建）某个字体集的状态；{@code null} 落到默认字体集。 */
    private static FontSetState setFor(CatFontSet fontSet) {
        CatFontSet key = fontSet != null ? fontSet : DEFAULT_FONT_SET;
        return SETS.computeIfAbsent(key, FontSetState::new);
    }

    /** 当前正在收集顶点的字体集；理论上 begin 之后必非空，这里只是防御。 */
    private static FontSetState activeSet() {
        return active != null ? active : setFor(DEFAULT_FONT_SET);
    }

    /** 逻辑字号 → 物理光栅化高度。按整数像素量化，保证同一字号共用一批字形。 */
    private static int rasterHeight(float size) {
        return Math.max(1, Math.round(size * (float) mc.getWindow().getScaleFactor()));
    }

    private static Color fromArgb(int argb) {
        return new Color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF);
    }

    /** 某个光栅高度下的字体缩放与基线偏移（物理像素）。 */
    private record SizeMetrics(int rasterHeight, float primaryScale, float fallbackScale,
                               float ascentPx) {}

    /**
     * 一个已进入图集的字形。
     *
     * <p>{@code x0}/{@code y0} 是位图左上角相对「笔位 + 基线」的偏移（stb 的 y 轴向下为正），
     * {@code w}/{@code h} 是位图尺寸，{@code u}/{@code v} 是图集 UV。</p>
     */
    private record Glyph(
        float x0, float y0, int w, int h,
        float u0, float v0, float u1, float v1
    ) {
        /** 空格之类无位图的字形，用来避免每帧重复查询 stb。 */
        static final Glyph EMPTY = new Glyph(0, 0, 0, 0, 0, 0, 0, 0);
    }

    /**
     * <b>一个字体集</b>的全部渲染状态：字体二进制、图集纹理、字形与度量缓存。
     *
     * <p>改造前这些字段全是 {@code SakuraTextRenderer} 的静态字段；现在整体搬进来，
     * 每个字体集各持一份，所以两套字体不会共用图集、不会互相挤掉字形、
     * 度量缓存也不会串（同名字号在两种字体下的 advance 完全不同）。</p>
     *
     * <p>与字体集无关的只有 mesh 与「正在收集」标志，它们留在外层静态字段里。</p>
     */
    private static final class FontSetState {
        private final CatFontSet fontSet;

        private final Map<Long, Glyph> glyphs = new HashMap<>();
        private final Map<Integer, SizeMetrics> metrics = new HashMap<>();

        /**
         * 字形 advance 缓存（性能修复，2026-10-07）。
         *
         * <p>键 = {@code rasterHeight << 32 | codepoint}。
         * 避免 {@code getWidth()} 每帧重复调用 native 的
         * {@code stbtt_GetCodepointHMetrics}（见 {@link #advanceOf}）。
         */
        private final Map<Long, Double> advanceCache = new HashMap<>();

        /** 字体二进制必须常驻：stbtt_InitFont 内部持有对这块内存的引用，不能被 GC 回收。 */
        private ByteBuffer primaryBuffer;
        private ByteBuffer fallbackBuffer;
        private STBTTFontinfo primaryInfo;
        private STBTTFontinfo fallbackInfo;

        private Texture texture;
        private ByteBuffer atlas;

        /** 图集 shelf 打包器的游标。 */
        private int packX, packY, packRowHeight;

        private boolean initialized;
        private boolean initFailed;
        /** 本帧是否有新字形写进 CPU 图集，需要重新上传。 */
        private boolean atlasDirty;
        /** 图集已写满；下一帧 begin() 时整张重建。 */
        private boolean atlasFull;

        private FontSetState(CatFontSet fontSet) {
            this.fontSet = fontSet;
        }

        /**
         * 绘制实现。裁剪在<b>逻辑坐标</b>下判定，写顶点前才换算成物理像素。
         */
        private void drawInternal(String text, float x, float y, float size, int argb,
                                  float clipLeft, float clipRight,
                                  float clipTop, float clipBottom) {
            drawCore(text, x, y, size, argb, argb, 0f, null, clipLeft, clipRight, clipTop, clipBottom);
        }

        /** 渐变版入口：左右两端颜色不同，逐顶点插值。 */
        private void drawGradientInternal(String text, float x, float y, float size,
                                          int argbLeft, int argbRight, float phase,
                                          float clipLeft, float clipRight,
                                          float clipTop, float clipBottom) {
            drawCore(text, x, y, size, argbLeft, argbRight, phase, null, clipLeft, clipRight,
                clipTop, clipBottom);
        }

        /** 逐顶点着色入口。 */
        private void drawShadedInternal(String text, float x, float y, float size,
                                        VertexColor shader,
                                        float clipLeft, float clipRight,
                                        float clipTop, float clipBottom) {
            drawCore(text, x, y, size, 0, 0, 0f, shader, clipLeft, clipRight,
                clipTop, clipBottom);
        }

        /**
         * 绘制实现。三种着色方式，按优先级选一种：
         * <ol>
         *     <li>{@code shader != null} —— 逐顶点调用回调取色（斜向流动 / 彩虹）</li>
         *     <li>{@code argbLeft != argbRight} —— 水平渐变（{@code phase > 0} 时是流动带）</li>
         *     <li>否则纯色（旧行为）</li>
         * </ol>
         */
        private void drawCore(String text, float x, float y, float size,
                              int argbLeft, int argbRight, float phase,
                              VertexColor shader,
                              float clipLeft, float clipRight,
                              float clipTop, float clipBottom) {
            float scaleFactor = (float) mc.getWindow().getScaleFactor();
            int rasterHeight = rasterHeight(size);
            SizeMetrics m = metricsFor(rasterHeight);

            // 逻辑 → 物理。UI_TEXT 的 MeshData 用的是 unscaledProjection，
            // 顶点必须是物理帧缓冲像素，否则会和面板错位。
            double penX = x * scaleFactor;
            double baselineY = y * scaleFactor + m.ascentPx;

            // 裁剪边界同样是逻辑坐标，先换算到物理像素
            double clipL = clipLeft * scaleFactor;
            double clipR = clipRight * scaleFactor;
            double clipT = clipTop * scaleFactor;
            double clipB = clipBottom * scaleFactor;

            boolean shaded = shader != null;
            boolean gradient = !shaded && argbLeft != argbRight;
            Color colorLeft = shaded ? null : fromArgb(argbLeft);
            Color colorRight = shaded ? null : fromArgb(argbRight);
            Color uniform = colorLeft;

            // 渐变的归一化基准：整行文字的物理宽度。
            // 需要先量一遍 advance；顺带也用于下面逐顶点求 t。
            double startX = penX;
            double totalWidth = 0.0;
            if (gradient) {
                for (int offset = 0; offset < text.length(); ) {
                    int cp = text.codePointAt(offset);
                    offset += Character.charCount(cp);
                    totalWidth += advanceOf(cp, m);
                }
            }

            int glyphCount = text.codePointCount(0, text.length());
            mesh.ensureCapacity(Math.max(1, glyphCount) * 4, Math.max(1, glyphCount) * 6);

            int length = text.length();
            for (int offset = 0; offset < length; ) {
                int cp = text.codePointAt(offset);
                offset += Character.charCount(cp);

                Glyph g = glyph(cp, rasterHeight, m);

                // 空格、图集写满时拿不到字形的字符：没有位图，但仍然要推进笔位，
                // 这样 draw 的排布才和 getWidth 的测量结果一致。
                if (g != null && g.w > 0 && g.h > 0) {
                    double x0 = penX + g.x0;
                    double x1 = x0 + g.w;

                    double vx0 = Math.max(x0, clipL);
                    double vx1 = Math.min(x1, clipR);

                    if (vx1 > vx0) {
                        // 按可见比例裁剪 UV，保证半个字形只显示该显示的那一半
                        double uSpan = g.u1 - g.u0;
                        double t0 = (vx0 - x0) / (x1 - x0);
                        double t1 = (vx1 - x0) / (x1 - x0);
                        float u0 = (float) (g.u0 + uSpan * t0);
                        float u1 = (float) (g.u0 + uSpan * t1);

                        double y0 = baselineY + g.y0;
                        double y1 = y0 + g.h;

                        // 垂直裁剪（同样的按比例裁 UV）：里程表滚动靠它把
                        // 滚出格子的字形切掉，避免落到相邻行上。
                        double vy0 = Math.max(y0, clipT);
                        double vy1 = Math.min(y1, clipB);
                        if (vy1 <= vy0) {
                            penX += advanceOf(cp, m);
                            continue;
                        }

                        double vSpan = g.v1 - g.v0;
                        double s0 = (vy0 - y0) / (y1 - y0);
                        double s1 = (vy1 - y0) / (y1 - y0);
                        float v0 = (float) (g.v0 + vSpan * s0);
                        float v1 = (float) (g.v0 + vSpan * s1);

                        // 左右边缘各自的颜色：纯色时两者相同，不额外开销
                        Color cLeft = uniform;
                        Color cRight = uniform;
                        if (shaded) {
                            // 回调收的是「相对文字左上角的逻辑像素」。
                            // 顶点是物理像素、且 vy 的原点在帧缓冲左下，所以：
                            //   lx = vx / scaleFactor - x
                            //   ly = vy / scaleFactor - y
                            // 注意不能写成 (vy - baselineY)/scaleFactor —— 那会漏掉
                            // ascent 这一项，使回调的 y 整体偏移约一个 ascent（实测 sf=2、
                            // 字号 11 时偏 9.78px），斜向条纹与彩虹的相位就会错位。
                            float lx0 = (float) (vx0 / scaleFactor) - x;
                            float lx1 = (float) (vx1 / scaleFactor) - x;
                            float ly0 = (float) (vy0 / scaleFactor) - y;
                            float ly1 = (float) (vy1 / scaleFactor) - y;

                            // 与普通分支同一顺序（见下方说明，绕行方向不是根因）
                            int q1 = mesh.vec2(vx0, vy0).vec2(u0, v0)
                                .color(fromArgb(shader.colorAt(lx0, ly0))).next();
                            int q2 = mesh.vec2(vx0, vy1).vec2(u0, v1)
                                .color(fromArgb(shader.colorAt(lx0, ly1))).next();
                            int q3 = mesh.vec2(vx1, vy1).vec2(u1, v1)
                                .color(fromArgb(shader.colorAt(lx1, ly1))).next();
                            int q4 = mesh.vec2(vx1, vy0).vec2(u1, v0)
                                .color(fromArgb(shader.colorAt(lx1, ly0))).next();
                            mesh.quad(q1, q2, q3, q4);
                            penX += advanceOf(cp, m);
                            continue;
                        }
                        if (gradient) {
                            cLeft = lerpColor(colorLeft, colorRight,
                                gradT(vx0, startX, totalWidth, phase));
                            cRight = lerpColor(colorLeft, colorRight,
                                gradT(vx1, startX, totalWidth, phase));
                        }

                        // ═══ 顶点绕行方向：与 Meteor 的 Font.render 保持一致 ═══
                        //
                        // ⛔ 已撤回的错误结论（2026-10-18）：我一度以为「文字不显示」
                        //    是绕行方向（顺时针）触发 `UI_TEXTURED` 的背面剔除导致的，
                        //    并把顺序翻成逆时针。实测【无效】—— 仍是只有原版字。
                        //
                        //    对照 Meteor 自己的 Font.render（Font.java:131-136）：
                        //        vec2(x + c.x0, y + c.y0)   // y 小
                        //        vec2(x + c.x0, y + c.y1)   // y 大
                        //        vec2(x + c.x1, y + c.y1)
                        //        vec2(x + c.x1, y + c.y0)
                        //    与这里原来的顺序【完全一致】，所以绕行方向不是根因。
                        //    现在恢复原顺序，不再改动。
                        int i1 = mesh.vec2(vx0, vy0).vec2(u0, v0).color(cLeft).next();
                        int i2 = mesh.vec2(vx0, vy1).vec2(u0, v1).color(cLeft).next();
                        int i3 = mesh.vec2(vx1, vy1).vec2(u1, v1).color(cRight).next();
                        int i4 = mesh.vec2(vx1, vy0).vec2(u1, v0).color(cRight).next();
                        mesh.quad(i1, i2, i3, i4);
                    }
                }

                penX += advanceOf(cp, m);
            }
        }

        /**
         * 顶点 x → 渐变参数。
         *
         * <p>{@code phase == 0}：普通单向渐变，直接返回 0~1 的归一化位置。
         *
         * <p>{@code phase > 0}：流动模式。用三角波
         * {@code 1 - |2 * frac(u) - 1|} 让参数沿 0→1→0 来回过渡，
         * 再把相位加进去，于是渐变带会随时间滚动且<b>没有接缝</b>
         * （若直接用 {@code frac(u + phase)}，环绕处会从右端色硬跳回左端色，出现一条竖线）。
         */
        private static float gradT(double vx, double startX, double totalWidth, float phase) {
            if (totalWidth <= 0.0) return 0f;
            double u = (vx - startX) / totalWidth;
            if (phase <= 0f) {
                return (float) (u < 0.0 ? 0.0 : (u > 1.0 ? 1.0 : u));
            }
            double shifted = u + phase;
            double wrapped = shifted - Math.floor(shifted);          // frac，落在 [0,1)
            double tri = 1.0 - Math.abs(2.0 * wrapped - 1.0);        // 三角波 0→1→0
            return (float) (tri < 0.0 ? 0.0 : (tri > 1.0 ? 1.0 : tri));
        }

        /** 两个 ARGB 颜色按比例插值。 */
        private static Color lerpColor(Color a, Color b, float t) {
            return new Color(
                Math.round(a.r + (b.r - a.r) * t),
                Math.round(a.g + (b.g - a.g) * t),
                Math.round(a.b + (b.b - a.b) * t),
                Math.round(a.a + (b.a - a.a) * t));
        }

        /**
         * 取一个字形；不在图集里就现场光栅化并追加。
         *
         * @return 可直接使用的字形；空格等无位图字符或图集已满时返回 {@code null}
         */
        private Glyph glyph(int cp, int rasterHeight, SizeMetrics m) {
            long key = ((long) rasterHeight << 32) | (cp & 0xFFFFFFFFL);
            Glyph cached = glyphs.get(key);
            if (cached != null) return cached;

            // 两个字体都没有这个字形：记成空字形，免得每帧重复查 stb。
            STBTTFontinfo info = pickFont(cp);
            if (info == null) {
                glyphs.put(key, Glyph.EMPTY);
                return null;
            }

            float scale = info == primaryInfo ? m.primaryScale : m.fallbackScale;

            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer w = stack.mallocInt(1);
                IntBuffer h = stack.mallocInt(1);
                IntBuffer xoff = stack.mallocInt(1);
                IntBuffer yoff = stack.mallocInt(1);

                ByteBuffer bitmap = STBTruetype.stbtt_GetCodepointBitmap(info, scale, scale, cp, w, h, xoff, yoff);

                int bw = w.get(0);
                int bh = h.get(0);

                // 空格之类没有可见像素的字符：不进图集，靠 advance 占位。
                if (bitmap == null || bw <= 0 || bh <= 0) {
                    if (bitmap != null) STBTruetype.stbtt_FreeBitmap(bitmap);
                    glyphs.put(key, Glyph.EMPTY);
                    return null;
                }

                int gx, gy;
                try {
                    gx = packX;
                    gy = packY;

                    // shelf 打包：一行放不下就换行，整张放不下就标记重建。
                    if (gx + bw + fontSet.padding() > fontSet.atlasSize()) {
                        gx = 0;
                        gy = packY + packRowHeight + fontSet.padding();
                    }
                    if (gy + bh + fontSet.padding() > fontSet.atlasSize()) {
                        atlasFull = true;
                        return null;
                    }

                    // 把 stb 的 8bit 位图逐行拷进 CPU 侧图集（行距 = bw）。
                    int base = gy * fontSet.atlasSize() + gx;
                    for (int row = 0; row < bh; row++) {
                        int src = row * bw;
                        int dst = base + row * fontSet.atlasSize();
                        for (int col = 0; col < bw; col++) {
                            atlas.put(dst + col, bitmap.get(src + col));
                        }
                    }
                } finally {
                    STBTruetype.stbtt_FreeBitmap(bitmap);
                }

                packX = gx + bw + fontSet.padding();
                packY = gy;
                packRowHeight = Math.max(packRowHeight, bh);
                atlasDirty = true;

                float inv = 1f / fontSet.atlasSize();
                Glyph g = new Glyph(
                    xoff.get(0), yoff.get(0), bw, bh,
                    gx * inv, gy * inv, (gx + bw) * inv, (gy + bh) * inv
                );
                glyphs.put(key, g);
                return g;
            }
        }

        /**
         * 选字体：主字体优先，它没有这个字形才回退到回退字体。
         *
         * @return 含该字形的字体；两个都没有时返回 {@code null}
         */
        private STBTTFontinfo pickFont(int cp) {
            if (STBTruetype.stbtt_FindGlyphIndex(primaryInfo, cp) != 0) return primaryInfo;
            if (STBTruetype.stbtt_FindGlyphIndex(fallbackInfo, cp) != 0) return fallbackInfo;
            return null;
        }

        /**
         * 字形 advance（物理像素）。与图集无关，所以 getWidth 可以独立使用。
         *
         * <p><b>★ 性能修复（2026-10-07，见性能审计）</b>：
         * 旧实现<b>每次调用</b>都 {@code MemoryStack.stackPush()} +
         * {@code stbtt_GetCodepointHMetrics}（一次 native 调用）。
         * 而 {@code getWidth()} 被 HUD 每帧大量调用：
         * <pre>
         *   CoordHud   每帧 6 次 getWidth（候选字符循环）
         *   PlayerListHud 每行 2 次 × 15 行 = 30 次
         *   SakuraTargetHud.fitText 对每个候选长度调一次 -> O(L²)
         * </pre>
         * 60fps 下就是每帧数百~数千次 native 调用。
         *
         * <p>现已缓存：键 = {@code rasterHeight << 32 | codepoint}
         * （与 {@link #glyph} 的键法一致；advance 只跟「哪个字体 + 光栅高度」有关）。
         */
        private double advanceOf(int cp, SizeMetrics m) {
            // 缓存键：用 SizeMetrics 的【身份哈希】而不是字段值 ——
            //   metricsFor(rasterHeight) 对每个光栅高度只创建一个实例并缓存，
            //   所以「实例身份」与「光栅高度」是 1:1 的稳定对应关系。
            //   （SizeMetrics 是 record，其 hashCode 由字段值决定，
            //     不同高度若算出相同的 scale/ascent 会撞键 -> 不能用值哈希。）
            long key = ((long) System.identityHashCode(m) << 32) | (cp & 0xFFFFFFFFL);
            Double cached = advanceCache.get(key);
            if (cached != null) return cached;

            STBTTFontinfo info = pickFont(cp);
            if (info == null) {
                advanceCache.put(key, 0.0);
                return 0.0;
            }

            float scale = info == primaryInfo ? m.primaryScale : m.fallbackScale;

            double value;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer advance = stack.mallocInt(1);
                STBTruetype.stbtt_GetCodepointHMetrics(info, cp, advance, null);
                value = advance.get(0) * scale;
            }
            advanceCache.put(key, value);
            return value;
        }

        /**
         * 某个光栅高度的字体度量（缓存）。
         *
         * <p>基线统一取<b>主字体</b>的 ascent：同一行里中英混排时，
         * 如果各用各的 ascent，两种字体的基线会错开。</p>
         */
        private SizeMetrics metricsFor(int rasterHeight) {
            SizeMetrics cached = metrics.get(rasterHeight);
            if (cached != null) return cached;

            float primaryScale = STBTruetype.stbtt_ScaleForPixelHeight(primaryInfo, rasterHeight);
            float fallbackScale = STBTruetype.stbtt_ScaleForPixelHeight(fallbackInfo, rasterHeight);

            float ascentPx;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer ascent = stack.mallocInt(1);
                STBTruetype.stbtt_GetFontVMetrics(primaryInfo, ascent, null, null);
                ascentPx = ascent.get(0) * primaryScale;
            }

            SizeMetrics m = new SizeMetrics(rasterHeight, primaryScale, fallbackScale, ascentPx);
            metrics.put(rasterHeight, m);
            return m;
        }

        /** 清空图集并丢弃全部字形缓存，下一帧按需重新光栅化。 */
        private void resetAtlas() {
            MemoryUtil.memSet(MemoryUtil.memAddress(atlas), 0, atlas.capacity());
            glyphs.clear();
            packX = 0;
            packY = 0;
            packRowHeight = 0;
            atlasFull = false;
            atlasDirty = true;
        }

        /** 懒加载本字体集的字体与纹理；只在渲染线程被调用。 */
        private boolean ensureInit() {
            if (initialized) return true;
            if (initFailed) return false;

            // getWidth 允许在 begin() 之前调用，但创建纹理必须留在渲染线程。
            if (!RenderSystem.isOnRenderThread()) return false;

            try {
                primaryBuffer = readFont(fontSet.primaryFont());
                fallbackBuffer = readFont(fontSet.fallbackFont());

                primaryInfo = STBTTFontinfo.create();
                fallbackInfo = STBTTFontinfo.create();

                if (!STBTruetype.stbtt_InitFont(primaryInfo, primaryBuffer)) {
                    throw new IOException("stbtt_InitFont 失败: " + fontSet.primaryFont());
                }
                if (!STBTruetype.stbtt_InitFont(fallbackInfo, fallbackBuffer)) {
                    throw new IOException("stbtt_InitFont 失败: " + fontSet.fallbackFont());
                }

                int atlasSize = fontSet.atlasSize();
                atlas = BufferUtils.createByteBuffer(atlasSize * atlasSize);
                texture = new Texture(atlasSize, atlasSize, TextureFormat.RED8, FilterMode.LINEAR, FilterMode.LINEAR);
                texture.upload(atlas);

                initialized = true;
                return true;
            } catch (IOException | RuntimeException e) {
                // ★ 诊断（照 img-diagnose-loop 铁律：绝不用 catch 把异常吞成「值恒为 0」）。
                //   原来这里只设 initFailed 就返回，浏览器日志里【一条线索都没有】——
                //   而后果是：begin() 静默 return -> building 恒 false
                //   -> 所有 draw() 直接丢 -> 「文字不显示，图标却正常」。
                initFailed = true;
                diagOnce("FontSetState.ensureInit FAILED set=" + fontSet
                    + " primary=" + fontSet.primaryFont()
                    + " fallback=" + fontSet.fallbackFont()
                    + " ex=" + e);
                return false;
            }
        }

        /** 释放本字体集的纹理与缓存；字段状态回到未初始化。 */
        private void destroy() {
            glyphs.clear();
            metrics.clear();

            if (texture != null) {
                texture.close();
                texture = null;
            }

            atlas = null;
            primaryBuffer = null;
            fallbackBuffer = null;
            primaryInfo = null;
            fallbackInfo = null;

            initialized = false;
            initFailed = false;
            atlasFull = false;
            atlasDirty = false;
        }
    }

    /** 把 classpath 上的字体读进直接内存（stb 需要一个常驻的直接缓冲区）。 */
    private static ByteBuffer readFont(String path) throws IOException {
        try (InputStream in = SakuraTextRenderer.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException("找不到字体资源: " + path);

            byte[] bytes = in.readAllBytes();
            ByteBuffer buffer = BufferUtils.createByteBuffer(bytes.length);
            buffer.put(bytes);
            buffer.flip();
            return buffer;
        }
    }
}
