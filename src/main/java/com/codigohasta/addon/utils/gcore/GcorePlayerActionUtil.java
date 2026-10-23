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

public final class GcorePlayerActionUtil {

    private GcorePlayerActionUtil() {
    }

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

    public static void doAttackEntity(Entity entity, boolean sneaking) {
        GcoreDoActionUtil.send(PlayerInteractEntityC2SPacket.attack(entity, sneaking));
    }

    /** 见 {@link #doAttackEntity(Entity, boolean)}，{@code sneaking} 取源默认值 {@code false}。 */
    public static void doAttackEntity(Entity entity) {
        doAttackEntity(entity, false);
    }
}
