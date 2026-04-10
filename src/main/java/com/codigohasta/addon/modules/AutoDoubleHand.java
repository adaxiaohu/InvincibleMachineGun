package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;

public class AutoDoubleHand extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // 增加延迟设置，这是防封号的核心
    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("switch-delay")
        .description("爆图腾后切主手图腾的延迟 (Tick)，建议至少为 2")
        .defaultValue(2)
        .min(1)
        .max(10)
        .build()
    );

    private boolean wasHoldingTotem = false;
    private boolean needsSwitch = false;
    private int delayTimer = 0;

    public AutoDoubleHand() {
        super(AddonTemplate.CATEGORY, "Legit自动双持", "爆图腾后切至快捷栏图腾");
    }

    @Override
    public void onActivate() {
        if (mc.player != null) {
            wasHoldingTotem = hasTotemInOffhand();
        }
        needsSwitch = false;
        delayTimer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        boolean holdingNow = hasTotemInOffhand();

        // 1. 状态机：监测图腾丢失 (爆破)
        if (wasHoldingTotem && !holdingNow) {
            needsSwitch = true;
            // 设定延迟，并在延迟上加入微小的随机扰动防严苛AC
            delayTimer = delay.get() + (Math.random() > 0.5 ? 1 : 0); 
        }

        wasHoldingTotem = holdingNow;

        // 2. 延迟队列执行
        if (needsSwitch) {
            if (delayTimer > 0) {
                delayTimer--;
            } else {
                executeSafeSwitch();
                needsSwitch = false;
            }
        }
    }

    private void executeSafeSwitch() {
        int slot = findHotbarTotem();
        if (slot != -1) {
            // 【核心修复】：1.21.11 中 selectedSlot 变为 private，无法直接读取。
            // 改为通过判断主手物品是否已经是图腾，来决定是否需要发送切换数据包。
            if (!mc.player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                
                // 使用 Meteor Client 的安全工具类进行无缝切换
                InvUtils.swap(slot, false);
                
            }
        }
    }

    private int findHotbarTotem() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
                return i;
            }
        }
        return -1;
    }

    private boolean hasTotemInOffhand() {
        return mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
    }
}