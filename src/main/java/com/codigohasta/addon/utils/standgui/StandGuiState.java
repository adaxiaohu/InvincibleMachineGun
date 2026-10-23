package com.codigohasta.addon.utils.standgui;

import meteordevelopment.meteorclient.settings.Setting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜单的导航状态：可见性、焦点、路径栈、每个列表各自的焦点/滚动记忆。
 *
 * <h2>为什么焦点按 path 分别记忆（E3(c)）</h2>
 * <p>用户明确要求「焦点要记忆，关掉菜单再打开也要恢复」。
 * Stand 的做法是两个独立开关（{@code tabskeepcursor} / {@code listskeepcursor}），
 * 我们直接做成「任何列表都各记各的」：
 * <pre>
 *   focusByPath  = { "IMG" -> 12, "IMG/TpAnchorAura/设置" -> 3 }
 * </pre>
 * 键是 {@link StandGuiEntry#path}，所以切分类、进出子页、关菜单都不会丢。
 *
 * <h2>导航栈</h2>
 * <p>{@link #stack} 是从「当前分类」到「当前列表」的节点链。
 * 例：{@code [IMG分类, TpAnchorAura模块, 设置分组]}。
 * <ul>
 *   <li>{@code 5} 进入子节点 → push</li>
 *   <li>{@code 0} 返回 → pop</li>
 *   <li>{@code 7}/{@code 1} 换分类 → 清空栈、重新从该分类开始</li>
 * </ul>
 */
public final class StandGuiState {

    // ═══════════ 可见性 ═══════════

    private boolean visible;

    public boolean visible() { return visible; }

    public void setVisible(boolean v) {
        visible = v;
        if (v) StandGuiTree.categories();   // 打开时确保树是最新的
    }

    public void toggle() { setVisible(!visible); }

    // ═══════════ 焦点记忆（E3(c) + D6）═══════════

    /** 每个列表（按 path）各自记住的焦点索引。 */
    private final Map<String, Integer> focusByPath = new HashMap<>();
    /** 每个列表（按 path）各自记住的滚动偏移（以行数计）。 */
    private final Map<String, Integer> scrollByPath = new HashMap<>();

    public int focus(String path) {
        return focusByPath.getOrDefault(path, 0);
    }

    public void setFocus(String path, int index, int size) {
        if (size <= 0) { focusByPath.put(path, 0); return; }
        int v = ((index % size) + size) % size;   // 环绕
        focusByPath.put(path, v);
    }

    public int scroll(String path) { return scrollByPath.getOrDefault(path, 0); }

    public void setScroll(String path, int v) { scrollByPath.put(path, Math.max(0, v)); }

    /** 清空全部记忆（操作界面的「重置默认状态」用）。 */
    public void clearMemory() {
        focusByPath.clear();
        scrollByPath.clear();
        stack.clear();
    }

    // ═══════════ 导航栈 ═══════════

    /** 当前路径链（至少 1 个元素：当前分类）。 */
    private final List<StandGuiEntry> stack = new ArrayList<>();

    /** 当前所在的节点（栈顶）。 */
    public StandGuiEntry current() {
        return stack.isEmpty() ? null : stack.get(stack.size() - 1);
    }

    /** 当前正在浏览的列表（= 栈顶的子节点）。 */
    public List<StandGuiEntry> currentList() {
        // ★ 操作界面：把动态操作项包成临时 Entry，复用同一套渲染/焦点逻辑。
        //   这样渲染器不用为「操作界面」单独写一遍绘制（少 100 行重复代码）。
        if (contextTarget != null) {
            return contextListCache();
        }
        StandGuiEntry c = current();
        if (c != null) return c.children;
        return StandGuiTree.categories();
    }

    /**
     * 操作项 → 临时 Entry 列表。
     *
     * <p><b>★ 不能按 target 缓存（2026-10-20 修）</b>：操作项的后缀是<b>活数据</b>
     * （「关联状态: 无」「快捷键 F8」「保存 / 取消保存」）。按 target 缓存会导致
     * 用户切换关联状态后界面仍显示「无」—— 看起来像点了没反应。
     *
     * <p>改成<b>每次重建</b>。代价是 new 十来个对象，相比一帧的几十个面板 + 文字
     * 绘制完全可忽略；正确性优先。
     */
    private List<StandGuiEntry> contextListCache() {
        List<StandGuiContextMenu.Item> items = StandGuiContextMenu.build(contextTarget);
        List<StandGuiEntry> out = new ArrayList<>(items.size());
        for (StandGuiContextMenu.Item it : items) {
            out.add(StandGuiEntry.action(it.label(),
                contextTarget.path + "#" + it.op().name(), it.suffix()));
        }
        return out;
    }


    /** 当前列表的 path（用于焦点记忆的键）。 */
    public String currentPath() {
        // 操作界面用独立的焦点键，避免和模块列表的焦点互相覆盖
        if (contextTarget != null) return contextTarget.path + "#context";
        StandGuiEntry c = current();
        return c == null ? "@root" : c.path;
    }

    /** 进入一个节点（若有子节点）。 */
    public void push(StandGuiEntry e) {
        if (e == null || !e.hasChildren()) return;
        stack.add(e);
    }

    /**
     * 进入「操作界面」（用户 {@code 3}）。
     *
     * <p>操作界面不压进 {@link #stack}（它不是树的节点，而是一组动态操作项），
     * 只记 {@link #contextTarget}。这样返回时 {@code pop()} 的行为不受影响，
     * 面包屑也仍然是模块那一级。
     */
    public void openContext(StandGuiEntry target) {
        if (target == null) return;
        contextTarget = target;
        contextFocus = 0;
    }

    /** 关闭操作界面。 */
    public void closeContext() {
        contextTarget = null;
        contextFocus = 0;
        // ★ 必须一并清掉「等待绑定」：否则下次进操作界面时它还留着 true，
        //   导致所有按键都被当成绑定键吞掉 → 5/4/6 全部失灵（用户实测到的问题）。
        awaitingSettingBind = false;
    }

    /** 是否在操作界面。 */
    public boolean inContext() {
        return contextTarget != null;
    }

    /**
     * 是否在等待「设置项本身的 Keybind 值」。
     *
     * <p>作用目标：某个 {@code KeybindSetting} 的值（比如 Autocrystal 的「按键开关」）。
     * <b>不是</b>给菜单项绑快捷键 —— 那个功能已取消，
     * 快捷键统一沿用 Meteor 的 {@code Module.keybind}（见 {@link StandGuiBindings}）。
     */
    public boolean isAwaitingSettingBind() {
        return awaitingSettingBind;
    }

    public void setAwaitingSettingBind(boolean v) {
        awaitingSettingBind = v;
    }

    private boolean awaitingSettingBind;

    /** 是否在 Color 编辑子页（R/G/B/A 四行）。 */
    public boolean isColorSubpage() {
        return colorSubpage;
    }

    public void setColorSubpage(boolean v) {
        colorSubpage = v;
        if (!v) colorSubpageTarget = null;
    }

    /** Color 子页的目标设置项。 */
    public Setting<?> colorSubpageTarget() {
        return colorSubpageTarget;
    }

    public void setColorSubpageTarget(Setting<?> s) {
        colorSubpageTarget = s;
    }

    private boolean colorSubpage;
    private Setting<?> colorSubpageTarget;

    /** 操作界面的目标节点。 */
    public StandGuiEntry contextTarget() {
        return contextTarget;
    }

    /** 操作界面的当前焦点索引。 */
    public int contextFocus() {
        return contextFocus;
    }

    public void setContextFocus(int v, int size) {
        if (size <= 0) { contextFocus = 0; return; }
        contextFocus = ((v % size) + size) % size;
    }

    private StandGuiEntry contextTarget;
    private int contextFocus;

    /** 返回上一层。顶层返回 false（C3(b)：顶层按 0/e 不关菜单）。 */
    public boolean pop() {
        if (stack.isEmpty()) return false;
        stack.remove(stack.size() - 1);
        return true;
    }

    /** 切到某个分类（7/1 用；清空栈、焦点归该分类记忆值）。 */
    public void selectCategory(StandGuiEntry category) {
        if (category == null) return;
        stack.clear();
        stack.add(category);
    }

    /** 从顶层分类列表进入（首次打开或从根进入某分类）。 */
    public void ensureCategory() {
        List<StandGuiEntry> cats = StandGuiTree.categories();
        if (cats.isEmpty()) return;

        // ★ 已选分类可能因为「隐藏分类」设置而消失（用户可在设置里随时改）。
        //   若栈顶不在当前列表里，就退回到第一个可用分类 —— 否则右栏会继续显示
        //   一个左栏已经不存在的分类的内容，看起来像"卡住了"。
        if (!stack.isEmpty()) {
            StandGuiEntry top = stack.get(0);
            boolean stillExists = false;
            for (StandGuiEntry c : cats) {
                if (c.path != null && c.path.equals(top.path)) { stillExists = true; break; }
            }
            if (!stillExists) stack.clear();
        }

        if (!stack.isEmpty()) return;

        // 默认落在 IMG 分类（本插件的模块），找不到就落在第一个
        StandGuiEntry pick = cats.get(0);
        for (StandGuiEntry c : cats) {
            if ("IMG".equals(c.path)) { pick = c; break; }
        }
        stack.add(pick);
    }

    /** 面包屑字符串（D8：{@code Stand 菜单 > IMG > Tp Anchor Aura > 设置}）。 */
    public String breadcrumb() {
        StringBuilder sb = new StringBuilder(StandGuiTree.rootName());
        for (StandGuiEntry e : stack) sb.append(" > ").append(e.label);
        return sb.toString();
    }

    /**
     * 带版本号的面包屑（用户 2026-10-20：「面包屑也要有版本号比如 Stand 1.8.5，
     * 对应的就是 IMG 的版本号」）。
     *
     * <p>形如 {@code Stand 1.8.5 > IMG > TpAnchorAura > 设置}。
     * 版本号取自 {@code fabric.mod.json} 的 {@code version}（构建时由
     * {@code gradle/libs.versions.toml} 的 {@code mod-version} 注入），
     * 取不到时退回 {@code "?"}，绝不抛异常。
     */
    public String breadcrumbWithVersion() {
        StringBuilder sb = new StringBuilder(StandGuiTree.rootName())
            .append(' ').append(StandGuiTree.modVersion());
        for (StandGuiEntry e : stack) sb.append(" > ").append(e.label);
        return sb.toString();
    }

    /**
     * 右栏位置指示（面包屑最右，用户 2026-10-20）。
     *
     * <p>照 Stand 截图 {@code 1/43}：<b>当前焦点序号 / 本列表总项数</b>（1 起算）。
     * 焦点到第 2 项就变 {@code 2/10}。
     *
     * <p>列表为空时返回空串（不画），避免显示 {@code 0/0}。
     */
    public String positionIndicator() {
        List<StandGuiEntry> list = currentList();
        if (list.isEmpty()) return "";
        int idx = Math.max(0, Math.min(list.size() - 1, focus(currentPath())));
        return (idx + 1) + "/" + list.size();
    }

    /** 左栏当前高亮的分类索引。 */
    public int categoryIndex() {
        List<StandGuiEntry> cats = StandGuiTree.categories();
        StandGuiEntry top = stack.isEmpty() ? null : stack.get(0);
        if (top == null) return 0;
        for (int i = 0; i < cats.size(); i++) {
            StandGuiEntry c = cats.get(i);
            // ★ 只按【引用】或 path 判等，不用 label 兜底。
            //   分类显示名改成中文后，label 可能撞名（两个分类都叫「世界」），
            //   用 label 比较会把焦点定位到错误行。
            //   path 是分类原始英文名，全局唯一。
            if (c == top || (c.path != null && c.path.equals(top.path))) return i;
        }
        return 0;
    }

    /** 供持久化：把记忆表导出（后续接 NBT）。 */
    public Map<String, Integer> exportFocus() { return new HashMap<>(focusByPath); }

    public void importFocus(Map<String, Integer> in) {
        if (in == null) return;
        focusByPath.clear();
        focusByPath.putAll(in);
    }
}
