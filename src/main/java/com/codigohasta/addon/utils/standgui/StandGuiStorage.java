package com.codigohasta.addon.utils.standgui;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * StandGui 的 NBT 持久化（用户 A6：走本模块的 {@code toTag/fromTag}，随 Meteor profile 一起切）。
 *
 * <h2>存什么</h2>
 * <pre>
 *   focus/     每个列表各自的焦点索引         Map&lt;path, int&gt;
 *   bindings/  快捷键绑定（多对多 + 按住模式）
 *   links/     关联状态（4 条件 + 反转）
 *   saved/     「保存菜单设置」记下的值        Map&lt;path, String&gt;
 *   savedList/ 「保存」列表（左栏 Stand &gt; 保存）
 *   layout/    X / Y / 缩放 / 可见行数 / 主题色
 * </pre>
 *
 * <h2>为什么用「一个扁平 NbtCompound + 命名子 Compound」而不是嵌套类</h2>
 * <p>NBT 一旦形成嵌套结构，升级格式就要写迁移代码。这里每个域都是
 * <b>独立的 {@code NbtCompound}，互不依赖</b>，某一块解析失败只丢那一块，
 * 不会让整个配置回滚（见 {@link #fromTag} 的逐块 try）。
 *
 * <h2>★ 全部解析都吞异常</h2>
 * <p>配置损坏绝不能让客户端崩或让模块加载失败 —— 丢配置比崩游戏好得多。
 * 每块独立 try，坏一块留默认值。
 */
public final class StandGuiStorage {

    private StandGuiStorage() {}

    // ═══════════ 写 ═══════════

    /**
     * 把当前状态写进 NBT。
     *
     * @param focus 焦点表（path → index）
     */
    public static NbtCompound toTag(Map<String, Integer> focus) {
        NbtCompound root = new NbtCompound();

        // ── focus ──
        NbtCompound focusTag = new NbtCompound();
        if (focus != null) {
            for (Map.Entry<String, Integer> e : focus.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) continue;
                focusTag.putInt(e.getKey(), e.getValue());
            }
        }
        root.put("focus", focusTag);

        // ── links ──
        NbtCompound linksTag = new NbtCompound();
        for (Map.Entry<String, StandGuiLinks.LinkState> e : StandGuiLinks.exportAll().entrySet()) {
            StandGuiLinks.LinkState st = e.getValue();
            if (st == null || st.link == StandGuiLinks.Link.无) continue;
            NbtCompound one = new NbtCompound();
            one.putString("link", st.link.name());
            one.putBoolean("invert", st.inverted);
            linksTag.put(e.getKey(), one);
        }
        root.put("links", linksTag);

        // ── saved states ──
        NbtCompound savedTag = new NbtCompound();
        for (Map.Entry<String, String> e : StandGuiSaved.exportStates().entrySet()) {
            if (e.getKey() == null || e.getValue() == null) continue;
            savedTag.putString(e.getKey(), e.getValue());
        }
        root.put("saved", savedTag);

        // ── saved list（顺序有意义，用 List）──
        NbtList listTag = new NbtList();
        for (String s : StandGuiSaved.exportList()) {
            if (s != null) listTag.add(NbtString.of(s));
        }
        root.put("savedList", listTag);

        // ── bindings ──


        return root;
    }

    // ═══════════ 读 ═══════════

    /**
     * 从 NBT 恢复。
     *
     * <p>逐块 try：某一块坏了只留默认值，其余照常恢复。
     *
     * @return 恢复的焦点表（不直接写进 state，交给调用方）
     */
    public static Map<String, Integer> fromTag(NbtCompound root) {
        Map<String, Integer> focus = new HashMap<>();
        if (root == null) return focus;

        // ── focus ──
        try {
            NbtCompound focusTag = root.getCompoundOrEmpty("focus");
            for (String key : focusTag.getKeys()) {
                focus.put(key, focusTag.getInt(key, 0));
            }
        } catch (Throwable ignored) { }

        // ── links ──
        try {
            Map<String, StandGuiLinks.LinkState> links = new HashMap<>();
            NbtCompound linksTag = root.getCompoundOrEmpty("links");
            for (String key : linksTag.getKeys()) {
                NbtCompound one = linksTag.getCompoundOrEmpty(key);
                StandGuiLinks.LinkState st = new StandGuiLinks.LinkState();
                st.link = parseLink(one.getString("link", "无"));
                st.inverted = one.getBoolean("invert", false);
                links.put(key, st);
            }
            StandGuiLinks.importAll(links);
        } catch (Throwable ignored) { }

        // ── saved states ──
        try {
            Map<String, String> saved = new HashMap<>();
            NbtCompound savedTag = root.getCompoundOrEmpty("saved");
            for (String key : savedTag.getKeys()) {
                saved.put(key, savedTag.getString(key, ""));
            }
            StandGuiSaved.importStates(saved);
        } catch (Throwable ignored) { }

        // ── saved list ──
        try {
            List<String> list = new ArrayList<>();
            NbtList listTag = root.getListOrEmpty("savedList");
            for (NbtElement el : listTag) {
                String v = el.asString().orElse("");
                if (!v.isEmpty()) list.add(v);
            }
            StandGuiSaved.importList(list);
        } catch (Throwable ignored) { }

        // 说明：快捷键不再由本模块持久化 —— 已改为单一来源（Meteor 的 module.keybind，
        // 由 Meteor 自己存进 modules.nbt）。旧版本写过的 "bindings" 标签会被忽略，
        // 不做迁移是刻意的：那套表本来就会与 Meteor 重复触发，留着反而有害。

        return focus;
    }

    /** 字符串 → Link 枚举（不认识就当「无」）。 */
    private static StandGuiLinks.Link parseLink(String s) {
        if (s == null) return StandGuiLinks.Link.无;
        for (StandGuiLinks.Link l : StandGuiLinks.Link.values()) {
            if (l.name().equals(s) || l.label.equals(s)) return l;
        }
        return StandGuiLinks.Link.无;
    }
}
