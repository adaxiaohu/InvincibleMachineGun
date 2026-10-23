package com.codigohasta.addon.utils.gcore;

import net.minecraft.block.AbstractChestBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * 玩家动作发包工具。
 *
 * <p>移植自 {@code dev.godden.core.Utils.PlayerActionUtil}（GCore）。
 *
 * <p><b>裁剪说明</b>：源类有 255 行、9 个方法。TP 锚**只用到
 * {@link #doPlaceAt(Hand, BlockPos, Direction)}** 一个入口
 * （经它间接用到 4 参重载与 3 参重载）。其余方法（{@code turnHead} /
 * {@code doUseItem} / {@code doBreakAt} / {@code doAttackEntity} /
 * {@code doPacketAction}）只有别的模块在用（弓/活塞/Nuker/PacketMine），
 * 按「按需裁剪」原则**不移植**。
 *
 * <p><b>★ 序列号（sequence）</b>：源用 {@code ReflectUtil} 反射拿
 * {@code ClientWorld.pendingUpdateManager} 再调 {@code incrementSequence()} /
 * {@code getSequence()}。**Meteor + Yarn 1.21.11 下这两个方法是公开的**，
 * 所以这里直调，不用反射（语义完全一致，只是去掉了不必要的反射层）。
 *
 * <p><b>⚠️ 源实现有个 bug，这里保留其行为</b>：{@code sneaking} 为真时，
 * 源**只 new 了 {@code ClientCommandC2SPacket} 却没有发出去**：
 * <pre>
 * if (sneaking) { new ClientCommandC2SPacket(player, Mode.PRESS_SHIFT_KEY); }  // 没 send
 * send(new PlayerInteractBlockC2SPacket(...));
 * if (sneaking) { new ClientCommandC2SPacket(player, Mode.RELEASE_SHIFT_KEY); } // 没 send
 * </pre>
 * 也就是说**潜行开关包从来没发出去**，{@code sneaking} 实际不影响任何行为。
 *
 * <p><b>移植处理</b>：既然发了也是空操作，而 1.21.11 的
 * {@code ClientCommandC2SPacket.Mode} **已经删掉了 PRESS/RELEASE_SHIFT_KEY**
 * （现存 7 个值里没有它），这两行**无法编译**。因为它们本来就无副作用，
 * 直接**省略**是行为等价的。{@code sneaking} 参数仍保留以维持调用签名一致。
 */
public final class GcorePlayerActionUtil {

    private GcorePlayerActionUtil() {
    }

    /**
     * 朝某个方块面放置。
     *
     * <p>{@code sneaking} 参数保留但**无实际作用**（见类注释：源里那两个包
     * 从未被发送，且 1.21.11 已移除对应的 Mode 枚举值）。
     */
    public static void doPlaceAt(Hand hand, BlockHitResult blockHitResult, boolean sneaking) {
        var world = MinecraftClient.getInstance().world;
        world.getPendingUpdateManager().incrementSequence();
        int sequenceId = world.getPendingUpdateManager().getSequence();

        // 源在此处 new ClientCommandC2SPacket(player, PRESS_SHIFT_KEY) 但从不发送；
        // 且 1.21.11 的 Mode 已无 SHIFT_KEY 相关值 —— 行为等价的省略。

        GcoreDoActionUtil.send(new PlayerInteractBlockC2SPacket(hand, blockHitResult, sequenceId));

        // 同上，RELEASE_SHIFT_KEY 也省略。
    }

    /** 见 {@link #doPlaceAt}，{@code sneaking} 按「是不是箱子」自动判断。 */
    public static void doPlaceAt(Hand hand, BlockHitResult blockHitResult) {
        boolean sneaking = MinecraftClient.getInstance().world
            .getBlockState(blockHitResult.getBlockPos()).getBlock() instanceof AbstractChestBlock;
        doPlaceAt(hand, blockHitResult, sneaking);
    }

    /**
     * 用坐标 + 朝向构造命中结果后放置。
     *
     * <p>这是 TP 锚调用的入口。命中点取方块中心（源经
     * {@code new BlockHitResult(placePos.toCenterPos(), facing, placePos, false)}）。
     */
    public static void doPlaceAt(Hand hand, BlockPos placePos, Direction placeFacing) {
        doPlaceAt(hand, new BlockPos.Mutable(placePos.getX(), placePos.getY(), placePos.getZ()), placeFacing);
    }

    /** 见 {@link #doPlaceAt(Hand, BlockPos, Direction)}，Mutable 版。 */
    public static void doPlaceAt(Hand hand, BlockPos.Mutable placePos, Direction placeFacing) {
        doPlaceAt(hand,
            new BlockHitResult(placePos.toCenterPos(), placeFacing, placePos, false),
            false);
    }

    /**
     * 攻击实体（发 {@code PlayerInteractEntityC2SPacket}）。
     *
     * <p>移植自源的 {@code PlayerActionUtil.doAttackEntity}（源仅 6 行）：
     * <pre>
     * public fun doAttackEntity(entity: class_1297, sneaking: Boolean = false) {
     *     val var10000: class_2824 = class_2824.method_34206(entity, sneaking);
     *     DoActionUtilKt.send(var10000 as class_2596&lt;?&gt;);
     * }
     * </pre>
     *
     * <p>{@code class_2824} = {@code PlayerInteractEntityC2SPacket}，
     * {@code method_34206} = {@code attack(entity, sneaking)} 静态工厂。
     *
     * <p><b>★ 这是 TP 水晶「打已有水晶」那一步的发包点</b> ——
     * 与 {@code doPlaceAt} 一样，**只能在客户端主线程调用**。
     */
    public static void doAttackEntity(Entity entity, boolean sneaking) {
        GcoreDoActionUtil.send(PlayerInteractEntityC2SPacket.attack(entity, sneaking));
    }

    /** 见 {@link #doAttackEntity(Entity, boolean)}，{@code sneaking} 取源默认值 {@code false}。 */
    public static void doAttackEntity(Entity entity) {
        doAttackEntity(entity, false);
    }
}
