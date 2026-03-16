package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Hand;

public class FastCrossbow extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public enum Mode {
        Native,  // AutoChorus同款逻辑，最稳
        Control, // 计算时间，极速
        Packet   // 暴力发包，激进
    }

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("模式")
        .description("Native: 最流畅(推荐); Control: 理论极限; Packet: 暴力。")
        .defaultValue(Mode.Native)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("射击延迟")
        .description("发射后的冷却Tick。建议 Native 设为 2-3，Control/Packet 设为 3-5。")
        .defaultValue(3)
        .min(0)
        .max(10)
        .build()
    );

    private final Setting<Integer> tolerance = sgGeneral.add(new IntSetting.Builder()
        .name("装填容错")
        .description("仅对 Control/Packet 模式有效。多拉几tick防止回弹。")
        .defaultValue(6)
        .min(0)
        .max(50)
        .visible(() -> mode.get() != Mode.Native)
        .build()
    );

    private int timer = 0;

    public FastCrossbow() {
        super(AddonTemplate.CATEGORY, "FastCrossbow", "三模式的机关弩，无情的机关枪。");
    }

    @Override
    public void onActivate() {
        timer = 0;
    }

    @Override
    public void onDeactivate() {
        mc.options.useKey.setPressed(false);
        if (mc.player != null) mc.interactionManager.stopUsingItem(mc.player);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        ItemStack handStack = mc.player.getMainHandStack();
        if (!(handStack.getItem() instanceof CrossbowItem)) return;

        // 必须按住右键才工作
        if (!mc.options.useKey.isPressed()) {
            return;
        }

        // 处理冷却
        if (timer > 0) {
            timer--;
            // 冷却期间松开按键，让状态同步
            mc.options.useKey.setPressed(false);
            return;
        }

        // 根据模式分发逻辑
        switch (mode.get()) {
            case Native -> handleNativeMode(handStack);
            case Control -> handleControlMode(handStack);
            case Packet -> handlePacketMode(handStack);
        }
    }

    // --- 模式 1: Native (AutoChorus同款) ---
    // 最稳，最流畅，利用客户端原生逻辑
    private void handleNativeMode(ItemStack stack) {
        if (CrossbowItem.isCharged(stack)) {
            // 满了 -> 发射
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.swingHand(Hand.MAIN_HAND);
            timer = delay.get();
        } else {
            // 没满 -> 按死右键
            mc.options.useKey.setPressed(true);
            
            // 确保拉弓动作开始
            if (!mc.player.isUsingItem()) {
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            }
        }
    }

    // --- 模式 2: Control (计算时间) ---
    // 理论上比原生更快，因为是时间一到强制发包松手
    private void handleControlMode(ItemStack stack) {
        if (CrossbowItem.isCharged(stack)) {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.swingHand(Hand.MAIN_HAND);
            timer = delay.get();
            return;
        }

        mc.options.useKey.setPressed(true);
        if (!mc.player.isUsingItem()) {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            return;
        }

        int requiredTime = getPullTime(stack) + tolerance.get();
        if (mc.player.getItemUseTime() >= requiredTime) {
            mc.interactionManager.stopUsingItem(mc.player);
            // 这里不设timer，让下一tick直接判断发射
        }
    }

    // --- 模式 3: Packet (暴力发包) ---
    // 激进，尝试更快的响应
    private void handlePacketMode(ItemStack stack) {
        if (CrossbowItem.isCharged(stack)) {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.swingHand(Hand.MAIN_HAND);
            timer = delay.get();
        } // 移除 return，尝试同一tick触发装填

        if (!mc.player.isUsingItem()) {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.options.useKey.setPressed(true);
            return;
        }

        mc.options.useKey.setPressed(true);
        
        int requiredTime = getPullTime(stack) + tolerance.get();
        if (mc.player.getItemUseTime() >= requiredTime) {
            mc.interactionManager.stopUsingItem(mc.player);
        }
    }

    // 计算装填时间 (1.21.4 适配)
    private int getPullTime(ItemStack stack) {
        try {
            var registry = mc.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
            var quickChargeEntry = registry.getOrThrow(Enchantments.QUICK_CHARGE);
            int level = EnchantmentHelper.getLevel(quickChargeEntry, stack);
            return Math.max(0, 25 - 5 * level);
        } catch (Exception e) {
            return 25;
        }
    }
}