package com.codigohasta.addon.utils.standgui;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;

import java.util.ArrayList;
import java.util.List;

/**
 * Stand 菜单的树节点。
 *
 * <p><b>为什么做成扁平 record 风格的 final 类而不是多态子类</b>：
 * 菜单只有 6 种节点（分类 / 模块 / 子菜单 / 设置 / 动作 / 保存项），
 * 用 {@link Kind} 判别、用可空字段承载差异，比 6 个子类少写 200 行 getter，
 * 且渲染器与输入层都只需一个 {@code switch}。
 *
 * <p><b>{@link #path} 的唯一性</b>：焦点记忆、绑定、关联状态全部按 path 存取，
 * 所以 path 必须全局唯一且稳定。构造规则见 {@link StandGuiTree}。
 */
public final class StandGuiEntry {

    public enum Kind {
        /** 左栏分类（Meteor 的 {@link Category}）。 */
        CATEGORY,
        /** 一个模块（有设置就是可进的子节点）。 */
        MODULE,
        /** 模块下的设置分组。 */
        GROUP,
        /** 一个设置项。 */
        SETTING,
        /** 纯动作（无子节点，按 5 立即执行）。 */
        ACTION,
        /** 「保存」列表项。 */
        SAVED
    }

    /** 节点类型。 */
    public final Kind kind;
    /** 显示名（{@code Module.title} / {@code Setting.title} / 分组名）。 */
    public final String label;
    /**
     * 右栏尾部显示的控制符。
     *
     * <p>{@code ">"} = 有子节点；{@code ""} = 无；Bool 由渲染器用复选框另画，
     * 所以这里不放。
     */
    public final String suffix;
    /** 全局唯一路径，如 {@code "IMG/TpAnchorAura/设置/放置间隔"}。 */
    public final String path;

    /**
     * 介绍文字（焦点项的说明面板，照 Stand 的 help text）。
     *
     * <p>来源：{@code Module.description} / {@code Setting.description}。
     * 空串表示没有说明，渲染器不画那一行。
     */
    public final String description;

    /**
     * 命令名（焦点项的命令面板，照 Stand 底部的 {@code 命令: xxx}）。
     *
     * <p>来源：模块类 / 设置字段的 <b>Java 字段名</b>（英文字段名），
     * 取不到时为空串 —— 这时不显示命令行（用户 F3(a)：跳过，不生成命令）。
     */
    public final String commandName;

    /** 所属分类（CATEGORY 节点为自身；其它为 null 或父分类）。 */
    public final Category category;
    /** 所属模块（MODULE 节点为自身；其下的 GROUP/SETTING 指向该模块）。 */
    public final Module module;
    /** 设置项（kind == SETTING 时非空）。 */
    public final Setting<?> setting;
    /** 子节点（CATEGORY / MODULE / GROUP 有）。 */
    public final List<StandGuiEntry> children = new ArrayList<>();

    private StandGuiEntry(Kind kind, String label, String suffix, String path,
                          Category category, Module module, Setting<?> setting,
                          String description, String commandName) {
        this.kind = kind;
        this.label = label == null ? "" : label;
        this.suffix = suffix == null ? "" : suffix;
        this.path = path;
        this.category = category;
        this.module = module;
        this.setting = setting;
        this.description = description == null ? "" : description;
        this.commandName = commandName == null ? "" : commandName;
    }

    public static StandGuiEntry category(Category c, String path) {
        return new StandGuiEntry(Kind.CATEGORY, c.name, ">", path, c, null, null, "", "");
    }

    /**
     * 分类节点，<b>显示名与 path 分离</b>。
     *
     * <p>为什么不直接用 {@code c.name} 当 label：
     * Meteor 内置分类是英文（Combat / Player …），用户要求左栏显示中文；
     * 但 path 必须继续用英文原名 —— 否则「隐藏分类」的配置、
     * 焦点记忆、命令都会随翻译失效。所以 label 中文、path 英文。
     */
    public static StandGuiEntry category(Category c, String path, String displayLabel) {
        return new StandGuiEntry(Kind.CATEGORY,
            displayLabel == null || displayLabel.isBlank() ? c.name : displayLabel,
            ">", path, c, null, null, "", "");
    }

    /**
     * 模块节点。
     *
     * <p><b>★ suffix 必须是空串（用户 2026-10-20）</b>：「右栏模块列表层（第一层）
     * 的那个右箭头可以去掉」。原因是模块行的 {@code 5} 是<b>开关</b>而不是进入
     * （进入用 {@code 9}），画 {@code >} 会让人以为按 5 会进去。
     * 开启状态由右侧勾选框图标表达，不需要箭头。
     */
    public static StandGuiEntry module(Module m, String label, String path,
                                       String description, String commandName) {
        return new StandGuiEntry(Kind.MODULE, label, "", path, m.category, m, null,
            description, commandName);
    }

    public static StandGuiEntry group(String label, String path, Module owner,
                                      String description) {
        return new StandGuiEntry(Kind.GROUP, label, ">", path, null, owner, null,
            description, "");
    }

    public static StandGuiEntry setting(Setting<?> s, String label, String suffix, String path,
                                        Module owner, String commandName) {
        return new StandGuiEntry(Kind.SETTING, label, suffix, path, null, owner, s,
            s.description, commandName);
    }

    public static StandGuiEntry action(String label, String path, String description) {
        return new StandGuiEntry(Kind.ACTION, label, "", path, null, null, null,
            description, "");
    }

    public static StandGuiEntry saved(String label, String path) {
        return new StandGuiEntry(Kind.SAVED, label, "", path, null, null, null, "", "");
    }

    /** 是否有子节点（决定 {@code 5} 是「进入」还是「执行」）。 */
    public boolean hasChildren() {
        return !children.isEmpty();
    }

    /** 是否是模块（操作界面只对模块开放，见 C6）。 */
    public boolean isModule() {
        return kind == Kind.MODULE;
    }

    @Override
    public String toString() {
        return kind + ":" + label + "@" + path;
    }
}
