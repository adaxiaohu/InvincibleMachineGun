package com.codigohasta.addon.utils.gcore;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * 按 slimefunhelper 的 <b>token（令牌）模型</b>发送移动包序列，支持<b>跨 tick 续传</b>。
 *
 * <p>移植自 {@code slimefunhelper.MovTasks.createMovingPacketsForMovSequence}
 * （源 294-516 行）＋ {@code scheduleFarawayMoveInternal}（源 572-601 行）＋
 * {@code MovingContext}（源 859-876 行）。
 *
 * <h2>为什么需要它</h2>
 * 服务端 {@code ServerGamePacketListenerImpl.handleMovePlayer} 对<b>同一个 tick</b> 内的
 * 位移做【累加】判定（{@code firstGoodX/Y/Z} 每 tick 才由 {@code tickPlayer()} 重置）：
 * <pre>
 *   i = receivedMovePacketCount - knownMovePacketCount;   // 本 tick 已收包数
 *   if (i &gt; max(allowedPlayerTicks, 5)) i = 1;            // ★ 超限 → i 归 1（阈值塌回 100）
 *   if (!hasRot &amp;&amp; !(d7 &gt; 0)) allowedPlayerTicks = 20;    // 无位移包 → 重置额度
 *   else                       allowedPlayerTicks--;
 *   阈值 = max(100, (movedTooQuicklyMultiplier · i · speed)²)   // 本服 = 100·i²
 *   拒绝: d7 - d6 &gt; 阈值  →  teleport(服务端当前位置) 拉回
 * </pre>
 *
 * <p>其中 {@code d7} 相对本 tick 起点，所以「第 k 个包要走到高度 h」必须满足
 * {@code h ≤ 10 · i}。要一次跳很高，就必须先用<b>无位移的空包</b>把 {@code i} 顶上去
 * —— 这就是 slimefunhelper 的「gain tokens」（源 333-382 行）。
 *
 * <h2>为什么还要跨 tick</h2>
 * 空包能把 {@code allowedPlayerTicks} 重置为 20，但 {@code i} 会一直被计入，
 * 所以「垫包 + 位移包」总数不能超过 20。超过时 slimefunhelper <b>不硬发</b>，
 * 而是把剩余动作 {@code Tasks.scheduleDelayed(..., 1)} 排到下一 tick，并
 * {@code context.resetTick()} 重置本 tick 状态（源 585-600 行的逻辑）。
 *
 * <p>本类把「排到下一 tick」简化为「返回剩余列表，由调用方在下一 tick 继续」——
 * 因为 Meteor 模块本来就有 {@code onTick} 钩子，不需要额外的调度器。
 *
 * <h2>★ 线程约束</h2>
 * 所有方法都会触碰真实玩家并发包，<b>只能在客户端主线程调用</b>。
 */
public final class GcoreMoveSequenceUtil {

    private GcoreMoveSequenceUtil() {
    }

    /** 单包安全位移（照 slimefunhelper 源 351/393 行的 9.9 / 9.8）。 */
    private static final double TOKEN_STEP_FIRST = 9.9;
    private static final double TOKEN_STEP = 9.8;

    /**
     * ★★ 服务端「单包位移倍率」的默认值 —— 服务端真实规则里的 {@code 10.0}。
     *
     * <p>来源（{@code ServerGamePacketListenerImpl:1527}）：
     * <pre>
     *   if (d7 - d6 &gt; Math.max(f2, Mth.square(SpigotConfig.movedTooQuicklyMultiplier * i * speed)))
     *   speed = walkingSpeed × 10 = 1.0
     *   ⇒ 阈值 = (multiplier × i)²  ⇒ 位移 ≤ multiplier × i
     * </pre>
     * Spigot 默认 {@code moved-too-quickly-multiplier: 10.0} ⇒ **位移 ≤ 10 × i**。
     *
     * <p>服务器若调大了它（或关掉了该检查），把模块的「服务端单包位移倍率」
     * 一起调大即可 —— 否则本类会在**规划阶段**误拒服务端本会接受的序列。
     */
    public static final double SERVER_STEP_MULT = 10.0;

    /**
     * 服务端 {@code allowedPlayerTicks} 的上限（源 369 行的 20）。
     *
     * <p>★ 现在只是**默认值** —— 实际上限由模块的「移动包上限」设置传入
     * （见 {@link Context#resetTick(Vec3d, int)}）。保留常量是为了兼容
     * 旧调用点与 {@link #DEFAULT_LIMIT}。
     */
    public static final int TOKEN_LIMIT_MAX = 20;

    /** 见 {@link #TOKEN_LIMIT_MAX}。 */
    public static final int DEFAULT_LIMIT = TOKEN_LIMIT_MAX;

    /** 一次发送的结果。 */
    public static final class Result {
        /** 已发出的动作数。 */
        public final int sent;
        /** 是否全部发完。 */
        public final boolean complete;
        /** 未发完的剩余动作（下一 tick 继续）；已发完则为空列表。 */
        public final List<Vec3d> remaining;

        Result(int sent, boolean complete, List<Vec3d> remaining) {
            this.sent = sent;
            this.complete = complete;
            this.remaining = remaining;
        }
    }

    /**
     * 跨 tick 的发送状态 —— 对应 slimefunhelper 的 {@code MovingContext}。
     *
     * <p>{@code tickFirstGoodVec} 对应服务端的 {@code firstGoodX/Y/Z}（本 tick 起点），
     * {@code tokenInTick} 对应服务端的 {@code i}，{@code tokenLimit} 对应
     * {@code allowedPlayerTicks}。三个量都必须由本类维护，因为客户端拿不到服务端的值。
     */
    public static final class Context {
        /** 本 tick 起点（= 服务端 firstGood）。 */
        private Vec3d tickFirstGood;
        /** 本序列**自己**已发的动作数（**不含**别人发的包）。 */
        private int tokenInTick;
        /** 剩余允许包数（= 服务端 allowedPlayerTicks）。 */
        private int tokenLimit;
        /** 上一包位置（用于 d7 的「相对上一包」项）。 */
        private Vec3d prev;

        /**
         * ★ 本 tick **别人**（GAntiLag / 原版）已发出的移动包数。
         *
         * <p>这就是服务端 {@code i} 在本序列第一包到达时的**起始值**。
         * slimefunhelper 的原始实现把它当 0（它假设世界里只有自己发包），
         * 而本项目的 {@code GAntiLag} 会在独立虚拟线程上抢发 6~8 个 ——
         * 不扣掉它，位移包就会被顶进惩罚区（{@code i > 20} → {@code i = 1}）。
         * 见 {@link GcoreMoveBudget} 的类注释与重审计文档 §三【BUG-1】。
         */
        private int alreadyInTick;

        /** ★ 本 tick 的**总**预算（服务端 1495 行的 {@code max(allowedPlayerTicks,5)}）。 */
        private int limit;

        /**
         * ★ 单包位移参考值（服务端阈值 = 10·下标）。
         *
         * <p>来源是模块的「单包位移」设置。默认 {@link #TOKEN_STEP_FIRST}（9.9），
         * 比服务端的 10 略保守 —— 留一点余量吸收浮点与 tick 边界误差。
         *
         * <p>它同时决定「需要多少下标」（{@code ceil(高度 / 本值)}），
         * 所以调小它会让垫包变多（更保守），调大会让垫包变少（更激进）。
         */
        private double step = TOKEN_STEP_FIRST;

        /**
         * 是否把「别人已发的包」计入下标（默认 true）。
         *
         * <p>设为 false 即**逐字退回修复前的行为** —— 供 A/B 对照实验使用
         * （{@code img-diagnose-loop} 阶段 4：修复必须可开关）。
         */
        private boolean countOthers = true;

        /**
         * ★ 「别人已发数」采样的**保守偏差** —— 采样值会减去它再用于垫包公式。
         *
         * <p>默认 {@link GcoreMoveBudget#TICK_BOUNDARY_BIAS}（= 1），
         * 来源是「客户端 tick 边界 ≠ 服务端 tick 边界」造成的固定多算 1。
         *
         * <p>方向永远是「减」（= 少算别人、多发垫包）。理由见
         * {@link GcoreMoveBudget#sentThisTickForBudget()}：少发垫包会让上跳包被
         * 服务端拒绝（致命），多发只多占一个预算（可承受）。
         *
         * <p>设为 0 即逐字退回「直接用实测计数」的旧行为，供 A/B 对照。
         */
        private int bias = GcoreMoveBudget.TICK_BOUNDARY_BIAS;

        /**
         * ★ 垫包数**下限** —— token 模型算出的最小垫包数若小于它，就按它发。
         *
         * <p><b>为什么需要</b>：{@code GAntiLag} 跑在独立虚拟线程上，
         * 「我们采样别人已发数」与「我们真正发包」之间存在时间差。
         * 若它恰好在这段缝隙里又发了一个包，我们的下标就会被推后一格，
         * 而该下标对应的阈值可能刚好不够。多发几个空包即可覆盖这个缝隙。
         *
         * <p>若加了下限会**超出预算**，则自动退回不加下限的版本
         * （下限是可选的保守措施，不应当导致本次序列完全发不出去）。
         */
        private int minFillers = 0;

        /**
         * ★★ 服务端「单包位移倍率」—— 服务端真实规则里的
         * {@code SpigotConfig.movedTooQuicklyMultiplier}，默认 {@link #SERVER_STEP_MULT}（10.0）。
         *
         * <p>服务端判据 {@code 位移 ≤ 倍率 × i}。默认 10.0 时等价于「单包位移 ≤ 10·i」。
         *
         * <p><b>为什么必须能调</b>：有些服务器把 {@code moved-too-quickly-multiplier}
         * 调大（或关掉该检查）。若本类仍按 10.0 判，就会在**规划阶段**把服务端
         * 本会接受的序列误拒（{@code planLadder} 返回 null ⇒ 一个包都不发）。
         *
         * <p>与 {@link #step} 的区别：{@code step} 是「我们**期望**一个包走多远」
         * （用于算垫包数，默认 9.9 略保守）；本值是「服务端**允许**一个包走多远」。
         * 两者一起决定序列能否发出。
         */
        private double serverStepMult = SERVER_STEP_MULT;

        public Context(Vec3d from) {
            this(from, TOKEN_LIMIT_MAX);
        }

        public Context(Vec3d from, int limit) {
            this.limit = Math.max(5, limit);
            resetTick(from);
            this.prev = from;
            this.tokenLimit = this.limit;
        }

        /** 新的一 tick：重置 i 与起点（照 slimefunhelper 的 {@code resetTick}）。 */
        public void resetTick(Vec3d from) {
            resetTick(from, this.limit);
        }

        /**
         * 见 {@link #resetTick(Vec3d)}，可同时更新预算上限。
         *
         * <p>★ 在「本 tick 尚未由本序列发包」的时刻采样别人已发的包数。
         * 采样值会扣除 {@link #bias}（默认 {@link GcoreMoveBudget#TICK_BOUNDARY_BIAS}），
         * 即「宁可多发垫包、不可少发」—— 详见
         * {@link GcoreMoveBudget#sentThisTickForBudget()} 的代数推导。
         */
        public void resetTick(Vec3d from, int limit) {
            this.limit = Math.max(5, limit);
            this.tokenLimit = this.limit;
            this.tickFirstGood = from;
            this.prev = from;
            this.alreadyInTick = this.countOthers ? sampleAlready() : 0;
        }

        /** 采样「别人已发数」并应用 {@link #bias}。 */
        private int sampleAlready() {
            return Math.max(0, GcoreMoveBudget.sentThisTick() - this.bias);
        }

        /**
         * 关闭「计入他人已发包」—— 逐字退回修复前行为，仅用于 A/B 对照。
         *
         * <p>会立即重采样一次，使当前 tick 就生效。
         */
        public void setCountOthers(boolean countOthers) {
            this.countOthers = countOthers;
            this.alreadyInTick = countOthers ? sampleAlready() : 0;
        }

        /** 见 {@link #setCountOthers(boolean)}。 */
        public boolean countOthers() {
            return this.countOthers;
        }

        /**
         * 见 {@link #bias}。
         *
         * <p>设 0 = 逐字退回「直接使用实测计数」的旧行为（A/B 对照用）。
         *
         * <p>★ 若本序列**尚未发过包**（{@code tokenInTick == 0}），会立即重采样，
         * 使本 tick 就生效。已发过包时**不重采样** —— 因为
         * {@link GcoreMoveBudget#sentThisTick()} 数的是全 tick 所有来源（含我们自己
         * 刚发的），此时重采样会把「自己的包」也算成「别人的」而重复扣减。
         * 调用方应保证在发包前设置本值（见 {@code TpAura.newContext}）。
         */
        public void setBias(int bias) {
            this.bias = Math.max(0, bias);
            if (this.tokenInTick == 0 && this.countOthers) {
                this.alreadyInTick = sampleAlready();
            }
        }

        /** 见 {@link #bias}。 */
        public int bias() {
            return this.bias;
        }

        /** 见 {@link #minFillers}。 */
        public void setMinFillers(int minFillers) {
            this.minFillers = Math.max(0, minFillers);
        }

        /** 见 {@link #minFillers}。 */
        public int minFillers() {
            return this.minFillers;
        }

        /** 见 {@link #step}。 */
        public void setStep(double step) {
            this.step = (step > 0.0) ? step : TOKEN_STEP_FIRST;
        }

        /** 见 {@link #step}。 */
        public double step() {
            return this.step;
        }

        /** 见 {@link #serverStepMult}。 */
        public void setServerStepMult(double mult) {
            this.serverStepMult = (mult > 0.0) ? mult : SERVER_STEP_MULT;
        }

        /** 见 {@link #serverStepMult}。 */
        public double serverStepMult() {
            return this.serverStepMult;
        }

        /** 本 tick 别人已发的移动包数（采样于 {@link #resetTick}）。 */
        public int alreadyInTick() {
            return this.alreadyInTick;
        }

        /**
         * ★ 2026-10-13：**本序列最后一个已成功发出的位置**。
         *
         * <p>{@code clearFallDebt} 的契约要求「y ≥ 最后一个已发位移包的 y」。
         * 之前调用方传 {@code Math.max(at.y, mc.player.getY())} —— 读的是**客户端**的
         * y，而客户端 y 会被服务端的 {@code teleport()} 推高（服务端认为你在高处，
         * 就把你拉上去）⇒ 形成「读高位 → 发更高的清债包 → 又被拒 → 又拉高」的**自我强化漂移**
         * （实测客户端 y 从 66 漂到 396）。
         *
         * <p>改用**我们自己发出去的坐标**就满足契约，且与客户端状态无关。
         *
         * @return 最后一个已发位置；本序列尚未发出任何包时为 {@code null}
         */
        public Vec3d lastSent() {
            return (this.tokenInTick > 0) ? this.prev : null;
        }

        /** 本 tick 的总预算。 */
        public int limit() {
            return this.limit;
        }

        /**
         * 服务端视角下，本序列的下一包将是本 tick 第几个包。
         *
         * <p>= 别人已发数 + 本序列自己已发数 + 1。
         */
        public int nextPacketIndex() {
            return this.alreadyInTick + this.tokenInTick + 1;
        }
    }

    /**
     * ① 规划 token：按<b>完整动作序列</b>算出需要多少个空包，并把它们发出去。
     *
     * <p>对应 slimefunhelper 的 {@code createMovingPacketsForMovSequence} 开头
     * （源 333-382 行）—— ★ 注意它是对<b>整段</b>（去程 + 回程）一起算的，
     * 而执行时才分成「去程 / 攻击 / 回程」三段。
     *
     * <h3>★ 对 slimefunhelper 原式的必要修正（本项目的核心 bug 修复）</h3>
     * 源式是 {@code tokenNeeded = ceil(d10 / 9.9) - packetCount} ——
     * 它假设**本序列就是从本 tick 第 1 个包开始**（{@code already = 0}）。
     * 但本项目的 {@code GAntiLag} 会在同 tick 抢先发 6~8 个移动包，
     * 服务端的下标 {@code i} 是从**全 tick 所有来源**累计的，
     * 所以必须再扣掉 {@code already}：
     * <pre>
     * tokenNeeded = ceil(d10 / 9.9) - packetCount - already
     * </pre>
     * 否则「垫包 + 位移」的总下标会超过预算 20 → 服务端
     * （1495 行）把 {@code i} 归 1 → 阈值塌回 100 → 位移包整包作废 → 回弹。
     *
     * @param ctx       本 tick 状态（其 {@code alreadyInTick} 已由 {@code resetTick} 采好）
     * @param fullMoves 完整动作序列（去程 + 回程）
     * @return 需要发出的空包数；返回 {@code -1} 表示<b>本 tick 装不下</b>，
     *         调用方应把整批留到下一 tick（源 363-367 行），
     *         或按 {@link #maxReachForBudget} 降低高度后重试
     */
    public static int planTokens(Context ctx, List<Vec3d> fullMoves) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.getNetworkHandler() == null || fullMoves.isEmpty()) {
            return 0;
        }

        int already = Math.max(0, ctx.alreadyInTick);

        int maxTokenNeeded = 0;
        Vec3d lastPos = ctx.tickFirstGood;
        int packetCount = 0;
        for (Vec3d movingPos : fullMoves) {
            packetCount += 1;
            double d7 = movingPos.subtract(lastPos).length();
            double d8 = movingPos.subtract(ctx.tickFirstGood).length();
            double d10 = Math.max(d7, d8);
            // 该动作最少要落在服务端第几个包上（阈值 = max(100,(10·i)²) ⇒ 位移 ≤ 10·i）
            int indexNeeded = (int) Math.ceil(d10 / ctx.step);
            // ★★ 不减去 already（2026-10-11 修正，见类头「垫包数为什么用纯几何公式」）。
            //   只扣掉「本序列自己前面的那些包」。这样：
            //       上跳包真实下标 k = a + f + 1 = a + need
            //       ⇒ k ≥ need 恒成立（不管 a 是多少）⇒ **永不因少发垫包而被拒**。
            //   上一版写的是 `- packetCount - already`：一旦 already 被高估 1，
            //   f 就少 1，k = need - 1，上跳包被服务端整包拒绝
            //   （实测 48 个 tick 少发，46 个恰好少 1）。
            int tokenNeeded = indexNeeded - packetCount;
            maxTokenNeeded = Math.max(maxTokenNeeded, tokenNeeded);
            lastPos = movingPos;
        }
        if (maxTokenNeeded < 0) {
            maxTokenNeeded = 0;
        }

        // ★ 垫包数下限：token 模型算出的最小垫包数若小于设置值，就按设置值发。
        //   若加了下限会超预算，则**自动退回**不加下限的版本
        //   （下限是保守措施，不应当导致整段发不出去）。
        int withMin = Math.max(maxTokenNeeded, ctx.minFillers);

        // ★★ 2026-10-11：改为**用服务端真实规则逐个数验证**，而不是「总数 ≤ limit」。
        //
        //   旧的判据 `already + f + moves ≤ limit` 只保证**包数**够，
        //   完全没建模 `allowedPlayerTicks` 的**递减**（每发一个有位移包 -1，`:1503`）。
        //   实测后果：拱门序列（4 个位移包）即使包数没超，也会因为衰减而塌陷被拒
        //   （离线脚本 `PlanTokensArch` 抓到：bare 序列 serverAccepts=false）。
        //
        //   现在：从「带下限」的垫包数开始往下试，取**第一个能通过服务端模拟**的值。
        //   这样既尊重下限（保守），又不会发出必被拒的序列。
        double[] disps = new double[fullMoves.size()];
        Vec3d prev = ctx.tickFirstGood;
        for (int i = 0; i < fullMoves.size(); i++) {
            Vec3d t = fullMoves.get(i);
            disps[i] = Math.max(t.subtract(prev).length(),
                                t.subtract(ctx.tickFirstGood).length());
            prev = t;
        }

        int chosen = -1;
        for (int f = Math.min(withMin, ctx.limit - already - fullMoves.size()); f >= 0; f--) {
            if (f < maxTokenNeeded) {
                break;                      // 低于几何下限 → 上跳包必被拒，停止
            }
            if (serverAcceptsSequence(already, withFillers(f, disps), ctx.serverStepMult)) {
                chosen = f;
                break;
            }
        }
        if (chosen < 0) {
            return -1;                      // 本 tick 装不下（调用方应降高度或延后）
        }
        maxTokenNeeded = chosen;

        for (int i = 0; i < maxTokenNeeded; i++) {
            ctx.tokenLimit = ctx.limit;
            ctx.tokenInTick++;
            sendDummy(mc, mc.player, ctx);
        }
        return maxTokenNeeded;
    }

    /** 在位移序列前插入 {@code n} 个「无位移」垫包（0.0 = 垫包）。 */
    private static double[] withFillers(int n, double[] disps) {
        if (n <= 0) {
            return disps;
        }
        double[] r = new double[n + disps.length];
        System.arraycopy(disps, 0, r, n, disps.length);
        return r;
    }

    /**
     * ★ 给定本 tick 预算，算出**最多能伪造多高**（供「超限自动降高度」模式使用）。
     *
     * <p>推导：设垫包数为 {@code f}，动作数为 {@code n}，别人已发 {@code a}。
     * <pre>
     * 需要的下标 = ceil(d / 9.9)
     * 实际下标   = a + f + 1
     * 预算约束   = a + f + n ≤ limit
     * </pre>
     * 取 {@code f = limit - a - n}（用满预算）时：
     * <pre>
     * ceil(d / 9.9) ≤ a + (limit - a - n) + 1 = limit - n + 1
     * ⇒ d ≤ 9.9 × (limit - n + 1)
     * </pre>
     * ★ 注意 {@code a}（别人已发数）在推导中**约掉了** ——
     * 别人的包虽然占下标，但也**免掉了等量的垫包**，所以总包数只取决于
     * 「需要多高的下标」，与别人发了几个无关。
     * 真正的限制是：别人发得太多时 {@code a + n > limit}，此时连一个动作都放不下。
     *
     * @param limit     本 tick 总预算（服务端上限 20）
     * @param already   别人已发的移动包数
     * @param moveCount 本序列的动作数
     * @return 能伪造的最大格数；{@code 0} 表示本 tick 完全放不下
     */
    public static double maxReachForBudget(int limit, int already, int moveCount) {
        return maxReachForBudget(limit, already, moveCount, TOKEN_STEP_FIRST);
    }

    /**
     * 见 {@link #maxReachForBudget(int, int, int)}，可指定单包位移参考值。
     *
     * @param step 单包位移参考值（模块的「单包位移」设置）
     */
    public static double maxReachForBudget(int limit, int already, int moveCount, double step) {
        int usableFillers = limit - already - moveCount;
        if (usableFillers < 0) {
            return 0.0;
        }
        int maxIndex = limit - moveCount + 1;
        if (maxIndex < 1) {
            return 0.0;
        }
        double s = (step > 0.0) ? step : TOKEN_STEP_FIRST;
        return s * maxIndex;
    }

    /**
     * ② 发一个位移包（对应源 385-505 行的一次循环）。
     *
     * <p><b>★ 与源的区别</b>：源的守卫是
     * {@code Math.min(ExtraTokenNeeded, tokenNow) >= Math.max(5, tokenLimit)} ——
     * 它同样把 {@code tokenNow} 当成服务端下标，**没有加上别人已发的包**。
     * 这里改成直接按「服务端视角的真实下标」判定，判据更直白也更容易核对：
     * <ol>
     *     <li>下标必须在预算内 —— 否则服务端 1495 行把 {@code i} 归 1，
     *         阈值塌回 100，本包必被拒（还会连带把玩家拉回）；</li>
     *     <li>该下标的阈值必须容得下这段位移（{@code 位移 ≤ 9.9 × 下标}）。</li>
     * </ol>
     *
     * @return {@code true} = 已发出；{@code false} = 装不下，
     *         调用方应把<b>本包及其后</b>留到下一 tick（源 433-454 行）
     */
    public static boolean sendOne(Context ctx, Vec3d to) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.getNetworkHandler() == null) {
            return false;
        }

        double len = to.subtract(ctx.prev).length();
        double len2 = ctx.tickFirstGood.subtract(to).length();
        double need = Math.max(len, len2);

        // ★ 服务端视角：本包将是本 tick 第几个移动包
        int index = ctx.nextPacketIndex();

        // ① 超预算 → 服务端把 i 归 1、阈值塌回 100
        if (index > ctx.limit) {
            return false;
        }
        // ② 该下标允许的最大位移 = 单包位移参考值 × index
        if (need > ctx.step * index) {
            return false;
        }

        ctx.tokenLimit--;
        ctx.tokenInTick++;
        sendMove(mc, to);
        ctx.prev = to;
        return true;
    }

    /**
     * 发一个「垫包」：把服务端 {@code allowedPlayerTicks} 重置为 20。
     *
     * <h2>⛔ 2026-10-14 的「垫包必须带坐标」结论，已于 2026-10-15 撤回</h2>
     *
     * <p><b>撤回理由</b>：实测（{@code docs/gcore/diag/delta-check.txt}）显示
     * 「每个服务端 tick 的第 1 个我们的包」其 {@code d7fg == 0} 占 <b>94.7%</b>，
     * 即客户端 {@code tickFirstGood} 与服务端 {@code firstGood} <b>本就一致</b> ——
     * 所以 {@code PositionAndOnGround(clientFirstGood)} 当时也能重置，
     * 旧结论（「带坐标才修复了回弹」）缺乏依据。
     *
     * <p>现在按 slimefunhelper 的原始做法（见下方方法体）。以下旧分析保留作史料。
     *
     * <h2>（旧）2026-10-14 的说法：垫包必须带坐标</h2>
     *
     * <p><b>原来（错的）</b>：发 {@code OnGroundOnly(false, …)}。
     *
     * <p><b>为什么错</b>：{@code OnGroundOnly}（服务端名 {@code StatusOnly}）的构造器
     * 字节码把 {@code hasPos} 设为 <b>false</b>（{@code iconst_0}）：
     * <pre>
     *   public StatusOnly(boolean, boolean);
     *       ...
     *       8: iconst_0     // ★ hasPos = false
     *       9: iconst_0     // ★ hasRot = false
     * </pre>
     * 而服务端 {@code ServerGamePacketListenerImpl:1454} 是
     * <pre>
     *   double d = clampHorizontal(packet.getX(this.player.getX()));   // ★ default = 玩家当前坐标
     * </pre>
     * 而 {@code getX(double)} 的字节码：
     * <pre>
     *   public double getX(double defaultX);
     *       1: getfield  hasPos:Z
     *       4: ifeq        14
     *       7: getfield  x:D
     *      11: goto        15
     *      14: dload_1              // ★ hasPos=false ⇒ 返回 defaultX（玩家当前坐标）
     * </pre>
     * ⇒ 垫包的 {@code d7 = |玩家当前位置 − firstGood|²}。
     *
     * <p><b>后果</b>：服务端 {@code :1500} 的重置条件是
     * {@code if (!packet.hasRot && !(d7 > 0.0)) allowedPlayerTicks = 20;}
     * —— 一旦玩家位置偏离本 tick 起点 {@code firstGood}（被 teleport、或被原版包推动），
     * 垫包 {@code d7 > 0} ⇒ <b>走 {@code :1503} 递减、不重置</b>
     * ⇒ {@code allowedPlayerTicks} 一路衰减（实测 8~19，**从未到 20**）
     * ⇒ {@code :1495} 判 {@code i > max(allowed,5)} ⇒ <b>塌陷、{@code i} 归 1</b>
     * ⇒ 阈值塌回 {@code max(100,(10·1)²)=100}
     * ⇒ 连 30 格的位移包都被拒 ⇒ {@code :1534 teleport()} 把客户端拉到半空 = <b>回弹</b>。
     *
     * <p><b>修法</b>：改用 {@code PositionAndOnGround}（服务端名 {@code Pos}，
     * 构造器是 {@code iconst_1} ⇒ {@code hasPos=true}），坐标填<b>本 tick 起点</b>
     * {@code ctx.tickFirstGood}。
     * <pre>
     *   d = 包里的坐标 = firstGood
     *   ⇒ d3 = d - firstGoodX = 0（水平/竖直都为 0）
     *   ⇒ d7 = 0
     *   ⇒ !(d7 > 0.0) 成立 ⇒ ★ 走 :1501 重置 allowedPlayerTicks = 20 ✓
     * </pre>
     *
     * <p>⚠️ 虽然它「带坐标」属于有位移包，但坐标 == {@code firstGood} ⇒ {@code d7 == 0}
     * ⇒ 服务端仍按「无位移」处理。这是本次修复的关键洞察。
     *
     * <p>证据：{@code docs/gcore/如来神掌-回弹与渲染形状-完整根因.md}、
     * {@code docs/gcore/diag/tpaura-verify-collapse.txt}（1177/1177 塌陷行皆 d7>100）。
     */
    private static void sendDummy(MinecraftClient mc, ClientPlayerEntity player, Context ctx) {
        // ★★★ 2026-10-15：照 slimefunhelper 的原始做法（MovTasks.java:412）
        //     packets.get(emptyMoveCnt).add(VPacket.newOnGroundOnly(currentOnGround, false));
        //   ⇒ OnGroundOnly 是 StatusOnly，构造器 iconst_0/iconst_0 ⇒ changePosition=false
        //   ⇒ 服务端 :1454 用 packet.getX(this.player.getX()) 取默认值
        //     ⇒ pkt 恒等于【服务端玩家当前位置】
        //     ⇒ d7 = max(|pkt-fg|², |pkt-prev|²-1, |pkt-lg|²-1)、且 pkt==prev==lg
        //       ⇒ 只要玩家还在 tick 起点，三项全 0 ⇒ d7=0 ⇒ ★ 重置 allowedPlayerTicks=20
        //
        //   依据（均已离线核实，非推测）：
        //     · 服务端 d7 公式：paper-1.21.11.jar 字节码偏移 350-427
        //     · 重置条件：偏移 536-559（d7>0 ⇒ 递减；否则 =20）
        //     · getX(default) 语义：minecraft-merged yarn jar 字节码（ifeq → dload_1）
        //     · OnGroundOnly 构造器：iconst_0/iconst_0（changePosition/changeLook = false）
        //   sf 全程用它、且工作正常 ⇒ 照抄。
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(
            false, player.horizontalCollision));
    }

    /** 发一个带位置的移动包。 */
    private static void sendMove(MinecraftClient mc, Vec3d to) {
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
            to.x, to.y, to.z, false, false));
    }

    // ─────────────────────── 清摔落债务（照 gcore，不用 slimefunhelper） ───────────────────────

    /**
     * 清掉本次伪造位移在服务端累积的 {@code fallDistance}。
     *
     * <p><b>移植自 gcore {@code GcoreTpUtil.clearFallDebt}（源 196-229 行）</b>
     * —— 这是本项目 gcore 侧既有的做法，**不依赖 slimefunhelper 的
     * {@code NoFall}/{@code setForceNoFall} 管线**，因此更可靠。
     *
     * <p><b>服务端机制</b>（{@code ServerGamePacketListenerImpl}）：
     * <pre>
     * d4 = packet.y - lastGoodY;
     * boolean flag = d4 &gt; 0.0;                     // 本包是否向上
     * this.player.doCheckFallDamage(..., packet.isOnGround());   // ★ 结算点
     * if (flag) this.player.resetFallDistance();   // ★ 只在一路向上时清零
     * </pre>
     * {@code Entity.checkFallDamage(y, onGround, ...)}：
     * <pre>
     * if (y &lt; 0)  fallDistance -= y;               // 下降 → 累积
     * if (onGround) { if (fallDistance &gt; 0) 造伤害; fallDistance = 0; }
     * </pre>
     *
     * <p>⇒ 只要有一个包申报 {@code onGround=true} 且 {@code fallDistance > 0}，
     * 服务端就当场结算摔落伤害。所以：
     * <ol>
     *     <li>所有伪造位移包一律申报 {@code onGround=false}（本类已经如此）→ 只累积不结算；</li>
     *     <li>末尾补发一个<b>抬高 {@code 9E-8} 且 {@code onGround=false}</b> 的包 →
     *         {@code d4 > 0} → 服务端 {@code resetFallDistance()}，债务清零。</li>
     * </ol>
     *
     * <p>与 {@code NoFall} 的 {@code NoFallLazy} 手法一致（同样是
     * {@code lastServerY + DELTA_Y}、{@code onGround=false}），常量也取自
     * {@code MovTasks.DELTA_Y = 9E-8}。区别是本方法<b>自带收尾</b>，
     * 不依赖 {@code NoFall} 模块是否在跑。
     *
     * <p><b>★★ 2026-10-11 修正（我的离线验收脚本抓出来的真实 bug）</b>：
     * 服务端的 {@code flag} 是
     * <pre>
     *   d4   = packet.y - lastGoodY
     *   flag = d4 &gt; 0.0
     * </pre>
     * 而 {@code lastGoodY} 在每次成功移动后被更新为<b>服务端玩家的真实 Y</b>
     * （{@code :1706 lastGoodY = player.getY()}）。
     *
     * <p>所以「{@code at.y + DELTA_Y}」**只有在 {@code at.y} 不低于最后一个已发包的 y 时**
     * 才会让 {@code d4 > 0}。反例（我实测踩到）：
     * <pre>
     *   MaceDMGPlus 先发 y=235，再发 y=65.25（落回 + 0.25）
     *   ⇒ lastGoodY = 65.25
     *   ⇒ 若再发 65.0 + 9E-8，d4 = -0.25 &lt; 0  ⇒ **flag=false，债务清不掉**
     * </pre>
     *
     * <p><b>契约</b>：调用方必须传入「**不低于最后一个已发位移包的 y**」的位置。
     * 通常就是那最后一个包的目标位置（例如落回点、或 {@code previouspos.y + 0.25}）。
     *
     * @param at 补包的位置；其 {@code y} 必须 ≥ 最后一个已发位移包的 y
     */
    public static void clearFallDebt(Vec3d at) {
        if (at == null) {
            return;
        }
        clearFallDebt(at.x, at.y, at.z);
    }

    /**
     * 见 {@link #clearFallDebt(Vec3d)}。
     *
     * @param y 必须 ≥ 最后一个已发位移包的 y（见上文的契约说明）
     */
    public static void clearFallDebt(double x, double y, double z) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.getNetworkHandler() == null) {
            return;
        }
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
            x, y + DELTA_Y, z, false, false));
    }

    /**
     * 抬高用的极小量 —— 取自 slimefunhelper {@code MovTasks.DELTA_Y}，
     * gcore 的 {@code clearFallDebt} 用的也是同一个值（{@code 9E-8}）。
     *
     * <p>小到无可见位移，但足以让服务端算出 {@code d4 > 0} → 走 {@code resetFallDistance()}。
     */
    public static final double DELTA_Y = 9E-8;

    /**
     * ★ 连击（同一 tick 内 N 次「上跳 + 落回」）的**垫包数 + 高度阶梯** —— 一次算清。
     *
     * <h3>实测背景（为什么需要它）</h3>
     * 服务端日志铁证（{@code E:\paper-test\tpdiag.log}）：
     * <pre>
     *   14:54:58  mv=[4.9, 97.0, 4.21]    ← 如来神掌图腾分支：垫包 0 个 ⇒ 上跳落 k=1 ⇒ 被拒
     *   14:55:04  mv=[0.0,170.0, 0.00]    ← 降龙十八掌：垫包 48 个 ⇒ i&gt;20 ⇒ i 归 1 ⇒ 被拒
     * </pre>
     * 前者「打不出伤害」，后者「多打几次被踢」。两者都是**垫包数不对**。
     *
     * <h3>为什么高度必须从预算反推</h3>
     * 服务端硬约束（{@code ServerGamePacketListenerImpl:1495/1527}）：
     * <pre>
     *   i ≤ 20（否则 i 归 1、阈值塌回 100）
     *   位移 ≤ 10 · i      ← 注意是【三维距离】，不是只看竖直
     * </pre>
     * 同一 tick 内 N 击的包序是
     * <pre>
     *   垫包 F → up1, land1, up2, land2, … upN, landN → 回程, 清债
     *   k_up(i)   = already + F + 2i - 1
     *   k_land(i) = already + F + 2i
     *   own       = 2N + extraMoves
     *   F         = limit - already - own            ← 用满预算
     * </pre>
     * ⇒ <b>越早的那一击下标越小、能到的高度越低</b>，第一击才是瓶颈。
     * 所以「170 + 10 + 10」在数学上不可能（N=3 时第一击上限仅 ~128.7）。
     * 见 {@code docs/gcore/diag/totem-height-design.txt}。
     *
     * <h3>为什么还要「严格递增」</h3>
     * {@code LivingEntity.hurtServer:1440-1443}：无敌帧内若
     * {@code amount <= lastHurt} 则**整击被丢弃**。所以 N 击的伤害必须一击比一击高
     * ⇒ 高度必须严格递增。（这就是模块里「递增高度」设置存在的根本原因。）
     *
     * <h3>★ 距离用三维，不只看竖直</h3>
     * 服务的判据是 {@code d7 = |packet - firstGood|²}（含水平分量）。
     * 若「落点」离「本 tick 起点」有水平偏移 {@code dxz}，上跳包的实际距离是
     * {@code sqrt(dxz² + dy²)}，比只算竖直更大。
     * 这里按三维精确求解：
     * <pre>
     *   dy_i ≤ sqrt( max(0, (step·k_up(i))² - dxz²) ) - baseDy
     *   h_i  = dy_i                      （h 是相对落点的抬升量）
     * </pre>
     *
     * @param limit      本 tick 总预算（服务端 20）
     * @param already    本 tick 别人已发的移动包数（本序列的起始下标）
     * @param step       单包位移参考值（服务端的 10 留一点余量 ⇒ 9.9）
     * @param hits       连击次数 N
     * @param requested  用户请求的基础高度（第一击的目标）
     * @param increment  相邻两击的高度差（必须 &gt; 0，否则绕不过无敌帧）
     * @param extraMoves 除「上跳/落回」外的动作数（回程、清债）
     * @param tickFirstGood 本 tick 起点（服务端 firstGood 的客户端近似）
     * @param landPos       每一击「落回」的目标位置（攻击位）
     * @return 阶梯；{@code null} = 排不出合法阶梯，调用方应**减少击数**或**延后**
     */
    public static final class Ladder {
        /** 应当精确发出的垫包数。 */
        public final int fillers;
        /** 每击相对 {@code landPos} 的抬升高度（严格递增）。 */
        public final double[] heights;

        Ladder(int fillers, double[] heights) {
            this.fillers = fillers;
            this.heights = heights;
        }
    }

    /**
     * ★★ 服务端真实规则模拟（{@code ServerGamePacketListenerImpl:1490-1538}）。
     *
     * <h3>为什么必须有这个函数（2026-10-11 实测定位）</h3>
     * 之前所有公式（{@code planTokens} / {@code planLadder}）都假设
     * <b>本 tick 的包数上限是一个固定的 20</b>。**这是错的。**
     * 服务端真实逻辑是：
     * <pre>
     *   this.receivedMovePacketCount++;
     *   int i = this.receivedMovePacketCount - this.knownMovePacketCount;
     *   this.allowedPlayerTicks += (System.currentTimeMillis()/50 - this.lastTick);  // 按时间回涨
     *   this.allowedPlayerTicks = Math.max(this.allowedPlayerTicks, 1);
     *   if (i &gt; Math.max(this.allowedPlayerTicks, 5)) {
     *       i = 1;                                        // ★ 塌陷
     *   }
     *   if (!packet.hasRot &amp;&amp; !(d7 &gt; 0.0)) {
     *       this.allowedPlayerTicks = 20;                  // 无位移包 → 重置为 20
     *   } else {
     *       this.allowedPlayerTicks--;                     // ★ 有位移包 → 递减 1
     *   }
     *   ...
     *   if (d7 - d6 &gt; max(100, (10*i*speed)^2)) { teleport(); return; }   // :1527
     * </pre>
     *
     * <h3>为什么这会导致「回弹到天上」</h3>
     * ⛔ <b>2026-10-14 更正：原文写「我们的垫包是 {@code OnGroundOnly}（无位移）
     * ⇒ 每个都把 {@code allowed} 重置为 20」—— <b>这个前提只在「玩家位置 == firstGood」时成立</b>，
     * 而实际几乎总是不成立。正确推导见 {@link #sendDummy} 的类注释：</b>
     * <pre>
     *   OnGroundOnly 的 hasPos=false ⇒ 服务端用【玩家当前坐标】算 d7
     *   ⇒ 一旦玩家位置偏离 firstGood ⇒ d7 > 0
     *   ⇒ :1500 的 !(d7 > 0.0) 为假 ⇒ 走 :1503 递减，★ 不重置
     *   ⇒ allowed 一路衰减（实测 8~19，从未到 20）
     *   ⇒ i > max(allowed,5) ⇒ :1495 塌陷、i 归 1 ⇒ 阈值塌回 100
     *   ⇒ 连 30 格的位移包都被拒
     * </pre>
     * 而「落回」包的位移**与上跳同样大**（{@code d7} 取
     * {@code max(|pkt-firstGood|², |pkt-prev|²-1, ...)}，从 base+rung 掉回 base 就是 rung）。
     * ⇒ 一旦塌陷落在某个「落回」包上，服务端**已经接受了上跳**却拒绝落回，
     * 于是它停在高处并 {@code teleport()} 把客户端拉上去 = **回弹到天上**。
     *
     * <p><b>实测对数</b>（{@code E:\paper-test\tpdiag.log}）：
     * <pre>
     *   t=16:10:35.379  from.y=177.00 → to.y=69.00  mv.y=-108.00  MOVED_TOO_QUICKLY
     *   基座 69 + 第 2 级 rung 108 = 177   ← 数值完全吻合
     * </pre>
     * 模拟同样的序列（F=14, rung=99/108）会得到「up→177 被接受、紧随的 land 塌陷被拒」，
     * 与日志一字不差。
     *
     * @param already   本 tick 别人已发的移动包数（i 的起始偏移）
     * @param fillers   本序列先发的无位移垫包数
     * @param disps     随后每个**有位移**包的位移量（三维距离），按发送顺序
     * @return true = 服务端会全部接受
     */
    public static boolean serverAccepts(int already, int fillers, double[] disps) {
        return serverAccepts(already, fillers, disps, SERVER_STEP_MULT);
    }

    /**
     * 见 {@link #serverAccepts(int, int, double[])}，可指定**服务端单包位移倍率**。
     *
     * <h3>★★ 为什么需要这个参数（2026-10-11）</h3>
     * 服务端的阈值是
     * <pre>
     *   (SpigotConfig.movedTooQuicklyMultiplier × i × speed)²
     *   speed = walkingSpeed × 10 = 1.0，multiplier 默认 10.0
     *   ⇒ 位移 ≤ 10 × i
     * </pre>
     * 所以本方法原来写死的 {@code 10.0} 就是「服务端真实规则」的复刻。
     *
     * <p><b>为什么必须可配置</b>：有些服务器会把 {@code moved-too-quickly-multiplier}
     * 调大（甚至关掉该检查）。此时写死的 10.0 会让我们**误判**——把服务端本会接受的
     * 大位移序列在**规划阶段**就拒掉（{@code planLadder} 返回 null ⇒ 一个包都不发）。
     *
     * <p>把倍率提成参数后，「服务器放宽了限制」的场景只需调大这个值。
     *
     * @param mult 服务端单包位移倍率；{@code <= 0} 时退回默认 {@link #SERVER_STEP_MULT}
     */
    public static boolean serverAccepts(int already, int fillers, double[] disps, double mult) {
        double m = (mult > 0.0) ? mult : SERVER_STEP_MULT;
        int a = Math.max(0, already);
        int allowed = 20;          // 垫包会把 allowed 重置为 20，所以初值取 20 是安全的
        int received = a;
        for (int j = 0; j < fillers; j++) {
            received++;
            int i = received;
            if (i > Math.max(allowed, 5)) {
                i = 1;
            }
            allowed = 20;          // 无位移包（hasRot=false 且 d7=0）→ 重置
        }
        for (double d : disps) {
            received++;
            int i = received;
            if (i > Math.max(allowed, 5)) {
                i = 1;             // ★ 塌陷
            }
            if (d > m * i) {
                return false;      // :1527 整包被拒
            }
            allowed--;             // 有位移包 → 递减
        }
        return true;
    }

    /**
     * 见 {@link Ladder}。
     *
     * <h3>★ 包序模型（2026-10-11 恢复拱门后）</h3>
     * 每一击按**拱门**发 {@code archPerHit} 个包：
     * <pre>
     *   archPerHit = 3 : ① 原地拔高(startPos+h)  ② 横移(finalPos+h)  ③ 下降(finalPos)
     *   archPerHit = 2 : ① 上跳(finalPos+h)      ② 落回(finalPos)
     * </pre>
     * 恢复拱门是修 I1「地表打地底 → MOVED_WRONGLY」：
     * 服务端 {@code :1574} 会自己跑 {@code player.move()}，**对角跳**（一个包同时带
     * 水平+竖直）的水平段会撞地形 ⇒ {@code :1591 MOVED_WRONGLY}。
     * 档案实测：对角跳 {@code dxz2=7.8456} ❌；拱门 {@code dxz2=0.0000} ✅。
     *
     * <h3>★ 为什么垫包可以「穿插」</h3>
     * 服务端每收到一个**无位移**包就把 {@code allowedPlayerTicks} 重置为 20
     * （{@code :1501}），只有**有位移**包才递减（{@code :1503}）。
     * 所以把垫包**插在动作之间**能把衰减清零 —— 这是拱门（包数更多）仍能塞进
     * 20 个额度内的关键。
     *
     * @param tailPositions 收尾包的目标位置（回程 / 清债），按发送顺序；可为 null。
     * @param archPerHit    每一击占几个包：3 = 拱门（推荐），2 = 旧的对角跳
     */
    public static Ladder planLadder(int limit, int already, double step, int hits,
                                    double requested, double increment,
                                    Vec3d[] tailPositions,
                                    Vec3d tickFirstGood, Vec3d landPos) {
        return planLadder(limit, already, step, hits, requested, increment,
                          tailPositions, tickFirstGood, landPos, 3);
    }

    /** 见 {@link #planLadder(int, int, double, int, double, double, Vec3d[], Vec3d, Vec3d)}。 */
    public static Ladder planLadder(int limit, int already, double step, int hits,
                                    double requested, double increment,
                                    Vec3d[] tailPositions,
                                    Vec3d tickFirstGood, Vec3d landPos,
                                    int archPerHit) {
        return planLadder(limit, already, step, hits, requested, increment,
                          tailPositions, tickFirstGood, landPos, archPerHit, SERVER_STEP_MULT);
    }

    /**
     * 见 {@link #planLadder(int, int, double, int, double, double, Vec3d[], Vec3d, Vec3d, int)}，
     * 可指定**服务端单包位移倍率**。
     *
     * @param serverStepMult 服务端 {@code moved-too-quickly-multiplier}；
     *                       {@code <= 0} 时退回默认 {@link #SERVER_STEP_MULT}（= 10.0）
     */
    public static Ladder planLadder(int limit, int already, double step, int hits,
                                    double requested, double increment,
                                    Vec3d[] tailPositions,
                                    Vec3d tickFirstGood, Vec3d landPos,
                                    int archPerHit, double serverStepMult) {
        if (hits <= 0 || increment <= 0.0 || tickFirstGood == null || landPos == null) {
            return null;
        }
        if (archPerHit != 2 && archPerHit != 3) {
            archPerHit = 3;
        }
        int a = Math.max(0, already);
        int tail = (tailPositions == null) ? 0 : tailPositions.length;
        double s = (step > 0.0) ? step : TOKEN_STEP_FIRST;

        // 落点（攻击位）相对本 tick 起点的水平 / 竖直偏移
        double dxz = Math.hypot(landPos.x - tickFirstGood.x, landPos.z - tickFirstGood.z);
        double baseDy = landPos.y - tickFirstGood.y;
        double landDist = Math.hypot(dxz, baseDy);
        // 拱顶（攻击位正上方 h 处）相对起点的距离：水平仍是 dxz，竖直是 baseDy + h
        // 起点正上方 h 处相对起点的距离：纯竖直 = h

        // 尾部包位移（与 sendOne 同公式：prev 逐包推进，起点为本 tick firstGood）
        double[] tailDisp = new double[tail];
        Vec3d prev = landPos;
        for (int i = 0; i < tail; i++) {
            Vec3d t = tailPositions[i];
            tailDisp[i] = Math.max(t.subtract(prev).length(),
                                   tickFirstGood.subtract(t).length());
            prev = t;
        }

        // 逐个尝试垫包数（从多到少）：垫包越多，阈值越大、但塌陷越早。
        // 取「能通过服务端模拟」的最大 F，以尽量抬高每一击的上限。
        //
        // ★★ 2026-10-13：改为**穿插**分布（此前是全部前置）。
        //   服务端每收到一个无位移包就把 allowedPlayerTicks 刷回 20
        //   （ServerGamePacketListenerImpl:1500），只有有位移包才递减（:1503）。
        //   全部前置 ⇒ 只重置一次 ⇒ 第 7~10 个位移包塌陷 ⇒ 3 击被降级
        //   （实测 70% 打不满 3 击，见 docs/gcore/diag/totem-degrade.txt）。
        // 逐个尝试垫包数（从多到少）：垫包越多，阈值越大、但塌陷越早。
        // 取「能通过服务端模拟」的最大 F，以尽量抬高每一击的上限。
        Ladder best = null;
        int maxF = limit - a - (archPerHit * hits + tail);
        if (maxF < 0) {
            return null;                       // 连动作都放不下
        }
        for (int F = maxF; F >= 0; F--) {
            // ① 各包的下标上限（三维精确）
            //
            //  ★ 位移公式必须与 sendOne 一致：need = max(|to-prev|, |to-firstGood|)
            //  设 h = 本击抬升、dxz = 落点相对起点的水平偏移、baseDy = landPos.y - firstGood.y：
            //
            //  拱门 3 包（① rise=firstGood+(0,h,0) ② apex=landPos+(0,h,0) ③ land=landPos）：
            //    ① rise: prev = land_{i-1}（第 1 击则是 firstGood）
            //         |rise - prev|      = hypot(dxz, h - baseDy)     ← ★ 从上一落点起算
            //         |rise - firstGood| = h
            //    ② apex: prev = rise
            //         |apex - prev|      = hypot(dxz, baseDy) = landDist   （与 h 无关）
            //         |apex - firstGood| = hypot(dxz, baseDy + h)          ← ★ 随 h 增大
            //    ③ land: prev = apex
            //         |land - prev|      = h
            //         |land - firstGood| = landDist
            double[] cap = new double[hits];
            boolean capOk = true;
            for (int i = 1; i <= hits; i++) {
                int base = a + F + archPerHit * (i - 1);
                int kRise, kApex, kLand;
                if (archPerHit == 3) {
                    kRise = base + 1;
                    kApex = base + 2;
                    kLand = base + 3;
                } else {
                    kRise = base + 1;      // 对角上跳（同时充当 ① 与 ②）
                    kApex = base + 1;
                    kLand = base + 2;
                }
                double capRise = s * kRise;
                double capApex = s * kApex;
                double capLand = s * kLand;

                // ③ 下降包：need = max(h, landDist) ≤ capLand
                if (landDist > capLand) {
                    capOk = false;
                    break;
                }
                // ② 横移包：landDist ≤ capApex，且 hypot(dxz, baseDy+h) ≤ capApex
                if (landDist > capApex || capApex * capApex < dxz * dxz) {
                    capOk = false;
                    break;
                }
                double byApex = Math.sqrt(capApex * capApex - dxz * dxz) - baseDy;

                double c;
                if (archPerHit == 3) {
                    // ① rise：h ≤ capRise 且 hypot(dxz, h-baseDy) ≤ capRise
                    if (capRise * capRise < dxz * dxz) {
                        capOk = false;
                        break;
                    }
                    double byRise = baseDy + Math.sqrt(capRise * capRise - dxz * dxz);
                    c = Math.min(Math.min(byApex, byRise), Math.min(capRise, capLand));
                } else {
                    // 对角：h ≤ sqrt(capApex² - dxz²) - baseDy，且 h ≤ capLand
                    c = Math.min(byApex, capLand);
                }
                cap[i - 1] = c;
            }
            if (!capOk) {
                continue;
            }

            // ② 自顶向下夹逼：≤ 各自上限、≤ 请求高度、且严格递增
            double[] h = new double[hits];
            h[hits - 1] = Math.min(requested + increment * (hits - 1), cap[hits - 1]);
            for (int i = hits - 2; i >= 0; i--) {
                h[i] = Math.min(Math.min(requested + increment * i, cap[i]),
                                h[i + 1] - increment);
            }
            boolean ok = true;
            for (int i = 0; i < hits; i++) {
                if (h[i] <= 2.0) {
                    ok = false;
                    break;
                }
            }
            if (!ok) {
                continue;
            }
            for (int i = 0; i + 1 < hits; i++) {
                if (h[i] >= h[i + 1]) {
                    ok = false;
                    break;
                }
            }
            if (!ok) {
                continue;
            }

            // ③ ★ 用服务端真实规则做最终校验（含 allowedPlayerTicks 递减与尾部包）
            //
            //  ★ 位移必须与 {@link #sendOne} **完全一致**：
            //      need = max( |to - prev| , |to - tickFirstGood| )
            //    （服务端 d7 取 firstGood / prev / lastGood 三者的最大值）
            //
            //  ★★ 尾部包（回程 / 清债）**也会被衰减惩罚**：
            //    它们同样是「有位移」包，会把 allowedPlayerTicks 继续减 1。
            //
            //  ⛔⛔ 2026-10-11 严重 bug（「图腾绕过经常跳过」的真正根因）：
            //    这里原本写成 `serverAcceptsSequence(a, disps)` —— **漏了垫包数 F**！
            //    旧实现是 `serverAccepts(a, F, disps)`（三参数）。
            //    漏掉 F 的后果：服务端第 k 个位移包的下标本应是
            //        i = a + F + k
            //    却按
            //        i = a + k
            //    来算 ⇒ 阈值 `10·i` 被严重低估 ⇒ **几乎所有序列都被判「装不下」**
            //    ⇒ 图腾连击直接 `return`（跳过）。
            //    实测（docs/gcore/diag/totem-scan.txt）：dxz=2 时 F=6 明明可行，
            //    真实方法却对**每一个 F** 都返回 null。
            //    必须用 withFillers(F, disps) 把垫包算进去。
            double[] disps = buildArchDisps(archPerHit, hits, h, tailPositions,
                                            tickFirstGood, landPos);
            if (!serverAcceptsSequence(a, withFillers(F, disps), serverStepMult)) {
                continue;
            }
            best = new Ladder(F, h);
            break;                                  // 从大到小，第一个通过的就是最优
        }
        return best;
    }

    /**
     * ★ 按真实包序构造每个包的位移，公式与 {@link #sendOne} 完全一致：
     * <pre>
     *   need = max( |to - prev| , |to - tickFirstGood| )
     * </pre>
     * 拱门 3 包：① rise=firstGood+(0,h,0) ② apex=landPos+(0,h,0) ③ land=landPos
     * 对角 2 包：① apex=landPos+(0,h,0) ② land=landPos
     *
     * <p>把它抽出来是为了**让校验与发包共用同一套几何** ——
     * 我第一版在校验里手写公式，把 ② 算成了与 h 无关，结果校验偏松/偏紧
     * （离线脚本 `ArchAcceptance` 抓出了这个不一致）。
     */
    private static double[] buildArchDisps(int archPerHit, int hits, double[] h,
                                           Vec3d[] tailPositions,
                                           Vec3d tickFirstGood, Vec3d landPos) {
        int tail = (tailPositions == null) ? 0 : tailPositions.length;
        double[] disps = new double[archPerHit * hits + tail];
        int p = 0;
        Vec3d prev = tickFirstGood;
        for (int i = 0; i < hits; i++) {
            Vec3d rise = tickFirstGood.add(0, h[i], 0);
            Vec3d apex = landPos.add(0, h[i], 0);
            if (archPerHit == 3) {
                disps[p++] = Math.max(rise.distanceTo(prev), rise.distanceTo(tickFirstGood));
                prev = rise;
                disps[p++] = Math.max(apex.distanceTo(prev), apex.distanceTo(tickFirstGood));
                prev = apex;
            } else {
                disps[p++] = Math.max(apex.distanceTo(prev), apex.distanceTo(tickFirstGood));
                prev = apex;
            }
            disps[p++] = Math.max(landPos.distanceTo(prev), landPos.distanceTo(tickFirstGood));
            prev = landPos;
        }
        for (int i = 0; i < tail; i++) {
            disps[p++] = Math.max(tailPositions[i].distanceTo(prev),
                                  tailPositions[i].distanceTo(tickFirstGood));
            prev = tailPositions[i];
        }
        return disps;
    }

    /**
     * 精确发出 {@code n} 个垫包（无位移），并推进 {@code ctx} 的下标。
     *
     * <p>与 {@link #planTokens} 的区别：那个按几何**自行决定**发几个；
     * 这个由调用方指定 —— 用于 {@link #planLadder} 这类
     * 「垫包数与高度必须严格配套」的场景。若两者口径不一致，
     * 阶梯的下标假设就会失效（要么被拒、要么浪费预算）。
     */
    public static void sendFillers(Context ctx, int n) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.getNetworkHandler() == null || n <= 0) {
            return;
        }
        for (int i = 0; i < n; i++) {
            ctx.tokenLimit = ctx.limit;
            ctx.tokenInTick++;
            sendDummy(mc, mc.player, ctx);
        }
    }

    /**
     * 一次发完整段序列（{@link #planTokens} + 逐个 {@link #sendOne} 的便捷包装）。
     *
     * <p>用于「不需要在中间插动作」的场景（例如整段回程）。
     *
     * @return 发送结果；{@code remaining} 非空时调用方应保存并在下一 tick 继续
     */
    public static Result sendSequence(Context ctx, List<Vec3d> moves) {
        int dummy = planTokens(ctx, moves);
        if (dummy < 0) {
            return new Result(0, false, new ArrayList<>(moves));
        }
        int sent = 0;
        for (int i = 0; i < moves.size(); i++) {
            if (!sendOne(ctx, moves.get(i))) {
                return new Result(sent, false, new ArrayList<>(moves.subList(i, moves.size())));
            }
            sent++;
        }
        return new Result(sent, true, List.of());
    }

    /**
     * ★ 本 tick 的完整计划：垫包数 + 是否装得下。
     *
     * <p><b>与 {@link #planTokens} 的区别</b>：本方法用<b>纯几何</b>公式，
     * <b>不减去「别人已发」</b> —— 这与两个能工作的参考实现一致：
     * <ul>
     *     <li>gcore {@code GcoreTpUtil.doTpMove:129}：{@code maxPacket = maxLeg - 1 + 4}，无 already 项；</li>
     *     <li>slimefunhelper {@code MovTasks.createMovingPacketsForMovSequence:351}：
     *         {@code tokenNeeded = ceil(d10/9.9) - packetCount}，同样无 already 项。</li>
     * </ul>
     *
     * <h3>为什么不再减 already（实测依据）</h3>
     * 客户端 tick 边界 ≠ 服务端 tick 边界，且 {@code GAntiLag} 在虚拟线程
     * {@code gcore-antilag} 上异步发包 ⇒ 客户端数到的「别人已发」与服务端本 tick
     * 实际计入的包数<b>没有稳定关系</b>。
     * 日志实测（{@code docs/gcore/diag/audit-others-mismatch.txt}）：
     * <pre>
     *   48 个 tick 少发垫包，其中 46 个恰好少 1
     *   「该 tick 一个 GAntiLag 包都没有」的样本里，仍有 44 个只发了 8 个垫包而需要 9 个
     * </pre>
     * 少发 1 个 ⇒ 上跳包落在 {@code k = need - 1} ⇒ 服务端 {@code :1527} 整包拒绝
     * ⇒ 只打出普通伤害 + 回弹 + 被抬到半空 + 飞行踢出。
     * 而多发的代价只是多占一个预算（有 {@link #TOKEN_LIMIT_MAX} 兜底）。
     *
     * <p>★ 配合 {@code MoveSequenceGate}（抑制原版移动包）后，
     * 「本 tick 的包全是我们发的」成立，{@code i} 就是确定值，
     * 纯几何公式即精确。
     *
     * @param ctx       本 tick 状态（其 {@code alreadyInTick} 不再参与垫包计算）
     * @param fullMoves 完整动作序列（去程 + 回程）
     * @return 垫包数；{@code -1} = 本 tick 装不下（调用方应延后或降高度）
     */
    public static int planTokensGeometric(Context ctx, List<Vec3d> fullMoves) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.getNetworkHandler() == null || fullMoves.isEmpty()) {
            return 0;
        }

        // 垫包自己占下标，所以「本序列从第几个包开始」要算进去。
        // ★ 抑制原版包之后，本序列通常从 1 开始；但 GAntiLag 仍可能在同 tick 抢先发包，
        //   所以这里用 already 作为「起点偏移」而不是「扣减项」：
        //      第 j 个动作落在 k = already + fillers + j
        //   要求 k >= ceil(d10_j / step)
        //   ⇒ fillers >= ceil(d10_j / step) - already - j
        //   ★ 与我上一版的差别：**不再把 already 从结果里再扣一次**，
        //     它只出现在「起点偏移」中，方向是「多发」而非「少发」。
        //     （上一版的 bug 是把同一个偏移用了两次，导致 46 个 tick 少发 1 个。）
        int already = Math.max(0, ctx.alreadyInTick);

        int maxTokenNeeded = 0;
        Vec3d lastPos = ctx.tickFirstGood;
        int packetCount = 0;
        for (Vec3d movingPos : fullMoves) {
            packetCount += 1;
            double d7 = movingPos.subtract(lastPos).length();
            double d8 = movingPos.subtract(ctx.tickFirstGood).length();
            double d10 = Math.max(d7, d8);
            int indexNeeded = (int) Math.ceil(d10 / ctx.step);
            int tokenNeeded = indexNeeded - already - packetCount;
            maxTokenNeeded = Math.max(maxTokenNeeded, tokenNeeded);
            lastPos = movingPos;
        }
        if (maxTokenNeeded < 0) {
            maxTokenNeeded = 0;
        }

        // ★ 垫包数下限（保底，覆盖 GAntiLag 的线程竞态）。
        //   若加了下限会超预算，则自动退回不加下限的版本。
        int withMin = Math.max(maxTokenNeeded, ctx.minFillers);
        if (already + withMin + fullMoves.size() <= ctx.limit) {
            maxTokenNeeded = withMin;
        }

        if (already + maxTokenNeeded + fullMoves.size() > ctx.limit) {
            return -1;
        }

        for (int i = 0; i < maxTokenNeeded; i++) {
            ctx.tokenLimit = ctx.limit;
            ctx.tokenInTick++;
            sendDummy(mc, mc.player, ctx);
        }
        return maxTokenNeeded;
    }

    /**
     * ★★ 通用版：给定**完整包序**，判断服务端是否全部接受。
     *
     * <p>与 {@link #serverAccepts(int, int, double[])} 的区别：这里允许
     * <b>垫包穿插在中间</b>。因为服务端每收到一个「无位移」包就把
     * {@code allowedPlayerTicks} <b>重置为 20</b>，所以「一击之后补一个垫包」
     * 可以**把衰减清零**，从而在同样的 20 个包额度内塞进更多动作。
     * 这正是拱门路径（多段）能塞进预算的关键。
     *
     * @param already 本 tick 别人已发的移动包数
     * @param disps   每个包的位移（三维距离）；<b>{@code 0.0} 表示垫包</b>（无位移）
     * @return true = 服务端会全部接受
     */
    public static boolean serverAcceptsSequence(int already, double[] disps) {
        return serverAcceptsSequence(already, disps, SERVER_STEP_MULT);
    }

    /**
     * 见 {@link #serverAcceptsSequence(int, double[])}，可指定**服务端单包位移倍率**。
     *
     * @param mult 服务端单包位移倍率（{@code moved-too-quickly-multiplier}）；
     *             {@code <= 0} 时退回默认 {@link #SERVER_STEP_MULT}（= 10.0）
     */
    public static boolean serverAcceptsSequence(int already, double[] disps, double mult) {
        if (disps == null) {
            return true;
        }
        double m = (mult > 0.0) ? mult : SERVER_STEP_MULT;
        int a = Math.max(0, already);
        int allowed = 20;
        int received = a;
        for (double d : disps) {
            received++;
            int i = received;
            if (i > Math.max(allowed, 5)) {
                i = 1;                       // ★ 塌陷
            }
            if (d <= 0.0) {
                allowed = 20;                // 无位移包 → 重置
            } else {
                if (d > m * i) {
                    return false;            // :1527 整包被拒
                }
                allowed--;                   // 有位移包 → 递减
            }
        }
        return true;
    }

    /**
     * ★★ {@code allowedPlayerTicks} 衰减约束下，本序列最多能发多少个垫包。
     *
     * <h3>服务端规则（{@code ServerGamePacketListenerImpl:1490-1538}）</h3>
     * <pre>
     *   if (i &gt; Math.max(allowedPlayerTicks, 5)) i = 1;      // :1495 塌陷
     *   if (!hasRot &amp;&amp; !(d7 &gt; 0)) allowedPlayerTicks = 20;   // :1501 无位移 → 重置
     *   else                       allowedPlayerTicks--;     // :1503 有位移 → 递减
     * </pre>
     *
     * <p>设垫包 {@code F}、别人已发 {@code a}、本序列**有位移**的包数 {@code n}，
     * 则第 {@code j} 个位移包：
     * <pre>
     *   i       = a + F + j
     *   allowed = 21 - j          （最后一个垫包重置为 20，其后 j-1 次递减）
     *   不塌陷 ⟺ a + F + j ≤ max(21 - j, 5)
     * </pre>
     * 取最紧的 {@code j = n}（当 {@code n < 16}）：
     * <pre>
     *   F ≤ 21 - 2n - a
     * </pre>
     *
     * <p><b>为什么必须显式建模</b>：塌陷一旦落在某个「下降」包上，
     * 服务端**已经接受了前面的上升**却拒绝下降 ⇒ 它停在高处并 {@code teleport()} 把
     * 客户端拉上去 = 「回弹到天上」。实测对数见 {@code docs/gcore/回弹与崩端-实施记录.md}。
     *
     * @return 允许的最大垫包数；**可能为负**，表示本序列无论怎么排都装不下
     */
    public static int maxFillersForDecay(int already, int ownMoves, int limit) {
        int a = Math.max(0, already);
        int n = Math.max(1, ownMoves);
        int decayCap = 21 - 2 * n - a;
        int totalCap = limit - a - n;
        return Math.min(decayCap, totalCap);
    }

    /**
     * 按 token 模型估算：给定动作序列，本 tick 能否完整发完。
     *
     * <p>供模块在发之前判断「这个高度能不能一次做完」，从而决定是否降级。
     * 与 {@link #planTokens} 用**同一个公式**（含 {@code already} 扣减），
     * 避免「算的时候说能、发的时候发不出」。
     *
     * @param moves         动作序列
     * @param tickFirstGood 本 tick 起点
     * @param already       本 tick 别人已发的移动包数
     * @param limit         本 tick 总预算
     * @return 本 tick 能否完整发完
     */
    public static boolean fitsInOneTick(List<Vec3d> moves, Vec3d tickFirstGood,
                                       int already, int limit) {
        if (moves.isEmpty()) {
            return true;
        }
        int maxTokenNeeded = 0;
        Vec3d lastPos = tickFirstGood;
        int packetCount = 0;
        for (Vec3d movingPos : moves) {
            packetCount += 1;
            Vec3d movement = movingPos.subtract(lastPos);
            lastPos = movingPos;
            double d10 = Math.max(movement.length(), movingPos.subtract(tickFirstGood).length());
            int indexNeeded = (int) Math.ceil(d10 / TOKEN_STEP_FIRST);
            int tokenNeeded = indexNeeded - already - packetCount;
            maxTokenNeeded = Math.max(maxTokenNeeded, tokenNeeded);
        }
        if (maxTokenNeeded < 0) {
            maxTokenNeeded = 0;
        }
        return already + maxTokenNeeded + moves.size() <= Math.max(5, limit);
    }

    /** 见 {@link #fitsInOneTick(List, Vec3d, int, int)}，用当前实时计数与默认预算。 */
    public static boolean fitsInOneTick(List<Vec3d> moves, Vec3d tickFirstGood) {
        return fitsInOneTick(moves, tickFirstGood, GcoreMoveBudget.sentThisTick(), TOKEN_LIMIT_MAX);
    }
}
