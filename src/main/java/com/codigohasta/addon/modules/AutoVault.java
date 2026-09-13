package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.mixin.InventoryAccessor;
import com.codigohasta.addon.utils.Timer;
import com.codigohasta.addon.utils.leaveshack.InventoryUtil;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.VaultBlock;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.client.network.SequencedPacketCreator;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

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
        if (mc.player == null || mc.world == null) return;
        if (mc.currentScreen != null) return;
        if (!(mc.crosshairTarget instanceof BlockHitResult hitResult)) return;

        BlockPos pos = hitResult.getBlockPos();
        Block block = mc.world.getBlockState(pos).getBlock();
        if (block != Blocks.VAULT) return;

        if (mc.player.getEyePos().squaredDistanceTo(pos.toCenterPos()) > range.get() * range.get()) return;

        if (mode.get() == Mode.Manual) {
            if (!mc.options.useKey.isPressed()) {
                manualUsed = false;
                return;
            }
            if (manualUsed) return;
            manualUsed = true;
        } else {
            if (!timer.passedMs(delay.get())) return;
        }

        boolean ominous = mc.world.getBlockState(pos).get(VaultBlock.OMINOUS);
        Item targetKey = ominous ? Items.OMINOUS_TRIAL_KEY : Items.TRIAL_KEY;

        boolean keyInHand = mc.player.getMainHandStack().getItem() == targetKey;

        int hotbarSlot = -1;
        int invSlot = -1;

        if (!keyInHand) {
            hotbarSlot = InventoryUtil.findItem(targetKey);
            if (hotbarSlot == -1 && inventorySwap.get()) {
                if (mc.player.getOffHandStack().getItem() == targetKey) {
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

        sendSequencedPacket(id -> new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hitResult, id));

        if (!keyInHand) {
            if (swapMode.get() == SwapMode.Hotbar) {
                if (hotbarSlot != -1) {
                    InventoryUtil.switchToSlot(oldSlot);
                }
            } else if (invSlot != -1) {
                InventoryUtil.inventorySwap(invSlot, oldSlot);
                mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
            }
        }

        timer.reset();
    }

    private void sendSequencedPacket(SequencedPacketCreator packetCreator) {
        if (mc.getNetworkHandler() == null || mc.world == null) return;
        try (PendingUpdateManager pendingUpdateManager = mc.world.getPendingUpdateManager().incrementSequence()) {
            int i = pendingUpdateManager.getSequence();
            mc.getNetworkHandler().sendPacket(packetCreator.predict(i));
        }
    }
}
