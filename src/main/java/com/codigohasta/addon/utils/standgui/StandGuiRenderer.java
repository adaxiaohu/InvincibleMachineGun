package com.codigohasta.addon.utils.standgui;

import com.codigohasta.addon.utils.sakura.render.SakuraSvgIcons;
import com.codigohasta.addon.utils.sakura.render.SakuraTextRenderer;
import com.codigohasta.addon.utils.sakura.render.Shader2DUtil;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.utils.render.color.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * 三栏菜单的绘制（照 {@code E:\standdep} 的取证布局与素材）。
 *
 * <h2>★ 两遍提交的铁律（本项目已踩过两次的坑）</h2>
 * <p>{@link Shader2DUtil} 是<b>裸 GL</b>，{@link SakuraTextRenderer} 与
 * {@link SakuraSvgIcons#drawViaMesh} 是<b>MeshBuilder</b>。
 * 若在 {@code begin()}/{@code end()} 之间插裸 GL 调用，会把文字那一批顶点冲掉 ——
 * 症状正是「面板有、文字全没有」。所以本类严格分两段：
 * <pre>
 *   第一遍：所有面板 / 焦点高亮 / 滚动条 / 封边      (Shader2DUtil，全部画完)
 *   第二遍：所有文字 + 勾选框图标                    (MeshBuilder 通路)
 * </pre>
 * <b>任何时候都不要把 Shader2DUtil 挪进 begin/end 之间。</b>
 * 参见 {@code .dsh/skills/img-notification-hud} §5.8。
 *
 * <h2>勾选框必须是图标（用户 2026-10-20）</h2>
 * <p>Stand 原版用两张 256×256 PNG：
 * <ul>
 *   <li>{@code 040_0827AF0_256x256.png} = 未勾选（白色圆角空心框）</li>
 *   <li>{@code 041_0827FF0_256x256.png} = 已勾选（白底圆角 + 黑色对勾）</li>
 * </ul>
 * 本项目把这两张等价重绘成 SVG（{@code vector/stand-check-off.svg} /
 * {@code stand-check-on.svg}），走 {@link SakuraSvgIcons#drawViaMesh}
 * —— 与文字同一条 MeshRenderer 通路，不需要任何裸 GL 与状态快照。
 *
 * <h2>★ 模块文字不变色（用户 2026-10-20）</h2>
 * <p>「模块选项的的字是不会变色的」——所以<b>不再</b>给已启用的模块换品红字。
 * 开启状态<b>只</b>由右侧勾选框图标表达。
 */
public final class StandGuiRenderer {

    // ═══════════ 配色 ═══════════

    /**
     * 主题色（面包屑条 / 焦点行背景 / 描边 / 滚动条）。
     *
     * <p><b>不再是常量</b>（用户 B2：可自定义，默认 Stand 品红）。
     * 由 {@code StandGui} 每帧从设置里注入 {@link #setAccent}；
     * 保留静态是为了让 {@code StandGuiCommandScreen} 也能共用同一个值。
     */
    private static Color ACCENT = new Color(0xFF, 0x00, 0xFF, 255);

    /**
     * 注入主题色。
     *
     * @param c 新主题色；null 或全透明时退回默认品红
     */
    public static void setAccent(Color c) {
        ACCENT = (c == null || c.a <= 0)
            ? new Color(0xFF, 0x00, 0xFF, 255)
            : new Color(c.r, c.g, c.b, c.a);

        // 描边与滚动条都跟随主题色（用户 B2：换主题色时整套跟着变，
        // 而不是只有标题条变色、边框还是品红）
        BORDER_ACCENT = new Color(ACCENT.r, ACCENT.g, ACCENT.b, 255);
        // 滚动条比主题色略暗，避免在深色面板上过于刺眼
        SCROLLBAR = new Color(
            Math.max(0, ACCENT.r * 3 / 4),
            Math.max(0, ACCENT.g * 3 / 4),
            Math.max(0, ACCENT.b * 3 / 4),
            235);
    }

    /** 取当前主题色（命令框用）。 */
    public static Color accent() {
        return ACCENT;
    }
    /**
     * 面板底色：近黑 + 低不透明度。
     *
     * <p><b>左右栏共用同一个颜色</b>（用户 2026-10-20：「左右栏的颜色一样，
     * 和右栏一样，透明度再降低一些」）。此前左栏 168、右栏 168 虽然已相同，
     * 但底部面板用了 200 更黑 —— 现在统一降到 150，整体更透。
     */
    private static final Color PANEL = new Color(0, 0, 0, 150);
    /** 底部介绍面板底色：与左右栏同一个色（用户要求「颜色一样」）。 */
    private static final Color PANEL_FOOT = new Color(0, 0, 0, 150);
    /** 正文白色。 */
    private static final Color TEXT = new Color(255, 255, 255, 255);
    /** 焦点行文字（压在品红上）。 */
    private static final Color TEXT_ON_ACCENT = new Color(255, 255, 255, 255);
    /** 介绍文字。 */
    private static final Color TEXT_HELP = new Color(215, 218, 226, 255);
    /** 命令文字。 */
    private static final Color TEXT_CMD = new Color(160, 165, 180, 255);
    /** 滚动条（很细的紫色，照截图右侧那条线）。 */
    private static Color SCROLLBAR = new Color(0xC0, 0x30, 0xC0, 235);
    /**
     * 面板描边色（用户 2026-10-20：「菜单的每个面板栏的边框有很细的品红色细线」）。
     *
     * <p>不透明度给足 255 —— 因为线宽只有 0.6px，再降 alpha 在深色背景上就看不见了。
     */
    private static Color BORDER_ACCENT = new Color(0xFF, 0x00, 0xFF, 255);

    // ═══════════ 几何（设计单位 × scale）═══════════

    /** 左栏最小宽度（用户：可以适当缩短）。 */
    private static final float LEFT_MIN_W = 84f;
    /** 左栏最大宽度（防止超长分类名把右栏挤没）。 */
    private static final float LEFT_MAX_W = 150f;
    /** 左栏文字左右内边距合计。 */
    private static final float LEFT_PAD_TOTAL = 12f;
    /**
     * 面包屑高度（设计单位）。
     *
     * <p>用户 2026-10-20：「面包屑的高度可以再细一些」。从 14 压到 11。
     */
    private static final float CRUMB_H = 11f;
    private static final float ROW_H = 15f;
    private static final float GAP = 2f;
    private static final float PAD_X = 6f;
    private static final float RIGHT_MIN_W = 210f;
    /** 滚动条宽（很细，照截图）。 */
    private static final float SCROLLBAR_W = 1.6f;
    /** 勾选框边长。 */
    private static final float CHECKBOX = 9f;
    private static final float FOOT_LINE_H = 11f;
    private static final float FOOT_PAD = 4f;
    /**
     * 面板描边宽度（设计单位，内部会被 {@code drawRoundedRect} 乘 scale 转成物理像素）。
     *
     * <p>取 1.0 —— 用户要的「很细的品红色细线」。
     */
    private static final float BORDER_W = 0.25f;

    /**
     * 面板圆角半径（设计单位）。
     *
     * <h2>⚠️ 这个值不能为 0，它是一个「必须大于描边宽度」的硬约束</h2>
     * <p>{@code rect.fsh} 的 SDF 是：
     * <pre>
     *   float roundedBoxSDF(vec2 center, vec2 halfSize, float r) {
     *       return length(max(abs(center) - halfSize + r, 0.0)) - r;
     *   }
     * </pre>
     * <p>面板<b>内部</b>任意点 {@code abs(center) - halfSize + r} 都是负数，
     * {@code max(...,0)} 把它夹成 0，于是 {@code dist = 0 - r = -r}。
     *
     * <p>而描边判定是 {@code borderAlpha = 1 - smoothstep(bw-1, bw, abs(dist))}：
     * <ul>
     *   <li>{@code r > 0}（例如 2.0）→ 内部 {@code abs(dist) = r}，只要 {@code r > bw}
     *       就 {@code borderAlpha = 0}，内部正常显示填充色 ✅</li>
     *   <li><b>{@code r = 0} → 内部 {@code abs(dist) = 0} → {@code borderAlpha = 1}
     *       → 整个面板被涂成描边色（全品红）</b> ❌</li>
     * </ul>
     *
     * <p>2026-10-20 实测「现在全品红了」就是这个原因：所有面板都传了 {@code radius = 0f}
     * 同时开了描边。项目里 {@code AbstractPanelHud} 一直没这个问题，因为它的
     * {@code panelRadius} 默认 3.5 > {@code BORDER_WIDTH} 0.5。
     *
     * <p><b>结论：r 必须 &gt; border。</b>这里取 2.0（视觉上仍是近乎直角，
     * 与 Stand 截图的方角观感一致）。
     */
    private static final float PANEL_RADIUS = 0.5f;

    private final StandGuiState state;
    private final StandGuiInput input;

    /** 本帧待画的勾选框图标：{x, y, size, on}。 */
    private final List<Object[]> checkboxes = new ArrayList<>();

    /** 本帧待画的左右箭头：{x, y, w, h, 左向?, 颜色}。 */
    private final List<Object[]> arrows = new ArrayList<>();

    /**
     * 箭头尺寸（设计单位）。
     *
     * <p>参考 {@code E:\standdep\extracted\gui_assets_png\右箭头.png}（172×256，
     * 宽高比 ≈ 0.67）。这里按行高的一小部分取，不喧宾夺主。
     */
    private static final float ARROW_W = 4.5f;
    private static final float ARROW_H = 6.5f;

    /**
     * 该项是否能用 {@code 4/6} 调值（决定要不要画左右箭头）。
     *
     * <p>只有 Int / Double / Enum 三类能调；Bool 是勾选框、Color/String 进子页，
     * 给它们画箭头会让用户以为按 4/6 有效果。
     */
    private static boolean isAdjustable(meteordevelopment.meteorclient.settings.Setting<?> s) {
        return s instanceof meteordevelopment.meteorclient.settings.IntSetting
            || s instanceof meteordevelopment.meteorclient.settings.DoubleSetting
            || s instanceof meteordevelopment.meteorclient.settings.EnumSetting<?>;
    }

    /** 收一个箭头（第二遍之后随图标一起画）。 */
    private void queueArrow(float x, float y, float w, float h, boolean pointLeft, Color tint) {
        arrows.add(new Object[]{x, y, w, h, pointLeft, tint});
    }

    public StandGuiRenderer(StandGuiState state, StandGuiInput input) {
        this.state = state;
        this.input = input;
    }

    /**
     * 绘制一帧。
     *
     * @param maxRows      左栏最多显示多少行
     * @param maxRowsRight 右栏最多显示多少行
     */
    public void render(float x0, float y0, float scale, int maxRows, int maxRowsRight) {
        List<StandGuiEntry> cats = StandGuiTree.categories();
        if (cats.isEmpty()) return;

        // ★ Color 编辑子页：独立绘制（R/G/B/A 四行 + 色块预览），不进主列表
        if (state.isColorSubpage() && state.colorSubpageTarget() != null) {
            renderColorSubpage(x0, y0, scale);
            return;
        }

        state.ensureCategory();
        checkboxes.clear();
        arrows.clear();

        List<StandGuiEntry> rightList = state.currentList();
        int catFocus = state.categoryIndex();
        int rightFocus = state.focus(state.currentPath());

        float fontPx = 8.5f * scale;
        float rowH = ROW_H * scale;
        float crumbH = CRUMB_H * scale;
        float padX = PAD_X * scale;
        float gap = GAP * scale;

        // ── 左栏宽度：自适应分类名长度（用户 2026-10-20）──
        // 取「最宽的分类名」+ 内边距，再夹到 [LEFT_MIN_W, LEFT_MAX_W]。
        // 之前写死 140，分类名短的时候（IMG / sfIMG / World）会留一大片空白。
        float widestCat = 0f;
        for (StandGuiEntry c : cats) {
            float w = (float) SakuraTextRenderer.getWidth(c.label, fontPx);
            if (w > widestCat) widestCat = w;
        }
        float leftW = Math.max(LEFT_MIN_W * scale,
            Math.min(LEFT_MAX_W * scale, widestCat + LEFT_PAD_TOTAL * scale));

        int leftRowsVisible = Math.max(1, Math.min(cats.size(), maxRows));
        int rightRowsVisible = Math.max(1, Math.min(rightList.size(), maxRowsRight));

        int leftScroll = scrollFor(cats.size(), leftRowsVisible, catFocus);
        int rightScroll = scrollFor(rightList.size(), rightRowsVisible, rightFocus);

        boolean leftHasBar = cats.size() > leftRowsVisible;
        boolean rightHasBar = rightList.size() > rightRowsVisible;
        float barW = SCROLLBAR_W * scale;

        // ── 右栏宽度 ──
        // 用【实时】后缀算宽度：数值变长（如 9 → 1000）时面板要能跟着变宽，
        // 否则用建树时的旧快照会算出偏窄的宽度，文字被裁。
        float box = CHECKBOX * scale;
        float rightWidth = RIGHT_MIN_W * scale;
        for (StandGuiEntry e : rightList) {
            String sSuffix = (e.setting == null) ? e.suffix : StandGuiTree.suffixOf(e.setting);
            float w = (float) SakuraTextRenderer.getWidth(e.label, fontPx)
                + (float) SakuraTextRenderer.getWidth(sSuffix, fontPx)
                + padX * 5f + box + barW * 3f;
            if (w > rightWidth) rightWidth = w;
        }

        // ── 底部高度 ──
        StandGuiEntry focused = listGet(rightList, rightFocus);
        String desc = focused == null ? "" : focused.description;
        String cmd = focused == null ? "" : focused.commandName;
        int footLines = (desc.isEmpty() ? 0 : 1) + (cmd.isEmpty() ? 0 : 1);
        float footH = footLines == 0 ? 0f
            : (footLines * FOOT_LINE_H * scale + FOOT_PAD * scale * 2f);

        float leftH = rowH * leftRowsVisible;
        float rightH = rowH * rightRowsVisible;
        float totalW = leftW + rightWidth;
        float rightX = x0 + leftW + gap;

        // 面板描边宽度（设计单位，drawRoundedRect 内部会乘 scale）
        float bw = BORDER_W;
        // 面板圆角：必须 > bw，否则 rect.fsh 会把整块涂成描边色（见 PANEL_RADIUS 说明）
        float pr = PANEL_RADIUS * scale;

        // ══════════════════════════════════════════
        //  第一遍：面板 / 高亮 / 滚动条 / 封边（裸 GL）
        // ══════════════════════════════════════════

        float y = y0;

        // 1. 面包屑（含版本号）+ 细品红描边
        Shader2DUtil.drawRoundedRect(x0, y, totalW, crumbH, pr, ACCENT, bw, ACCENT);
        y += crumbH + gap;

        // 2. 左栏底 + 细品红描边（用户：每个面板栏都要很细的品红边线）
        Shader2DUtil.drawRoundedRect(x0, y, leftW, leftH, pr, PANEL, bw, BORDER_ACCENT);

        // 3. 左栏焦点行（宽度扣掉滚动条，避免与滚动条重叠）
        int leftRel = catFocus - leftScroll;
        float leftHlW = leftW - (leftHasBar ? barW * 2f : 0f);
        if (leftRel >= 0 && leftRel < leftRowsVisible) {
            Shader2DUtil.drawRoundedRect(x0, y + rowH * leftRel, leftHlW, rowH, 0f,
                ACCENT, 0f, null);
        }

        // 4. 右栏底 + 细品红描边
        Shader2DUtil.drawRoundedRect(rightX, y, rightWidth, rightH, pr, PANEL, bw, BORDER_ACCENT);

        // 5. 右栏焦点行（同样扣掉滚动条宽度）
        int rightRel = rightFocus - rightScroll;
        float rightHlW = rightWidth - (rightHasBar ? barW * 2f : 0f) - scale;
        if (rightRel >= 0 && rightRel < rightRowsVisible) {
            Shader2DUtil.drawRoundedRect(rightX, y + rowH * rightRel, rightHlW, rowH, 0f,
                ACCENT, 0f, null);
        }

        // 6. 滚动条：贴在面板【最右内侧边缘】，1.6px 细紫线
        //    ★ 之前画在 rightWidth - 3 - 1 处，和焦点高亮重叠；现在高亮已缩短，
        //      滚动条固定在最右 1px 内沿，且【最后画】保证它在最上层。
        if (leftHasBar) {
            drawScrollbar(x0 + leftW - barW - scale, y, leftH, scale,
                cats.size(), leftRowsVisible, leftScroll);
        }
        if (rightHasBar) {
            drawScrollbar(rightX + rightWidth - barW - scale * 1.5f, y, rightH, scale,
                rightList.size(), rightRowsVisible, rightScroll);
        }

        // 7. 右侧品红封边（外框，比描边稍宽以示强调）
        Shader2DUtil.drawRoundedRect(rightX + rightWidth, y, scale, rightH, 0f, ACCENT, 0f, null);

        // 8. 底部介绍面板 + 细品红描边
        float footY = y + Math.max(leftH, rightH) + gap;
        if (footH > 0f) {
            Shader2DUtil.drawRoundedRect(x0, footY, totalW, footH, pr, PANEL_FOOT, bw, BORDER_ACCENT);
        }

        // ── 收集勾选框图标（第二遍随文字一起画，走 MeshBuilder）──
        for (int i = 0; i < rightRowsVisible; i++) {
            int idx = rightScroll + i;
            if (idx >= rightList.size()) break;
            StandGuiEntry e = rightList.get(idx);

            Boolean on = checkboxStateOf(e);
            if (on == null) continue;

            float bx = rightX + rightWidth - padX * 1.6f - box - barW;
            float by = y + rowH * i + (rowH - box) / 2f;
            checkboxes.add(new Object[]{bx, by, box, on});
        }

        // ══════════════════════════════════════════
        //  第二遍：文字 + 勾选框图标（MeshBuilder，允许互相同批）
        // ══════════════════════════════════════════
        SakuraTextRenderer.begin();
        try {
            // 面包屑（左侧）+ 右侧位置指示
            float crumbY = y0 + (crumbH - fontPx) / 2f;
            String indicator = state.positionIndicator();
            float indicatorW = indicator.isEmpty()
                ? 0f
                : (float) SakuraTextRenderer.getWidth(indicator, fontPx) + padX;

            // ★ 面包屑必须 fit 到「总宽 - 指示器宽 - 内边距」，
            //   否则路径很长时（IMG > 模块 > 分组 > 设置）会压到 1/10 上面。
            drawText(fit(state.breadcrumbWithVersion(), totalW - indicatorW - padX * 2f, fontPx),
                x0 + padX, crumbY, fontPx, TEXT_ON_ACCENT);

            // ★ 面包屑【最右】的右栏位置指示：{@code 当前/总数}
            //   照用户给的 Stand 截图「1/43」；焦点到第 2 项就变「2/10」。
            if (!indicator.isEmpty()) {
                float iw = (float) SakuraTextRenderer.getWidth(indicator, fontPx);
                drawText(indicator, x0 + totalW - padX - iw, crumbY, fontPx, TEXT_ON_ACCENT);
            }

            // 左栏
            for (int i = 0; i < leftRowsVisible; i++) {
                int idx = leftScroll + i;
                if (idx >= cats.size()) break;
                boolean hl = idx == catFocus;
                float ry = y + rowH * i;
                drawText(fit(cats.get(idx).label, leftHlW - padX * 2f, fontPx),
                    x0 + padX, ry + (rowH - fontPx) / 2f, fontPx,
                    hl ? TEXT_ON_ACCENT : TEXT);
            }

            // 右栏
            for (int i = 0; i < rightRowsVisible; i++) {
                int idx = rightScroll + i;
                if (idx >= rightList.size()) break;
                StandGuiEntry e = rightList.get(idx);
                boolean hl = idx == rightFocus;

                // ★ 模块文字不变色（用户 2026-10-20）；只有焦点行是白字压在品红上
                Color c = hl ? TEXT_ON_ACCENT : TEXT;

                float ry = y + rowH * i;
                float textY = ry + (rowH - fontPx) / 2f;
                boolean hasBox = checkboxStateOf(e) != null;
                float availW = rightHlW - padX * 3f - (hasBox ? box + padX : 0f);
                drawText(fit(e.label, availW, fontPx), rightX + padX, textY, fontPx, c);

                // ★ 后缀必须【每帧实时算】，不能用 e.suffix 快照。
                //
                //   e.suffix 是建树时（StandGuiTree.rebuild）算好的死字符串，
                //   只有模块数量变化才会重建 —— 所以按 4/6 改值、按 5 改 Enum 之后
                //   界面上的数字<b>永远不变</b>，看起来像"改不了滑块"（用户实测现象）。
                //   命令框那条路早就用的是实时 suffixOf(...)，只有渲染器漏了。
                String liveSuffix = (e.setting == null) ? e.suffix
                    : StandGuiTree.suffixOf(e.setting);
                if (!liveSuffix.isEmpty() && !(e.setting instanceof BoolSetting)) {
                    float sw = (float) SakuraTextRenderer.getWidth(liveSuffix, fontPx);
                    float valueX = rightX + rightWidth - barW * 2.5f - padX * 1.6f - sw;
                    drawText(liveSuffix, valueX, textY, fontPx, c);

                    // ★ 数值两侧的左右箭头（用户：滑块类的数值左右要有箭头图标）
                    //   只有「能按 4/6 调值」的项才画，否则会误导（Bool/字符串没有箭头）。
                    if (isAdjustable(e.setting)) {
                        float arrowW = ARROW_W * scale;
                        float arrowH = ARROW_H * scale;
                        float arrowY = ry + (rowH - arrowH) / 2f;
                        // 左箭头在数值左边，右箭头在数值右边
                        queueArrow(valueX - arrowW - 2f * scale, arrowY, arrowW, arrowH,
                            true, c);
                        queueArrow(valueX + sw + 2f * scale, arrowY, arrowW, arrowH,
                            false, c);
                    }
                }
            }

            // 勾选框图标放在文字批【之后】画（见下方 end() 后的循环）

            // 底部介绍 + 命令
            if (footH > 0f) {
                float fy = footY + FOOT_PAD * scale;
                float lineH = FOOT_LINE_H * scale;
                if (!desc.isEmpty()) {
                    drawText(fit(desc, totalW - padX * 2f, fontPx),
                        x0 + padX, fy + (lineH - fontPx) / 2f, fontPx, TEXT_HELP);
                    fy += lineH;
                }
                if (!cmd.isEmpty()) {
                    drawText("命令: " + cmd, x0 + padX, fy + (lineH - fontPx) / 2f,
                        fontPx, TEXT_CMD);
                }
            }
        } finally {
            SakuraTextRenderer.end();
        }

        // ══════════════════════════════════════════
        //  第三遍：勾选框图标
        // ══════════════════════════════════════════
        //
        // ★ 为什么必须放在 text 的 end() 之后：
        //   SakuraSvgIcons.drawViaMesh 内部会自己 MeshRenderer.begin().end() 立即提交
        //   一次 draw call。若把它夹在 text 的 begin()/end() 之间，
        //   就会在文字 mesh 还没 end 时插进去一次独立提交 —— 与「裸 GL 夹在中间」
        //   同一类风险（img-notification-hud §5.8）。分开画最安全，代价仅多 1~N 次 draw call。
        for (Object[] cb : checkboxes) {
            boolean on = (Boolean) cb[3];
            SakuraSvgIcons.drawViaMesh(on ? "stand-check-on" : "stand-check-off",
                (Float) cb[0], (Float) cb[1], (Float) cb[2], Color.WHITE);
        }

        // 数值两侧的左右箭头（用户：滑块类数值左右要有箭头图标）。
        // 两个方向的 SVG 各自一份（stand-arrow-right / stand-arrow-left），
        // 避免依赖「水平翻转」这种着色器没暴露的能力。
        for (Object[] a : arrows) {
            boolean left = (Boolean) a[4];
            Color tint = (Color) a[5];
            SakuraSvgIcons.drawViaMesh(
                left ? "stand-arrow-left" : "stand-arrow-right",
                (Float) a[0], (Float) a[1], (Float) a[3], tint);
        }
    }

    // ═══════════ 助手 ═══════════

    /**
     * 该行要不要画勾选框；要的话返回状态（true = 勾选）。
     *
     * <p>照 Stand：<b>可启用的项</b>（模块 / Bool 设置）才有勾选框；其它返回 null。
     */
    private static Boolean checkboxStateOf(StandGuiEntry e) {
        if (e.kind == StandGuiEntry.Kind.MODULE && e.module != null) {
            return e.module.isActive();
        }
        if (e.kind == StandGuiEntry.Kind.SETTING && e.setting instanceof BoolSetting bs) {
            return Boolean.TRUE.equals(bs.get());
        }
        // Int / Double / Enum 行的右侧是数值文字，不画勾选框
        if (e.kind == StandGuiEntry.Kind.SETTING
            && (e.setting instanceof IntSetting
                || e.setting instanceof DoubleSetting
                || e.setting instanceof EnumSetting<?>)) {
            return null;
        }
        return null;
    }

    private static void drawScrollbar(float x, float y, float trackH, float scale,
                                      int total, int visible, int scroll) {
        float w = SCROLLBAR_W * scale;
        float frac = (float) visible / (float) total;
        float barH = Math.max(12f * scale, trackH * frac);
        float maxScroll = Math.max(1, total - visible);
        float t = Math.max(0f, Math.min(1f, (float) scroll / (float) maxScroll));
        float barY = y + (trackH - barH) * t;
        Shader2DUtil.drawRoundedRect(x, barY, w, barH, w / 2f, SCROLLBAR, 0f, null);
    }

    private static int scrollFor(int total, int visible, int focus) {
        if (total <= visible) return 0;
        int half = visible / 2;
        int sc = focus - half;
        return Math.max(0, Math.min(total - visible, sc));
    }

    private static StandGuiEntry listGet(List<StandGuiEntry> list, int i) {
        if (list.isEmpty()) return null;
        return list.get(Math.max(0, Math.min(list.size() - 1, i)));
    }

    private static void drawText(String text, float x, float y, float fontPx, Color c) {
        if (text == null || text.isEmpty()) return;
        int argb = (c.a << 24) | (c.r << 16) | (c.g << 8) | c.b;
        SakuraTextRenderer.draw(text, x, y, fontPx, argb);
    }

    private static String fit(String text, float maxW, float fontPx) {
        if (text == null) return "";
        if (maxW <= 1f) return "";
        if (SakuraTextRenderer.getWidth(text, fontPx) <= maxW) return text;
        String ell = "...";
        float ellW = (float) SakuraTextRenderer.getWidth(ell, fontPx);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (SakuraTextRenderer.getWidth(sb.toString() + text.charAt(i), fontPx) + ellW > maxW) {
                break;
            }
            sb.append(text.charAt(i));
        }
        return sb + ell;
    }

    // ═══════════ Color 编辑子页 ═══════════

    /**
     * 绘制 Color 编辑子页：4 行分量（红/绿/蓝/透明度）+ 顶部色块预览。
     *
     * <p>同样遵守「两遍提交」：先所有面板与色块（裸 GL），再所有文字。
     */
    private void renderColorSubpage(float x0, float y0, float scale) {
        Setting<?> s = state.colorSubpageTarget();
        if (s == null) return;

        float fontPx = 8.5f * scale;
        float rowH = ROW_H * scale;
        float crumbH = CRUMB_H * scale;
        float padX = PAD_X * scale;
        float gap = GAP * scale;
        float pr = PANEL_RADIUS * scale;
        int row = input.colorRow();

        // 尺寸：单栏足够（4 行 + 预览）
        float w = 200f * scale;
        float previewH = 26f * scale;
        float h = previewH + rowH * 4 + padX;

        // 当前颜色（用于预览色块）
        int r = StandGuiEditors.colorComponent(s, 0);
        int g = StandGuiEditors.colorComponent(s, 1);
        int b = StandGuiEditors.colorComponent(s, 2);
        int a = StandGuiEditors.colorComponent(s, 3);
        // alpha 太低时色块几乎看不见，给个下限便于辨认
        Color preview = new Color(r, g, b, Math.max(40, a));

        // ── 第一遍 ──
        // 面包屑
        Shader2DUtil.drawRoundedRect(x0, y0, w, crumbH, pr, ACCENT, BORDER_W, ACCENT);
        float y = y0 + crumbH + gap;
        // 主体
        Shader2DUtil.drawRoundedRect(x0, y, w, h, pr, PANEL, BORDER_W, BORDER_ACCENT);
        // 色块预览（画在主体顶部）
        Shader2DUtil.drawRoundedRect(x0 + padX, y + padX * 0.5f,
            w - padX * 2f, previewH - padX, 0f, preview, 0f, null);
        // 焦点行高亮
        Shader2DUtil.drawRoundedRect(x0, y + previewH + rowH * row, w, rowH, 0f,
            ACCENT, 0f, null);

        // ── 第二遍：文字 ──
        SakuraTextRenderer.begin();
        try {
            drawText(fit("主题色: " + s.title, w - padX * 2f, fontPx),
                x0 + padX, y0 + (crumbH - fontPx) / 2f, fontPx, TEXT_ON_ACCENT);

            int[] vals = {
                StandGuiEditors.colorComponent(s, 0),
                StandGuiEditors.colorComponent(s, 1),
                StandGuiEditors.colorComponent(s, 2),
                StandGuiEditors.colorComponent(s, 3)
            };
            for (int i = 0; i < 4; i++) {
                float ry = y + previewH + rowH * i + (rowH - fontPx) / 2f;
                boolean hl = i == row;
                drawText(StandGuiEditors.RGB_LABELS[i], x0 + padX, ry, fontPx,
                    hl ? TEXT_ON_ACCENT : TEXT);
                String v = String.valueOf(vals[i]);
                float vw = (float) SakuraTextRenderer.getWidth(v, fontPx);
                drawText(v, x0 + w - padX - vw, ry, fontPx, hl ? TEXT_ON_ACCENT : TEXT);
            }
        } finally {
            SakuraTextRenderer.end();
        }
    }
}
