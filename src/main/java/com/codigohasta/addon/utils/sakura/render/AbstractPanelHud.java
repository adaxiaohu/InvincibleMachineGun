package com.codigohasta.addon.utils.sakura.render;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.Sprite;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Atlases;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractPanelHud extends Module {

    /** 列表行高（{@code gf.q} 画面板用的 16.0F）。 */
    protected static final float ROW_HEIGHT = 16f;
    /** 列表行宽 = 文字宽 + 此值（{@code gf.q} 的 24.0F）。 */
    protected static final float ROW_WIDTH_PAD = 24f;
    /** 列表行图标边长（{@code gf.q} 的 10.0F）。 */
    protected static final float ROW_ICON_SIZE = 10f;
    /** 列表行图标 x 偏移。 */
    protected static final float ROW_ICON_X = 2f;
    /** 列表行图标 y 偏移（16 高面板里 10px 图标垂直居中）。 */
    protected static final float ROW_ICON_Y = 1.5f;
    /** 列表行文字 x 偏移（{@code gf.q} 的 18.0F）。 */
    protected static final float ROW_TEXT_X = 18f;
    /** 列表行文字 y 偏移。 */
    protected static final float ROW_TEXT_Y = 2f;
    /** 物品图标的标准边长（原版物品贴图是 16×16）。 */
    protected static final float ITEM_SIZE = 16f;

    // ═══════════ 公共设置 ═══════════

    protected final SettingGroup sgGeneral = settings.getDefaultGroup();
    protected final SettingGroup sgLook = settings.createGroup("外观");

    private final Setting<Boolean> hudEnabled;
    private final Setting<Integer> hudX;
    private final Setting<Integer> hudY;
    private final Setting<Double> hudScale;

    private final Setting<SettingColor> panelColor = sgLook.add(new ColorSetting.Builder()
        .name("面板颜色").description("面板背景色。")
        .defaultValue(new SettingColor(0, 0, 0, 120)).build());
    private final Setting<Boolean> panelBlur = sgLook.add(new BoolSetting.Builder()
        .name("背景模糊").description("面板后方的圆角背景模糊。").defaultValue(true).build());
    private final Setting<Double> blurStrength = sgLook.add(new DoubleSetting.Builder()
        .name("模糊强度").description("越大越糊。").defaultValue(6.0).min(1.0).max(20.0)
        .sliderRange(1.0, 20.0).visible(panelBlur::get).build());
    private final Setting<Double> panelRadius = sgLook.add(new DoubleSetting.Builder()
        .name("面板圆角").description("圆角半径。").defaultValue(3.5)
        .min(0.0).max(20.0).sliderRange(0.0, 20.0).build());
    private final Setting<Double> fontSize = sgLook.add(new DoubleSetting.Builder()
        .name("字号").description("文字大小。").defaultValue(9.0)
        .min(5.0).max(20.0).sliderRange(5.0, 20.0).build());
    private final Setting<SettingColor> textColor = sgLook.add(new ColorSetting.Builder()
        .name("文字颜色").description("文字与图标的颜色。")
        .defaultValue(new SettingColor(255, 255, 255, 255)).build());
    private final Setting<SettingColor> dividerColor = sgLook.add(new ColorSetting.Builder()
        .name("分隔线颜色").description("物品格子之间的细分割线颜色。")
        .defaultValue(new SettingColor(255, 255, 255, 40)).build());
    private final Setting<Boolean> showDividers = sgLook.add(new BoolSetting.Builder()
        .name("格子分隔线").description("在每个物品格子之间画一条细分割线。")
        .defaultValue(true).build());
    private final Setting<Boolean> panelBorder = sgLook.add(new BoolSetting.Builder()
        .name("面板描边")
        .description("在面板边缘画一圈细描边。面板是「填充 + 描边」两层"
            + "（见其 rect.fsh 的 uBorderWidth/uBorderColor），描边让面板在深色背景上不糊边。")
        .defaultValue(true).build());

    /**
     * 面板描边宽度（设计单位，写死不暴露设置）。
     *
     * <p>用户 2026-10-06 指定：所有 CatHud 描边统一 <b>0.5</b>，并去掉宽度滑块。
     */
    protected static final float BORDER_WIDTH = 0.5f;
    /**
     * 描边色相对面板色的亮度偏移量（0~255）。
     *
     * <p>用户 2026-10-06 指定：描边色要<b>跟随面板色</b>、且要明显。
     *
     * <p><b>为什么不能向固定色 lerp（上一版的 bug）</b>：旧实现是
     * {@code lerp(面板色, #2F3631, 0.85)} —— 85% 来自固定色，只有 15% 来自面板色。
     * 于是黑面板得 rgb(40,46,42)、红面板得 rgb(67,52,48)，几乎一样
     * —— 用户换面板色时描边<b>看起来没变</b>。
     *
     * <p>改为<b>按亮度往对比方向偏移</b>：暗面板提亮、亮面板压暗。
     * 这样既跟随面板色相，又始终与面板形成清晰边界。
     */
    protected static final int BORDER_DELTA = 55;

    // ═══════════ 每帧队列（三遍提交用）═══════════

    /** 待画的面板（GL：模糊 + 底色）。 */
    private final List<float[]> panels = new ArrayList<>();
    /** 待画的纯色形状（进度条、分隔线等）。 */
    private final List<Object[]> shapes = new ArrayList<>();
    /** 待画的文字。 */
    private final List<Object[]> texts = new ArrayList<>();
    /** 待画的 SVG 图标（与文字同属第二遍：都需要 SakuraTextRenderer 的 begin/end 包裹）。 */
    private final List<Object[]> icons = new ArrayList<>();
    /** 待画的物品图标。 */
    private final List<Object[]> items = new ArrayList<>();
    /**
     * 待画的<b>覆盖层文字</b>（画在物品<b>之上</b>的第四遍）。
     *
     * <p><b>为什么需要单独一层</b>：物品图标用 {@code RenderUtils.drawItem} 画，
     * 它是原版渲染管线的独立四边形，<b>会盖住</b>同位置已画的文字。所以
     * 「物品数量」这类要压在物品上的字，必须在物品<b>之后</b>再画一遍。
     *
     * <p>条目格式：{text, x, y, alpha, fontPx}。
     */
    private final List<Object[]> overlayTexts = new ArrayList<>();

    protected AbstractPanelHud(String id, String description,
                               int defaultX, int defaultY, double defaultScale) {
        super(AddonTemplate.IMG_HUD_CATEGORY, id, description);

        // 位置类设置用构造参数创建，好让【每个组件有自己的默认值】
        // （写成字段初始化器就只能全用同一组值，多个组件会叠在一起）
        hudEnabled = sgGeneral.add(new BoolSetting.Builder()
            .name("启用").description("显示这个 HUD。").defaultValue(true).build());
        hudX = sgGeneral.add(new IntSetting.Builder()
            .name("X坐标").description("锚点 X（逻辑坐标）。")
            .defaultValue(defaultX).sliderRange(0, 2000).build());
        hudY = sgGeneral.add(new IntSetting.Builder()
            .name("Y坐标").description("锚点 Y（逻辑坐标）。")
            .defaultValue(defaultY).sliderRange(0, 2000).build());
        hudScale = sgGeneral.add(new DoubleSetting.Builder()
            .name("缩放").description("整体缩放。").defaultValue(defaultScale)
            .min(0.5).max(2.0).sliderRange(0.5, 2.0).build());
    }

    // ═══════════ 子类接口 ═══════════

    /**
     * 声明本帧要画的内容（用下面那些 {@code queueXxx} 助手入队）。
     *
     * @param x     锚点 X（逻辑坐标）
     * @param y     锚点 Y（逻辑坐标）
     * @param scale 当前缩放
     */
    protected abstract void render(float x, float y, float scale);

    /** 每 tick 回调（需要缓存/采样的组件覆写；默认空实现）。 */
    protected void onTickUpdate() {}

    // ═══════════ 入队助手（子类用）═══════════

    /** 入队一个面板（模糊 + 底色）。 */
    protected final void queuePanel(float x, float y, float w, float h, float alpha) {
        panels.add(new float[]{x, y, w, h, alpha});
    }

    /** 入队一个纯色圆角形状（进度条、格子分隔线等）。 */
    protected final void queueShape(float x, float y, float w, float h, float radius,
                                    Color color, float alpha) {
        shapes.add(new Object[]{x, y, w, h, radius, color, alpha});
    }

    /** 入队一段文字。 */
    protected final void queueText(String text, float x, float y, float alpha) {
        if (text == null || text.isEmpty()) return;
        texts.add(new Object[]{text, x, y, alpha, null});
    }

    /**
     * 入队一段文字，<b>指定字号</b>（覆盖面板的默认字号）。
     *
     * <p>用途：某些文字需要比面板默认字号更小 —— 例如 FlightHud 的 6 行数值，
     * 面板只有 120 宽、文字从 x=67 起（可用 53），默认字号 9.0 下最长行宽 57.6
     * <b>会溢出面板</b>（STB 实测，见 {@code FlightTextProbe}）。
     *
     * @param fontSize 字号（设计单位）；null = 用面板默认
     */
    protected final void queueText(String text, float x, float y, float alpha, Float fontSize) {
        if (text == null || text.isEmpty()) return;
        texts.add(new Object[]{text, x, y, alpha, fontSize});
    }

    /**
     * 入队一个 SVG 图标（如标题的 {@code inventory-widget}）。
     *
     * <p>与文字同属第二遍：{@code SakuraSvgIcons.draw} 内部走
     * {@link Shader2DUtil} 的贴图管线，需要在 {@link SakuraTextRenderer#begin()} /
     * {@link SakuraTextRenderer#end()} 之间调用（与 {@code AbstractRowHud} 的做法一致）。
     *
     * @param name 图标文件名（不含 .svg），位于 {@code assets/invincible_machine_gun/vector/}
     */
    protected final void queueIcon(String name, float x, float y, float size, float alpha) {
        if (name == null || name.isEmpty()) return;
        icons.add(new Object[]{name, x, y, size, alpha, 0f});
    }

    /**
     * 入队一个 SVG 图标，<b>带旋转</b>（用于指南针指针这类随角度转的图标）。
     *
     * <p><b>⚠️ 为什么必须走队列、不能直接调 {@code SakuraSvgIcons.drawRotated}</b>：
     * {@code render()} 只是<b>收集</b>阶段，真正的绘制在它之后的 4 遍里按顺序进行。
     * 若在 {@code render()} 里直接画，那次绘制会发生在<b>第一遍（面板）之前</b>
     * —— 结果就是图标被面板盖住（本项目踩过的坑，2026-10-08）。
     *
     * <p>旋转在着色器里做（{@code rect_textured.fsh} 的 {@code uRotation}）：
     * SVG 只栅格化一次、任意角度复用同一张纹理。
     *
     * @param rotationDeg 旋转角（度，顺时针）
     */
    protected final void queueIcon(String name, float x, float y, float size, float alpha,
                                   float rotationDeg) {
        if (name == null || name.isEmpty()) return;
        icons.add(new Object[]{name, x, y, size, alpha, rotationDeg, null});
    }

    /**
     * 入队一个 SVG 图标，<b>带旋转 + 独立染色 + 独立栅格化尺寸</b>。
     *
     * <p><b>⚠️ {@code rasterSize} 必须传【稳定值】，不能含动画系数</b>
     * （本项目坑 30）：{@code SakuraSvgIcons} 的缓存键含栅格化像素尺寸，
     * 若把逐帧变化的尺寸（如 {@code 15 * scale * k}，k 在 0~1.15 间弹）
     * 当栅格化尺寸，就会<b>每帧缓存未命中</b> → 每帧 {@code registerTexture()} 新建 GL 纹理，
     * 而缓存只增不减 → <b>永久泄漏</b>。
     *
     * <p>正解：{@code rasterSize} 用<b>动画结束后的最终尺寸</b>（如 {@code 15 * scale}），
     * {@code size} 用当前动画尺寸。两者不同是安全的（着色器的 {@code unit} 会算对映射）。
     *
     * @param tint       染色；null = 用基类文字色
     * @param rotationDeg 旋转角（度，顺时针）
     * @param rasterSize 栅格化尺寸（<b>必须稳定</b>）；&lt;=0 时退回用 {@code size}
     */
    protected final void queueIcon(String name, float x, float y, float size, float alpha,
                                   float rotationDeg,
                                   meteordevelopment.meteorclient.utils.render.color.Color tint,
                                   float rasterSize) {
        if (name == null || name.isEmpty()) return;
        icons.add(new Object[]{name, x, y, size, alpha, rotationDeg, tint,
            rasterSize > 0f ? rasterSize : size});
    }

    /** 入队一个 SVG 图标，<b>带旋转 + 独立染色</b>（栅格化尺寸同显示尺寸）。 */
    protected final void queueIcon(String name, float x, float y, float size, float alpha,
                                   float rotationDeg,
                                   meteordevelopment.meteorclient.utils.render.color.Color tint) {
        queueIcon(name, x, y, size, alpha, rotationDeg, tint, 0f);
    }

    /**
     * 入队一段<b>覆盖层文字</b> —— 画在物品<b>之上</b>（第四遍）。
     *
     * <p>用于「物品数量」这类必须压在物品图标上的文字。
     *
     * <p><b>⚠️ 用的是原版字体（{@code mc.textRenderer}），不是 Sakura 字体</b>：
     * 原版文字走 {@code DrawContext.drawText} → 入队到「文字队列」，
     * 而帧末的绘制顺序是 简单元素 → <b>文字</b> → <b>物品</b> → 特殊元素。
     * 只有入队到文字队列，才能保证排在物品之后（详见 {@code onRender2D} 第四遍的说明）。
     * 代价是字号固定为原版的 9 逻辑像素、不可缩放 —— 对「数字 + 阴影」正合适。
     *
     * <p>量宽度请用 {@code mc.textRenderer.getWidth(text)}（原版字体度量）。
     */
    protected final void queueOverlayText(String text, float x, float y, float alpha) {
        if (text == null || text.isEmpty()) return;
        overlayTexts.add(new Object[]{text, x, y, alpha, Boolean.FALSE});
    }

    /**
     * 入队一段<b>小字号</b>覆盖层文字。
     *
     * <p>用户 2026-10-07：「InventoryHud 的物品数字的字体大小再缩小一些」。
     *
     * <p><b>为什么不能用缩放倍率</b>：覆盖层文字走原版文字队列 + 定制字体，
     * 字号由字体资源文件（{@code font/cat_small.json} 的 {@code size} 字段）决定，
     * 没有「传一个缩放倍率」的入口。所以正解是<b>另注册一套更小字号的字体</b>
     * （{@code invincible_machine_gun:cat_small}，size 6.5），让这类文字挂上它。
     */
    protected final void queueOverlayTextSmall(String text, float x, float y, float alpha) {
        if (text == null || text.isEmpty()) return;
        overlayTexts.add(new Object[]{text, x, y, alpha, Boolean.TRUE});
    }

    /** 入队一个物品图标（{@code size} 为逻辑边长，16 = 原始大小）。 */
    protected final void queueItem(ItemStack stack, float x, float y, float size, float alpha) {
        if (stack == null || stack.isEmpty()) return;
        items.add(new Object[]{stack, x, y, size, alpha});
    }

    /**
     * 入队一张<b>原版贴图集里的 sprite</b>（如药水效果图标 {@code mob_effect/<id>}）。
     *
     * <p>药水效果没有 {@code ItemStack}，图标是图集 sprite，所以不能用
     * {@link #queueItem}。这里直接记录 {@code Sprite}，在第二遍随图标一起画
     * （{@code DrawContext.drawSpriteStretched}）。
     *
     * @param sprite 由 {@link #spriteOf} 取得
     */
    protected final void queueSprite(Object sprite, float x, float y, float w, float h, float alpha) {
        if (sprite == null) return;
        sprites.add(new Object[]{sprite, x, y, w, h, alpha});
    }

    /**
     * 取原版 {@code gui} 图集里的 sprite。
     *
     * <p>药水图标路径：{@code minecraft:textures/mob_effect/<effect_id>.png}，
     * 它注册在 {@code gui} 图集（见 {@code assets/minecraft/atlases/gui.json}
     * 的 {@code "prefix": "mob_effect/"} 条目）。
     *
     * <p><b>图集 id 必须用 {@link Atlases#GUI} 常量</b>，不能自己拼
     * {@code "textures/atlas/gui.png"} —— {@code AtlasManager.getAtlasTexture}
     * 是按 {@code Atlases} 里的常量注册的，传错 id 会<b>抛 IllegalArgumentException</b>
     * （而不是返回 null），静默 catch 掉就表现为「图标不显示」。
     *
     * @param path 图集内的路径，如 {@code "mob_effect/speed"}
     * @return sprite；查不到返回 null（调用方需容错）
     */
    protected final Object spriteOf(String path) {
        if (path == null || path.isEmpty()) return null;
        try {
            var atlas = mc.getAtlasManager().getAtlasTexture(Atlases.GUI);
            if (atlas == null) return null;
            return atlas.getSprite(Identifier.of("minecraft", path));
        } catch (Throwable t) {
            return null;
        }
    }

    /** 待画的 sprite（第二遍，与 SVG 图标同批）。 */
    private final List<Object[]> sprites = new ArrayList<>();

    // ═══════════ 渲染 ═══════════

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;
        onTickUpdate();
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!hudEnabled.get()) return;
        if (mc.player == null || mc.world == null) return;

        panels.clear();
        shapes.clear();
        texts.clear();
        icons.clear();
        items.clear();
        overlayTexts.clear();
        sprites.clear();

        // 本帧时长（秒），供子类推进动画用。
        // 取 event.frameTime 并做合理性夹取（首帧/卡顿时可能是 0 或很大的值）。
        float dt = (float) event.frameTime;
        frameDelta = (dt > 0f && dt < 0.5f) ? dt : 1f / 60f;

        float scale = hudScale.get().floatValue();
        render(hudX.get(), hudY.get(), scale);

        // ── 第一遍：面板 + 形状（GL）──
        for (float[] p : panels) {
            float radius = Math.max(0f,
                Math.min(panelRadius.get().floatValue() * scale, Math.min(p[2], p[3]) / 2f));
            var pc = panelColor.get();
            int a = Math.round(pc.a * p[4]);
            if (panelBlur.get()) {
                Shader2DUtil.drawRoundedBlur(p[0], p[1], p[2], p[3], radius,
                    new java.awt.Color(pc.r, pc.g, pc.b, Math.max(0, a)),
                    blurStrength.get().floatValue(), 0.9f * p[4], true);
            }
            Color borderCol = panelBorder.get() ? derivedBorderColor(pc, p[4]) : null;
            float bw = panelBorder.get() ? BORDER_WIDTH * scale : 0f;
            Shader2DUtil.drawRoundedRect(p[0], p[1], p[2], p[3], radius,
                new Color(pc.r, pc.g, pc.b, Math.max(0, a)), bw, borderCol);
        }
        for (Object[] s : shapes) {
            Color c = (Color) s[5];
            float alpha = (Float) s[6];
            Shader2DUtil.drawRoundedRect((Float) s[0], (Float) s[1], (Float) s[2], (Float) s[3],
                (Float) s[4],
                new Color(c.r, c.g, c.b, Math.round(c.a * alpha)), 0f, null);
        }

        // ── 第二遍：SVG 图标 + 文字（SakuraTextRenderer 需要 begin/end 包裹）──
        float fontPx = fontSize.get().floatValue() * scale;
        var tc = textColor.get();
        Color fg = new Color(tc.r, tc.g, tc.b, tc.a);
        SakuraTextRenderer.begin();
        try {
            for (Object[] ic : icons) {
                float alpha = (Float) ic[4];
                if (alpha <= 0.01f) continue;
                String name = (String) ic[0];
                float ix = (Float) ic[1], iy = (Float) ic[2], iw = (Float) ic[3];
                // 第 7 项是可选的【独立染色】；null = 用基类文字色
                Color custom = (ic.length > 6) ? (Color) ic[6] : null;
                Color tint = custom != null
                    ? new Color(custom.r, custom.g, custom.b, Math.round(custom.a * alpha))
                    : new Color(tc.r, tc.g, tc.b, Math.round(tc.a * alpha));
                // 第 6 项是可选的【旋转角】（度）
                float rot = (ic.length > 5) ? (Float) ic[5] : 0f;
                // 第 8 项是可选的【栅格化尺寸】（必须稳定，见 queueIcon 的说明）
                float raster = (ic.length > 7) ? (Float) ic[7] : iw;
                if (rot == 0f) {
                    SakuraSvgIcons.draw(name, ix, iy, iw, raster, tint);
                } else {
                    // 带旋转：旋转在着色器里做（零额外纹理）
                    SakuraSvgIcons.drawRotated(name, ix, iy, iw, raster, rot, tint);
                }
            }
            for (Object[] t : texts) {
                float alpha = (Float) t[3];
                int argb = (Math.round(tc.a * alpha) << 24) | (tc.r << 16) | (tc.g << 8) | tc.b;
                // 第 5 项是可选的【字号覆盖】（null = 用面板默认字号）
                Float override = (t.length > 4) ? (Float) t[4] : null;
                float px = override != null ? override * scale : fontPx;
                SakuraTextRenderer.draw((String) t[0], (Float) t[1], (Float) t[2], px, argb);
            }
        } finally {
            SakuraTextRenderer.end();
        }

        // ── 第二遍之补：原版图集 sprite（药水效果图标等）──
        // 必须用原版 DrawContext 画（SakuraTextRenderer 的贴图管线不认图集 sprite）。
        // 放在这里（物品之前）是因为 sprite 与物品不重叠 —— 它们各占自己的面板。
        DrawContext ctx = event.drawContext;
        for (Object[] sp : sprites) {
            float alpha = (Float) sp[5];
            if (alpha <= 0.01f) continue;
            int a8 = Math.round(255 * Math.max(0f, Math.min(1f, alpha)));
            ctx.drawSpriteStretched(RenderPipelines.GUI_TEXTURED,
                (Sprite) sp[0],
                Math.round((Float) sp[1]), Math.round((Float) sp[2]),
                Math.round((Float) sp[3]), Math.round((Float) sp[4]),
                (a8 << 24) | 0xFFFFFF);
        }

        // ── 第三遍：物品图标 ──
        // ★ 这里把原版的 overlay（数量字）【关掉】：物品被缩小后，原版数量字会
        //   跟着一起缩小到看不清；改由子类用 queueOverlayText 在第四遍自己画。
        for (Object[] it : items) {
            ItemStack stack = (ItemStack) it[0];
            float ix = (Float) it[1];
            float iy = (Float) it[2];
            float size = (Float) it[3];
            float alpha = (Float) it[4];
            // alpha < 1 时原版物品渲染不支持整体透明（没有该参数），
            // 本项目这几个组件不做淡入淡出，故直接画；
            // 若将来要动画，需自行用 GlStateManager 压 alpha（见 SakuraTargetHud 的做法）。
            if (alpha <= 0.01f) continue;
            RenderUtils.drawItem(ctx, stack, (int) ix, (int) iy, size / ITEM_SIZE,
                false, null, false);
        }

        // ── 第四遍：覆盖层文字（画在物品【之上】，用原版字体）──
        //
        // ★ 图层问题的历史（连修两次才对，记录清楚免得再踩）★
        //
        //   1.21.11 的 GUI 是【延迟渲染 + 分类队列】。GuiRenderer 的 prepare 顺序
        //       prepareSimpleElements(299) → prepareTextElements(600)
        //       → prepareItemElements(609) → prepareSpecialElements(693)
        //   即【物品整体排在所有文字之后】。
        //
        //   而 SakuraTextRenderer 是【立即绘制】的独立 GL 管线 —— 它在 Render2DEvent 里
        //   当场落屏，早于帧末的队列渲染。所以「用 Sakura 字体画压在物品上的字」结果是：
        //   字先落屏 → 帧末物品队列再画 → 【物品压住字】。
        //
        //   结论：凡是【与物品重叠】的文字（如背包格子里的数量），只能用原版文字队列
        //   （DrawContext.drawText），代价是只能用原版字体。
        //   凡是【不与物品重叠】的文字（如冷却/药水的时间，在独立面板里），
        //   直接用 {@code queueText} 走 Sakura 定制字体即可 —— 见 CooldownsHud。
        if (!overlayTexts.isEmpty()) {
            for (Object[] t : overlayTexts) {
                float alpha = (Float) t[3];
                int a8 = Math.round(255 * Math.max(0f, Math.min(1f, alpha)));
                int argb = (a8 << 24) | 0xFFFFFF;
                // ★ 用【定制字体】而非原版字体：给文字挂上我们注册的字体 id
                //   （见 catFontStyle()）。原版 TextRenderer 会按 Style 里的 font
                //   路由到对应 FontStorage —— 于是字形来自 regular_medium.otf，
                //   而绘制仍走原版文字队列（排在物品之后，能压在物品上）。
                //   shadow=true 即原版的「右下 1px 暗影」，保证压在杂色物品上仍可读。
                ctx.drawText(mc.textRenderer, overlayText(t[0], Boolean.TRUE.equals(t[4])),
                    Math.round((Float) t[1]), Math.round((Float) t[2]), argb, true);
            }
        }
    }

    private static Text overlayText(Object raw) {
        return overlayText(raw, false);
    }

    /**
     * 把覆盖层文字包成带定制字体的 {@link Text}。
     *
     * @param small true = 用更小字号的那套字体（{@code cat_small}）
     */
    private static Text overlayText(Object raw, boolean small) {
        return Text.literal((String) raw)
            .setStyle(small ? CAT_FONT_STYLE_SMALL : CAT_FONT_STYLE);
    }

    /** 定制字体样式（常规字号，所有覆盖层文字共用）。 */
    private static final Style CAT_FONT_STYLE = Style.EMPTY.withFont(
        new StyleSpriteSource.Font(Identifier.of("invincible_machine_gun", "cat")));

    /**
     * 定制字体样式（<b>小字号</b>，size 6.5）。
     *
     * <p>用于物品数量这类「压在物品角上的小数字」。
     * 字体定义见 {@code assets/invincible_machine_gun/font/cat_small.json}。
     */
    private static final Style CAT_FONT_STYLE_SMALL = Style.EMPTY.withFont(
        new StyleSpriteSource.Font(Identifier.of("invincible_machine_gun", "cat_small")));

    /**
     * 量一段<b>覆盖层文字</b>的宽度（逻辑像素）。
     *
     * <p>子类布局时必须用这个，而不是 {@code mc.textRenderer.getWidth(String)} ——
     * 后者按<b>原版字体</b>度量，与定制字体（cat.json）的字形宽度不一致，
     * 会导致右对齐/居中偏移。
     */
    protected final int overlayTextWidth(String text) {
        return mc.textRenderer.getWidth(Text.literal(text).setStyle(CAT_FONT_STYLE));
    }

    // ═══════════ 供子类读取的访问器 ═══════════

    /** 当前字号（逻辑像素）。子类做文字度量时需要。 */
    protected final float currentFontPx() {
        return fontSize.get().floatValue() * hudScale.get().floatValue();
    }

    /**
     * 本帧时长（秒），供子类推进动画。
     *
     * <p>由 {@code onRender2D} 每帧从 {@code event.frameTime} 更新并夹取到
     * {@code (0, 0.5)} 区间 —— 首帧或卡顿时 frameTime 可能是 0 或异常大的值，
     * 直接用会让动画跳变或永远不动。
     */
    protected final float frameDelta() {
        return frameDelta;
    }

    private float frameDelta = 1f / 60f;

    /**
     * 由面板色派生描边色：<b>按亮度往对比方向偏移</b>（暗提亮 / 亮压暗）。
     *
     * <p>详见 {@link #BORDER_DELTA} 的说明 —— 旧版「向固定色 lerp」导致换面板色时
     * 描边几乎不变（用户实测反馈），这是本版修正的核心。
     */
    private Color derivedBorderColor(SettingColor panel, float panelAlpha) {
        int lum = (panel.r * 299 + panel.g * 587 + panel.b * 114) / 1000;
        int d = lum < 128 ? BORDER_DELTA : -BORDER_DELTA;   // 暗面板提亮，亮面板压暗
        int r = Math.max(0, Math.min(255, panel.r + d));
        int g = Math.max(0, Math.min(255, panel.g + d));
        int b = Math.max(0, Math.min(255, panel.b + d));
        int a = Math.round(panel.a * Math.max(0f, Math.min(1f, panelAlpha)));
        return new Color(r, g, b, a);
    }

    /** 当前缩放。 */
    protected final float currentScale() {
        return hudScale.get().floatValue();
    }

    /** 分隔线颜色。 */
    protected final SettingColor dividerColorSetting() {
        return dividerColor.get();
    }

    /** 是否显示格子分隔线。 */
    protected final boolean dividersEnabled() {
        return showDividers.get();
    }
}
