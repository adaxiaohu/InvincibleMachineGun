package com.codigohasta.addon.utils.gcore;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「天罗地网 / 困阵」的**分段诊断日志**。
 *
 * <h2>解决什么问题</h2>
 * 这两个模块有 **7 个静默 {@code return}** —— 任何一个触发，用户看到的现象都是
 * 「模块开着但什么都不做」，且日志里**没有任何线索**。曾因此只能靠 12MB 的
 * {@code gcore-servequiv.log} 反推，代价极高。
 *
 * <p>本类把每个阶段的实际结果写进 {@code gcore-webtrap.log}，一行一个阶段，
 * 于是「卡在哪一步」一眼可见。
 *
 * <h2>为什么用 {@code currentModule()} 取模块名</h2>
 * 模块内的代码都跑在
 * {@code try (var ignored = GcoreServerEquivMove.enter(MODULE_NAME))} 里，
 * 所以 {@link GcoreServerEquivMove#currentModule()} 已经是正确的模块名 ——
 * **共享工具类（{@code GcoreTpUtil}）也能因此自动带上调用方模块名**，
 * 不必层层传参。
 *
 * <h2>开关</h2>
 * 按**模块名**登记（不是单个布尔），所以两个模块各自有开关、互不干扰。
 * 见 {@link #setModuleDiag(String, boolean)}。
 */
public final class GcoreWebTrapDiag {

    private GcoreWebTrapDiag() {
    }

    /** 打开了诊断的模块名集合。 */
    private static final Set<String> ENABLED = ConcurrentHashMap.newKeySet();

    private static volatile BufferedWriter writer;
    private static volatile boolean started;

    /**
     * 按模块开关诊断。
     *
     * <p>用「集合」而不是一个布尔：两个模块各有自己的设置项，
     * 若共用一个布尔，关掉其中一个就会把另一个的日志也关掉。
     */
    public static void setModuleDiag(String module, boolean on) {
        if (module == null) {
            return;
        }
        if (on) {
            ENABLED.add(module);
        } else {
            ENABLED.remove(module);
        }
    }

    public static boolean isEnabled() {
        return !ENABLED.isEmpty();
    }

    /** 指定模块是否开了诊断。 */
    public static boolean isEnabledFor(String module) {
        return module != null && ENABLED.contains(module);
    }

    // ─────────────────────── 写日志 ───────────────────────

    /**
     * 记一个阶段。
     *
     * <p><b>模块名从 {@link GcoreServerEquivMove#currentModule()} 取</b> ——
     * 所以本方法**只能在 {@code enter(...)} 作用域内调用**。
     * 作用域外（例如模块的 {@code onTick} 里、进入虚拟线程之前）请用
     * {@link #logFor(String, String, String)} 显式传模块名，否则模块名会是 {@code ?}。
     *
     * <p><b>只查「本模块」是否开启</b>（不是「任一模块开启」）——
     * 早先的实现查的是 {@link #isEnabled()}，导致「只开 A 的日志，B 也往里写」。
     *
     * @param stage  阶段名（如「目标」「物品」「放置点」「站位」「执行」）
     * @param detail 结果（如「蛛网缺失」「tpVec=null 搜索失败」）
     */
    public static void log(String stage, String detail) {
        String module = GcoreServerEquivMove.currentModule();
        if (module == null) {
            // 不在 enter 作用域内 —— 无法判断属于哪个模块。
            // 不静默丢弃（那会丢证据），而是记成 "?"，调用点应改用 logFor 修正。
            if (!isEnabled()) {
                return;
            }
            write("?", stage, detail);
            return;
        }
        if (!isEnabledFor(module)) {
            return;
        }
        write(module, stage, detail);
    }

    /** 见 {@link #log}，支持 {@code String.format}。 */
    public static void logf(String stage, String fmt, Object... args) {
        String module = GcoreServerEquivMove.currentModule();
        if (module != null && !isEnabledFor(module)) {
            return;
        }
        if (module == null && !isEnabled()) {
            return;
        }
        log(stage, String.format(java.util.Locale.ROOT, fmt, args));
    }

    /** 见 {@link #log}，显式指定模块名（用于不在 {@code enter} 内的调用点）。 */
    public static void logFor(String module, String stage, String detail) {
        if (!isEnabledFor(module)) {
            return;
        }
        write(module, stage, detail);
    }

    /** 见 {@link #logFor}，支持 {@code String.format}。 */
    public static void logfFor(String module, String stage, String fmt, Object... args) {
        if (!isEnabledFor(module)) {
            return;
        }
        write(module, stage, String.format(java.util.Locale.ROOT, fmt, args));
    }

    private static synchronized void write(String module, String stage, String detail) {
        try {
            if (writer == null) {
                Path dir = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir();
                writer = Files.newBufferedWriter(
                    dir.resolve("gcore-webtrap.log"),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
            }
            if (!started) {
                started = true;
                writer.write("=== GcoreWebTrapDiag start " + System.currentTimeMillis() + " ===");
                writer.newLine();
                writer.write("# 格式: 时间 模块 | 阶段 | 结果");
                writer.newLine();
                writer.flush();
            }
            long ms = System.currentTimeMillis();
            writer.write(String.format(java.util.Locale.ROOT,
                "%d %s | %s | %s", ms, module, stage, detail));
            writer.newLine();
            writer.flush();
        } catch (Throwable ignored) {
            // 诊断绝不能影响游戏
        }
    }
}
