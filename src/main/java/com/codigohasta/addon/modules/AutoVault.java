package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.mixin.InventoryAccessor;
import com.codigohasta.addon.utils.Timer;
import com.codigohasta.addon.utils.leaveshack.InventoryUtil;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.client.multiplayer.prediction.PredictiveAction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class AutoVault extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("Mode")
        .description("Manual: 按右键触发 / Auto: 看向宝库自动开")
        .defaultValue(Mode.Manual)
        .build()
    );
    private final Setting<SwapMode> swapMode = sgGeneral.add(new EnumSetting.Builder<SwapMode>()
        .name("SwapMode")
        .description("Inventory: 静默切换 / Hotbar: 切快捷栏")
        .defaultValue(SwapMode.Inventory)
        .build()
    );
    private final Setting<Boolean> inventorySwap = sgGeneral.add(new BoolSetting.Builder()
        .name("InventorySwap")
        .description("允许从背包静默换钥匙")
        .defaultValue(true)
        .build()
    );
    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("Range")
        .description("最大操作距离")
        .defaultValue(5.0)
        .min(1.0)
        .max(10.0)
        .sliderRange(1.0, 10.0)
        .build()
    );
    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("Delay")
        .description("Auto模式操作间隔(ms)")
        .defaultValue(200)
        .min(0)
        .max(1000)
        .sliderRange(0, 1000)
        .build()
    );

    private final Timer timer = new Timer();
    private boolean manualUsed = false;

    public AutoVault() {
        super(AddonTemplate.CATEGORY, "自动开宝库", "右键宝库自动切换对应钥匙并开启");
    }

    @Override
    public void onActivate() {
        timer.setMs(99999);
        manualUsed = false;
    }

    public enum Mode { Manual, Auto }
    public enum SwapMode { Inventory, Hotbar }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;
        if (mc.screen != null) return;
        if (!(mc.hitResult instanceof BlockHitResult hitResult)) return;

        BlockPos pos = hitResult.getBlockPos();
        Block block = mc.level.getBlockState(pos).getBlock();
        if (block != Blocks.VAULT) return;

        if (mc.player.getEyePosition().distanceToSqr(pos.getCenter()) > range.get() * range.get()) return;

        if (mode.get() == Mode.Manual) {
            if (!mc.options.keyUse.isDown()) {
                manualUsed = false;
                return;
            }
            if (manualUsed) return;
            manualUsed = true;
        } else {
            if (!timer.passedMs(delay.get())) return;
        }

        boolean ominous = mc.level.getBlockState(pos).getValue(VaultBlock.OMINOUS);
        Item targetKey = ominous ? Items.OMINOUS_TRIAL_KEY : Items.TRIAL_KEY;

        boolean keyInHand = mc.player.getMainHandItem().getItem() == targetKey;

        int hotbarSlot = -1;
        int invSlot = -1;

        if (!keyInHand) {
            hotbarSlot = InventoryUtil.findItem(targetKey);
            if (hotbarSlot == -1 && inventorySwap.get()) {
                if (mc.player.getOffhandItem().getItem() == targetKey) {
                    keyInHand = true;
                } else {
                    invSlot = InventoryUtil.findItemInventorySlot(targetKey);
                }
            }
        }

        if (!keyInHand && hotbarSlot == -1 && invSlot == -1) return;

        int oldSlot = ((InventoryAccessor) mc.player.getInventory()).getSelectedSlot();

        if (!keyInHand) {
            if (swapMode.get() == SwapMode.Hotbar || hotbarSlot != -1) {
                int slot = hotbarSlot != -1 ? hotbarSlot : invSlot;
                if (slot >= 0 && slot <= 8) {
                    InventoryUtil.switchToSlot(slot);
                } else {
                    return;
                }
            } else if (invSlot != -1) {
                InventoryUtil.inventorySwap(invSlot, oldSlot);
            }
        }

        sendSequencedPacket(id -> new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, hitResult, id));

        if (!keyInHand) {
            if (swapMode.get() == SwapMode.Hotbar) {
                if (hotbarSlot != -1) {
                    InventoryUtil.switchToSlot(oldSlot);
                }
            } else if (invSlot != -1) {
                InventoryUtil.inventorySwap(invSlot, oldSlot);
                mc.getConnection().send(new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
            }
        }

        timer.reset();
    }

    private void sendSequencedPacket(PredictiveAction packetCreator) {
        if (mc.getConnection() == null || mc.level == null) return;
        try (BlockStatePredictionHandler pendingUpdateManager = mc.level.getBlockStatePredictionHandler().startPredicting()) {
            int i = pendingUpdateManager.currentSequence();
            mc.getConnection().send(packetCreator.predict(i));
        }
    }
}
