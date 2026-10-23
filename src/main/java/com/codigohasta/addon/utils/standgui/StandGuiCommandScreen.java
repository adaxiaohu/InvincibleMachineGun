package com.codigohasta.addon.utils.standgui;

import com.codigohasta.addon.utils.sakura.render.SakuraTextRenderer;
import com.codigohasta.addon.utils.sakura.render.Shader2DUtil;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Stand 命令框（<b>Stand 风格自绘</b>，不是 Meteor 主题窗口）。
 *
 * <h2>为什么自绘（用户 2026-10-20 反馈）</h2>
 * <p>用户原话：「这个命令框并不是我要求的 stand风格的，他是meteor的」。
 * 上一版用 {@code WindowScreen + WTextBox}，出来的是 Meteor 的圆角窗口，
 * 与 Stand 的「品红标题条 + 近黑输入区 + 细紫滚动条」完全不同。
 *
 * <p>本版沿用菜单同一套视觉：{@link Shader2DUtil}（面板）+
 * {@link SakuraTextRenderer}（文字），风格与 {@link StandGuiRenderer} 一致。
 *
 * <h2>为什么不卡（用户：「打开这个命令框变卡了 fps降低了很多」）</h2>
 * <p>上一版卡顿的根因是<strong>每帧</strong>都做：
 * <pre>
 *   onRenderBefore → refreshSuggestions() → allCommands()
 *     → 遍历全部模块 × 全部设置（2000+）× 每项一次反射
 *     → 然后 clear() 整个控件树 + 重新 add 所有控件（Meteor 立即模式，全量重排）
 * </pre>
 * <p>现在：
 * <ol>
 *   <li>{@code allCommandsCached()} 只建一次表（按模块数失效）；</li>
 *   <li>建议列表只在<b>输入文本变化</b>时重算（{@link #lastQuery}）；</li>
 *   <li>绘制是纯 2D，没有控件树、没有布局、没有反射。</li>
 * </ol>
 *
 * <h2>键盘（仍用 Screen，所以能拿到输入法）</h2>
 * <p>为什么仍然是 {@code Screen} 而不是纯 HUD：命令框要收英文/数字/中文，
 * 只有走原版 {@code charTyped} 才能正确走输入法。但<b>外观</b>全部自绘。
 *
 * <ul>
 *   <li>回车 → 执行</li>
 *   <li>↑/↓ → 输入为空时翻历史，非空时在建议里选</li>
 *   <li>Tab → 把选中的建议填入输入框</li>
 *   <li>Esc → 关闭</li>
 * </ul>
 */
public class StandGuiCommandScreen extends WidgetScreen {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    // ── 视觉（与 StandGuiRenderer 同色）──
    private static final Color PANEL = new Color(0, 0, 0, 150);
    private static final Color TEXT = new Color(255, 255, 255, 255);
    private static final Color TEXT_DIM = new Color(170, 175, 190, 255);
    private static final Color TEXT_HL = new Color(255, 255, 255, 255);
    private static final Color TEXT_ERR = new Color(255, 140, 140, 255);

    /**
     * 主题色：与菜单共用（由 {@code StandGuiRenderer.setAccent} 注入）。
     *
     * <p>为什么不在本类也存一份常量：用户 B2 要「换主题色整套跟着变」，
     * 两处各自存一份就会出现「菜单变了、命令框还是品红」的不一致。
     */
    private static Color accent() {
        return StandGuiRenderer.accent();
    }

    // ── 几何（设计单位）──
    private static final float PAD = 6f;
    private static final float TITLE_H = 16f;
    private static final float LINE_H = 14f;
    private static final float FONT = 8.5f;
    private static final int MAX_SUGGESTIONS = 8;

    /**
     * 面板描边宽度（设计单位，{@code drawRoundedRect} 内部会乘 scale）。
     *
     * <p>与菜单一致取 1.0。
     */
    private static final float BORDER_W = 0.25f;

    /**
     * 面板圆角半径（设计单位）。
     *
     * <p>⚠️ <b>必须 &gt; {@link #BORDER_W}</b>：{@code rect.fsh} 的 SDF 在
     * {@code radius = 0} 时让面板内部 {@code dist = 0}，于是
     * {@code borderAlpha = 1} → <b>整块涂成描边色（全品红）</b>。
     * 完整推导见 {@code StandGuiRenderer.PANEL_RADIUS}。
     */
    private static final float PANEL_RADIUS = 0.5f;

    /** 输入缓冲（自己维护，不依赖 WTextBox）。 */
    private final StringBuilder buffer = new StringBuilder();
    /** 预填文本。 */
    private final String prefill;

    /** 建议缓存：只有输入变化才重算。 */
    private String lastQuery;
    private List<StandGuiCommands.Cmd> suggestions = new ArrayList<>();
    /** 建议选中索引（供 Tab / ↑↓）。 */
    private int selected = -1;
    /** 最近一条执行结果。 */
    private String resultText = "";
    private boolean resultOk = true;

    /** 光标闪烁相位（用系统时间，避免依赖帧计数）。 */
    private static final long BLINK_MS = 500L;

    /**
     * 是否还需要吞掉「开框那一下」残留的字符事件。
     *
     * <h2>★ 为什么改成「只吞一次」而不是时间窗（2026-10-20 修）</h2>
     * <p>上一版用 60ms 时间窗，结果用户按 `` ` `` 打开后<b>立刻</b>输入数字，
     * 那几个字符全被吞掉 —— 表现为「命令框里打不了字 / 数值改不了」。
     *
     * <p>真正要挡的只有<b>一个</b>事件：触发开框的那次按键，它的 {@code charTyped}
     * 会在 {@code setScreen} 之后才派发到新屏幕。所以只需吞掉<b>第一个</b>字符事件，
     * 之后一律正常接收。
     */
    private boolean swallowFirstChar = true;

    public StandGuiCommandScreen() {
        this("");
    }

    public StandGuiCommandScreen(String prefill) {
        super(GuiThemes.get(), StandGuiTree.rootName() + " 命令框");
        this.prefill = prefill == null ? "" : prefill;
        buffer.append(this.prefill);
        // 有预填时（数值项按 5 打开）说明是菜单主动开的，不存在残留按键，
        // 不必吞任何字符 —— 否则第一个数字会被吃掉。
        this.swallowFirstChar = this.prefill.isEmpty();
    }

    /** 打开命令框。 */
    public static void open(String prefill) {
        try {
            mc.setScreen(new StandGuiCommandScreen(prefill));
        } catch (Throwable ignored) {
            // 打不开也不影响键处理
        }
    }

    /**
     * 不创建任何 Meteor 控件 —— 整个界面由 {@link #onRenderBefore} 自绘。
     *
     * <p>这是「不卡」的关键之一：控件树是空的，Meteor 每帧布局什么都不用做。
     * 但仍保留 {@code WidgetScreen} 以复用 {@code Screen} 的输入法/键事件。
     */
    @Override
    public void initWidgets() {
        // 故意留空：Stand 风格全部自绘
    }

    // ═══════════ 键事件 ═══════════

    /**
     * 键事件。
     *
     * <p><b>⚠️ 必须用 yarn 名 {@code keyPressed}/{@code charTyped}</b>：
     * 本项目编译环境是 yarn 映射（已用 javap 核对 MC 1.21.11 的
     * {@code Element}/{@code Screen}）。写成 {@code method_25404} 或
     * {@code class_11908} 都编译不过 —— 前两版连错两轮就是这个原因。
     */
    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        int key = input.key();
        switch (key) {
            case org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER -> {
                execute();
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE -> {
                if (buffer.length() > 0) buffer.deleteCharAt(buffer.length() - 1);
                StandGuiHistory.resetCursor();
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_TAB -> {
                acceptSuggestion();
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_UP -> {
                if (buffer.length() == 0) {
                    String p = StandGuiHistory.prev();
                    if (p != null) {
                        buffer.setLength(0);
                        buffer.append(p);
                    }
                } else {
                    moveSelection(-1);
                }
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN -> {
                if (buffer.length() == 0) {
                    String n = StandGuiHistory.next();
                    if (n != null) {
                        buffer.setLength(0);
                        buffer.append(n);
                    }
                } else {
                    moveSelection(1);
                }
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE -> {
                close();
                return true;
            }
            default -> {
                // 交给默认处理（不吞），保证输入法/字符输入正常
                return super.keyPressed(input);
            }
        }
    }

    /** 字符输入（走原版输入法，所以中文也能输入）。 */
    @Override
    public boolean charTyped(net.minecraft.client.input.CharInput input) {
        // 只吞掉「开框那次按键」残留的第一个字符事件（见 swallowFirstChar 的说明）
        if (swallowFirstChar) {
            swallowFirstChar = false;
            return true;
        }
        char c = (char) input.codepoint();
        // 过滤控制字符；允许中文、空格、斜杠、点等
        if (c >= 32 && c != 127) {
            buffer.append(c);
            StandGuiHistory.resetCursor();
        }
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;   // Esc 由我们自己处理（避免双重关闭）
    }

    // ═══════════ 建议 ═══════════

    /** 只在输入变化时重算建议（性能关键）。 */
    private void updateSuggestionsIfNeeded() {
        String q = buffer.toString();
        if (q.equals(lastQuery)) return;
        lastQuery = q;
        selected = -1;
        suggestions = StandGuiCommands.suggestions(q, MAX_SUGGESTIONS);
    }

    /** Tab：把选中项（或第一项）的命令名填入。 */
    private void acceptSuggestion() {
        updateSuggestionsIfNeeded();
        if (suggestions.isEmpty()) return;
        StandGuiCommands.Cmd pick =
            suggestions.get(selected >= 0 ? Math.min(selected, suggestions.size() - 1) : 0);

        // 多词命令（如 "tpanchoraura placedelay"）补全后加空格，方便继续输入值
        String fill = pick.name();
        buffer.setLength(0);
        buffer.append(fill);
        if (fill.contains(" ")) buffer.append(' ');
        lastQuery = null;
        StandGuiHistory.resetCursor();
    }

    private void moveSelection(int delta) {
        updateSuggestionsIfNeeded();
        if (suggestions.isEmpty()) return;
        int n = suggestions.size();
        selected = selected < 0 ? (delta > 0 ? 0 : n - 1)
                               : ((selected + delta) % n + n) % n;
    }

    // ═══════════ 执行 ═══════════

    private void execute() {
        String text = buffer.toString().trim();
        if (text.isEmpty()) return;

        StandGuiCommands.Result r = StandGuiCommands.run(text);
        resultOk = r.ok();
        resultText = (r.ok() ? "✔ " : "✘ ") + r.message();

        StandGuiHistory.push(text);
        buffer.setLength(0);
        lastQuery = null;
        selected = -1;
        suggestions = new ArrayList<>();
    }

    // ═══════════ 绘制 ═══════════

    /**
     * 在 {@code WidgetScreen} 的 unscaledProjection 区间内自绘。
     *
     * <p>已核对 {@code WidgetScreen.renderCustom}：第 270 行
     * {@code Utils.unscaledProjection()}、第 290 行恢复，而
     * {@code onRenderBefore} 恰好在这个区间内被调用 ——
     * 与 {@link Shader2DUtil} / {@link SakuraTextRenderer} 的坐标前提一致。
     */
    @Override
    protected void onRenderBefore(net.minecraft.client.gui.DrawContext ctx, float delta) {
        updateSuggestionsIfNeeded();

        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

        float w = Math.min(screenW * 0.6f, 520f);
        float rows = Math.max(1, suggestions.size());
        float h = TITLE_H + LINE_H + PAD * 2f
            + (rows > 0 ? LINE_H * (rows + 1) : 0f)
            + (resultText.isEmpty() ? 0f : LINE_H);
        float x = (screenW - w) / 2f;
        float y = screenH * 0.28f;

        // ── 第一遍：面板 / 高亮 / 光标（裸 GL，全部画完）──
        Shader2DUtil.drawRoundedRect(x, y, w, h, PANEL_RADIUS, PANEL, BORDER_W, accent());
        // 标题条
        Shader2DUtil.drawRoundedRect(x, y, w, TITLE_H, 0f, accent(), 0f, null);
        // 输入行底（比面板略深）
        Shader2DUtil.drawRoundedRect(x, y + TITLE_H, w, LINE_H + PAD * 2f, 0f,
            new Color(0, 0, 0, 190), 0f, null);
        // 选中建议行高亮
        if (selected >= 0 && selected < suggestions.size()) {
            Shader2DUtil.drawRoundedRect(x, y + TITLE_H + LINE_H + PAD * 2f + LINE_H * selected,
                w, LINE_H, 0f, new Color(accent().r, accent().g, accent().b, 90), 0f, null);
        }
        // 右侧封边
        Shader2DUtil.drawRoundedRect(x + w, y, 1.5f, h, 0f, accent(), 0f, null);

        // 输入光标（同属第一遍 —— 绝不能在 begin/end 之间画裸 GL）
        if ((System.currentTimeMillis() / BLINK_MS) % 2 == 0) {
            float cw = (float) SakuraTextRenderer.getWidth("> " + buffer, FONT);
            Shader2DUtil.drawRoundedRect(x + PAD + cw + 1f, y + TITLE_H + PAD - 1f,
                1.2f, FONT + 2f, 0f, TEXT, 0f, null);
        }

        // ── 第二遍：文字 ──
        SakuraTextRenderer.begin();
        try {
            // 标题
            drawText(StandGuiTree.rootName() + " 命令框", x + PAD,
                y + (TITLE_H - FONT) / 2f, TEXT);

            // 输入行
            float inY = y + TITLE_H + PAD;
            drawText("> " + buffer, x + PAD, inY, TEXT);

            // 建议列表
            float sy = inY + LINE_H + PAD;
            for (int i = 0; i < suggestions.size(); i++) {
                StandGuiCommands.Cmd c = suggestions.get(i);
                boolean hl = i == selected;
                drawText((hl ? "▸ " : "  ") + c.usage(), x + PAD, sy + LINE_H * i,
                    hl ? TEXT_HL : TEXT_DIM);
                float hw = (float) SakuraTextRenderer.getWidth(c.help(), FONT);
                drawText(c.help(), x + w - PAD - hw, sy + LINE_H * i, TEXT_DIM);
            }

            // 执行结果
            if (!resultText.isEmpty()) {
                drawText(resultText, x + PAD,
                    sy + LINE_H * suggestions.size() + LINE_H * 0.5f,
                    resultOk ? TEXT : TEXT_ERR);
            }
        } finally {
            SakuraTextRenderer.end();
        }
    }

    private static void drawText(String s, float x, float y, Color c) {
        if (s == null || s.isEmpty()) return;
        int argb = (c.a << 24) | (c.r << 16) | (c.g << 8) | c.b;
        SakuraTextRenderer.draw(s, x, y, FONT, argb);
    }
}
