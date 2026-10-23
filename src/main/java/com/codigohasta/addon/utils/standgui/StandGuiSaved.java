package com.codigohasta.addon.utils.standgui;

import com.codigohasta.addon.utils.notify.ImgNotificationType;
import com.codigohasta.addon.utils.notify.ImgNotifications;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 操作界面里那些「对某一项做点什么」的动作。
 *
 * <h2>语义（用户逐条确认过）</h2>
 * <table>
 *   <tr><th>动作</th><th>做什么</th></tr>
 *   <tr><td>保存菜单设置</td><td>把本项状态记进「默认配置」（内存 + 待接 NBT）</td></tr>
 *   <tr><td>加载状态</td><td>从「默认配置」读回本项状态</td></tr>
 *   <tr><td>重置默认状态</td><td>恢复本项默认值（= {@code Setting.reset()}）</td></tr>
 *   <tr><td>保存</td><td>把本项加入左栏 {@code Stand > 保存} 列表</td></tr>
 *   <tr><td>复制选项位置</td><td>把 {@code IMG > 模块 > 分组 > 设置} 写进剪贴板</td></tr>
 * </table>
 *
 * <h2>为什么「保存菜单设置」不直接写 Meteor 的配置文件</h2>
 * <p>用户的 A6 定案是「持久化走本模块的 {@code toTag/fromTag}」，随 Meteor profile 一起切。
 * 所以这里先记进内存快照，由 {@code StandGui} 在保存 profile 时写进 NBT。
 * 直接改 Meteor 的 {@code modules.nbt} 会绕过 Meteor 自己的保存时机，容易写坏。
 */
public final class StandGuiSaved {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    /** path -> 已保存的状态值（字符串形式，够用且与 setting.parse 对称）。 */
    private static final Map<String, String> SAVED_STATE = new HashMap<>();

    /** 「保存」列表（左栏 Stand 分类下）。 */
    private static final List<String> SAVED_LIST = new ArrayList<>();

    /** 反馈通知驻留秒数。 */
    private static final int NOTIFY_SECONDS = 3;

    private StandGuiSaved() {}

    // ═══════════ 状态存取 ═══════════

    /** 保存菜单设置：把本项当前值记下来。 */
    public static void saveState(StandGuiEntry target) {
        String v = currentValueOf(target);
        if (v == null) { notifyErr("这一项无法保存状态"); return; }
        SAVED_STATE.put(target.path, v);
        notifyOk("已保存: " + target.label + " = " + v);
    }

    /** 加载状态：把记下的值写回去。 */
    public static void loadState(StandGuiEntry target) {
        String v = SAVED_STATE.get(target.path);
        if (v == null) { notifyErr("没有已保存的状态: " + target.label); return; }
        if (!applyValue(target, v)) { notifyErr("加载失败: " + v); return; }
        notifyOk("已加载: " + target.label + " = " + currentValueOf(target));
    }

    /** 重置默认状态：恢复到该项的默认值。 */
    public static void resetState(StandGuiEntry target) {
        if (target.kind == StandGuiEntry.Kind.MODULE && target.module != null) {
            target.module.settings.reset();
            notifyOk("已重置设置: " + target.module.title);
            return;
        }
        if (target.setting != null) {
            target.setting.reset();
            notifyOk("已重置: " + target.label);
        }
    }

    /** 数值设为最小 / 最大值。 */
    public static void setNumeric(StandGuiEntry target, boolean minimum) {
        Setting<?> s = target.setting;
        if (s instanceof IntSetting is) {
            is.set(minimum ? is.min : is.max);
        } else if (s instanceof DoubleSetting ds) {
            ds.set(minimum ? ds.min : ds.max);
        } else {
            notifyErr("不是数值项");
            return;
        }
        notifyOk(target.label + " = " + currentValueOf(target));
    }

    // ═══════════ 保存列表 ═══════════

    /** 把本项加入左栏 Stand > 保存 列表。 */
    public static void addToSavedList(StandGuiEntry target) {
        String v = StandGuiContextMenu.pathForCopy(target);
        if (SAVED_LIST.contains(v)) { notifyErr("已在保存列表中"); return; }
        SAVED_LIST.add(v);
        // 让左栏「保存」分类重建，立刻能看到新增项
        StandGuiTree.invalidateStructure();
        notifyOk("已加入保存: " + target.label);
    }

    /**
     * 把本项从「保存」列表移除（用户：不知道怎么取消保存列表的）。
     *
     * <p>与 {@link #addToSavedList} 对称：操作界面里同一行会在
     * 「保存」/「取消保存」之间切换，所以用户不需要记不同的入口。
     */
    public static void removeFromSavedList(StandGuiEntry target) {
        String v = StandGuiContextMenu.pathForCopy(target);
        if (!SAVED_LIST.remove(v)) { notifyErr("不在保存列表中: " + target.label); return; }
        StandGuiTree.invalidateStructure();
        notifyOk("已取消保存: " + target.label);
    }

    /** 已保存的路径列表（左栏 Stand > 保存 用）。 */
    public static List<String> savedList() {
        return new ArrayList<>(SAVED_LIST);
    }

    // ═══════════ 复制路径 ═══════════

    /** 把路径写进剪贴板。 */
    public static void copyPath(StandGuiEntry target) {
        String p = StandGuiContextMenu.pathForCopy(target);
        try {
            mc.keyboard.setClipboard(p);
            notifyOk("已复制: " + p);
        } catch (Throwable t) {
            notifyErr("复制失败");
        }
    }

    // ═══════════ 值读写 ═══════════

    /** 取当前值的字符串形式；不支持的类型返回 null。 */
    private static String currentValueOf(StandGuiEntry target) {
        if (target.kind == StandGuiEntry.Kind.MODULE && target.module != null) {
            return String.valueOf(target.module.isActive());
        }
        Setting<?> s = target.setting;
        if (s == null) return null;
        Object v = s.get();
        return v == null ? "null" : String.valueOf(v);
    }

    /** 把字符串写回去（走 {@code Setting.parse}，与命令框同一条路）。 */
    private static boolean applyValue(StandGuiEntry target, String value) {
        if (target.setting != null) return target.setting.parse(value);
        return false;
    }

    // ═══════════ 通知 ═══════════

    private static void notifyOk(String msg) {
        ImgNotifications.INSTANCE.post("", msg, ImgNotificationType.SUCCESS, NOTIFY_SECONDS);
    }

    private static void notifyErr(String msg) {
        ImgNotifications.INSTANCE.post("", msg, ImgNotificationType.ERROR, NOTIFY_SECONDS);
    }

    // ═══════════ 持久化（待接 NBT）═══════════

    public static Map<String, String> exportStates() { return new HashMap<>(SAVED_STATE); }

    public static void importStates(Map<String, String> in) {
        SAVED_STATE.clear();
        if (in != null) SAVED_STATE.putAll(in);
    }

    public static List<String> exportList() { return new ArrayList<>(SAVED_LIST); }

    public static void importList(List<String> in) {
        SAVED_LIST.clear();
        if (in != null) SAVED_LIST.addAll(in);
    }
}
