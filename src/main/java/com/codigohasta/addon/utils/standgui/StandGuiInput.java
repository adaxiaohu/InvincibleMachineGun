package com.codigohasta.addon.utils.standgui;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Module;

import java.util.List;

/**
 * 键码 → 菜单动作。
 *
 * <h2>为什么单独一层而不是写进模块类</h2>
 * <p>两套键位（小键盘 / 无小键盘）只是「键码常量不同」，动作完全一样。
 * 所以这里只做**语义动作**（上移 / 下移 / 确定 / 返回 / 切分类 / 调值），
 * 具体哪个键映射到哪个动作，由 {@link StandGuiKeys} 决定。
 *
 * <h2>为什么返回 boolean</h2>
 * <p>返回 {@code true} = 已消费，调用方应 {@code event.cancel()} 把键吞掉，
 * 不让原版/Meteor 再处理（这是 E 键能"吞掉背包"的原理，
 * 见 {@code KeyboardMixin} 在 HEAD 处注入且可 cancel）。
 *
 * <h2>★ 按住连发（用户 2026-10-20：「stand的滑块是可以按住滑动的」）</h2>
 * <p>GLFW 的按键重复（{@code GLFW_REPEAT}）频率由操作系统决定，且 Meteor
 * 的 {@code KeyEvent} 会把 repeat 一起 post 过来。这里<b>不</b>依赖系统重复，
 * 而是自己按 {@link #holdStepMs} 计时：
 * <h2>★ 按住连发（用户 2026-10-20）</h2>
 * <p>用户原话：「滑动速度的那个 按住8 2 或者7 1 或者4 6 自动滚动没生效」。
 * 所以<b>不只是</b>调值 {@code 4}/{@code 6}，<b>上移/下移/切分类</b>也都要能按住连发。
 *
 * <p>GLFW 的按键重复（{@code GLFW_REPEAT}）频率由操作系统决定，且 Meteor 的
 * {@code KeyEvent} 会把 repeat 一起 post 过来。这里<b>不</b>依赖系统重复，
 * 而是自己按 {@link #holdStepMs} 计时：
 * <pre>
 *   任意导航键按下  → 立刻执行一次，并记录「当前按住的动作」
 *   每 tick 检查    → 距上次执行 ≥ holdStepMs 就再执行一次
 *   松开           → 清掉「按住中」
 * </pre>
 * 这样连发速度可调（{@code 滑动速度} 设置），且不受系统键盘重复设置影响。
 */
public final class StandGuiInput {

    private final StandGuiState state;

    // ═══════════ 按住连发状态 ═══════════

    /** 目前按住的动作（null = 没按住）。 */
    private Runnable holdAction;
    /** 上次执行的时刻（毫秒）。 */
    private long holdLastMs;
    /** 连发间隔（毫秒）；由模块设置注入。 */
    private int holdStepMs = 60;

    public StandGuiInput(StandGuiState state) {
        this.state = state;
    }

    /** 由模块设置注入连发速度（毫秒/步，越小越快）。 */
    public void setHoldStepMs(int ms) {
        holdStepMs = Math.max(0, ms);
    }

    /**
     * 输入层用：开始/结束「按住某个动作」。
     *
     * @param action  按下后要重复执行的动作（可为 null 表示无）
     * @param pressed true = 按下，false = 松开
     */
    public void trackHold(Runnable action, boolean pressed) {
        if (pressed) {
            holdAction = action;
            holdLastMs = System.currentTimeMillis();
        } else {
            holdAction = null;
        }
    }

    /**
     * 每 tick 推进按住连发。
     *
     * <p>为什么用 tick 而不是每帧：{@code Render2DEvent} 帧率随 FPS 波动，
     * 而连发速度应当稳定 → 挂 tick（约 20Hz），再按毫秒补偿。
     *
     * @param spendMicros 本 tick 可用预算（本阶段不用，保留签名给将来限速）
     */
    public void tickHold(long spendMicros) {
        Runnable action = holdAction;
        if (action == null) return;
        long now = System.currentTimeMillis();
        if (now - holdLastMs < holdStepMs) return;
        holdLastMs = now;
        action.run();
    }

    /** 当前是否在按住连发。 */
    public boolean isHolding() {
        return holdAction != null;
    }

    /** 松开任何正在连发的动作（切换菜单可见性时调用，避免"卡住一直滚"）。 */
    public void releaseHold() {
        holdAction = null;
    }

    // ═══════════ 调值 ═══════════

    /**
     * 调值一步（{@code 4}/{@code 6} 或 {@code ←}/{@code →}）。
     *
     * @param dir -1 = 减，+1 = 增
     */
    public boolean step(int dir) {
        StandGuiEntry e = focusedEntry();
        if (e == null || e.kind != StandGuiEntry.Kind.SETTING) return true;
        Setting<?> s = e.setting;
        if (s == null) return true;

        if (s instanceof IntSetting is) {
            Integer v = is.get();
            int nv = (v == null ? 0 : v) + dir * stepSize();
            // IntSetting 的合法区间是 [min,max]，越界 set() 会返回 false 静默丢弃
            nv = Math.max(is.min, Math.min(is.max, nv));
            is.set(nv);
            return true;
        }
        if (s instanceof DoubleSetting ds) {
            Double v = ds.get();
            // ★ DoubleSetting 没有 stepSize 字段（已核对双文件）：
            //   步长取「滑块区间/100」与「显示精度」的较大者，保证拖动不黏在 0 上。
            double sp = doubleStep(ds);
            double nv = (v == null ? 0 : v) + dir * sp;
            // 夹到 [min,max]，并吸到小数点精度，避免 0.30000000000000004
            nv = Math.max(ds.min, Math.min(ds.max, nv));
            double factor = Math.pow(10, Math.max(0, Math.min(6, ds.decimalPlaces)));
            nv = Math.round(nv * factor) / factor;
            ds.set(nv);
            return true;
        }
        if (s instanceof EnumSetting<?> es) {
            cycleEnum(es, dir);
            return true;
        }
        // Bool / Color / String / Keybind / List：4/6 不适用
        return true;
    }

    /** Int 步长（本阶段固定 1，后续接设置）。 */
    private int stepSize() {
        return 1;
    }

    /**
     * Double 的一步。
     *
     * <p><b>为什么不能固定 0.1</b>：项目里有 {@code 0.01~10000}（移动速度）也有
     * {@code 0~1}（模糊强度）。固定步长会让前者调不动、后者一按跳过。
     * 所以按「滑块区间 / 100」自适应，但再用 {@code decimalPlaces} 兜底下限，
     * 保证至少能变动一个显示位。
     */
    private static double doubleStep(DoubleSetting ds) {
        double span = ds.sliderMax - ds.sliderMin;
        if (span <= 0) span = ds.max - ds.min;

        // 滑块区间 / 100：让「0.01~10000」这种大区间按 4/6 也能感受到变化
        double bySpan = span > 0 ? span / 100.0 : 0.01;

        // ★ 下限必须用【显示精度 max(2, decimalPlaces)】，不能用 decimalPlaces 原值。
        //   坑：很多 DoubleSetting 的 decimalPlaces 是 0，于是 byDecimals = 10^0 = 1，
        //   再 max(1, bySpan) 就变成「按一下跳 1」；对 0~1 这种区间等于一键跳满，
        //   对 0~0.05 这种区间则<b>完全跳不动</b>（被夹到最大值）。
        //   与 suffixOf 的显示位数保持一致：用户看到几位小数，就能改到那一位。
        int dp = Math.max(2, Math.min(6, ds.decimalPlaces));
        double byDecimals = Math.pow(10, -dp);

        // 再兜一层：步长不超过区间的 1/10，否则一按就跨过半个滑块
        double cap = span > 0 ? span / 10.0 : byDecimals;
        return Math.max(byDecimals, Math.min(bySpan, cap));
    }

    /** 枚举上下翻。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void cycleEnum(EnumSetting es, int dir) {
        Object cur = es.get();
        if (!(cur instanceof Enum)) return;
        Object[] vals = ((Enum<?>) cur).getDeclaringClass().getEnumConstants();
        if (vals.length == 0) return;
        int idx = 0;
        for (int i = 0; i < vals.length; i++) {
            if (vals[i] == cur) { idx = i; break; }
        }
        idx = ((idx + dir) % vals.length + vals.length) % vals.length;
        es.set((Enum) vals[idx]);
    }

    // ═══════════ 语义动作 ═══════════

    /** 上移一格。 */
    public boolean up() {
        List<StandGuiEntry> list = state.currentList();
        if (list.isEmpty()) return true;
        int i = state.focus(state.currentPath());
        state.setFocus(state.currentPath(), i - 1, list.size());
        return true;
    }

    /** 下移一格。 */
    public boolean down() {
        List<StandGuiEntry> list = state.currentList();
        if (list.isEmpty()) return true;
        int i = state.focus(state.currentPath());
        state.setFocus(state.currentPath(), i + 1, list.size());
        return true;
    }

    /**
     * 确定（{@code 5}）。
     *
     * <p><b>★ 语义的最终定稿（用户 2026-10-20 第三次明确）</b>：
     * <pre>
     *   分类下的【模块列表】层：  5 = 开关该模块（不进入！进入用 9）
     *   模块设置页的【分组】层：  5 = 进入该分组
     *   设置项（Bool/数值/枚举）：5 = 执行（Bool 取反 / 数值开命令框 / 枚举下一个）
     *   9：                      只做「进入」
     * </pre>
     *
     * <p><b>⚠️ 为什么不能统一用 {@code hasChildren() ? 进入 : 执行}</b>：
     * 模块<b>既有</b>子节点（它的设置页）又是个开关。若按 {@code hasChildren()}
     * 判断，就会「按 5 想开关模块却进了设置」—— 用户实测报了这个 bug。
     * 所以模块行的 {@code 5} 必须是<b>开关</b>，进入交给 {@code 9}。
     * 而分组行没有「开关」语义，{@code 5} 就应当进入 —— 这就是分层判断的依据。
     */
    public boolean confirm() {
        List<StandGuiEntry> list = state.currentList();
        if (list.isEmpty()) return true;
        StandGuiEntry e = list.get(clamp(state.focus(state.currentPath()), list.size()));
        if (e == null) return true;

        switch (e.kind) {
            case MODULE -> {
                // 模块行：5 = 开关（无论有没有设置）
                if (e.module != null) {
                    e.module.toggle();
                }
            }
            // 分组 / 子菜单：5 = 进入
            case GROUP -> {
                if (e.hasChildren()) state.push(e);
            }
            case SETTING -> primaryAction(e.setting);
            case SAVED -> {
                // 「保存」列表里的项：按 path 反查真实节点再执行
                StandGuiEntry real = StandGuiTree.findByPath(e.path);
                if (real == null) return true;
                if (real.kind == StandGuiEntry.Kind.MODULE && real.module != null) {
                    real.module.toggle();
                } else if (real.setting != null) {
                    // ★ 必须传 real 而不是靠 focusedEntry()：
                    //   此刻焦点在 SAVED 项上，focusedEntry() 拿到的是那个壳
                    //   （没有 module），命令框预填会直接失败。
                    primaryActionOn(real);
                } else if (real.hasChildren()) {
                    state.push(real);
                }
            }
            default -> {
                // CATEGORY / ACTION：有子节点就进入，没有就什么都不做
                if (e.hasChildren()) state.push(e);
            }
        }
        return true;
    }

    /**
     * 进入（{@code 9}）：只对有子节点的项生效。
     *
     * @return 是否真的进入了（供调用方决定要不要吞键）
     */
    public boolean enter() {
        List<StandGuiEntry> list = state.currentList();
        if (list.isEmpty()) return false;
        StandGuiEntry e = list.get(clamp(state.focus(state.currentPath()), list.size()));
        if (e == null) return false;

        // ★ 「保存」列表里的项：先反查真实节点，再按它的子节点决定能不能进
        //   （用户：保存的列表要能支持和之前一样的「9 进入设置」）
        if (e.kind == StandGuiEntry.Kind.SAVED) {
            StandGuiEntry real = StandGuiTree.findByPath(e.path);
            if (real != null && real.hasChildren()) {
                state.push(real);
                return true;
            }
            return false;
        }

        if (!e.hasChildren()) return false;
        state.push(e);
        return true;
    }

    /**
     * 对「无子节点的设置项」按 {@code 5} 的行为。
     *
     * <p>规则（用户 2026-10-20 确认）：
     * <ul>
     *   <li><b>Bool</b> → 取反</li>
     *   <li><b>Int / Double</b> → <b>打开命令框并预填</b>
     *       {@code "<模块字段名> <设置字段名> "}，用户补数字后回车即生效。
     *       这正是 Stand 的 {@code menu.show_command_box("searchanim ")} 机制</li>
     *   <li><b>Enum</b> → 直接切到下一个值（枚举值少，开框反而慢）</li>
     *   <li>其它（Color/String/Keybind/List）→ 本阶段不做</li>
     * </ul>
     */
    private void primaryAction(Setting<?> s) {
        primaryActionOn(focusedEntry());
    }

    /**
     * 对某个具体节点执行「确定」动作。
     *
     * <p>为什么要接 Entry 而不是只接 Setting：
     * 「保存」列表里的项是个壳子（{@code Kind.SAVED}），它自己<b>没有 module</b>；
     * 需要先反查出真实节点再执行。只接 {@code Setting} 就会丢掉模块信息，
     * 导致命令框预填（需要 {@code 模块字段名}）直接失败。
     */
    private void primaryActionOn(StandGuiEntry entry) {
        if (entry == null) return;
        Setting<?> s = entry.setting;
        if (s == null) {
            // 模块类（没有设置项参与）：直接走开关
            if (entry.module != null) entry.module.toggle();
            return;
        }

        if (s instanceof BoolSetting bs) {
            Boolean v = bs.get();
            bs.set(v == null || !v);
            return;
        }
        if (s instanceof IntSetting || s instanceof DoubleSetting) {
            openCommandBoxForSetting(entry, s);
            return;
        }
        if (s instanceof EnumSetting<?> es) {
            cycleEnum(es, 1);
            return;
        }
        // ★ List 类：借 Meteor 的编辑器（用户 D9(a)，关掉后回菜单）
        if (StandGuiEditors.isListSetting(s)) {
            if (entry.module != null) StandGuiEditors.openMeteorModuleScreen(entry.module);
            return;
        }
        // ★ Color / String / Keybind：进自绘编辑子页
        if (StandGuiEditors.needsSubpage(s)) {
            openValueSubpage(entry, s);
        }
    }

    /**
     * 打开「值编辑子页」（Color 四分量 / String 文本 / Keybind 绑定键）。
     *
     * <p>Keybind 走「等下一个键」；Color 进自绘四分量页；
     * String 走命令框预填（文本输入在自绘 HUD 里最麻烦，命令框已有输入法支持）。
     */
    private void openValueSubpage(StandGuiEntry entry, Setting<?> s) {
        if (s instanceof meteordevelopment.meteorclient.settings.KeybindSetting) {
            state.setAwaitingSettingBind(true);
            return;
        }
        if (s instanceof meteordevelopment.meteorclient.settings.ColorSetting) {
            state.setColorSubpageTarget(s);
            state.setColorSubpage(true);
            state.setFocus("color#" + s.title, 0, 4);
            return;
        }
        if (s instanceof meteordevelopment.meteorclient.settings.StringSetting) {
            openCommandBoxForSetting(entry, s);
        }
    }

    /**
     * 打开命令框并预填「{@code <模块> <设置> }」，让用户只补数值。
     *
     * <p>预填内容用<b>字段名</b>（英文），因为命令解析走的也是字段名；
     * 若该模块/设置取不到英文名（helper 类注册的设置），则<b>不预填</b>、
     * 只打开空命令框 —— 用户仍可手打，不会给出一个敲不进去的提示串。
     */
    private void openCommandBoxForSetting(StandGuiEntry entry, Setting<?> s) {
        if (entry == null || entry.module == null) return;

        String moduleField = entry.module.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
        String settingField = StandGuiTree.fieldNameOf(entry.module, s);

        String prefill = (settingField == null || settingField.isEmpty())
            ? ""
            : moduleField + " " + settingField + " ";

        com.codigohasta.addon.utils.standgui.StandGuiCommandScreen.open(prefill);
    }

    /** 返回（{@code 0}）：退一层；顶层返回 false 表示「没有上一层了」（C3(b) 不关菜单）。 */
    public boolean back() {
        // Color 子页优先退出
        if (state.isColorSubpage()) {
            state.setColorSubpage(false);
            return true;
        }
        return state.pop();
    }

    // ═══════════ Color 编辑子页（R/G/B/A 四行）═══════════

    /** Color 子页：当前行号（0..3）。 */
    public int colorRow() {
        Setting<?> s = state.colorSubpageTarget();
        if (s == null) return 0;
        return Math.max(0, Math.min(3, state.focus("color#" + s.title)));
    }

    /** Color 子页：上移 / 下移。 */
    public boolean colorUp() {
        Setting<?> s = state.colorSubpageTarget();
        if (s == null) return true;
        state.setFocus("color#" + s.title, colorRow() - 1, 4);
        return true;
    }

    public boolean colorDown() {
        Setting<?> s = state.colorSubpageTarget();
        if (s == null) return true;
        state.setFocus("color#" + s.title, colorRow() + 1, 4);
        return true;
    }

    /** Color 子页：4/6 调分量（±5，按住连发就很快）。 */
    public boolean colorStep(int dir) {
        Setting<?> s = state.colorSubpageTarget();
        if (s == null) return true;
        int row = colorRow();
        int cur = StandGuiEditors.colorComponent(s, row);
        StandGuiEditors.setColorComponent(s, row, cur + dir * 5);
        return true;
    }

    /**
     * 等待态下收到一个键：写进 {@code KeybindSetting} 的值。
     *
     * <p>与 {@link #bindKey} 的区别：那个是给<b>菜单项</b>绑快捷键，
     * 这个是改<b>某个设置的 Keybind 值本身</b>（比如 Autocrystal 的「按键开关」）。
     */
    public boolean bindSettingKey(int key, int mods) {
        if (!state.isAwaitingSettingBind()) return false;
        state.setAwaitingSettingBind(false);

        StandGuiEntry e = focusedEntry();
        if (e == null || e.setting == null) return true;
        // ★ 必须先确认类型再强转：焦点可能在等待期间被 7/1 切到别的项，
        //   直接强转会抛 ClassCastException（catch 到也只是白吞一次按键）。
        if (!(e.setting instanceof meteordevelopment.meteorclient.settings.KeybindSetting kb)) {
            return true;
        }

        try {
            @SuppressWarnings("unchecked")
            meteordevelopment.meteorclient.settings.Setting<meteordevelopment.meteorclient.utils.misc.Keybind> s =
                (meteordevelopment.meteorclient.settings.Setting<meteordevelopment.meteorclient.utils.misc.Keybind>) kb;
            s.set(meteordevelopment.meteorclient.utils.misc.Keybind.fromKeys(key, mods));
        } catch (Throwable t) {
            AddonTemplate.LOG.warn("[StandGui] 写入 Keybind 设置失败", t);
        }
        return true;
    }

    /** 切下一个分类（{@code 1} / 右Ctrl）。 */
    public boolean nextCategory() {
        List<StandGuiEntry> cats = StandGuiTree.categories();
        if (cats.isEmpty()) return true;
        int i = state.categoryIndex();
        state.selectCategory(cats.get(((i + 1) % cats.size() + cats.size()) % cats.size()));
        return true;
    }

    /** 切上一个分类（{@code 7} / 右Shift）。 */
    public boolean prevCategory() {
        List<StandGuiEntry> cats = StandGuiTree.categories();
        if (cats.isEmpty()) return true;
        int i = state.categoryIndex();
        state.selectCategory(cats.get(((i - 1) % cats.size() + cats.size()) % cats.size()));
        return true;
    }

    // ═══════════ 操作界面（用户 3 进入）═══════════

    /** 打开操作界面（{@code 3}）：只对模块开放（用户 C6）。 */
    public boolean openContext() {
        StandGuiEntry e = focusedEntry();
        if (e == null) return false;

        // ★ 「保存」列表里的项：反查真实节点后再开操作界面
        //   （用户：保存的列表要能支持和之前一样的「3 进入选项」）
        if (e.kind == StandGuiEntry.Kind.SAVED) {
            StandGuiEntry real = StandGuiTree.findByPath(e.path);
            if (real == null) return false;
            e = real;
        }

        if (e.kind != StandGuiEntry.Kind.MODULE) return false;
        state.openContext(e);
        return true;
    }

    /** 操作界面的条目列表（供渲染）。 */
    public List<StandGuiContextMenu.Item> contextItems() {
        return StandGuiContextMenu.build(state.contextTarget());
    }

    /**
     * 操作界面的上移 / 下移。
     *
     * <p>★ 必须写进 {@link StandGuiState#currentPath()} 那套焦点表：
     * 渲染器统一读 {@code focus(currentPath())}，若这里另用一套索引，
     * 高亮和实际选中项就会错位。
     */
    public boolean contextUp() {
        List<StandGuiContextMenu.Item> items = contextItems();
        int i = state.focus(state.currentPath());
        state.setFocus(state.currentPath(), i - 1, items.size());
        return true;
    }

    public boolean contextDown() {
        List<StandGuiContextMenu.Item> items = contextItems();
        int i = state.focus(state.currentPath());
        state.setFocus(state.currentPath(), i + 1, items.size());
        return true;
    }

    /**
     * 操作界面按 {@code 5}：执行当前项。
     *
     * <p>每一项的语义见 {@link StandGuiContextMenu.Op}。
     */
    public boolean contextConfirm() {
        List<StandGuiContextMenu.Item> items = contextItems();
        if (items.isEmpty()) return true;
        int idx = state.focus(state.currentPath());
        idx = Math.max(0, Math.min(items.size() - 1, idx));
        StandGuiContextMenu.Item item = items.get(idx);
        StandGuiEntry target = state.contextTarget();
        if (target == null) { state.closeContext(); return true; }

        switch (item.op()) {
            case LINK_CYCLE -> StandGuiLinks.cycle(target.path);
            case LINK_INVERT -> StandGuiLinks.toggleInvert(target.path);

            case SAVE_STATE -> StandGuiSaved.saveState(target);
            case LOAD_STATE -> StandGuiSaved.loadState(target);
            case RESET_STATE -> StandGuiSaved.resetState(target);

            case SET_MIN -> StandGuiSaved.setNumeric(target, true);
            case SET_MAX -> StandGuiSaved.setNumeric(target, false);

            case SAVE_TO_LIST -> StandGuiSaved.addToSavedList(target);
            case COPY_PATH -> StandGuiSaved.copyPath(target);

            case HOTKEY -> {
                // ★ 改为「打开 Meteor 的模块界面去绑定」（2026-10-20 重构）
                //
                //   为什么不再自己收键绑定：
                //   Meteor 的 Modules.onKey 是 EventPriority.HIGH，我们默认是 MEDIUM，
                //   所以同一个键会被两边各处理一次 → 表现为「按一下开关两次（像没反应）」
                //   或者两边状态不同步。用户明确要求「沿用 Meteor 那边的」。
                //
                //   打开官方 ModuleScreen 让用户在 Meteor 自己的键位控件里绑，
                //   这样只有一套绑定、零冲突，且支持组合键与手柄。
                if (target.module != null) {
                    StandGuiEditors.openMeteorModuleScreen(target.module);
                }
            }

            // 清除快捷键（改的是 Meteor 的 module.keybind）
            case CLEAR_BINDS -> {
                if (StandGuiBindings.clearHotkey(target)) {
                    try {
                        com.codigohasta.addon.utils.notify.ImgNotifications.INSTANCE.post(
                            "", "已清除快捷键: " + target.label,
                            com.codigohasta.addon.utils.notify.ImgNotificationType.WARNING, 3);
                    } catch (Throwable ignored) { }
                }
            }

            // 取消保存（用户：不知道怎么取消保存列表的）
            case UNSAVE_FROM_LIST -> StandGuiSaved.removeFromSavedList(target);

            case BACK -> state.closeContext();
        }
        return true;
    }

    /**
     * 等待绑定态下收到一个键：把它绑给目标。
     *
     * @param key  GLFW 键码
     * @param mods 修饰键
     * @return 是否消费了该键
     */

    /** {@code 0} 在操作界面里 = 返回。 */
    public boolean contextBack() {
        state.closeContext();
        return true;
    }

    private static int clamp(int i, int size) {
        return Math.max(0, Math.min(size - 1, i));
    }

    /** 供模块类使用的当前聚焦项。 */
    public StandGuiEntry focusedEntry() {
        List<StandGuiEntry> list = state.currentList();
        if (list.isEmpty()) return null;
        return list.get(clamp(state.focus(state.currentPath()), list.size()));
    }

    /** 当前聚焦项是否为模块（决定 {@code 9}/{@code 3} 是否可用）。 */
    public boolean focusIsModule() {
        StandGuiEntry e = focusedEntry();
        return e != null && e.isModule();
    }
}
