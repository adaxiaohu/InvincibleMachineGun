package com.codigohasta.addon.utils.img;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPosition;
import net.minecraft.entity.TrackedPosition;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionSyncS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ImgPositionPredict {

    /** 全局单例 —— 由 {@code AddonTemplate.onInitialize()} 订阅到事件总线。 */
    public static final ImgPositionPredict INSTANCE = new ImgPositionPredict();

    /** 每个实体最多保留多少条历史。 */
    public static final int MAX_HISTORY = 80;

    /**
     * 追踪表过期时间（tick）。超过这么久没有位置包就丢弃该实体的记录，
     * 避免实体卸载后 Map 无限增长。
     */
    private static final int TRACK_EXPIRE_TICKS = 400;

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    /** 预测模式。 */
    public enum Mode {
        /** 不外推，只返回「包里的当前位置」（仍然消掉了插值滞后）。 */
        NONE,
        /** 线性回归外推（对匀速直线目标最准）。 */
        LINEAR,
        /** 指数加权速度外推（对变速目标更跟手）。 */
        EXP_WEIGHTED,
        /**
         * ★ 旋转外推（对**绕圈 / 转弯**目标最准）。
         *
         * <p>LINEAR / EXP_WEIGHTED 都是**直线**外推，对「绕圈飞」的目标会系统性偏差：
         * 它们总往「速度方向的延长线」预测，而目标在转弯。
         *
         * <p>本模式把位移分解成「水平长度 + 水平方向角 + 竖直分量」，
         * 对**角度变化率**也做外推 ⇒ 能跟上圆弧运动。
         *
         * <p>思路参考 slimefunhelper 的 {@code MathUtils.RotationalPredictor}
         * （{@code MAX_TURN_ANGLE = 60°}、指数加权），但代码是 IMG 自己写的。
         */
        ROTATIONAL
    }

    /** 一个实体的追踪状态。 */
    private static final class Track {
        /** ★ 核心：按包里的 delta 自行推进的「服务端位置」。 */
        final TrackedPosition tracked = new TrackedPosition();
        /** 历史位置（带 tick 号），旧的在前。 */
        final Deque<Known> history = new ArrayDeque<>();
        /** 最后一次收到位置包的 tick。 */
        int lastPacketTick;
        /** 是否已经用第一个包初始化过基准位置。 */
        boolean initialized;
    }

    /** 一条历史记录。 */
    private record Known(Vec3d pos, int tick) {
    }

    private final Map<Integer, Track> tracks = new ConcurrentHashMap<>();

    /** 客户端 tick 计数器（用于给历史打时间戳 + 判断过期）。 */
    private int tick;

    private ImgPositionPredict() {
    }

    // ─────────────────────── ★ 按延迟自动算预判刻数 ───────────────────────

    /** 一 tick 的毫秒数（20 TPS）。 */
    public static final double MS_PER_TICK = 50.0;

    /**
     * ★ 按**往返延迟**自动算「该往前预测几个 tick」。
     *
     * <h3>为什么是「半个 RTT」</h3>
     * 我们的攻击包要飞 <b>1/2 RTT</b> 才到服务端，服务端才做距离判定。
     * 而目标在这段时间里又往前走了一段。所以要把「目标位置」外推到
     * <pre>
     *   未来 = 1/2 RTT 之后
     * </pre>
     * 才与服务端判定时刻对齐。
     *
     * <h3>为什么用「自己的」延迟而不是目标的</h3>
     * 服务端判定用的是<b>它自己</b>处理我们包的时刻；目标的位置也由服务端维护。
     * 所以真正影响「包到达时目标在哪」的是<b>我们这条链路的延迟</b>
     * （上行 + 服务端处理 + 下行广播到我们这里的观测量）。
     * 用 {@code mc.player} 的 tab 列表延迟即可。
     *
     * <h3>换算</h3>
     * <pre>
     *   ticks = round( (latency_ms / 2) / 50 )
     * </pre>
     * 取整到最近的 tick；结果钳制在 {@code [minTicks, maxTicks]}。
     *
     * @param minTicks 下限（一般给 0，即「只消插值滞后、不外推」）
     * @param maxTicks 上限（防止高延迟时外推过头）
     * @return 建议的预判刻数
     */
    public static int ticksFromPing(int minTicks, int maxTicks) {
        int latency = playerLatencyMs();
        if (latency <= 0) {
            // 拿不到延迟（单机 / 数据未到）→ 保守取 0：只消插值滞后，不外推
            return Math.max(0, Math.min(minTicks, maxTicks));
        }
        int ticks = (int) Math.round((latency / 2.0) / MS_PER_TICK);
        if (ticks < minTicks) {
            ticks = minTicks;
        }
        if (ticks > maxTicks) {
            ticks = maxTicks;
        }
        return ticks;
    }

    /**
     * 取本地玩家的 tab 列表延迟（毫秒）。
     *
     * @return 延迟毫秒；拿不到时返回 {@code -1}
     */
    public static int playerLatencyMs() {
        try {
            if (mc.player == null || mc.getNetworkHandler() == null) {
                return -1;
            }
            var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (entry == null) {
                return -1;
            }
            return entry.getLatency();
        } catch (Throwable ignored) {
            return -1;
        }
    }

    // ─────────────────────────── 事件入口 ───────────────────────────

    /**
     * 旁听**收到**的位置包，自己维护「服务端位置」。
     *
     * <p>★ 本事件在 <b>Netty 线程</b>触发，且<b>早于</b>实体被更新
     * （meteor 注入在 {@code handlePacket} 之前）。
     * 所以这里<b>不能</b>读 {@code entity.getEntityPos()} —— 那还是旧值。
     * 我们只用包里的 delta / 绝对坐标自行推进。
     */
    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.world == null) {
            return;
        }

        if (event.packet instanceof EntityS2CPacket packet) {
            // 增量包：位置 = 基准 + delta
            Track t = tracks.get(packet.id);
            if (t == null) {
                return;   // 没见过基准位置，等绝对坐标包
            }
            synchronized (t) {
                if (packet.isPositionChanged()) {
                    Vec3d pos = t.tracked.withDelta(
                        packet.getDeltaX(), packet.getDeltaY(), packet.getDeltaZ());
                    record(t, pos);
                }
            }
        } else if (event.packet instanceof EntityPositionS2CPacket packet) {
            // 绝对坐标包（大位移 / 传送）
            Track t = trackFor(packet.entityId());
            synchronized (t) {
                EntityPosition applied = EntityPosition.apply(
                    new EntityPosition(t.tracked.getPos(), Vec3d.ZERO, 0.0f, 0.0f),
                    packet.change(),
                    packet.relatives());
                t.tracked.setPos(applied.position());
                record(t, applied.position());
            }
        } else if (event.packet instanceof EntityPositionSyncS2CPacket packet) {
            // 同步包（绝对坐标）
            Track t = trackFor(packet.id());
            synchronized (t) {
                Vec3d pos = packet.values().position();
                t.tracked.setPos(pos);
                record(t, pos);
            }
        }
    }

    /**
     * tick 边界：推进 tick 号，并清理过期实体。
     *
     * <p>用 LOWEST 优先级 —— 与 {@code GcoreMoveBudget} 同款做法，
     * 保证在所有模块的 {@code TickEvent.Post}（默认 MEDIUM）<b>之后</b>执行。
     */
    @EventHandler(priority = EventPriority.LOWEST)
    private void onTickPost(TickEvent.Post event) {
        tick++;
        if (tracks.isEmpty() || tick % 100 != 0) {
            return;
        }
        Iterator<Map.Entry<Integer, Track>> it = tracks.entrySet().iterator();
        while (it.hasNext()) {
            Track t = it.next().getValue();
            boolean expired;
            synchronized (t) {
                expired = tick - t.lastPacketTick > TRACK_EXPIRE_TICKS;
            }
            if (expired) {
                it.remove();
            }
        }
    }

    /** 断开连接 / 切换世界时清空，防止跨世界串数据。 */
    public void clear() {
        tracks.clear();
        tick = 0;
    }

    // ─────────────────────────── 查询接口 ───────────────────────────

    /**
     * 预测实体 {@code ticksLater} 刻后的位置。
     *
     * <p>★ 缺数据时<b>回退</b>到实体当前位置（不返回 null）。
     *
     * @param entity     目标实体
     * @param ticksLater 预测多少刻之后（0 = 只要「包里的当前位置」，用于消掉插值滞后）
     * @param mode       外推算法
     */
    public Vec3d predict(Entity entity, int ticksLater, Mode mode) {
        if (entity == null) {
            return null;
        }
        Track t = tracks.get(entity.getId());

        // 没有任何位置包 → 回退到实体当前位置（行为与「无预判」一致）
        if (t == null) {
            return entity.getEntityPos();
        }

        Vec3d base;
        List<Vec3d> points;
        synchronized (t) {
            if (!t.initialized) {
                return entity.getEntityPos();
            }
            base = t.tracked.getPos();
            points = new ArrayList<>(t.history.size());
            for (Known k : t.history) {
                points.add(k.pos());
            }
        }

        if (ticksLater <= 0 || mode == Mode.NONE || points.size() < 2) {
            return base;
        }

        return switch (mode) {
            case LINEAR -> linear(points, ticksLater);
            case EXP_WEIGHTED -> expWeighted(points, ticksLater);
            case ROTATIONAL -> rotational(points, ticksLater);
            case NONE -> base;
        };
    }

    /** 见 {@link #predict(Entity, int, Mode)}，默认用线性外推。 */
    public Vec3d predict(Entity entity, int ticksLater) {
        return predict(entity, ticksLater, Mode.LINEAR);
    }

    /**
     * 「包里的当前位置」—— 已消掉插值滞后，但<b>不外推</b>。
     *
     * <p>用于索敌距离判定：比 {@code entity.getEntityPos()} 更接近服务端认知。
     */
    public Vec3d syncedPos(Entity entity) {
        return predict(entity, 0, Mode.NONE);
    }

    /** 是否已经收到过该实体的位置包（供诊断用）。 */
    public boolean isTracking(Entity entity) {
        if (entity == null) {
            return false;
        }
        Track t = tracks.get(entity.getId());
        if (t == null) {
            return false;
        }
        synchronized (t) {
            return t.initialized;
        }
    }

    /** 已知速度（每 tick 位移），供诊断用。没有数据时返回 {@link Vec3d#ZERO}。 */
    public Vec3d knownVelocity(Entity entity) {
        if (entity == null) {
            return Vec3d.ZERO;
        }
        Track t = tracks.get(entity.getId());
        if (t == null) {
            return Vec3d.ZERO;
        }
        synchronized (t) {
            if (t.history.size() < 2) {
                return Vec3d.ZERO;
            }
            Iterator<Known> it = t.history.descendingIterator();
            Known newest = it.next();
            Known second = it.next();
            int dt = newest.tick() - second.tick();
            Vec3d d = newest.pos().subtract(second.pos());
            return dt <= 0 ? d : d.multiply(1.0 / dt);
        }
    }

    // ─────────────────────────── 内部 ───────────────────────────

    private Track trackFor(int entityId) {
        return tracks.computeIfAbsent(entityId, k -> new Track());
    }

    /** 记录一条历史（去重 + 裁剪），必须在 {@code synchronized(t)} 内调用。 */
    private void record(Track t, Vec3d pos) {
        t.tracked.setPos(pos);
        t.initialized = true;
        t.lastPacketTick = tick;

        Known last = t.history.peekLast();
        // 去重：同一 tick 且位置相同则不重复记录
        if (last == null || last.tick() != tick || !last.pos().equals(pos)) {
            t.history.addLast(new Known(pos, tick));
        }
        while (t.history.size() > MAX_HISTORY) {
            t.history.removeFirst();
        }
    }

    // ─────────────────────── 外推算法（自己实现） ───────────────────────

    /**
     * 线性回归外推。
     *
     * <p>把历史点的时间轴设成 {@code -n+1 .. 0}（最后一点为 0），
     * 对 x / y / z 各做一次最小二乘，再取 {@code f(ticksLater)}。
     */
    private static Vec3d linear(List<Vec3d> points, int ticksLater) {
        int n = points.size();
        double sumX = 0, sumX2 = 0, sumYx = 0, sumYy = 0, sumYz = 0;
        double sumXYx = 0, sumXYy = 0, sumXYz = 0;
        for (int i = 0; i < n; i++) {
            double x = -n + 1 + i;          // 时间轴：最后一点 = 0
            Vec3d p = points.get(i);
            sumX += x;
            sumX2 += x * x;
            sumYx += p.x;
            sumYy += p.y;
            sumYz += p.z;
            sumXYx += x * p.x;
            sumXYy += x * p.y;
            sumXYz += x * p.z;
        }
        double denom = n * sumX2 - sumX * sumX;
        if (Math.abs(denom) < 1e-10) {
            return points.get(n - 1);
        }
        double ax = (n * sumXYx - sumX * sumYx) / denom;
        double ay = (n * sumXYy - sumX * sumYy) / denom;
        double az = (n * sumXYz - sumX * sumYz) / denom;
        double bx = (sumYx - ax * sumX) / n;
        double by = (sumYy - ay * sumX) / n;
        double bz = (sumYz - az * sumX) / n;
        return new Vec3d(
            ax * ticksLater + bx,
            ay * ticksLater + by,
            az * ticksLater + bz);
    }

    /**
     * 指数加权速度外推。
     *
     * <pre>
     *   diff = 相邻点之差（越新的越靠后）
     *   d = 0
     *   for v in diff: d = (d + v) * 0.5      ← 越早的权重越低
     *   return last + d * ticksLater
     * </pre>
     * 对「正在变速」的目标比线性回归更跟手。
     */
    private static Vec3d expWeighted(List<Vec3d> points, int ticksLater) {
        int n = points.size();
        if (n < 2) {
            return points.get(n - 1);
        }
        Vec3d d = Vec3d.ZERO;
        Vec3d prev = points.get(0);
        for (int i = 1; i < n; i++) {
            Vec3d cur = points.get(i);
            d = d.add(cur.subtract(prev)).multiply(0.5);
            prev = cur;
        }
        return points.get(n - 1).add(d.multiply(ticksLater));
    }

    /** 单步转向超过这个角度就不计入「平均转向率」（照 slimefunhelper 的 60°）。 */
    private static final double MAX_TURN_ANGLE = Math.toRadians(60.0);

    /**
     * ★ 旋转外推 —— 对**绕圈 / 转弯**目标最准。
     *
     * <h3>为什么需要它</h3>
     * {@link #linear} 和 {@link #expWeighted} 都是**直线**外推：
     * 它们把速度当作恒定的矢量，往「延长线」上预测。
     * 而绕圈飞的目标速度方向一直在变 ⇒ **系统性偏差**。
     *
     * <h3>做法</h3>
     * 把每步位移分解成三个标量序列：
     * <pre>
     *   水平长度 hLen = hypot(dx, dz)
     *   竖直分量 dY
     *   水平方向角 ang = atan2(dz, dx)
     * </pre>
     * 然后：
     * <ol>
     *     <li>对 {@code hLen} / {@code dY} 做指数加权（与 {@link #expWeighted} 同法）；</li>
     *     <li>对「相邻两步的角度差」求平均（**只统计 ≤ 60° 的**，滤掉瞬移/抖动）；</li>
     *     <li>最终方向角 = 最后方向 + 平均角速度 × ticksLater，再合成位移。</li>
     * </ol>
     *
     * <p>思路参考 slimefunhelper {@code MathUtils.RotationalPredictor}，
     * 但代码是 IMG 自己写的（不引用它的类）。
     */
    private static Vec3d rotational(List<Vec3d> points, int ticksLater) {
        int n = points.size();
        Vec3d current = points.get(n - 1);
        if (ticksLater <= 0) {
            return current;
        }
        if (n < 2) {
            return current;
        }

        // 每步的水平长度 / 竖直分量 / 方向角
        int steps = n - 1;
        double[] hLen = new double[steps];
        double[] dY = new double[steps];
        double[] ang = new double[steps];
        for (int i = 0; i < steps; i++) {
            Vec3d delta = points.get(i + 1).subtract(points.get(i));
            hLen[i] = Math.hypot(delta.x, delta.z);
            dY[i] = delta.y;
            ang[i] = Math.atan2(delta.z, delta.x);
        }

        // 只有 2 个点 ⇒ 退化成直线外推
        if (steps < 2) {
            Vec3d v = current.subtract(points.get(0));
            return current.add(v.multiply(ticksLater));
        }

        // ① 平均转向角速度（只统计 ≤ 60° 的相邻差，滤掉瞬移）
        double sumTurn = 0.0;
        int turnPairs = 0;
        for (int i = 1; i < steps; i++) {
            double d = ang[i] - ang[i - 1];
            while (d > Math.PI) d -= 2 * Math.PI;
            while (d < -Math.PI) d += 2 * Math.PI;
            if (Math.abs(d) <= MAX_TURN_ANGLE) {
                sumTurn += d;
                turnPairs++;
            }
        }
        double avgTurn = (turnPairs > 0) ? (sumTurn / turnPairs) : 0.0;

        // ② 水平长度 / 竖直分量的指数加权外推
        double predLen = expWeightedScalar(hLen);
        double predDY = expWeightedScalar(dY);
        if (predLen < 0.0) {
            predLen = 0.0;
        }

        // ③ 合成
        double finalAng = ang[steps - 1] + avgTurn * ticksLater;
        double dx = Math.cos(finalAng) * predLen;
        double dz = Math.sin(finalAng) * predLen;
        return current.add(dx, predDY, dz);
    }

    /**
     * 对一维序列做指数加权外推（与 {@link #expWeighted} 同一套权重）。
     *
     * <pre>
     *   d = 0
     *   for v in diff: d = (d + v) * 0.5
     *   return last + d * ticksLater
     * </pre>
     */
    private static double expWeightedScalar(double[] samples) {
        int n = samples.length;
        if (n == 0) {
            return 0.0;
        }
        if (n == 1) {
            return samples[0];
        }
        double d = 0.0;
        for (int i = 1; i < n; i++) {
            d = (d + (samples[i] - samples[i - 1])) * 0.5;
        }
        return samples[n - 1] + d;
    }

    /** 供诊断：当前追踪了多少个实体。 */
    public int trackedCount() {
        return tracks.size();
    }

    /** 供诊断：历史条数。 */
    public int historySize(Entity entity) {
        if (entity == null) {
            return 0;
        }
        Track t = tracks.get(entity.getId());
        if (t == null) {
            return 0;
        }
        synchronized (t) {
            return t.history.size();
        }
    }
}
