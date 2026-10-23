package com.codigohasta.addon.utils.standgui;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.Keybind;

import java.util.ArrayList;
import java.util.List;

/**
 * 快捷键 —— <b>只作为 Meteor {@code Module.keybind} 的适配层</b>。
 *
 * <h2>★ 为什么不再维护自己的绑定表（2026-10-20 重写）</h2>
 * <p>用户报「两套快捷键打架」。根因已从 Meteor 源码核实：
 * <pre>
 *   Modules.onKey(KeyEvent)   @EventHandler(priority = EventPriority.HIGH)
 *       → onAction(...) → module.keybind.matches(...) → module.toggle()
 * </pre>
 * <p>而我们的 {@code StandGui.onKey} 是<b>默认优先级（MEDIUM = 0）</b>，
 * 比 {@code HIGH (100)} <b>低</b>。所以同一个按键会：
 * <ol>
 *   <li>先被 Meteor 处理 → 模块被 toggle 一次</li>
 *   <li>再被我们的绑定表处理 → 又 toggle 一次</li>
 * </ol>
 * <p>表现为「按一下开关两次（等于没反应）」，且两边状态可能不同步。
 *
 * <h2>现在的做法：单一事实来源</h2>
 * <ul>
 *   <li><b>触发</b>：完全交给 Meteor，我们<b>不再监听</b>快捷键；
 *       这样不会重复触发，也不受优先级影响。</li>
 *   <li><b>显示</b>：直接读 {@code module.keybind}。</li>
 *   <li><b>设置/清除</b>：改的也是 {@code module.keybind}，
 *       或直接打开 Meteor 的 ModuleScreen（官方 UI，零冲突）。</li>
 * </ul>
 *
 * <h2>代价（如实说明）</h2>
 * <p>Meteor 的 {@code keybind} 只支持<b>一个</b>键，所以「一项多键」的能力没有了。
 * 这是用户明确选择的方向（「快捷键还是沿用 methane 那边的吧」）——
 * 单一来源比「多键 + 双系统打架」更可靠。
 *
 * <h2>适用对象</h2>
 * <p>只有<b>模块</b>有 Meteor 键位。设置项（Bool/数值…）没有快捷键概念，
 * 菜单里也不再给它们提供绑定入口。
 */
public final class StandGuiBindings {

    /** 没有绑定的占位（保持 API 形状，仅用于显示）。 */
    public record Bind(int key, int mods, boolean hold) {}

    private StandGuiBindings() {}

    /**
     * 取某项的快捷键显示文本。
     *
     * @return 已绑定则返回键名（如 {@code F8}）；没有返回空串
     */
    public static String hotkeyText(StandGuiEntry e) {
        if (e == null || e.module == null) return "";
        Keybind kb = e.module.keybind;
        if (kb == null || !kb.isSet()) return "";
        String s = kb.toString();
        return s == null ? "" : s;
    }

    /** 该项是否有快捷键。 */
    public static boolean hasHotkey(StandGuiEntry e) {
        return !hotkeyText(e).isEmpty();
    }

    /**
     * 清除该项的 Meteor 快捷键。
     *
     * @return 是否成功清除
     */
    public static boolean clearHotkey(StandGuiEntry e) {
        if (e == null || e.module == null) return false;
        Keybind kb = e.module.keybind;
        if (kb == null || !kb.isSet()) return false;
        kb.reset();
        return true;
    }

    /**
     * 把所有模块的 Meteor 快捷键列出来（诊断/未来列表用）。
     */
    public static List<String> allHotkeys() {
        List<String> out = new ArrayList<>();
        for (Module m : Modules.get().getAll()) {
            if (m.keybind == null || !m.keybind.isSet()) continue;
            out.add(m.title + " = " + m.keybind);
        }
        return out;
    }
}
