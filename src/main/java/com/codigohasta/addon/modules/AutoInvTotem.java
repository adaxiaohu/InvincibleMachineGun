package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.entity.Entity;
import net.minecraft.screen.slot.SlotActionType;

public class AutoInvTotem extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Integer> minDelay = sgGeneral.add(new IntSetting.Builder()
        .name("min-delay-ms")
        .description("操作最小延迟(毫秒) ")
        .defaultValue(200)
        .min(50)
        .max(1000)
        .build()
    );

    private final Setting<Integer> maxDelay = sgGeneral.add(new IntSetting.Builder()
        .name("max-delay-ms")
        .description("操作最大延迟(毫秒)")
        .defaultValue(350)
        .min(50)
        .max(1000)
        .build()
    );

    private final Setting<Boolean> moveFromHotbar = sgGeneral.add(new BoolSetting.Builder()
        .name("move-from-hotbar")
        .description("是否从快捷栏拿取")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> disableLogs = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-logs")
        .description("禁用聊天栏提示")
        .defaultValue(true)
        .build()
    );

    // 状态机变量
    private boolean needsTotem = false;
    private long executeTime = 0; // 高精度毫秒执行时间
    private boolean hadTotemInOffhand = false;

    public AutoInvTotem() {
        super(AddonTemplate.CATEGORY, "Legit自动图腾", "企图合法换图腾");
    }

    @Override
    public void onActivate() {
        resetState();
    }

    @Override
    public void onDeactivate() {
        resetState();
    }

    private void resetState() {
        needsTotem = false;
        executeTime = 0;
        if (mc.player != null) {
            hadTotemInOffhand = hasTotemInOffhand();
        }
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        resetState();
    }
@EventHandler
private void onPacketReceive(PacketEvent.Receive event) {
    if (mc.player == null) return;

    if (event.packet instanceof EntityStatusS2CPacket packet) {
        if (packet.getStatus() == 35) { 
            Entity entity = packet.getEntity(mc.world);
            if (entity != null && entity.getId() == mc.player.getId()) {
                needsTotem = true;
                long humanJitter = minDelay.get() + (long)(Math.random() * (maxDelay.get() - minDelay.get() + 1));
                executeTime = System.currentTimeMillis() + humanJitter;
            }
        }
    }
}

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        boolean currentlyHasTotem = hasTotemInOffhand();
        if (hadTotemInOffhand && !currentlyHasTotem && !needsTotem) {
            needsTotem = true;
            long humanJitter = minDelay.get() + (long)(Math.random() * (maxDelay.get() - minDelay.get() + 1));
            executeTime = System.currentTimeMillis() + humanJitter;
        }

        hadTotemInOffhand = currentlyHasTotem;
        if (needsTotem && mc.currentScreen instanceof InventoryScreen) {
            if (System.currentTimeMillis() >= executeTime) {
                executeSafeSwap();
            }
        }
        if (currentlyHasTotem && needsTotem) {
            needsTotem = false;
        }
    }

    
    private void executeSafeSwap() {
        int totemSlot = findTotemSlot();
        if (totemSlot == -1) return;

        try {
            int containerSlot = totemSlot < 9 ? totemSlot + 36 : totemSlot;
            int syncId = mc.player.playerScreenHandler.syncId; 
            mc.interactionManager.clickSlot(
                syncId, 
                containerSlot, 
                40, 
                SlotActionType.SWAP, 
                mc.player
            );

            if (!disableLogs.get()) {
                info("Safely swapped totem to offhand.");
            }

            needsTotem = false;
            executeTime = System.currentTimeMillis() + 500; 

        } catch (Exception e) {
            if (!disableLogs.get()) error("Failed to move totem.");
        }
    }

    private int findTotemSlot() {
        for (int i = 9; i < 36; i++) {
            if (isTotem(mc.player.getInventory().getStack(i))) return i;
        }
        if (moveFromHotbar.get()) {
            for (int i = 0; i < 9; i++) {
                if (isTotem(mc.player.getInventory().getStack(i))) return i;
            }
        }
        return -1;
    }

    private boolean hasTotemInOffhand() {
        if (mc.player == null) return false;
        return isTotem(mc.player.getOffHandStack());
    }

    private boolean isTotem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem().toString().toLowerCase().contains("totem_of_undying");
    }
}
