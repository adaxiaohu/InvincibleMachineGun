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

    // 废弃 Tick 设置，改为更直观且安全的毫秒 (ms) 设置
    private final Setting<Integer> minDelay = sgGeneral.add(new IntSetting.Builder()
        .name("min-delay-ms")
        .description("操作最小延迟(毫秒) - 建议150")
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
        // 修复编译错误：根据报错，你的映射可能需要 getStatus() 和 getEntityId()
        // 或者在 1.21.11 中，EntityStatusS2CPacket 依然通过 getEntity 方法访问
        
        // 【1.21.11 安全写法】：尝试兼容 Record 和 旧版映射
        // 如果 packet.status() 报错，说明映射名为 getStatus()
        if (packet.getStatus() == 35) { 
            // 同样，如果 entityId() 报错，尝试 getEntityId() 或通过 world 查找
            // 在 1.21.11 中，最稳妥的判断是：
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

        // 原有兜底检测：如果本地检测到图腾消耗（补充网络包没抓到的情况）
        if (hadTotemInOffhand && !currentlyHasTotem && !needsTotem) {
            needsTotem = true;
            long humanJitter = minDelay.get() + (long)(Math.random() * (maxDelay.get() - minDelay.get() + 1));
            executeTime = System.currentTimeMillis() + humanJitter;
        }

        hadTotemInOffhand = currentlyHasTotem;

        // 执行拿图腾动作：必须处于打开背包的状态，并且到达了仿生延迟时间
        if (needsTotem && mc.currentScreen instanceof InventoryScreen) {
            if (System.currentTimeMillis() >= executeTime) {
                executeSafeSwap();
            }
        }

        // 已经有图腾则重置状态
        if (currentlyHasTotem && needsTotem) {
            needsTotem = false;
        }
    }

    /**
     * 安全的交换逻辑：规避 Grim 的 InventoryTweaks 检测
     */
    private void executeSafeSwap() {
        int totemSlot = findTotemSlot();
        if (totemSlot == -1) return;

        try {
            // 将玩家背包槽位索引转为容器槽位索引 (1-36区)
            int containerSlot = totemSlot < 9 ? totemSlot + 36 : totemSlot;
            
            // 【安全强化】直接获取 playerScreenHandler 的同步ID，确保是玩家自身背包的 ID (通常为 0)
            int syncId = mc.player.playerScreenHandler.syncId; 

            // 发送原版的 SWAP(40) 数据包，模拟玩家按下副手切换键
            mc.interactionManager.clickSlot(
                syncId, 
                containerSlot, 
                40, 
                SlotActionType.SWAP, 
                mc.player
            );

            if (!disableLogs.get()) {
                info("Safely swapped totem to offhand (Bypassed Anticheat).");
            }

            needsTotem = false;
            
            // 操作后额外增加冷却惩罚，防止异常的二次发包
            executeTime = System.currentTimeMillis() + 500; 

        } catch (Exception e) {
            if (!disableLogs.get()) error("Failed to move totem.");
        }
    }

    private int findTotemSlot() {
        // 优先搜寻主背包 (9-35)
        for (int i = 9; i < 36; i++) {
            if (isTotem(mc.player.getInventory().getStack(i))) return i;
        }
        // 如果开启了快捷栏搜寻 (0-8)
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

    // 【1.21.11 准则】彻底废弃 Items.TOTEM_OF_UNDYING 的直接调用，改用安全的 String 校验
    private boolean isTotem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem().toString().toLowerCase().contains("totem_of_undying");
    }
}