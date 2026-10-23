package com.codigohasta.addon.utils.standgui;

import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import net.minecraft.client.MinecraftClient;

/**
 * 各种 Setting 类型在菜单里的「编辑动作」。
 *
 * <h2>类型 → 操作（用户 A4：尽量支持所有设置项）</h2>
 * <table>
 *   <tr><th>类型</th><th>4/6</th><th>5</th><th>9</th></tr>
 *   <tr><td>Bool</td><td>—</td><td>取反</td><td>—</td></tr>
 *   <tr><td>Int / Double</td><td>± 一步</td><td>命令框预填</td><td>—</td></tr>
 *   <tr><td>Enum</td><td>上/下一个</td><td>下一个</td><td>—</td></tr>
 *   <tr><td>Color</td><td>—</td><td><b>进编辑子页</b></td><td>—</td></tr>
 *   <tr><td>String</td><td>—</td><td><b>进编辑子页</b></td><td>—</td></tr>
 *   <tr><td>Keybind</td><td>—</td><td><b>进绑定态</b></td><td>—</td></tr>
 *   <tr><td>各类 List</td><td>—</td><td><b>开 Meteor 的 ModuleScreen</b></td><td>—</td></tr>
 * </table>
 *
 * <h2>为什么 List 类直接开 Meteor 的屏（用户 D9(a)）</h2>
 * <p>List 类的编辑器（实体类型选择、物品选择、方块选择…）在 Meteor 里是
 * {@code WListSelect / WItemListSetting} 那种交互，鼠标点选 + 搜索框 + 分页。
 * 在纯键盘 HUD 里重写一遍代价极大且体验更差。用户已确认接受
 * 「短暂开 Meteor 的界面，关掉后回菜单」。
 *
 * <p>用的是官方入口 {@code GuiThemes.get().moduleScreen(module)}（已核对
 * {@code GuiTheme:230}），不自己 {@code new}，避免主题不一致。
 *
 * <h2>Color / String 为什么自绘子页而不开 Screen</h2>
 * <p>它们只需要「几个数字 / 一行文本」，自绘子页可以保持在 HUD 里、
 * 不打断游戏（WASD 仍可用）。而 List 类必须借 Meteor 的控件，所以那两类分开处理。
 */
public final class StandGuiEditors {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private StandGuiEditors() {}

    /** 该设置项是否需要用「编辑子页」而不是就地操作。 */
    public static boolean needsSubpage(Setting<?> s) {
        return s instanceof meteordevelopment.meteorclient.settings.ColorSetting
            || s instanceof meteordevelopment.meteorclient.settings.StringSetting
            || s instanceof meteordevelopment.meteorclient.settings.KeybindSetting;
    }

    /**
     * 该设置项是否属于「List 类」（需要开 Meteor 的编辑器）。
     *
     * <p>判据用「getDefaultValue 是 {@code java.util.List}」——
     * 这样一次覆盖 {@code ItemListSetting / BlockListSetting / EntityTypeListSetting /
     * StringListSetting / ColorListSetting / StatusEffectListSetting …} 全部子类，
     * 不必逐个 {@code instanceof}（新增的 List 类型也能自动覆盖）。
     */
    public static boolean isListSetting(Setting<?> s) {
        if (s == null) return false;
        try {
            Object def = s.getDefaultValue();
            return def instanceof java.util.List<?>;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 打开 Meteor 的 ModuleScreen（List 类专用，用户 D9(a)）。
     *
     * @param module 该设置所属的模块
     */
    public static void openMeteorModuleScreen(meteordevelopment.meteorclient.systems.modules.Module module) {
        if (module == null) return;
        try {
            mc.setScreen(GuiThemes.get().moduleScreen(module));
        } catch (Throwable t) {
            com.codigohasta.addon.AddonTemplate.LOG.warn(
                "[StandGui] 打开 Meteor ModuleScreen 失败", t);
        }
    }

    // ═══════════ 子页的取值助手 ═══════════

    /** Color 的分量名（编辑子页的四行）。 */
    public static final String[] RGB_LABELS = {"红", "绿", "蓝", "透明度"};

    /** 取 Color 的某个分量（0~255）。 */
    public static int colorComponent(Setting<?> s, int idx) {
        if (!(s.get() instanceof meteordevelopment.meteorclient.utils.render.color.SettingColor c)) {
            return 0;
        }
        return switch (idx) {
            case 0 -> c.r;
            case 1 -> c.g;
            case 2 -> c.b;
            default -> c.a;
        };
    }

    /** 设置 Color 的某个分量（夹到 0~255）。 */
    public static void setColorComponent(Setting<?> s, int idx, int value) {
        if (!(s.get() instanceof meteordevelopment.meteorclient.utils.render.color.SettingColor c)) {
            return;
        }
        int v = Math.max(0, Math.min(255, value));
        // SettingColor 是可变对象，但 set() 才会触发 onChanged（保存/通知），
        // 所以必须构造新实例再 set()，不能只改字段。
        var next = new meteordevelopment.meteorclient.utils.render.color.SettingColor(
            idx == 0 ? v : c.r,
            idx == 1 ? v : c.g,
            idx == 2 ? v : c.b,
            idx == 3 ? v : c.a,
            c.rainbow);
        @SuppressWarnings("unchecked")
        Setting<meteordevelopment.meteorclient.utils.render.color.SettingColor> cast =
            (Setting<meteordevelopment.meteorclient.utils.render.color.SettingColor>) s;
        cast.set(next);
    }

    /** 取枚举的当前值名。 */
    public static String enumValue(Setting<?> s) {
        Object v = s.get();
        return v == null ? "" : v.toString();
    }
}
