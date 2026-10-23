package com.codigohasta.addon.utils.standgui;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;

import java.util.ArrayList;
import java.util.List;

/**
 * 操作界面（用户截图 2 的「高级菜单」，光标停在模块上按 {@code 3} 进入）。
 *
 * <h2>形态</h2>
 * <pre>
 *   关联状态: 无          &gt;    ← 循环 无 / 菜单打开时 / 步行时 / 瞄准时
 *   反转关联状态               ← 开关
 *   保存菜单设置               ← 把本项状态写进默认配置
 *   加载状态                   ← 从默认配置读回本项状态
 *   重置默认状态               ← 恢复本项默认值
 *   快捷键                &gt;    ← 进二级（本阶段未实现）
 *   保存                       ← 加入左栏 Stand &gt; 保存 列表
 *   复制选项位置               ← 复制路径到剪贴板
 *   设置为最小值 / 最大值      ← 仅数值项（D3，用户确认要加）
 *   返回                       ← 退出操作界面
 * </pre>
 *
 * <h2>为什么「即时生成 List&lt;Item&gt;」而不是往树里塞固定节点</h2>
 * <p>操作项随目标类型变化（Bool 才有关联状态、数值才有最小值/最大值），
 * 且取值（如「关联状态: 无」）随时会变。生成一份 List 供渲染器画、
 * 供输入层按索引执行，比维护动态子树简单且不会与树的重建冲突。
 *
 * <h2>语义依据</h2>
 * <p>关联状态 4 值与「反转」照 Stand 的实际行为
 * （[选项设置教程](https://www.kancloud.cn/pandrao/zxc12566/3040901)），
 * 其余各项照同一份教程 + 用户逐条确认。
 */
public final class StandGuiContextMenu {

    /** 操作种类（决定按 5 时做什么）。 */
    public enum Op {
        /** 循环「关联状态」。 */
        LINK_CYCLE,
        /** 反转关联状态。 */
        LINK_INVERT,
        /** 保存本项状态到默认配置。 */
        SAVE_STATE,
        /** 从默认配置读回本项状态。 */
        LOAD_STATE,
        /** 恢复本项默认值。 */
        RESET_STATE,
        /** 进入快捷键二级菜单。 */
        HOTKEY,
        /** 把本项加入「保存」列表。 */
        SAVE_TO_LIST,
        /** 把本项从「保存」列表移除（用户：不知道怎么取消保存列表的）。 */
        UNSAVE_FROM_LIST,
        /** 清除本项的全部快捷键绑定（用户：不知道怎么取消绑定）。 */
        CLEAR_BINDS,
        /** 复制本项路径。 */
        COPY_PATH,
        /** 数值设为最小值。 */
        SET_MIN,
        /** 数值设为最大值。 */
        SET_MAX,
        /** 退出操作界面。 */
        BACK
    }

    /** 一条操作项。 */
    public record Item(Op op, String label, String suffix) {}

    private StandGuiContextMenu() {}

    /** 为目标节点生成操作项。 */
    public static List<Item> build(StandGuiEntry target) {
        List<Item> out = new ArrayList<>();
        if (target == null) {
            out.add(new Item(Op.BACK, "返回", ""));
            return out;
        }

        Setting<?> s = target.setting;
        boolean isToggle = target.kind == StandGuiEntry.Kind.MODULE || s instanceof BoolSetting;
        boolean isNumeric = s instanceof IntSetting || s instanceof DoubleSetting;

        // 1. 关联状态（仅开关式）
        if (isToggle) {
            StandGuiLinks.LinkState st = StandGuiLinks.get(target.path);
            out.add(new Item(Op.LINK_CYCLE, "关联状态", st.link.label));
            out.add(new Item(Op.LINK_INVERT, "反转关联状态", st.inverted ? "开" : ""));
        }

        // 2. 状态存取（开关式 / 调节式 / 枚举）
        if (isToggle || isNumeric || s instanceof EnumSetting<?>) {
            out.add(new Item(Op.SAVE_STATE, "保存菜单设置", ""));
            out.add(new Item(Op.LOAD_STATE, "加载状态", ""));
            out.add(new Item(Op.RESET_STATE, "重置默认状态", ""));
        }

        // 3. 数值特有（D3）
        if (isNumeric) {
            out.add(new Item(Op.SET_MIN, "设置为最小值", ""));
            out.add(new Item(Op.SET_MAX, "设置为最大值", ""));
        }

        // 4. 快捷键：显示 Meteor 的 module.keybind（单一事实来源，不再有自建绑定表）
        String hotkeyText = StandGuiBindings.hotkeyText(target);
        // 没有键 → 显示 ">" 表示「按 5 去 Meteor 界面设置」；有键 → 直接显示键名
        out.add(new Item(Op.HOTKEY, "快捷键",
            hotkeyText.isEmpty() ? ">" : hotkeyText));

        // 4b. 有键才显示「清除快捷键」（改的是 Meteor 的 keybind）
        if (!hotkeyText.isEmpty()) {
            out.add(new Item(Op.CLEAR_BINDS, "清除快捷键", ""));
        }

        // 5. 保存列表：已保存 → 显示「取消保存」，否则显示「保存」
        boolean alreadySaved = StandGuiSaved.savedList().contains(pathForCopy(target));
        if (alreadySaved) {
            out.add(new Item(Op.UNSAVE_FROM_LIST, "取消保存", ""));
        } else {
            out.add(new Item(Op.SAVE_TO_LIST, "保存", ""));
        }

        out.add(new Item(Op.COPY_PATH, "复制选项位置", ""));

        // 6. 返回
        out.add(new Item(Op.BACK, "返回", ""));
        return out;
    }

    /** 复制路径用的显示形式（{@code IMG > 模块 > 分组 > 设置}）。 */
    public static String pathForCopy(StandGuiEntry target) {
        return target == null ? "" : target.path.replace("/", " > ");
    }
}
