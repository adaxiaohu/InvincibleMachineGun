package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.mixin.InventoryAccessor;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;

public class adaAttributeSwap extends Module {
    public adaAttributeSwap() {
        super(AddonTemplate.CATEGORY, "矛冲刺", "自动属性交换，可以自动换成矛冲刺，可以优化一下成指定某个快捷键按下时候触发。没有测试是否能在反作弊环境使用");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<TriggerMode> triggerMode = sgGeneral.add(new EnumSetting.Builder<TriggerMode>()
        .name("触发模式")
        .description("Reach: 剑切矛获取距离; Dash: 杂物切矛触发突进。")
        .defaultValue(TriggerMode.Dash)
        .build()
    );

    private final Setting<Boolean> packetSwap = sgGeneral.add(new BoolSetting.Builder()
        .name("数据包切换")
        .description("强制向服务器发送切换包，提高服务器成功率。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> stayTicks = sgGeneral.add(new IntSetting.Builder()
        .name("持有刻")
        .description("在手中持有目标武器多少刻后再切回(服务器建议 1-3)。")
        .defaultValue(2)
        .min(1)
        .sliderMax(10)
        .build()
    );

    private int originalSlot = -1;
    private int timer = 0;
    private boolean isSwapping = false;
    private boolean wasAttackPressed = false;

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // 1. 处理切回逻辑
        if (isSwapping) {
            timer++;
            if (timer >= stayTicks.get()) {
                executeSwap(originalSlot);
                resetSwap();
            }
            return;
        }

        // 2. 检测左键按下（处理长按和单击的判定）
        boolean isAttackPressed = mc.options.attackKey.isPressed();
        
        if (isAttackPressed && !wasAttackPressed) {
            ItemStack mainHand = mc.player.getMainHandStack();

            if (isCorrectTrigger(mainHand)) {
                FindItemResult target = InvUtils.findInHotbar(this::isTargetItem);

                if (target.found() && target.slot() != getCurrentSlot()) {
                    originalSlot = getCurrentSlot();
                    
                    // 【核心】切换到目标
                    executeSwap(target.slot());
                    
                    // 标记状态
                    isSwapping = true;
                    timer = 0;
                }
            }
        }
        wasAttackPressed = isAttackPressed;
    }

    /**
     * 执行切换逻辑：支持客户端和数据包同步
     */
    private void executeSwap(int slot) {
        if (slot == -1) return;
        
        // 修改客户端本地显示
        ((InventoryAccessor) mc.player.getInventory()).setSelectedSlot(slot);
        
        // 【关键】如果是服务器环境，发送数据包告知服务器
        if (packetSwap.get() && mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }
    }

    private int getCurrentSlot() {
        return ((InventoryAccessor) mc.player.getInventory()).getSelectedSlot();
    }

    private void resetSwap() {
        isSwapping = false;
        originalSlot = -1;
        timer = 0;
    }

    private boolean isCorrectTrigger(ItemStack stack) {
        // 1.21.11 字符串判定规则
        String itemStr = stack.getItem().toString().toLowerCase();
        return switch (triggerMode.get()) {
            case Reach -> itemStr.contains("sword") || itemStr.contains("_axe");
            case Dash -> !itemStr.contains("sword") && !itemStr.contains("_axe") && !itemStr.contains("mace") && !itemStr.contains("spear") && !itemStr.contains("trident");
            case Any -> true;
        };
    }

    private boolean isTargetItem(ItemStack stack) {
        String itemStr = stack.getItem().toString().toLowerCase();
        // 目标是矛、重锤或三叉戟
        return itemStr.contains("spear") || itemStr.contains("mace") || itemStr.contains("trident");
    }

    public enum TriggerMode {
        Reach, Dash, Any
    }

    @Override
    public void onDeactivate() {
        if (isSwapping && originalSlot != -1) {
            executeSwap(originalSlot);
        }
        resetSwap();
    }
}