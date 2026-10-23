package com.codigohasta.addon.utils.gcore;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.function.BiConsumer;

public final class GcoreTpUtil {

    private GcoreTpUtil() {
    }

    public static void doTpMove(GcoreTpOption tpOption, Vec3d fromVec, Vec3d toVec,
                                Map<BlockPos, BlockState> extraPosMap,
                                BiConsumer<MovePacketCounter, Vec3d> action) {
        doTpMove(
            tpOption.getSearchVclipMode(),
            fromVec, toVec,
            tpOption.getMoveDistance(),
            tpOption.getSearchFindStep(),
            tpOption.getBack(),
            tpOption.getAllowIntoVoid(),
            tpOption.getLimitPacket(),
            tpOption.getPrintWhenTooManyPacket(),
            extraPosMap,
            action,
            tpOption.getServerStepMult());
    }

    public static void doTpMove(GcoreTpOption.TpVClipFindType type, Vec3d fromVec, Vec3d toVec,
                                double moveDistance, double vClipSearchStep, boolean back,
                                boolean allowIntoVoid, int limitPacket, boolean printWhenTooManyPacket,
                                Map<BlockPos, BlockState> extraPosMap,
                                BiConsumer<MovePacketCounter, Vec3d> action) {
        doTpMove(type, fromVec, toVec, moveDistance, vClipSearchStep, back,
                 allowIntoVoid, limitPacket, printWhenTooManyPacket, extraPosMap, action,
                 GcoreMoveSequenceUtil.SERVER_STEP_MULT);
    }

    /**
     * 见 {@link #doTpMove(GcoreTpOption.TpVClipFindType, Vec3d, Vec3d, double, double,
     * boolean, boolean, int, boolean, Map, BiConsumer)}，可指定**服务端单包位移倍率**。
     *
     * @param serverStepMult 服务端 {@code moved-too-quickly-multiplier}（默认 10.0）；
     *                       {@code <= 0} 时不做该项兜底校验
     */
    public static void doTpMove(GcoreTpOption.TpVClipFindType type, Vec3d fromVec, Vec3d toVec,
                                double moveDistance, double vClipSearchStep, boolean back,
                                boolean allowIntoVoid, int limitPacket, boolean printWhenTooManyPacket,
                                Map<BlockPos, BlockState> extraPosMap,
                                BiConsumer<MovePacketCounter, Vec3d> action,
                                double serverStepMult) {
        MovePacketCounter counter = new MovePacketCounter();

        Vec3d goVClipVec = findVClipVecToMove(fromVec, toVec, vClipSearchStep, allowIntoVoid, type, extraPosMap);
        if (goVClipVec == null) {
            GcoreWebTrapDiag.logf("TP", "★去程找不到 VClip 高度（拱门搜索失败）→ 放弃  起点=[%.1f,%.1f,%.1f] 目标=[%.1f,%.1f,%.1f] 距离=%.1f",
                fromVec.x, fromVec.y, fromVec.z, toVec.x, toVec.y, toVec.z, fromVec.distanceTo(toVec));
            return;
        }

        Vec3d backVClipVec = findVClipVecToMove(toVec, fromVec, vClipSearchStep, allowIntoVoid, type, extraPosMap);
        if (backVClipVec == null) {
            GcoreWebTrapDiag.logf("TP", "★回程找不到 VClip 高度 → 放弃  目标=[%.1f,%.1f,%.1f] 起点=[%.1f,%.1f,%.1f]",
                toVec.x, toVec.y, toVec.z, fromVec.x, fromVec.y, fromVec.z);
            return;
        }

        // ★★★ 拱门形路径（Arch）—— 解决「水平穿墙在 1.21.11 不可能」。
        //
        //   源 GCore 的路径是**一条对角跳**：
        //       from → goVClip（原地拔高）→ toVec（斜着蹦过去）
        //   而服务端 `player.move()` 是**逐轴**的（先 Y，再水平）。对角跳被拆开后，
        //   水平那一半仍然直撞墙 → `sim != movement` → `movedWrongly` → 回拉。
        //   实测（档案 4.20）：dxz2 = 5.29 ~ 7.85，远超阈值 0.0625。
        //
        //   ⇒ 唯一出路是「抬到墙顶之上 → 水平横移 → 下降」的**拱门**：
        //       from → goVClip（拔到拱顶高度）→ goArch（水平横移）→ toVec（下降）
        //   三段的 x/z 各自改变一个轴，服务端每段都能完整走完（dxz2 ≈ 0）。
        //
        //   拱顶取 goVClipVec 的 y —— `findVClipVecToMove` 已经保证了
        //   「该高度上 checkCanMove(拱顶水平段) 可通」，正是拱门需要的性质。
        //
        //   ★ 整段拱门与「摔落申报」都收在同一个开关下：网关关闭时
        //     TPServerEquivalent 就不再是「可开关的实验性修复」，
        boolean arch = GcoreServerEquivMove.shouldUseForCurrentThread();
        Vec3d goArchVec = arch ? new Vec3d(toVec.x, goVClipVec.y, toVec.z) : goVClipVec;
        Vec3d backArchVec = arch ? new Vec3d(fromVec.x, backVClipVec.y, fromVec.z) : backVClipVec;

        //   注意 c 用 fromVec -> toVec（不是 toVec -> toVec），源在这里是正确的
        //   （acid 的同类实现有 c 恒为 0 的 bug，GCore 没有）。
        //
        //   ★ 拱门形路径比源多两段（水平横移），所以把拱门各段距离也纳入预算，
        //     否则最长段被低估、包数不足会触发服务端的 moved-too-quickly。
        int a = (int) Math.ceil(fromVec.distanceTo(goVClipVec) / moveDistance);      // 去程：竖直
        int b = (int) Math.ceil(toVec.distanceTo(backVClipVec) / moveDistance);      // 回程：竖直
        int c = (int) Math.ceil(fromVec.distanceTo(toVec) / moveDistance);           // 直线参考
        int maxLeg = Math.max(a, Math.max(b, c));

        // ★ 同时记录「最长的一段**实际距离**」—— 供下面按服务端规则兜底用。
        //   服务端 d7 取 max(|pkt-firstGood|, |pkt-prev|)，
        //   而 firstGood = 本 tick 起点 = fromVec，所以从 fromVec 量即可。
        double maxLegDist = Math.max(fromVec.distanceTo(goVClipVec),
                                     Math.max(fromVec.distanceTo(toVec),
                                              toVec.distanceTo(backVClipVec)));
        if (arch) {
            int a2 = (int) Math.ceil(goVClipVec.distanceTo(goArchVec) / moveDistance);     // 去程：水平
            int a3 = (int) Math.ceil(goArchVec.distanceTo(toVec) / moveDistance);          // 去程：下降
            int b2 = (int) Math.ceil(backVClipVec.distanceTo(backArchVec) / moveDistance); // 回程：水平
            int b3 = (int) Math.ceil(backArchVec.distanceTo(fromVec) / moveDistance);      // 回程：下降
            maxLeg = Math.max(maxLeg, Math.max(Math.max(a2, a3), Math.max(b2, b3)));
            maxLegDist = Math.max(maxLegDist,
                Math.max(fromVec.distanceTo(goArchVec),
                    Math.max(fromVec.distanceTo(backVClipVec),
                        Math.max(fromVec.distanceTo(backArchVec), 0.0))));
        }
        int maxPacket = maxLeg - 1 + 4;

        // ★★ 2026-10-11：再用**服务端真实规则**兜一次底。
        //
        //   服务端对第 i 个包允许的位移是 `mult × i`（`mult` =
        //   spigot.yml 的 moved-too-quickly-multiplier，默认 10.0）。
        //   本方法发出的位移包落在下标 maxPacket / +1 / +2，其中最长的
        //   一段位移就是 `maxLegDist`。
        //
        //   ⇒ 必须满足 `maxLegDist ≤ mult × maxPacket`
        //     ⇒ `maxPacket ≥ ceil(maxLegDist / mult)`
        //
        //   原式 `maxLeg - 1 + 4` 只在「单包位移 ≈ 服务端倍率」时成立：
        //   例如 单包位移=50、倍率=10、距离=170 ⇒ 原式 7，而实际需要 17
        //   ⇒ 位移包会被服务端整包拒绝（表现为「TP 没生效 / 被拉回」）。
        //
        //   ★ 取 max 是**单向安全**的：只在原式不足时补足，原式够用时结果不变。
        //     代价是包数可能变多 —— 若因此超过「移动包上限」就会放弃本次传送
        //     （放弃 优于 发出去被服务端拒绝）。
        //
        //   ★★★ 只在用户**显式改过**倍率时才启用本兜底 —— 保证默认配置零回归。
        //
        //   理由：gcore 系 TP 模块当前是**可用**的（用户实测「都是好的能用的 不回弹」）。
        //   若无条件套用本兜底，默认配置下包数会变多（moveDistance 默认 50 > 倍率 10），
        //   反而可能让本来能用的场景变成「放弃」。所以：
        //       倍率 == 默认(10.0) → **完全走原逻辑**，行为逐字不变；
        //       倍率 != 默认       → 说明用户知道自己在做什么（服务器放宽了限制），
        //                            此时才用服务端真实规则兜底。
        boolean multChanged = Math.abs(serverStepMult - GcoreMoveSequenceUtil.SERVER_STEP_MULT) > 1.0E-9;
        if (multChanged && serverStepMult > 0.0 && maxLegDist > 0.0) {
            int needed = (int) Math.ceil(maxLegDist / serverStepMult);
            if (needed > maxPacket) {
                maxPacket = needed;
            }
        }

        if (maxPacket > limitPacket) {
            GcoreWebTrapDiag.logf("TP", "★包数超限 %d > 上限 %d → 放弃  距离=%.1f 单包位移=%.1f",
                maxPacket, limitPacket, fromVec.distanceTo(toVec), moveDistance);
            if (printWhenTooManyPacket) {
                GcoreChatUtils.chat("§7TP packet limit !!!");
            }
            return;
        }

        // ★★ 关于 onGround：源调用的是 `sendC04$default(..., false, 8, null)` ——
        //   最后一个 int 是 Kotlin 默认参数掩码，`n & 8` 为真时
        //   所以源实际发出的**所有** TP 包都声明「在地面」。
        //
        //      若 fallDistance 已累积，会**当场结算摔落伤害**
        //      （见 GamePacketListenerImpl 第 1705 行 doCheckFallDamage）。
        //      正确做法是全程 false + 末尾补一个抬高包清债，见 clearFallDebt()。
        //
        //   （历史上曾把「直线被墙挡住就真站到 TP 点」归因于传 false 触发飞行校验 ——
        //     已证伪：真因是水平撞墙导致 movedWrongly 回拉，与 onGround 无关。）

        // 重复发「起点」包，填充时间窗口（让服务端看到的「距离/包数」比值更合理）。
        // ★ onGround 同样申报 false：若此刻 fallDistance 已有累积（例如起手时正在下落），
        //   申报 true 会被服务端**当场结算**成摔落伤害。
        for (int i = 1; i < maxPacket; i++) {
            counter.sendC04(fromVec.x, fromVec.y, fromVec.z, !arch);
        }

        if (arch) {
            // ★★★ 拱门形：竖直升 → 水平横移 → 竖直降，三段一律申报 onGround=false。
            counter.sendC04(goVClipVec.x, goVClipVec.y, goVClipVec.z, false);
            counter.sendC04(goArchVec.x, goArchVec.y, goArchVec.z, false);
            counter.sendC04(toVec.x, toVec.y, toVec.z, false);
        } else {
            counter.sendC04(goVClipVec.x, goVClipVec.y, goVClipVec.z);
            counter.sendC04(toVec.x, toVec.y, toVec.z);
        }

        // ★ 在目标位置执行动作（放锚 / 点火 / 攻击）
        action.accept(counter, toVec);

        if (!back) {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            player.setPosition(toVec);
            if (arch) {
                clearFallDebt(counter, toVec);
            }
            return;
        }

        if (arch) {
            // ★★★ 回程：同构的拱门（拱顶高度来自 backVClipVec）
            counter.sendC04(backVClipVec.x, backVClipVec.y, backVClipVec.z, false);
            counter.sendC04(backArchVec.x, backArchVec.y, backArchVec.z, false);
            counter.sendC04(fromVec.x, fromVec.y, fromVec.z, false);
            clearFallDebt(counter, fromVec);
        } else {
            counter.sendC04(backVClipVec.x, backVClipVec.y, backVClipVec.z);
            counter.sendC04(fromVec.x, fromVec.y, fromVec.z);
        }
    }

    /**
     * 清掉本次 TP 在服务端累积的 {@code fallDistance}，避免下次落地时结算成摔落伤害。
     *
     * <p><b>服务端的机制</b>（Luminol 1.21.11 {@code ServerGamePacketListenerImpl}）：
     * <pre>
     * d4 = packet.y - lastGoodY;                  // 1589 行附近
     * boolean flag = d4 &gt; 0.0;                    // 1596 行
     * this.player.move(...);                      // 1619
     * this.player.doCheckFallDamage(..., packet.isOnGround());   // 1705 ★ 结算点
     * if (flag) this.player.resetFallDistance();  // 1707 ★ 只在一路向上时清零
     * </pre>
     * {@code Entity.checkFallDamage(y, onGround, ...)}：
     * <pre>
     * if (y &lt; 0)  fallDistance -= y;              // 下降 → 累积
     * if (onGround) { if (fallDistance &gt; 0) 造伤害; fallDistance = 0; }
     * </pre>
     *
     * <p>⇒ 只要**任何一个包**申报 {@code onGround=true} 且此时 {@code fallDistance &gt; 0}，
     * 服务端就当场结算。因此：
     * <ol>
     *     <li>拱门三段全部申报 {@code onGround=false} → 全程不结算（只累积）；</li>
     *     <li>最后补发一个**抬高 {@code 9E-8} 且 {@code onGround=false}** 的包 →
     *         {@code d4 > 0} → 服务端走 1707 行 {@code resetFallDistance()}，债务清零。</li>
     * </ol>
     *
     * <p>第 ② 步与有效版 {@code NoFall.NoFallLazy.applyPreTickModify} 的手法完全一致
     * （它同样是发 {@code lastServerY + DELTA_Y}、{@code onGround=false} 的包），
     * 常量也取自 {@code MovTasks.DELTA_Y = 9E-8}。区别只是这里**自带**收尾，
     * 不依赖 {@code NoFall} 模块是否在跑 —— 而 {@code NoFall} 的管线只有在
     * {@code SlimefunRuntime.modifying()}（即**有任一 sf 模块启用**）时才会执行。
     */
    private static void clearFallDebt(MovePacketCounter counter, Vec3d at) {
        counter.sendC04(at.x, at.y + 9E-8, at.z, false);
    }

    public static Vec3d findVClipVecToMove(Vec3d fromVec, Vec3d toVec, double searchStep,
                                           boolean allowIntoVoid, GcoreTpOption.TpVClipFindType type,
                                           Map<BlockPos, BlockState> extraPosMap) {
        // ★★★ 服务端等价模式 → 改搜「拱门拱顶高度」。
        //
        //   原逻辑搜的是「一个 y，使 checkCanMove((from.x,y,from.z) → toVec) 成立」——
        //   那是**一条对角跳**。在 1.21.11 里，只要水平方向有墙，
        //   对角跳被服务端逐轴拆开后，水平那半必然撞墙 → `movedWrongly`。
        //
        //   拱门逻辑搜的是「一个 y，使**同一高度上的水平段**畅通」。
        //   这才是有解的那一类路径（见档案 4.20 的定论实验）。
        if (GcoreServerEquivMove.shouldUseForCurrentThread()) {
            return findArchVecToMove(fromVec, toVec, searchStep, allowIntoVoid, type, extraPosMap);
        }

        double step = 0.0;

        //     clientWorld.getDimensionEffects().getSkyType()
        //     voidY = (skyType == NORMAL) ? -64 : 0
        //   `DimensionEffects` 在 1.21.11 已被移除。
        //   源表达的本质是「该维度的世界最低 Y」（主世界 -64，下界/末地 0），
        //   所以改用 `world.getBottomY()` —— 语义完全等价，且对自定义维度更准确。
        int voidY = MinecraftClient.getInstance().world.getBottomY();

        //     continue block5 -> 跳回 while 条件（**跳过 step += searchStep**）
        //     break           -> 只跳出 switch，之后仍会执行 step += searchStep
        block5:
        while (step <= 200.0) {
            switch (type) {
                case ALL: {
                    Vec3d vec = new Vec3d(fromVec.x, fromVec.y + step, fromVec.z);
                    if (allowIntoVoid && fromVec.y <= (double) voidY) continue block5;
                    if (GcoreBlockUtil.checkNoPosCollie(vec, extraPosMap, GcorePlayerUtil.PlayerState.Normal)
                        && GcoreBlockUtil.checkCanMove(vec, toVec, extraPosMap, GcorePlayerUtil.PlayerState.Normal)) {
                        return vec;
                    }

                    vec = new Vec3d(fromVec.x, fromVec.y - step, fromVec.z);
                    if (allowIntoVoid && fromVec.y <= (double) voidY) continue block5;
                    if (!GcoreBlockUtil.checkNoPosCollie(vec, extraPosMap, GcorePlayerUtil.PlayerState.Normal)
                        || !GcoreBlockUtil.checkCanMove(vec, toVec, extraPosMap, GcorePlayerUtil.PlayerState.Normal)) {
                        break;
                    }
                    return vec;
                }
                case OnlyUp: {
                    Vec3d vec = new Vec3d(fromVec.x, fromVec.y + step, fromVec.z);
                    if (allowIntoVoid && fromVec.y <= (double) voidY) continue block5;
                    if (!GcoreBlockUtil.checkNoPosCollie(vec, extraPosMap, GcorePlayerUtil.PlayerState.Normal)
                        || !GcoreBlockUtil.checkCanMove(vec, toVec, extraPosMap, GcorePlayerUtil.PlayerState.Normal)) {
                        break;
                    }
                    return vec;
                }
                case OnlyDown: {
                    Vec3d vec = new Vec3d(fromVec.x, fromVec.y - step, fromVec.z);
                    if (allowIntoVoid && fromVec.y <= (double) voidY) continue block5;
                    if (!GcoreBlockUtil.checkNoPosCollie(vec, extraPosMap, GcorePlayerUtil.PlayerState.Normal)
                        || !GcoreBlockUtil.checkCanMove(vec, toVec, extraPosMap, GcorePlayerUtil.PlayerState.Normal)) {
                        break;
                    }
                    return vec;
                }
            }
            step += searchStep;
        }
        return null;
    }

    /** 见 {@link #findVClipVecToMove}，用虚拟方块图。 */
    public static Vec3d findVClipVecToMove(Vec3d fromVec, Vec3d toVec, double searchStep,
                                           boolean allowIntoVoid, GcoreTpOption.TpVClipFindType type) {
        return findVClipVecToMove(fromVec, toVec, searchStep, allowIntoVoid, type,
            GcoreBlockUtil.getBufferMap());
    }

    public static Vec3d findArchVecToMove(Vec3d fromVec, Vec3d toVec, double searchStep,
                                          boolean allowIntoVoid, GcoreTpOption.TpVClipFindType type,
                                          Map<BlockPos, BlockState> extraPosMap) {
        int voidY = MinecraftClient.getInstance().world.getBottomY();
        GcorePlayerUtil.PlayerState state = GcorePlayerUtil.PlayerState.Normal;

        double step = 0.0;
        while (step <= 200.0) {
            boolean tryUp = type != GcoreTpOption.TpVClipFindType.OnlyDown;
            boolean tryDown = type != GcoreTpOption.TpVClipFindType.OnlyUp;

            if (tryUp) {
                Vec3d apex = tryArchApex(fromVec, toVec, fromVec.y + step, voidY,
                    allowIntoVoid, extraPosMap, state);
                if (apex != null) {
                    return apex;
                }
            }
            if (tryDown) {
                Vec3d apex = tryArchApex(fromVec, toVec, fromVec.y - step, voidY,
                    allowIntoVoid, extraPosMap, state);
                if (apex != null) {
                    return apex;
                }
            }

            step += searchStep;
        }
        return null;
    }

    /**
     * 检验单个拱顶高度是否可行。可行则返回拱顶起点，否则 {@code null}。
     *
     * <p>四道检查（缺一不可）：
     * <ol>
     *     <li>拱顶起点 {@code (from.x, apexY, from.z)} 不卡方块</li>
     *     <li>拱顶终点 {@code (to.x,   apexY, to.z)} 不卡方块</li>
     *     <li>竖直两段可走：{@code from → 拱顶起点} 与 {@code 拱顶终点 → to}</li>
     *     <li><b>水平段可走</b>：{@code 拱顶起点 → 拱顶终点} —— 这是拱门的核心</li>
     * </ol>
     */
    private static Vec3d tryArchApex(Vec3d fromVec, Vec3d toVec, double apexY, int voidY,
                                     boolean allowIntoVoid, Map<BlockPos, BlockState> extraPosMap,
                                     GcorePlayerUtil.PlayerState state) {
        if (allowIntoVoid && fromVec.y <= (double) voidY) {
            return null;
        }
        if (apexY <= (double) voidY) {
            return null;
        }

        Vec3d apexStart = new Vec3d(fromVec.x, apexY, fromVec.z);
        Vec3d apexEnd = new Vec3d(toVec.x, apexY, toVec.z);

        // ①② 两端落点不能卡在方块里
        if (!GcoreBlockUtil.checkNoPosCollie(apexStart, extraPosMap, state)
            || !GcoreBlockUtil.checkNoPosCollie(apexEnd, extraPosMap, state)) {
            return null;
        }

        // ③ 竖直两段（x/z 不变，纯 Y）
        if (!canMoveArch(fromVec, apexStart, extraPosMap, state)
            || !canMoveArch(apexEnd, toVec, extraPosMap, state)) {
            return null;
        }

        // ④ ★ 水平段（y 不变，纯 x/z）—— 拱门的关键
        if (!canMoveArch(apexStart, apexEnd, extraPosMap, state)) {
            return null;
        }

        return apexStart;
    }

    /**
     * 拱门专用的可达性判定 —— **强制以 {@code onGround = false} 判定**。
     *
     * <p><b>为什么必须这样（这是一个真实的翻车点）</b>：服务端
     * {@code Entity.collide()} 的「自动上台阶」分支有一道 {@code onGround} 门槛：
     * <pre>
     * boolean collidedDownwards = collidedY &amp;&amp; movement.y &lt; 0.0;
     * if ((!collidedDownwards &amp;&amp; !this.onGround) || (!collidedX &amp;&amp; !collidedZ))
     *     return collided;                        // ★ onGround=false 时**不给**自动抬升
     * </pre>
     * 而拱门的所有包都申报 {@code onGround = false}（为了压住摔落伤害，
     * 见 {@link #clearFallDebt}）。若判定时仍用客户端的 {@code player.isOnGround()}
     * （站在篮子地面上 = {@code true}），就会**把服务端根本不会给的抬升算进去**，
     * 从而批准一段实际会被拒的水平位移。
     *
     * <p><b>实测（10:17-10:19 日志）</b>：拱顶取在 {@code y=72.8}，
     * 而墙顶在 73.0 —— 玩家碰撞箱下沿只比墙顶低 0.2，落在
     * {@code stepHeight = 0.6} 的自动抬升范围内。以 {@code onGround=true} 判定时
     * 服务端「能上台阶」，于是水平段被放行；但包申报 {@code false}，
     * 服务端不给抬升 → 水平直撞墙 → {@code MOVED_WRONGLY} + {@code CLIPPED_INTO_BLOCK}
     * → **回弹**。同一段几何的对照：
     * <pre>
     * onGround=true  → dxz2 = 0.0000  → 放行（错！服务端实际拒绝）
     * onGround=false → dxz2 = 3.8926  → 拒绝（与服务端一致）
     * </pre>
     * 改用 {@code false} 判定后，搜索会**继续往上找**，取到墙顶之上的
     * {@code y=74.6}，该高度的水平段本就空中畅通 → 三段全过。
     */
    private static boolean canMoveArch(Vec3d fromVec, Vec3d toVec,
                                       Map<BlockPos, BlockState> extraPosMap,
                                       GcorePlayerUtil.PlayerState state) {
        Entity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return true; // 与 canMoveAsServer 相同的保守取向
        }
        return GcoreServerEquivMove.canMoveAsServer(
            fromVec, toVec, extraPosMap, state, player.getStepHeight(), false);
    }

    public static void sendMovePacket(double x, double y, double z, boolean onGround) {
        MinecraftClient.getInstance().getNetworkHandler()
            .sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, onGround, false));
    }

    /** 见 {@link #sendMovePacket}。 */
    public static void sendMovePacket(Vec3d vec3d, boolean onGround) {
        sendMovePacket(vec3d.x, vec3d.y, vec3d.z, onGround);
    }

    // ═══════════════════════ 找「能打到目标的站位」═══════════════════════

    public static final int FIND_VEC_SCAN_BORDER = 7;
    /** {@link #findVecToAttack} 的虚空 Y 门槛（源 370-412 行，与其它地方一致）。 */
    private static final double FIND_VEC_VOID_Y = -64.0;

    public static Vec3d findVecToAttack(Vec3d targetVec, double targetEyeY) {
        int cx = (int) Math.floor(targetVec.x);
        int cy = (int) Math.floor(targetVec.y);
        int cz = (int) Math.floor(targetVec.z);

        int r = FIND_VEC_SCAN_BORDER;

        for (int xf = cx - r; xf <= cx + r; xf++) {
            for (int yf = cy - r; yf <= cy + r; yf++) {
                for (int zf = cz - r; zf <= cz + r; zf++) {
                    if (yf > FIND_VEC_VOID_Y) {
                        Vec3d vec = new Vec3d(xf + 0.5, yf + 0.0, zf + 0.5);

                        if (GcoreBlockUtil.checkNoPosCollie(vec)
                            && GcoreAimUtil.checkCanHit(vec, 1.62, targetVec, targetEyeY, 6.0, 3.0)) {
                            return vec;
                        }
                    }
                }
            }
        }

        return null;
    }

    /**
     * 简单传送：按 {@code moveDistance} 把一段位移拆成多个包。
     *
     * <p>与 {@link #doTpMove} 不同，这个方法**只发包、不做穿墙搜索、不回来**。
     */
    public static void doTp(double fromX, double fromY, double fromZ,
                            double toX, double toY, double toZ,
                            double moveDistance, boolean onGround) {
        double squaredDis = GcoreMathUtil.squaredDistance(fromX, fromY, fromZ, toX, toY, toZ);
        int step = (int) Math.ceil(Math.sqrt(squaredDis) / moveDistance);
        for (int i = 1; i < step; i++) {
            sendMovePacket(fromX, fromY, fromZ, onGround);
        }
        sendMovePacket(toX, toY, toZ, onGround);
    }

    /** 见 {@link #doTp}。 */
    public static void doTp(Vec3d fromVec, Vec3d toVec, double moveDistance, boolean onGround) {
        doTp(fromVec.x, fromVec.y, fromVec.z, toVec.x, toVec.y, toVec.z, moveDistance, onGround);
    }

    /**
     * 移动包计数器。
     *
     * <p>每发一个包自动 +1，可被 {@code action} 回调读取 ——
     * 便于调试「这轮到底发了多少包」。
     */
    public static final class MovePacketCounter {

        private int packetCount;

        public int getPacketCount() {
            return this.packetCount;
        }

        public void setPacketCount(int packetCount) {
            this.packetCount = packetCount;
        }

        /**
         * 计数并发包。
         *
         * <p><b>关于 {@code onGround}</b>：源调用点统一传 {@code false}，
         * 但 Kotlin 的默认参数掩码（{@code n & 8}）会把它改成 {@code true}
         * —— 即**实际发出的所有 TP 包都声明「在地面」**（比悬空更保守，
         * 不容易触发重力检查）。移植后**直接传 true** 以还原真实行为。
         */
        public void sendC04(double x, double y, double z, boolean onGround) {
            this.packetCount = this.packetCount + 1;
            MinecraftClient.getInstance().getNetworkHandler()
                .sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, onGround, false));
        }

        public void sendC04(double x, double y, double z) {
            sendC04(x, y, z, true);
        }
    }
}
