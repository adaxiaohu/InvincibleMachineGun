package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.FireworkRocketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;

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
        super(AddonTemplate.CATEGORY, "auto-firework", "Silently uses fireworks when a custom key is pressed while gliding.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // 计时器倒退
        if (timer > 0) {
            timer--;
        }

        // 1. 检查是否在滑翔 (1.21.4 使用 isGliding)
        if (!mc.player.isGliding()) return;

        // 2. 检查按键是否按下
        if (!keybind.get().isPressed()) return;

        // 3. 检查冷却
        if (timer > 0) return;

        // 4. 执行逻辑
        // 优先使用手上的，如果没有则去快捷栏找
        if (isFirework(mc.player.getMainHandStack())) {
            useFirework(Hand.MAIN_HAND);
        } else if (isFirework(mc.player.getOffHandStack())) {
            useFirework(Hand.OFF_HAND);
        } else {
            int slot = findFireworkSlot();
            if (slot != -1) {
                useFireworkSilent(slot);
            }
        }
    }

    // 普通使用（手上已有）
    private void useFirework(Hand hand) {
        // 1.21.4 必须传入 sequence, yaw, pitch
        mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(
            hand, 
            0, 
            mc.player.getYaw(), 
            mc.player.getPitch()
        ));
        
        mc.player.swingHand(hand);
        timer = delay.get();
    }

    // 静默使用（从快捷栏切换）
    private void useFireworkSilent(int slot) {
        int prevSlot = mc.player.getInventory().selectedSlot;

        // 1. 发包切槽位
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));

        // 2. 发包使用 (服务器认为现在主手是烟花)
        mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(
            Hand.MAIN_HAND, 
            0, 
            mc.player.getYaw(), 
            mc.player.getPitch()
        ));

        // 3. 发包切回原槽位
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(prevSlot));
        
        // 如果想要完全隐蔽，可以把下面的挥手注释掉
        // mc.player.swingHand(Hand.MAIN_HAND); 
        
        timer = delay.get();
    }

    private int findFireworkSlot() {
        for (int i = 0; i < 9; i++) {
            if (isFirework(mc.player.getInventory().getStack(i))) {
                return i;
            }
        }
        return -1;
    }

    private boolean isFirework(ItemStack stack) {
        return stack.getItem() instanceof FireworkRocketItem;
    }
}