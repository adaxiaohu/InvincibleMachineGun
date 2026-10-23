package com.codigohasta.addon.utils.gcore;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 「本 tick 已发出的移动包数」计数器 —— 服务端 {@code i} 的客户端镜像。
 *
 * <h2>为什么需要它（本类存在的唯一理由）</h2>
 * 服务端 {@code ServerGamePacketListenerImpl#handleMovePlayer} 里：
 * <pre>
 * 1490: this.receivedMovePacketCount++;
 * 1491: int i = this.receivedMovePacketCount - this.knownMovePacketCount;  // ★ 本 tick 第几个包
 * 1495: if (i &gt; Math.max(this.allowedPlayerTicks, 5)) { i = 1; }          // ★ 超限 → 塌回
 * 1527: if (d7 - d6 &gt; Math.max(100.0, Mth.square(multiplier * i * speed)))
 * 1528:     → MOVED_TOO_QUICKLY → teleport(...) → return                  // ★ 整包作废
 * </pre>
 * 其中 {@code d7} 是**相对本 tick 起点**（{@code firstGoodX/Y/Z}）的平方距离。
 * 两个推论：
 * <ol>
 *     <li><b>第 k 个移动包最多能请求 10k 格</b>（阈值 {@code 100k²} 开方）；</li>
 *     <li><b>k &gt; 20 时服务端把 {@code i} 归 1</b>，阈值塌回 100 —— 此时哪怕只请求
 *         10 格也会被拒。</li>
 * </ol>
 *
 * <p><b>★ 关键事实</b>：{@code k} 是「本 tick <b>所有来源</b>加起来」的到达序号，
 * 不是「我自己发的第几个」。而本项目的 {@code GAntiLag} 会在**独立虚拟线程**
 * （{@code gcore-antilag}，见 {@code GAntiLag.java:288}）上抢发 6~8 个
 * {@code PositionAndOnGround} 包 —— 于是 TpAura / MaceDMGPlus 的垫包公式
 * （它们都假设自己从 {@code k=0} 开始）会把位移包顶到惩罚区。
 *
 * <p>实测证据（{@code docs/gcore/diag/module-attribution.txt}）：
 * <pre>
 *   GAntiLag前=0 : 上跳落在 k=17 → 通过
 *   GAntiLag前=8 : 上跳落在 k=25 → 26 个样本中 9 个落惩罚区
 * </pre>
 * 本文档 {@code 如来神掌发包-重审计与新方案.md} §三【BUG-1】有完整推导。
 *
 * <h2>语义与「谁在什么时候读」</h2>
 * 计数在**每个 {@code PlayerMoveC2SPacket} 发出时** +1，
 * 在 {@code TickEvent.Post} 的 **LOWEST** 优先级（= 所有模块的 tick 处理器都跑完之后）
 * 归零。于是模块在自己 {@code TickEvent.Post}（MEDIUM）里读到的值
 * = 「自上一条 tick 边界以来，别人（原版包 + GAntiLag）已发掉的移动包数」，
 * **不含本 tick 自己将要发的包**。这正是垫包公式需要的起点 {@code already}。
 *
 * <p><b>线程安全</b>：{@code GAntiLag} 从虚拟线程发、模块从 Render thread 发，
 * 所以用 {@link AtomicInteger}。
 *
 * <p><b>★ 已知残余误差（如实记录）</b>：{@code GAntiLag} 若恰好在「模块读取之后、
 * tick 边界归零之前」发包子，这批包会被计入随后又被清掉 —— 即**低概率漏算**。
 * 日志实测其包稳定排在最前（{@code tick=1291} 的 seq 0..7），
 * 且垫包公式用的是 {@code 9.9} 而非服务端的 {@code 10.0}
 * （自带约 1 个包的余量），可吸收该误差。
 */
public final class GcoreMoveBudget {

    /** 全局单例 —— 由 {@code AddonTemplate.onInitialize()} 订阅到事件总线。 */
    public static final GcoreMoveBudget INSTANCE = new GcoreMoveBudget();

    /**
     * 服务端 {@code allowedPlayerTicks} 的硬上限（见源码 1495 行）。
     *
     * <p>空包会把额度刷回 20（1500-1501 行），所以 {@code k} 最多涨到 20；
     * 第 21 个包起 {@code i} 被归 1。
     */
    public static final int DEFAULT_BUDGET = 20;

    private static final AtomicInteger SENT = new AtomicInteger();

    private GcoreMoveBudget() {
    }

    /**
     * 旁听**发出**的移动包。
     *
     * <p>统计 {@link PlayerMoveC2SPacket} 的**全部**子类
     * （{@code PositionAndOnGround} / {@code OnGroundOnly} / {@code Full} /
     * {@code LookAndOnGround}）—— 它们都走服务端 {@code handleMovePlayer}。
     *
     * <p>★ {@link VehicleMoveC2SPacket} 也**必须**计入：虽然它走的是
     * {@code handleMoveVehicle}（另一个方法），但该方法用的是**同一个**
     * {@code receivedMovePacketCount} / {@code knownMovePacketCount}
     * （服务端源码 {@code :649-650} 与玩家分支的 {@code :1490-1491} 是同一对字段）。
     * 漏掉它会让骑乘状态下的下标估算偏小。
     */
    @EventHandler(priority = EventPriority.LOWEST)
    private void onPacketSend(PacketEvent.Send event) {
        if (event.packet instanceof PlayerMoveC2SPacket
            || event.packet instanceof VehicleMoveC2SPacket) {
            SENT.incrementAndGet();
        }
    }

    /**
     * tick 边界归零。
     *
     * <p><b>为什么用 LOWEST</b>：orbit 按优先级降序调用，
     * LOWEST 保证它在所有模块的 {@code TickEvent.Post}（默认 MEDIUM）**之后**执行，
     * 从而「模块读到的值 = 别人已发的包」，而不是「别人 + 自己刚发的」。
     */
    @EventHandler(priority = EventPriority.LOWEST)
    private void onTickPost(TickEvent.Post event) {
        SENT.set(0);
    }

    /** 自上一条 tick 边界以来已发出的移动包数（= 服务端 {@code i} 的当前值，**未修正**）。 */
    public static int sentThisTick() {
        return SENT.get();
    }

    /**
     * ★ 供**垫包公式**使用的「别人已发数」—— 已扣除 {@link #TICK_BOUNDARY_BIAS}。
     *
     * <h3>为什么要减 1</h3>
     * 服务端的 {@code i = receivedMovePacketCount - knownMovePacketCount}，
     * 其中 {@code knownMovePacketCount} 在**服务端 tick 边界**更新
     * （{@code ServerGamePacketListenerImpl.java:472}）。
     * 而本计数器在**客户端 {@code TickEvent.Post}** 归零。
     * 两者相差半个 tick 时，那一条原版 {@code sendMovementPackets} 包会被计入
     * 「本 tick」，而服务端已把它归入**上一** tick ⇒ **我们多算 1**。
     *
     * <p>实测证据（{@code docs/gcore/diag/already-offbyone.txt}）：
     * <pre>
     *   debugLog 报「别人已发=1」共 456 次，但同一 tick 的 GAntiLag 实际发了 0 个包
     *   实测上跳下标 k 恒比 K+F+1 小 1  ⇒ 服务端认可的 already 是 0
     *   825 个上跳样本里 198 个因「垫包少发 1」而落到 k &lt; need（必被拒）
     * </pre>
     *
     * <h3>为什么宁可多发（减而不是加）</h3>
     * 把垫包式 {@code fillers = need - 1 - already} 代入预算式
     * {@code already + fillers + ownMoves ≤ 20}，得
     * <pre>
     *   need + ownMoves ≤ 21        ← already 被约掉了
     * </pre>
     * 即「能打到多高」**与别人发几个包无关**。于是两种偏差的代价不对称：
     * <ul>
     *     <li>少发垫包 → 上跳包被服务端拒绝（**致命**：无伤害 + 回弹 + 被抬到半空）</li>
     *     <li>多发垫包 → 多占 1 个预算（可承受，超限时由 {@code overBudgetMode} 兜底）</li>
     * </ul>
     * ⇒ **宁可多发**。所以这里的偏差方向是「减」。
     */
    public static int sentThisTickForBudget() {
        return Math.max(0, SENT.get() - TICK_BOUNDARY_BIAS);
    }

    /**
     * 客户端 tick 边界与服务端 tick 边界不同步造成的固定偏差。
     *
     * <p>实测为 1（见 {@link #sentThisTickForBudget()}）。做成常量便于以后按版本校正。
     */
    public static final int TICK_BOUNDARY_BIAS = 1;

    /** 距预算上限还剩几个包位（按修正后的值算）。 */
    public static int remaining(int budget) {
        return Math.max(0, budget - sentThisTickForBudget());
    }

    /** 手动归零（仅供离线测试/模块重置使用）。 */
    public static void reset() {
        SENT.set(0);
    }
}
