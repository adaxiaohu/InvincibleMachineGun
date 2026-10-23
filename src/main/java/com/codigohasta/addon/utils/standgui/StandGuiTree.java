package com.codigohasta.addon.utils.standgui;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 从 Meteor 的 {@link Modules} 构建菜单树。
 *
 * <h2>为什么每次打开菜单都重建，而不是启动时建一次</h2>
 * <p>模块可以被其它 addon 事后注册、设置可以被 {@code Settings.reset()} 改动值，
 * 而 <b>目录结构本身几乎不变</b>（只有模块增删）。所以：
 * <ul>
 *   <li>结构（分类 / 模块 / 设置节点）在 {@link #rebuild()} 时建一次，
 *       每 tick 重算的只有「值后缀」；</li>
 *   <li>重建的触发点 = 模块集合的规模变化（便宜，且能捕获热重载）。</li>
 * </ul>
 *
 * <h2>path 的构造规则</h2>
 * <pre>
 *   分类        "IMG"
 *   模块        "IMG/模块显示名"
 *   设置分组    "IMG/模块显示名/分组名"
 *   设置项      "IMG/模块显示名/分组名/设置显示名"
 * </pre>
 * <p><b>★ 用「显示名」而不是内部 name</b>：用户的焦点记忆/绑定/关联都按 path 存，
 * 用显示名他才能在「复制选项位置」里得到和菜单上看到的一致的路径（照 Stand 的做法）。
 * <b>代价</b>：以后若改中文显示名，旧 path 的绑定会丢 —— 与 i18n 里「name 同时是存档键」
 * 是同一类取舍，已知并接受。
 */
public final class StandGuiTree {

    private StandGuiTree() {}

    /**
     * Meteor 内置分类的中文显示名（用户 2026-10-20：左栏显示成中文）。
     *
     * <p><b>为什么用映射而不是改 {@code Categories} 里的字符串</b>：
     * {@code Category.name} 是 Meteor 自己的东西，改它会影响 Meteor 的 GUI、
     * 模块存档键与命令（{@code module <category>}）。这里<b>只改显示</b>，
     * 内部仍用原名做匹配 —— 与 i18n 的「只动 title 不动 name」同一个原则。
     *
     * <p>未列出的分类（addon 自带的 {@code IMG / sfIMG / IMG-hud}）保持原名，
     * 因为它们的名字本身就是品牌标识。
     */
    private static final java.util.Map<String, String> CATEGORY_DISPLAY = java.util.Map.of(
        "Combat", "战斗",
        "Player", "自我",
        "Movement", "移动",
        "Render", "渲染",
        "World", "世界",
        "Misc", "杂项"
    );

    /** 分类的显示名（内置分类翻译成中文，其它原样返回）。 */
    public static String categoryDisplayName(String raw) {
        if (raw == null) return "";
        String zh = CATEGORY_DISPLAY.get(raw);
        return zh == null ? raw : zh;
    }

    /** 上一次构建时的模块总数，用于判断是否需要重建结构。 */
    private static int lastModuleCount = -1;

    /** 顶层：所有分类节点。 */
    private static final List<StandGuiEntry> CATEGORIES = new ArrayList<>();

    /** 取顶层（必要时重建）。 */
    public static List<StandGuiEntry> categories() {
        int now = Modules.get().getAll().size();
        if (now != lastModuleCount) rebuild();
        return CATEGORIES;
    }

    /** 强制重建结构。 */
    public static synchronized void rebuild() {
        CATEGORIES.clear();
        lastModuleCount = Modules.get().getAll().size();

        for (Category cat : Modules.loopCategories()) {
            List<Module> group = Modules.get().getGroup(cat);
            if (group == null || group.isEmpty()) continue;

            // ★ 用户可在设置里隐藏某些分类（见 StandGui 的「隐藏的分类」）
            if (isHiddenCategory(cat.name)) continue;

            // 显示名用中文映射，但 path 仍用原始 name —— 这样
            // 「隐藏的分类」配置、命令、焦点记忆都用稳定的英文键，不受翻译影响。
            StandGuiEntry catNode = StandGuiEntry.category(cat, cat.name,
                categoryDisplayName(cat.name));
            // 按【显示名】排序（D7：IMG 分类有 134 个模块，按标题更好找）
            List<Module> sorted = new ArrayList<>(group);
            sorted.sort(Comparator.comparing(m -> titleOf(m), String.CASE_INSENSITIVE_ORDER));

            for (Module m : sorted) {
                catNode.children.add(buildModule(catNode.path, m));
            }
            CATEGORIES.add(catNode);
        }

        // ★「保存」分类（用户 E9：左栏的 Stand 分类里有一个保存的选项）
        //   放在最后，内容由 StandGuiSaved 的保存列表动态填充。
        CATEGORIES.add(buildSavedCategory());
    }

    // ═══════════ 隐藏分类（用户：可以自定义隐藏左栏的一些特定插件的项）═══════════

    /** 被隐藏的分类原名集合（由 StandGui 的设置注入）。 */
    private static final java.util.Set<String> HIDDEN = new java.util.LinkedHashSet<>();

    /** 设置哪些分类要隐藏（传原始 name，如 {@code Combat} / {@code sfIMG}）。 */
    public static synchronized void setHiddenCategories(java.util.Collection<String> names) {
        HIDDEN.clear();
        if (names != null) {
            for (String n : names) {
                if (n != null && !n.isBlank()) HIDDEN.add(n.trim());
            }
        }
        lastModuleCount = -1;   // 触发下次访问时重建
    }

    /** 该分类是否被隐藏。 */
    public static boolean isHiddenCategory(String rawName) {
        return rawName != null && HIDDEN.contains(rawName);
    }

    /** 当前被隐藏的分类（供设置界面显示）。 */
    public static java.util.Set<String> hiddenCategories() {
        return new java.util.LinkedHashSet<>(HIDDEN);
    }

    /** 「保存」分类的 path 与显示名。 */
    public static final String SAVED_CATEGORY_NAME = "保存";

    /**
     * 构建「保存」分类。
     *
     * <p>子项是用户用操作界面的「保存」加进来的路径串（形如
     * {@code IMG > 模块 > 分组 > 设置}）。每项按 path 反查回真实节点，
     * 这样选中后按 5 就能作用到真正的模块/设置上。
     */
    private static StandGuiEntry buildSavedCategory() {
        StandGuiEntry cat = StandGuiEntry.category(
            new meteordevelopment.meteorclient.systems.modules.Category(SAVED_CATEGORY_NAME),
            SAVED_CATEGORY_NAME);

        for (String display : StandGuiSaved.savedList()) {
            if (display == null || display.isEmpty()) continue;

            // 显示串 → 真实 path（tree 里用 "/" 分隔）
            String realPath = display.replace(" > ", "/");
            StandGuiEntry real = findByPath(realPath);

            // 标签取最后一段（"放置间隔" 而不是整条路径）
            int cut = display.lastIndexOf(" > ");
            String label = cut >= 0 ? display.substring(cut + 3) : display;

            // path 用真实节点的（可被 find 到 → 能执行）；
            // 找不到（模块已卸载）就退化成显示串本身，至少还能看
            cat.children.add(StandGuiEntry.saved(label, real == null ? display : real.path));
        }
        return cat;
    }

    /** 让「保存」分类在内容变化后重建（操作界面执行「保存」后调用）。 */
    public static void invalidateStructure() {
        lastModuleCount = -1;
    }

    /** 构建一个模块节点（含它的设置分组）。 */
    private static StandGuiEntry buildModule(String parentPath, Module m) {
        String title = titleOf(m);
        String path = parentPath + "/" + title;
        // 命令名 = Java 字段名（取不到就空，命令框里不生成该条命令）
        StandGuiEntry node = StandGuiEntry.module(m, title, path,
            m.description == null ? "" : m.description, fieldNameOf(m, m));

        for (SettingGroup sg : m.settings) {
            if (sg.name == null) continue;
            String groupPath = path + "/" + sg.name;
            StandGuiEntry groupNode = StandGuiEntry.group(sg.name, groupPath, m, "");

            for (Setting<?> s : sg) {
                if (s == null || !s.isVisible()) continue;
                groupNode.children.add(buildSetting(groupPath, m, s));
            }

            // 空分组（所有设置都不可见）不显示，避免点进去是空的
            if (!groupNode.children.isEmpty()) node.children.add(groupNode);
        }
        return node;
    }

    /** 构建一个设置节点。 */
    private static StandGuiEntry buildSetting(String parentPath, Module m, Setting<?> s) {
        String path = parentPath + "/" + s.title;
        return StandGuiEntry.setting(s, s.title, suffixOf(s), path, m, fieldNameOf(m, s));
    }

    /**
     * 取 Setting 在模块类里的 <b>Java 字段名</b>（英文），作为命令框的命令名。
     *
     * <p>逻辑照抄项目已有的 {@code JsonEnglishTranslationManager.findFieldName}：
     * 沿 {@code getSuperclass()} 上溯、逐个 {@code getDeclaredFields()} 比对引用相等。
     * <b>为什么用引用相等而不是 equals</b>：{@code Setting} 没重写 equals，
     * 但用 {@code ==} 语义更明确，也不会被将来重写 equals 破坏。
     *
     * <p><b>public</b>：命令框的补全表也要用它（{@code StandGuiCommands.allCommands}）。
     *
     * @return 字段名；找不到返回 {@code ""}（helper 类里注册的设置会走这条路）
     */
    public static String fieldNameOf(Module module, Object value) {
        if (module == null || value == null) return "";
        for (Class<?> t = module.getClass(); t != null && t != Module.class; t = t.getSuperclass()) {
            for (java.lang.reflect.Field f : t.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    if (f.get(module) == value) return f.getName();
                } catch (Throwable ignored) {
                    // 反射失败（模块未加载/字段不可达）→ 当作没有命令名
                }
            }
        }
        return "";
    }

    /**
     * 按 <b>Java 字段名</b> 找模块里的 Setting（命令框自动层用）。
     *
     * <p>为什么要遍历模块的设置再比对字段名，而不是直接反射取字段：
     * 直接取只能拿到「声明在模块类里」的字段，helper 类注册的设置拿不到；
     * 而遍历设置 + {@link #fieldNameOf} 能保证「tree 里怎么命名，命令就怎么解析」，
     * 两边同源，不会出现「菜单显示有命令名但敲不进去」。
     *
     * @return 找到的 Setting；没有返回 null
     */
    public static Setting<?> settingByFieldName(Module module, String fieldName) {
        if (module == null || fieldName == null || fieldName.isEmpty()) return null;
        for (SettingGroup sg : module.settings) {
            for (Setting<?> s : sg) {
                if (fieldName.equals(fieldNameOf(module, s))) return s;
            }
        }
        return null;
    }

    /**
     * 右栏尾部显示的控制符 / 数值。
     *
     * <p>Bool 返回空串 —— 复选框由渲染器单独画（与 demo 的 {@code .checkbox} 一致）。
     */
    public static String suffixOf(Setting<?> s) {
        if (s instanceof BoolSetting) return "";
        if (s instanceof IntSetting is) {
            Integer v = is.get();
            return v == null ? "0" : String.valueOf(v);
        }
        if (s instanceof DoubleSetting ds) {
            Double v = ds.get();
            if (v == null) return "0";
            // ★ 显示位数取 max(2, decimalPlaces)。
            //   只用 decimalPlaces 会有个坑：很多 DoubleSetting 的 decimalPlaces 是 0 或 1，
            //   于是 1.00 按 4/6 改成 1.01 后<b>显示仍然是 "1"</b> ——
            //   用户会以为「4/6 改不了滑块」（实测报过这个现象）。
            //   至少留 2 位小数，改动就一定能看出来。
            int dp = Math.max(2, Math.min(6, ds.decimalPlaces));
            return String.format("%." + dp + "f", v);
        }
        if (s instanceof EnumSetting<?> es) {
            Object v = es.get();
            return v == null ? "" : v.toString();
        }
        // Color / String / Keybind / List 等：进子页或暂时不支持
        return ">";
    }

    /** 模块显示名（翻译后的 title；为空时退回 name）。 */
    public static String titleOf(Module m) {
        if (m.title != null && !m.title.isBlank()) return m.title;
        return m.name;
    }

    /** 面包屑根名。 */
    public static String rootName() {
        return "Stand";
    }

    /**
     * 本插件版本号（面包屑显示成 {@code Stand 1.8.5}，用户 2026-10-20 要求）。
     *
     * <p>来源：{@code fabric.mod.json} 的 {@code version}（构建时由
     * {@code gradle/libs.versions.toml} → {@code mod-version} 注入）。
     * 取不到时返回 {@code "?"}，<b>绝不抛异常</b> —— 菜单不能因为读版本失败而崩。
     */
    public static String modVersion() {
        if (cachedVersion != null) return cachedVersion;
        String v = "?";
        try {
            // mod id 与 fabric.mod.json 的 "id" 一致
            var container = net.fabricmc.loader.api.FabricLoader.getInstance()
                .getModContainer("invincible_machine_gun");
            if (container.isPresent()) {
                v = container.get().getMetadata().getVersion().getFriendlyString();
            }
        } catch (Throwable ignored) {
            // 加载器不可用/元数据缺失：退化为 "?"
        }
        cachedVersion = v;
        return v;
    }

    /** 版本号缓存（只读一次）。 */
    private static String cachedVersion;

    /** 供诊断：当前树规模。 */
    public static String describe() {
        int mods = 0;
        for (StandGuiEntry c : categories()) mods += c.children.size();
        return "categories=" + categories().size() + " modules=" + mods;
    }

    /**
     * 按 path 在整棵树里找节点。
     *
     * <p>谁在用：{@link StandGuiLinks} 每 tick 按 path 把「关联状态」写进目标项。
     * 它拿到的只有 path 字符串（来自配置），所以必须能反查节点。
     *
     * <p><b>为什么不在建树时维护一张 path → 节点 的 Map</b>：
     * 树会因为模块增删而重建，Map 也要跟着重建；而这里只在「关联状态触发了变化」
     * 时才调用（不是每帧），线性查找的代价可忽略，少维护一份易失步的索引。
     *
     * @return 找到的节点；没有返回 null
     */
    public static StandGuiEntry findByPath(String path) {
        if (path == null || path.isEmpty()) return null;
        for (StandGuiEntry cat : categories()) {
            StandGuiEntry hit = findIn(cat, path);
            if (hit != null) return hit;
        }
        return null;
    }

    private static StandGuiEntry findIn(StandGuiEntry node, String path) {
        // ★ 跳过 SAVED 节点：它的 path 是「复制」自真实节点的，
        //   如果先命中它，调用方拿到的是个没有 module/setting 的壳，
        //   「9 进入设置 / 3 操作界面 / 5 执行」都会失效。
        //   必须让 findByPath 永远返回【真实】节点。
        if (node.kind != StandGuiEntry.Kind.SAVED && path.equals(node.path)) return node;
        for (StandGuiEntry child : node.children) {
            StandGuiEntry hit = findIn(child, path);
            if (hit != null) return hit;
        }
        return null;
    }

    /**
     * 按 {@link Module} 对象找它的节点（供 Meteor 键位回灌用）。
     *
     * <p>为什么不用 path 查：Meteor 的 {@code Module} 只知道自己的对象，
     * 而 path 里含「翻译后的显示名」，反向拼 path 容易出错。
     * 直接比对引用最可靠。
     *
     * @return 找到的模块节点；没有返回 null（模块被隐藏或未加载）
     */
    public static StandGuiEntry findModule(Module m) {
        if (m == null) return null;
        for (StandGuiEntry cat : categories()) {
            for (StandGuiEntry child : cat.children) {
                if (child.kind == StandGuiEntry.Kind.MODULE && child.module == m) return child;
            }
        }
        return null;
    }
}
