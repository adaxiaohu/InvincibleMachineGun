package com.codigohasta.addon.modules;

import net.minecraft.world.phys.Vec3;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;

public class AutoFirework extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // 设置：按键绑定
    private final Setting<Keybind> keybind = sgGeneral.add(new KeybindSetting.Builder()
        .name("keybind")
        .description("The key to press to use a firework.")
        .defaultValue(Keybind.none())
        .build()
    );

    // 设置：延迟 (防止按住时瞬间消耗过多)
    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("The delay between using fireworks in ticks.")
        .defaultValue(10)
        .min(1)
        .sliderMax(40)
        .build()
    );

    private int timer = 0;

    public AutoFirework() {
        super(AddonTemplate.CATEGORY, "打烟花", "用烟花飞。应该没用的功能");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;

        // 计时器倒退
        if (timer > 0) {
            timer--;
        }

        // 1. 检查是否在滑翔 (1.21.4 使用 isGliding)
        if (!mc.player.isFallFlying()) return;

        // 2. 检查按键是否按下
        if (!keybind.get().isPressed()) return;

        // 3. 检查冷却
        if (timer > 0) return;

        // 4. 执行逻辑
        // 优先使用手上的，如果没有则去快捷栏找
        if (isFirework(mc.player.getMainHandItem())) {
            useFirework(InteractionHand.MAIN_HAND);
        } else if (isFirework(mc.player.getOffhandItem())) {
            useFirework(InteractionHand.OFF_HAND);
        } else {
            int slot = findFireworkSlot();
            if (slot != -1) {
                useFireworkSilent(slot);
            }
        }
    }

    // 普通使用（手上已有）
    private void useFirework(InteractionHand hand) {
        // 1.21.4 必须传入 sequence, yaw, pitch
        mc.getConnection().send(new ServerboundUseItemPacket(
            hand, 
            0, 
            mc.player.getYRot(), 
            mc.player.getXRot()
        ));
        
        mc.player.swing(hand);
        timer = delay.get();
    }

    // 静默使用（从快捷栏切换）
    private void useFireworkSilent(int slot) {
        int prevSlot = ((com.codigohasta.addon.mixin.InventoryAccessor) mc.player.getInventory()).getSelectedSlot();

        // 1. 发包切槽位
        mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));

        // 2. 发包使用 (服务器认为现在主手是烟花)
        mc.getConnection().send(new ServerboundUseItemPacket(
            InteractionHand.MAIN_HAND, 
            0, 
            mc.player.getYRot(), 
            mc.player.getXRot()
        ));

        // 3. 发包切回原槽位
        mc.getConnection().send(new ServerboundSetCarriedItemPacket(prevSlot));
        
        // 如果想要完全隐蔽，可以把下面的挥手注释掉
        // mc.player.swingHand(InteractionHand.MAIN_HAND); 
        
        timer = delay.get();
    }

    private int findFireworkSlot() {
        for (int i = 0; i < 9; i++) {
            if (isFirework(mc.player.getInventory().getItem(i))) {
                return i;
            }
        }
        return -1;
    }

    private boolean isFirework(ItemStack stack) {
        return stack.getItem() instanceof FireworkRocketItem;
    }
}