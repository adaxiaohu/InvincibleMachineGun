package com.codigohasta.addon.utils.sakura.render;

import java.util.ArrayDeque;
import java.util.Deque;

public final class TpsSampler {

    public static final TpsSampler INSTANCE = new TpsSampler();

    /** 样本窗口（保留最近 N 个 tick 间隔）。 */
    private static final int WINDOW = 20;

    /** 相邻 tick 间隔（毫秒）。 */
    private final Deque<Long> intervals = new ArrayDeque<>();

    private long lastTickMs = 0L;

    /** 最近一次 tick 的世界时间（毫秒）。 */
    private long lastSampleMs = 0L;

    /** 缓存的即时 TPS。 */
    private volatile float instantTps = 20f;

    /** 缓存的平均 TPS。 */
    private volatile float averageTps = 20f;

    /**
     * 即时 TPS 的平滑缓存。
     *
     * <p><b>为什么要平滑（用户 2026-10-07 反馈）</b>：
     * 「tps 的第二个数据变化太快了」—— 第二个数值是即时 TPS，
     * 它直接取<b>最近一次</b> tick 间隔（{@code 1000 / 间隔}）。
     * 单个 tick 的间隔抖动很大（49ms / 51ms 交替 → 20.4 / 19.6 来回跳），
     * 读数根本看不清。
     *
     * <p><b>做法</b>：照 FpsHud 的做法做指数平滑（{@code lerp}），
     * 取 {@link #INSTANT_SMOOTH} = 0.35 —— 既跟得上真实变化，
     * 又不会被单帧抖动主导。
     */
    private static final float INSTANT_SMOOTH = 0.35f;
    private boolean instantInit = false;

    private TpsSampler() {}

    /**
     * 每个客户端 tick 调用一次。
     *
     * <p>用 {@code System.nanoTime()} 而不是 world time ——
     * 后者会被服务端时间同步影响，测不出「本地收到 tick 的间隔」。
     */
    public synchronized void onTick() {
        long now = System.currentTimeMillis();
        if (lastTickMs != 0L) {
            long dt = now - lastTickMs;
            // 只接受合理区间（20 TPS = 50ms；超过 1s 视为切维度/卡顿，丢弃）
            if (dt > 0 && dt < 1000) {
                intervals.addLast(dt);
                while (intervals.size() > WINDOW) intervals.removeFirst();
            }
        }
        lastTickMs = now;
        lastSampleMs = now;
        recompute();
    }

    /** 重算即时/平均 TPS。 */
    private void recompute() {
        if (intervals.isEmpty()) return;
        long sum = 0;
        for (long v : intervals) sum += v;
        float avgMs = (float) sum / intervals.size();
        if (avgMs < 1f) avgMs = 1f;
        // 照 nf.g: 1000 / max(平均间隔, 1)
        averageTps = clamp(1000f / avgMs);

        // 即时值：最近一次间隔，再做指数平滑（否则单 tick 抖动会让读数十位跳）
        float rawInstant = clamp(1000f / Math.max(1f, intervals.peekLast()));
        if (!instantInit) {
            instantTps = rawInstant;
            instantInit = true;
        } else {
            instantTps = instantTps + (rawInstant - instantTps) * INSTANT_SMOOTH;
        }
    }

    private static float clamp(float v) {
        if (v < 0f) return 0f;
        if (v > 20f) return 20f;
        return v;
    }

    /** 即时 TPS（最近一次间隔）。 */
    public float instant() {
        return instantTps;
    }

    /** 平均 TPS（最近 WINDOW 次的均值）。 */
    public float average() {
        return averageTps;
    }

    public synchronized boolean hasEnoughSamples() {
        return intervals.size() >= Math.min(5, WINDOW);
    }

    /** 距上次采样过了多久（毫秒）—— 用于判断「服务器没在发 tick」。 */
    public long sinceLastSampleMs() {
        return lastSampleMs == 0L ? Long.MAX_VALUE : System.currentTimeMillis() - lastSampleMs;
    }

    /** 清空（换服务器时）。 */
    public synchronized void reset() {
        intervals.clear();
        lastTickMs = 0L;
        lastSampleMs = 0L;
        instantTps = 20f;
        averageTps = 20f;
        instantInit = false;
    }
}
