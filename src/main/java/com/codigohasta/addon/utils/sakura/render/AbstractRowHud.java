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
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;

public abstract class AbstractRowHud extends Module {

    // ═══════════ 几何常量（✅ gf.X()，详见类注释）═══════════

    /** 面板高。{@code ldc 13.0f}。 */
    protected static final float ROW_HEIGHT = 13f;
    /** 面板宽 = 文字宽 + 此值。{@code ldc 19.0f}（元素宽是 +20，面板比元素窄 1）。 */
    protected static final float PANEL_PAD = 19f;
    /** 图标边长。{@code ldc 10.0f}。 */
    protected static final float ICON_SIZE = 10f;
    /** 图标 x 偏移（源码 {@code fconst_2}）；y 现在按面板垂直居中。 */
    protected static final float ICON_X = 2f;
    /** 文字 x 偏移（源码 {@code ldc 16.0f}）；y 现在按面板垂直居中。 */
    protected static final float TEXT_X = 16f;

    /** 竖线 x 偏移（⚠️ 推断值：居中于图标右缘 12 与文字左缘 16 之间）。 */
    protected static final float DIVIDER_X = 14f;
    protected static final float DIVIDER_W = 0.2f;
    protected static final float DIVIDER_H = 8.5f;

    // ═══════════ 公共设置 ═══════════
    // 声明在基类，但 JSON 键用「子类模块名 + 这些字段名」（findFieldName 沿继承链找）。

    protected final SettingGroup sgGeneral = settings.getDefaultGroup();
    protected final SettingGroup sgLook = settings.createGroup("外观");

    private final Setting<Boolean> hudEnabled;
    private final Setting<Integer> hudX;
    private final Setting<Integer> hudY;
    private final Setting<Double> hudScale;

    private final Setting<Boolean> showIcon = sgLook.add(new BoolSetting.Builder()
        .name("显示图标").description("在文字左侧画一个 10×10 的图标。")
        .defaultValue(true).build());
    private final Setting<Boolean> showDivider = sgLook.add(new BoolSetting.Builder()
        .name("分隔竖线").description("在图标与文字之间画一条细竖线。")
        .defaultValue(true).build());
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
        .name("竖线颜色").description("图标与文字之间那条细竖线的颜色。")
        .defaultValue(new SettingColor(255, 255, 255, 70))
        .visible(showDivider::get).build());
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
     * 旧版「向固定色 {@code #2F3631} lerp 0.85」导致换面板色时描边几乎不变，
     * 改为<b>按亮度往对比方向偏移</b>（暗提亮 / 亮压暗）。
     */
    protected static final int BORDER_DELTA = 55;

    /**
     * @param id            模块 id（同时是存档键，别改）
     * @param description   描述
     * @param defaultX      默认 X（每个组件位置不同，故由子类传入）
     * @param defaultY      默认 Y
     * @param defaultScale  默认缩放
     */
    protected AbstractRowHud(String id, String description,
                             int defaultX, int defaultY, double defaultScale) {
        super(AddonTemplate.IMG_HUD_CATEGORY, id, description);

        // 位置类设置在这里创建，好让【每个组件有自己的默认值】。
        // （写成字段初始化器就只能全用同一组默认值，三个组件会叠在同一处。）
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

    // ═══════════ 子类实现 ═══════════

    /** 图标文件名（不含 .svg）。{@code null} = 不画图标。 */
    protected abstract String iconName();

    /** 本帧显示的文字；{@code null} = 本帧不显示（整个面板不画）。 */
    protected abstract String displayText();

    /** 每 tick 回调（需要采样的组件覆写；默认空实现）。 */
    protected void onTickUpdate() {}

    /**
     * 当前字号（逻辑像素）—— 子类做文字度量时需要。
     *
     * <p>{@link SakuraTextRenderer#getWidth} 要求传入字号才能量宽，
     * 而字号 = {@code fontSize 设置 × 缩放}，两个都是基类的私有设置，故在此暴露。
     */
    protected float currentFontPx() {
        return fontSize.get().floatValue() * hudScale.get().floatValue();
    }

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

        String text = displayText();
        if (text == null) return;

        float scale = hudScale.get().floatValue();
        float fontPx = fontSize.get().floatValue() * scale;
        float panelH = ROW_HEIGHT * scale;
        float radius = Math.max(0f, Math.min(panelRadius.get().floatValue() * scale, panelH / 2f));

        float textW = (float) SakuraTextRenderer.getWidth(text, fontPx);
        float panelW = textW + PANEL_PAD * scale;
        float x = hudX.get();
        float y = hudY.get();

        // ── 垂直：图标 / 竖线 / 文字 三者中心都落在面板中线上 ──
        // （源码是固定 y+0 / y+3 / y+1，三者中心互不重合；见类注释的取舍说明。）
        float iconPx = ICON_SIZE * scale;
        float divPx = DIVIDER_H * scale;
        float iconY = y + (panelH - iconPx) / 2f;
        float divY = y + (panelH - divPx) / 2f;
        float textY = y + (panelH - fontPx) / 2f;

        // ── 水平：把「图标…文字」当成一整组，在面板内左右留白相等 ──
        // 组内容从图标左缘(ICON_X)到文字右缘(TEXT_X + textW)；
        // 把这一段的左缘推到 (面板宽 − 组宽)/2 处，得到的平移量对所有元素一致。
        // ★ 单位：textW 是逻辑像素（getWidth 已除过 scale），常量要乘 scale —— 别混用。
        float groupLeft = ICON_X * scale;
        float groupW = (TEXT_X * scale + textW) - groupLeft;
        float shift = (panelW - groupW) / 2f - groupLeft;
        float iconX = x + groupLeft + shift;
        // 竖线 x 吸附到物理像素边界：见下方 snapToPhysicalPixel 的说明
        float divX = snapToPhysicalPixel(x + DIVIDER_X * scale + shift);
        float textX = x + TEXT_X * scale + shift;

        var pc = panelColor.get();
        Color bg = new Color(pc.r, pc.g, pc.b, pc.a);
        var tc = textColor.get();
        Color fg = new Color(tc.r, tc.g, tc.b, tc.a);

        // 1) 面板：模糊 → 底色 → 描边（顺序照水印 HUD；底色必须始终画）
        if (panelBlur.get()) {
            Shader2DUtil.drawRoundedBlur(x, y, panelW, panelH, radius,
                new java.awt.Color(bg.r, bg.g, bg.b, bg.a),
                blurStrength.get().floatValue(), 0.9f, true);
        }
        Color borderCol = panelBorder.get() ? derivedBorderColor(pc) : null;
        float bw = panelBorder.get() ? BORDER_WIDTH * scale : 0f;
        Shader2DUtil.drawRoundedRect(x, y, panelW, panelH, radius, bg, bw, borderCol);

        // 2) 图标 + 竖线 + 文字
        String icon = iconName();
        SakuraTextRenderer.begin();
        try {
            if (showIcon.get() && icon != null) {
                SakuraSvgIcons.draw(icon, iconX, iconY, iconPx, fg);
            }
            if (showDivider.get()) {
                var dc = dividerColor.get();
                Shader2DUtil.drawRoundedRect(divX, divY, DIVIDER_W * scale, divPx, 0f,
                    new Color(dc.r, dc.g, dc.b, dc.a), 0f, null);
            }
            SakuraTextRenderer.draw(text, textX, textY, fontPx,
                (fg.a << 24) | (fg.r << 16) | (fg.g << 8) | fg.b);
        } finally {
            SakuraTextRenderer.end();
        }
    }

    /**
     * 把逻辑坐标吸附到<b>物理像素边界</b>。
     *
     * <p><b>为什么需要它</b>：细分隔线（0.2 设计单位 × 缩放 0.9 = 0.18 逻辑像素）
     * 换算到帧缓冲只有 {@code 0.18 × scaleFactor} 个物理像素 —— 在 guiScale=2 时是
     * <b>0.36px，远小于 1 像素</b>。这种宽度无法占满任何一个像素，GPU 会把它按覆盖率
     * 分摊到相邻的一两个像素上。
     *
     * <p>于是「竖线落在子像素的哪个位置」就决定了它看起来有多粗：落在像素中央时摊到
     * 两个像素（各 ~50% 灰，看着细且淡），落得偏时几乎集中在一个像素上（看着实）。
     * 而多行面板里每行的竖线 x 都不同（依赖该行文字宽），<b>每行落点都不一样</b>
     * —— 这就是「同一根竖线，有的行看着粗、有的行细」的原因，不是宽度值不统一。
     *
     * <p><b>做法</b>：把坐标四舍五入到物理像素网格再换回逻辑像素
     * （{@code round(v * sf) / sf}）。<b>不改宽度</b>，所以线依然是细的，
     * 只是每行都落在同一类子像素位置上，粗细因此一致。
     */
    private float snapToPhysicalPixel(float logical) {
        float sf = (float) mc.getWindow().getScaleFactor();
        if (sf <= 0f) return logical;
        return Math.round(logical * sf) / sf;
    }

    /**
     * 由面板色派生描边色：<b>按亮度往对比方向偏移</b>（暗提亮 / 亮压暗）。
     *
     * <p>旧版「向固定色 {@code #2F3631} lerp 0.85」的问题：85% 来自固定色，
     * 只有 15% 来自面板色 —— 换面板色时描边几乎不变（用户实测反馈）。
     * 本版按亮度决定偏移方向，既跟随面板色相、又始终与面板形成清晰边界。
     */
    private Color derivedBorderColor(SettingColor panel) {
        int lum = (panel.r * 299 + panel.g * 587 + panel.b * 114) / 1000;
        int d = lum < 128 ? BORDER_DELTA : -BORDER_DELTA;
        int r = Math.max(0, Math.min(255, panel.r + d));
        int g = Math.max(0, Math.min(255, panel.g + d));
        int b = Math.max(0, Math.min(255, panel.b + d));
        int a = Math.round(panel.a);
        return new Color(r, g, b, a);
    }
}
