package com.codigohasta.addon.utils.standgui;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 命令框的命令集与解析器。
 *
 * <h2>解析顺序（照 Meteor 自己的 {@code ClientPlayNetworkHandlerMixin:191-193}）</h2>
 * <pre>
 *   输入以 Meteor 前缀开头（默认 "."）  → Commands.dispatch(去掉前缀)   ← Meteor 命令
 *   输入以 "/" 开头                     → sendChatCommand(去掉"/")     ← 服务端指令
 *   其它                               → 查我们自己的命令表
 * </pre>
 *
 * <p><b>为什么照 Meteor 的判断</b>：Meteor 用的就是
 * {@code message.startsWith(Config.get().prefix.get())}，前缀可配置。
 * 我们把同一套规则搬过来，用户在命令框里写 {@code .vclip 5} 和聊天框里行为一致，
 * 学习成本为零。前缀被改成 {@code /} 时<b>前缀优先</b>（与 Meteor 一致）。
 *
 * <h2>命令的两层（用户 E1：甲 + 丙 组合）</h2>
 * <ul>
 *   <li><b>手写层（丙）</b>：{@code say} / {@code search} / {@code list} / {@code module}
 *       / {@code reset} / {@code default} / {@code pos} / {@code ping} / {@code help}</li>
 *   <li><b>自动层（甲）</b>：{@code <模块字段名> <设置字段名> <值>}
 *       —— 例 {@code tpanchoraura placedelay 100}</li>
 * </ul>
 * 自动层的命令名来自 {@link StandGuiEntry#commandName}（反射取的 Java 字段名），
 * 取不到英文名的设置<b>不生成命令</b>（用户 F3(a)）。
 */
public final class StandGuiCommands {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private StandGuiCommands() {}

    /** 执行结果。 */
    public record Result(boolean ok, String message) {
        public static Result ok(String m) { return new Result(true, m); }
        public static Result err(String m) { return new Result(false, m); }
    }

    /** 一条可提示的命令（用于补全与搜索）。 */
    public record Cmd(String name, String usage, String help) {}

    // ═══════════ 执行 ═══════════

    /**
     * 执行一行输入。
     *
     * @param input 用户输入的原始文本（不含命令框前缀）
     */
    public static Result run(String input) {
        if (input == null) return Result.err("空命令");
        String line = input.trim();
        if (line.isEmpty()) return Result.err("空命令");

        // ★ 先剥掉可选的前缀字符，再判断 `say /xxx` 这种组合。
        //   用户实测报的 bug：「say /命令 都只会发出普通聊天」——
        //   根因是 `say` 在 runOwn 里直接 sendChatMessage，把 `/xxx` 当普通文本发了。
        //   所以 `say` 必须把参数**重新交给**本方法（排除 say 自己，避免递归）。
        String[] head = line.split("\\s+", 2);
        if (head.length == 2 && head[0].equalsIgnoreCase("say")) {
            String payload = head[1].trim();
            if (payload.isEmpty()) return Result.err("用法: say <内容 或 /指令 或 .Meteor命令>");
            if (payload.startsWith("/") || startsWithMeteorPrefix(payload)) {
                String rest = payload;
                while (rest.startsWith("/")) rest = rest.substring(1);
                Result r = dispatchRaw(rest, payload.startsWith("/"));
                return r.ok() ? Result.ok("say: " + r.message()) : r;
            }
            mc.player.networkHandler.sendChatMessage(payload);
            return Result.ok("已发送聊天: " + payload);
        }

        return dispatchRaw(line, false);
    }

    /** 该文本是否以 Meteor 的命令前缀开头（前缀可被用户在配置里改）。 */
    private static boolean startsWithMeteorPrefix(String s) {
        String p = Config.get().prefix.get();
        return p != null && !p.isEmpty() && s.startsWith(p);
    }

    /**
     * 真正的分发：Meteor 前缀 → Meteor；{@code /} → 服务端；其它 → 我们自己的命令。
     *
     * @param forceVanilla true = 即使不带 {@code /} 也按服务端指令发（{@code say /xxx} 已剥过斜杠）
     */
    private static Result dispatchRaw(String line, boolean forceVanilla) {
        // ── 1. Meteor 前缀 → Meteor 命令 ──
        if (startsWithMeteorPrefix(line)) {
            String p = Config.get().prefix.get();
            try {
                meteordevelopment.meteorclient.commands.Commands.dispatch(
                    line.substring(p.length()));
                return Result.ok("已执行 Meteor 命令: " + line);
            } catch (Throwable t) {
                return Result.err("Meteor 命令失败: " + t.getMessage());
            }
        }

        // ── 2. "/" 或强制 → 服务端指令 ──
        if (forceVanilla || line.startsWith("/")) {
            String cmd = line.startsWith("/") ? line.substring(1) : line;
            try {
                if (mc.player != null && mc.player.networkHandler != null) {
                    mc.player.networkHandler.sendChatCommand(cmd);
                    return Result.ok("已发送指令: /" + cmd);
                }
                return Result.err("未连接服务器");
            } catch (Throwable t) {
                return Result.err("指令发送失败: " + t.getMessage());
            }
        }

        // ── 3. 我们自己的命令 ──
        try {
            return runOwn(line);
        } catch (Throwable t) {
            return Result.err("执行异常: " + t.getMessage());
        }
    }

    /** 解析我们自己的命令。 */
    private static Result runOwn(String line) {
        String[] parts = line.split("\\s+");
        String head = parts[0].toLowerCase(Locale.ROOT);

        switch (head) {
            case "say":
                // 不可达：say 已在 run() 里提前处理（它要判断参数是不是 / 或 Meteor 前缀）
                return Result.err("内部错误：say 应在 run() 中处理");
            case "list": {
                String cat = parts.length > 1 ? parts[1] : null;
                List<String> names = new ArrayList<>();
                for (Module m : Modules.get().getAll()) {
                    if (cat != null && !m.category.name.equalsIgnoreCase(cat)) continue;
                    names.add(m.title);
                }
                names.sort(String.CASE_INSENSITIVE_ORDER);
                if (names.isEmpty()) return Result.ok("没有模块");
                return Result.ok(names.size() + " 个: "
                    + String.join(", ", names.subList(0, Math.min(12, names.size())))
                    + (names.size() > 12 ? " …" : ""));
            }
            case "module": {
                if (parts.length < 3) return Result.err("用法: module <名> on/off/toggle");
                Module m = findModule(parts[1]);
                if (m == null) return Result.err("找不到模块: " + parts[1]);
                String op = parts[2].toLowerCase(Locale.ROOT);
                switch (op) {
                    case "on" -> { if (!m.isActive()) m.toggle(); }
                    case "off" -> { if (m.isActive()) m.toggle(); }
                    case "toggle" -> m.toggle();
                    default -> { return Result.err("只支持 on/off/toggle"); }
                }
                return Result.ok(m.title + " = " + (m.isActive() ? "开" : "关"));
            }
            case "reset": {
                if (parts.length < 2) return Result.err("用法: reset <模块名>");
                Module m = findModule(parts[1]);
                if (m == null) return Result.err("找不到模块: " + parts[1]);
                m.settings.reset();
                return Result.ok("已重置: " + m.title);
            }
            case "default": {
                for (Module m : Modules.get().getAll()) m.settings.reset();
                return Result.ok("已重置全部模块设置");
            }
            case "pos": {
                if (mc.player == null) return Result.err("不在世界中");
                // 1.21.11 没有 Entity#getPos()（已核对该版本的 API），项目统一用 getX/Y/Z
                return Result.ok(String.format("%.1f %.1f %.1f",
                    mc.player.getX(), mc.player.getY(), mc.player.getZ()));
            }
            case "ping": {
                if (mc.player == null || mc.getNetworkHandler() == null) return Result.err("不在服务器");
                var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
                return Result.ok("Ping: " + (entry == null ? "?" : entry.getLatency()) + " ms");
            }
            case "search": {
                String kw = parts.length > 1 ? line.substring(6).trim() : "";
                if (kw.isEmpty()) return Result.err("用法: search <关键词>");
                List<String> hits = new ArrayList<>();
                for (Cmd c : allCommands()) {
                    if (c.name.toLowerCase(Locale.ROOT).contains(kw.toLowerCase(Locale.ROOT))
                        || c.help.contains(kw)) {
                        hits.add(c.name);
                        if (hits.size() >= 12) break;
                    }
                }
                return hits.isEmpty() ? Result.ok("无匹配") : Result.ok("匹配: " + String.join(", ", hits));
            }
            case "help": {
                if (parts.length > 1) {
                    for (Cmd c : allCommands()) {
                        if (c.name.equalsIgnoreCase(parts[1])) {
                            return Result.ok(c.usage + "  —  " + c.help);
                        }
                    }
                    return Result.err("没有该命令: " + parts[1]);
                }
                return Result.ok("命令: say / search / list / module / reset / default / pos / ping / help"
                    + "，以及 <模块> <设置> <值>");
            }
            default:
                return runAutoPath(parts, line);
        }
    }

    /**
     * 自动层：{@code <模块字段名> <设置字段名> <值>}。
     *
     * <p>也接受「不带值」的形式：{@code <模块> <设置>} → 只回显当前值。
     */
    private static Result runAutoPath(String[] parts, String line) {
        if (parts.length < 2) return Result.err("未知命令: " + parts[0]);

        Module m = findModuleByFieldName(parts[0]);
        if (m == null) return Result.err("未知命令: " + parts[0]);

        Setting<?> s = findSettingByFieldName(m, parts[1]);
        if (s == null) return Result.err(m.title + " 没有设置: " + parts[1]);

        if (parts.length < 3) {
            return Result.ok(m.title + " > " + s.title + " = " + StandGuiTree.suffixOf(s));
        }

        String value = line.substring(line.indexOf(parts[1]) + parts[1].length()).trim();
        boolean ok = s.parse(value);
        return ok
            ? Result.ok(m.title + " > " + s.title + " = " + StandGuiTree.suffixOf(s))
            : Result.err("值不合法: " + value);
    }

    // ═══════════ 查找 ═══════════

    /** 按显示名或内部 name 找模块（大小写不敏感）。 */
    private static Module findModule(String key) {
        for (Module m : Modules.get().getAll()) {
            if (m.title.equalsIgnoreCase(key) || m.name.equalsIgnoreCase(key)) return m;
        }
        return null;
    }

    /** 按 Java 字段名找模块（自动层用）。 */
    private static Module findModuleByFieldName(String key) {
        for (Module m : Modules.get().getAll()) {
            if (fieldNameOfModule(m).equalsIgnoreCase(key)) return m;
        }
        return null;
    }

    /** 模块自身的 Java 类名（去掉包名并小写）作为命令名。 */
    private static String fieldNameOfModule(Module m) {
        String n = m.getClass().getSimpleName();
        return n.toLowerCase(Locale.ROOT);
    }

    /** 按 Java 字段名找设置（自动层用）。 */
    private static Setting<?> findSettingByFieldName(Module m, String key) {
        for (var group : m.settings) {
            for (Setting<?> s : group) {
                if (s.title.equalsIgnoreCase(key)) return s;
            }
        }
        // 字段名匹配：走 StandGuiTree 的反射助手（与树构建同源）
        return StandGuiTree.settingByFieldName(m, key);
    }

    // ═══════════ 补全数据 ═══════════

    /** 全部可提示命令（手写 + 自动），用于补全与 search。 */
    public static List<Cmd> allCommands() {
        Set<Cmd> out = new LinkedHashSet<>();

        out.add(new Cmd("say", "say <内容>", "发送一条聊天消息"));
        out.add(new Cmd("search", "search <关键词>", "搜索命令与模块"));
        out.add(new Cmd("list", "list [分类]", "列出模块"));
        out.add(new Cmd("module", "module <名> on/off/toggle", "开关模块"));
        out.add(new Cmd("reset", "reset <模块名>", "重置某模块的全部设置"));
        out.add(new Cmd("default", "default", "重置全部模块设置"));
        out.add(new Cmd("pos", "pos", "显示当前坐标"));
        out.add(new Cmd("ping", "ping", "显示延迟"));
        out.add(new Cmd("help", "help [命令]", "命令帮助"));

        // 自动层：每个有英文名的设置生成一条
        for (Module m : Modules.get().getAll()) {
            String mn = fieldNameOfModule(m);
            for (var group : m.settings) {
                for (Setting<?> s : group) {
                    String fn = StandGuiTree.fieldNameOf(m, s);
                    if (fn == null || fn.isEmpty()) continue;   // F3(a)：没有英文名就不生成
                    out.add(new Cmd(mn + " " + fn,
                        mn + " " + fn + " <值>",
                        m.title + " > " + s.title));
                }
            }
        }
        return new ArrayList<>(out);
    }

    // ═══════════ 缓存（★ 性能修复）═══════════
    //
    // 用户实测：「打开这个 stand命令框 变卡了 fps降低了很多」。
    // 根因：命令框每帧都会调 suggestions() → allCommands()，而后者要遍历
    // 【全部模块 × 全部设置 ≈ 2000+ 项】并且**每项都做一次反射**（fieldNameOf），
    // 再重建整个 Meteor 控件树（verticalList.clear() + 重新 add）。
    // 每帧数千次反射 + 整棵 GUI 重建 = 必然掉帧。
    //
    // 修复分两步：
    //   1. 【这里】全量命令表只建一次，用「模块数量」做失效键
    //      （模块集合变化才会变；设置值的变化不影响命令名的集合）
    //   2. 【命令框】建议列表只在输入文本变化时重建，且复用已有控件

    /** 全量命令表缓存。 */
    private static List<Cmd> cachedAll;
    /** 建缓存时的模块数量（用于失效）。 */
    private static int cachedModuleCount = -1;

    /**
     * 补全：按前缀筛选命令名（走缓存，O(命令数)）。
     *
     * <p>支持「按词补全」：输入 {@code "tpanchoraura pl"} 时，
     * 先用第一段匹配命令名、再用第二段过滤，这样才符合「打一半就想补全」的直觉。
     */
    public static List<Cmd> suggestions(String input, int limit) {
        String q = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            // 空输入：给出常用命令（便于用户知道有什么）
            List<Cmd> out = new ArrayList<>();
            for (Cmd c : allCommandsCached()) {
                if (!c.name.contains(" ")) out.add(c);
                if (out.size() >= limit) break;
            }
            return out;
        }

        List<Cmd> out = new ArrayList<>();
        for (Cmd c : allCommandsCached()) {
            if (matchesCommand(c.name, q)) {
                out.add(c);
                if (out.size() >= limit) break;
            }
        }
        return out;
    }

    /**
     * 命令名是否匹配输入。
     *
     * <pre>
     *   "say"           匹配 "say"            （前缀）
     *   "say h"         匹配 "say &lt;内容&gt;"     （第一词命中 + 后续随便）
     *   "tpanchoraura p"匹配 "tpanchoraura placedelay"（逐词前缀）
     * </pre>
     */
    private static boolean matchesCommand(String name, String query) {
        if (name.toLowerCase(Locale.ROOT).startsWith(query)) return true;

        String[] nParts = name.toLowerCase(Locale.ROOT).split("\\s+");
        String[] qParts = query.split("\\s+", 2);
        if (nParts.length == 0) return false;

        // 第一词必须命中
        if (!nParts[0].equals(qParts[0]) && !nParts[0].startsWith(qParts[0])) return false;
        if (qParts.length == 1) return true;

        // 有多词输入时，剩余部分整体匹配后半段
        String rest = name.toLowerCase(Locale.ROOT).substring(nParts[0].length()).trim();
        return rest.startsWith(qParts[1]);
    }

    /** 带缓存的 {@link #allCommands()}。 */
    public static synchronized List<Cmd> allCommandsCached() {
        int now = Modules.get().getAll().size();
        if (cachedAll != null && now == cachedModuleCount) return cachedAll;
        cachedAll = allCommands();
        cachedModuleCount = now;
        return cachedAll;
    }

    /** 清缓存（模块热重载/开发期用）。 */
    public static synchronized void invalidate() {
        cachedAll = null;
        cachedModuleCount = -1;
    }

    /** 诊断。 */
    public static String describe() {
        return "commands=" + allCommandsCached().size();
    }
}
