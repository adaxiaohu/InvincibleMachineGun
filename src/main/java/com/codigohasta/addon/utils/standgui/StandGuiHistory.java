package com.codigohasta.addon.utils.standgui;


import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 命令历史（用户 F6：存盘；G5：位置确认）。
 *
 * <h2>存储位置</h2>
 * <pre>
 *   &lt;游戏目录&gt;/meteor-client/stand-command-history.txt
 * </pre>
 * <p>与 Meteor 自己的配置同目录（{@code MeteorClient.FOLDER}），
 * 所以换客户端实例时历史跟着走，符合用户预期。
 *
 * <h2>为什么是纯文本一行一条</h2>
 * <p>历史是「仅供人查看/上下翻」的临时数据，不值得进 NBT：
 * 用纯文本可以直接用记事本看、手工删，且不会因为格式升级出兼容问题。
 * 上限 {@link #MAX} 条，FIFO（写盘时只保留最后 N 条）。
 *
 * <h2>★ 所有 IO 都吞异常</h2>
 * <p>历史读写失败绝不能让命令执行失败或让菜单崩 —— 它只是便利功能。
 */
public final class StandGuiHistory {

    /** 最多保留多少条。 */
    private static final int MAX = 100;

    /** 内存镜像（旧的在前，新的在后）。 */
    private static final List<String> ENTRIES = new ArrayList<>();

    private static boolean loaded;
    /** 浏览位置：-1 = 不在浏览历史（正在编辑新命令）。 */
    private static int cursor = -1;

    private StandGuiHistory() {}

    /** 历史文件路径。 */
    private static Path file() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir()
            .resolve("meteor-client").resolve("stand-command-history.txt");
    }

    /** 首次访问时从磁盘载入。 */
    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            Path p = file();
            if (!Files.exists(p)) return;
            for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                if (line == null) continue;
                String t = line.trim();
                if (!t.isEmpty()) ENTRIES.add(t);
            }
            while (ENTRIES.size() > MAX) ENTRIES.remove(0);
        } catch (Throwable ignored) {
            // 读不到就当空历史
        }
    }

    /** 记一条（执行成功后调用）。重复的相邻命令只留一条。 */
    public static void push(String cmd) {
        ensureLoaded();
        if (cmd == null) return;
        String t = cmd.trim();
        if (t.isEmpty()) return;
        if (!ENTRIES.isEmpty() && ENTRIES.get(ENTRIES.size() - 1).equals(t)) {
            cursor = -1;
            return;
        }
        ENTRIES.add(t);
        while (ENTRIES.size() > MAX) ENTRIES.remove(0);
        cursor = -1;
        save();
    }

    /**
     * 上翻一条（↑）。
     *
     * @return 要填进输入框的文本；没有更早的返回 null
     */
    public static String prev() {
        ensureLoaded();
        if (ENTRIES.isEmpty()) return null;
        if (cursor == -1) cursor = ENTRIES.size();       // 从"最新之后"开始
        if (cursor <= 0) return null;
        cursor--;
        return ENTRIES.get(cursor);
    }

    /**
     * 下翻一条（↓）。
     *
     * @return 要填进输入框的文本；已到最新时返回空串（表示清空输入）
     */
    public static String next() {
        ensureLoaded();
        if (ENTRIES.isEmpty() || cursor == -1) return null;
        cursor++;
        if (cursor >= ENTRIES.size()) {
            cursor = -1;
            return "";
        }
        return ENTRIES.get(cursor);
    }

    /** 退出浏览态（用户开始自己打字时调用）。 */
    public static void resetCursor() {
        cursor = -1;
    }

    /** 写盘（只保留最后 MAX 条）。 */
    private static void save() {
        try {
            Path p = file();
            Files.createDirectories(p.getParent());
            int from = Math.max(0, ENTRIES.size() - MAX);
            Files.write(p, ENTRIES.subList(from, ENTRIES.size()), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException ignored) {
            // 存不了就算了，不影响使用
        }
    }

    /** 全部历史（诊断用）。 */
    public static List<String> all() {
        ensureLoaded();
        return new ArrayList<>(ENTRIES);
    }
}
