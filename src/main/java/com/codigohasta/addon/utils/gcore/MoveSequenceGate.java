package com.codigohasta.addon.utils.gcore;

/**
 * 「抑制下一条原版移动包」的**独立**开关（不依赖 slimefunhelper）。
 *
 * <h2>为什么需要它</h2>
 * 服务端 {@code ServerGamePacketListenerImpl.handleMovePlayer} 的「第几个包」判据
 * {@code i = receivedMovePacketCount - knownMovePacketCount} 是<b>全 tick 所有来源累计</b>的：
 * <pre>
 *   i = receivedMovePacketCount - knownMovePacketCount;   // :1491
 *   if (i &gt; max(allowedPlayerTicks, 5)) i = 1;            // :1495 超限 → 阈值塌回 100
 *   阈值 = max(100, (10 · i · speed)²)                     // :1527
 * </pre>
 * 所以「垫包够不够」取决于<b>服务端眼里本 tick 一共收到几个包</b>。
 *
 * <p>原版 {@code ClientPlayerEntity.sendMovementPackets} 每 tick 发 1~2 个移动包
 * （本项目日志里是 {@code class_2830/2831}）。它在网络流里的位置是
 * 「上一条我们的序列之后、下一条我们的序列之前」：
 * <pre>
 *   … S(N) , V(N+1) , S(N+1) , V(N+2) , S(N+2) …
 *              ↑ 它就是污染 S(N+1) 的那个包（让我们的首个垫包变成 i=2 而不是 1）
 * </pre>
 * 因为模块在 {@code TickEvent.Post}（= 客户端 tick 的 TAIL）发包，
 * 而 {@code sendMovementPackets} 在该 tick 的 <b>player 阶段</b>（更早）执行，
 * 所以「在 Post 阶段置位 → 抑制下一条 V」在时序上正好成立。
 *
 * <h2>时序（实测依据）</h2>
 * {@code gcore-tp-diag.log} 的 tick=182 显示包序为
 * {@code 垫包1-8 → 位移9-12 → class_2831(13)}：
 * 那条 {@code class_2831} 其实属于<b>下一个</b>客户端 tick 的 player 阶段
 * （诊断的 tickCounter 在 Post 阶段自增，所以把它记成了本 tick）。
 *
 * <h2>手法来源</h2>
 * 照 slimefunhelper {@code ClientPlayerMixin.sf$packets}（它改
 * {@code sendMovementPackets} 里 {@code isCamera()} 的返回值让方法提前返回），
 * 但<b>不 import 也不调用</b> slimefunhelper 的任何东西 —— 完全独立实现。
 *
 * <h2>★ 安全（两个防线）</h2>
 * <ol>
 *     <li><b>一次性</b>：{@link #consumeSuppress()} 被查询时立即清除 ——
 *         一次置位只抑制<b>一条</b> {@code sendMovementPackets} 调用，
 *         绝不会连续抑制；</li>
 *     <li><b>每 tick 兜底清空</b>：模块每个 tick 重新决定「要不要置位」，
 *         且停用时显式 {@link #disarm()}。任何异常路径都不会导致原版移动包
 *         永久停发（那会让玩家彻底无法移动）。</li>
 * </ol>
 *
 * <p>用 {@link ThreadLocal} 而非静态字段：即使将来有代码从别的线程触碰发包路径，
 * 也不会互相污染（见 {@code img-lessons} 坑 47）。
 */
public final class MoveSequenceGate {

    /** 本线程是否已置位「抑制下一条原版移动包」。 */
    private static final ThreadLocal<Boolean> ARMED =
        ThreadLocal.withInitial(() -> Boolean.FALSE);

    /**
     * 置位：抑制**下一条**原版移动包。
     *
     * <p>由模块在 {@code TickEvent.Post} 阶段调用（见类注释的时序推导）。
     */
    public static void arm() {
        ARMED.set(Boolean.TRUE);
    }

    /** 解除置位（模块停用 / 关闭该功能时调用）。 */
    public static void disarm() {
        ARMED.remove();
    }

    /**
     * 查询并**消费**置位 —— 由 mixin 在 {@code sendMovementPackets} 里调用。
     *
     * <p>一次性语义：返回 {@code true} 的同时清除标志，
     * 保证一次 {@link #arm()} 只抑制一条调用。
     *
     * @return {@code true} = 本次应抑制（不发原版移动包）
     */
    public static boolean consumeSuppress() {
        if (Boolean.TRUE.equals(ARMED.get())) {
            ARMED.remove();
            return true;
        }
        return false;
    }

    /** 当前是否已置位（仅供诊断/断言，**不消费**）。 */
    public static boolean isArmed() {
        return Boolean.TRUE.equals(ARMED.get());
    }

    private MoveSequenceGate() {
    }
}
