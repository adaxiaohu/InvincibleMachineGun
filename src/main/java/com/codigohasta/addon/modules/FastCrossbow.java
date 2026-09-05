package com.codigohasta.addon.modules;

import net.minecraft.world.phys.Vec3;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;

public class FastCrossbow extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public enum Mode {
        Native,  // 模拟按键，最稳
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
        // 更新了描述，使其匹配当前功能
        super(AddonTemplate.CATEGORY, "FastCrossbow", "快速射击弓与弩，无情的机关枪。");
    }

    @Override
    public void onActivate() {
        timer = 0;
    }

    @Override
    public void onDeactivate() {
        mc.options.keyUse.setDown(false);
        if (mc.player != null) mc.gameMode.releaseUsingItem(mc.player);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;

        // [新增逻辑] 1. 优先检查主手，如果主手不是武器，则检查副手
        InteractionHand hand = InteractionHand.MAIN_HAND;
        ItemStack stack = mc.player.getMainHandItem();
        
        if (!isWeapon(stack.getItem())) {
            stack = mc.player.getOffhandItem();
            hand = InteractionHand.OFF_HAND;
        }
        
        // 如果主副手都不是弓或弩，直接返回
        if (!isWeapon(stack.getItem())) return;

        // 必须按住右键才工作
        if (!mc.options.keyUse.isDown()) {
            return;
        }

        // 处理冷却
        if (timer > 0) {
            timer--;
            mc.options.keyUse.setDown(false);
            return;
        }

        // [新增逻辑] 2. 区分是弩还是弓，分发到不同的处理链路
        boolean isCrossbow = stack.getItem() instanceof CrossbowItem;

        if (isCrossbow) {
            switch (mode.get()) {
                case Native -> handleCrossbowNative(stack, hand);
                case Control -> handleCrossbowControl(stack, hand);
                case Packet -> handleCrossbowPacket(stack, hand);
            }
        } else {
            // 如果不是弩，必定是弓 (BowItem)
            switch (mode.get()) {
                case Native -> handleBowNative(stack, hand);
                case Control -> handleBowControl(stack, hand);
                case Packet -> handleBowPacket(stack, hand);
            }
        }
    }

    // --- 武器判断工具方法 ---
    private boolean isWeapon(Item item) {
        return item instanceof CrossbowItem || item instanceof BowItem;
    }

    // ================= 弩的处理逻辑 (Crossbow) =================

    private void handleCrossbowNative(ItemStack stack, InteractionHand hand) {
        if (CrossbowItem.isCharged(stack)) {
            mc.gameMode.useItem(mc.player, hand);
            mc.player.swing(hand);
            timer = delay.get();
        } else {
            mc.options.keyUse.setDown(true);
            if (!mc.player.isUsingItem()) {
                mc.gameMode.useItem(mc.player, hand);
            }
        }
    }

    private void handleCrossbowControl(ItemStack stack, InteractionHand hand) {
        if (CrossbowItem.isCharged(stack)) {
            mc.gameMode.useItem(mc.player, hand);
            mc.player.swing(hand);
            timer = delay.get();
            return;
        }

        mc.options.keyUse.setDown(true);
        if (!mc.player.isUsingItem()) {
            mc.gameMode.useItem(mc.player, hand);
            return;
        }

        int requiredTime = getPullTime(stack) + tolerance.get();
        if (mc.player.getTicksUsingItem() >= requiredTime) {
            mc.gameMode.releaseUsingItem(mc.player);
        }
    }

    private void handleCrossbowPacket(ItemStack stack, InteractionHand hand) {
        if (CrossbowItem.isCharged(stack)) {
            mc.gameMode.useItem(mc.player, hand);
            mc.player.swing(hand);
            timer = delay.get();
        } 

        if (!mc.player.isUsingItem()) {
            mc.gameMode.useItem(mc.player, hand);
            mc.options.keyUse.setDown(true);
            return;
        }

        mc.options.keyUse.setDown(true);
        int requiredTime = getPullTime(stack) + tolerance.get();
        if (mc.player.getTicksUsingItem() >= requiredTime) {
            mc.gameMode.releaseUsingItem(mc.player);
        }
    }

    // ================= 弓的处理逻辑 (Bow) =================
    // 弓的特点：拉弓满后，松开右键即触发发射！没有 charged 状态

    private void handleBowNative(ItemStack stack, InteractionHand hand) {
        mc.options.keyUse.setDown(true);
        if (!mc.player.isUsingItem()) {
            mc.gameMode.useItem(mc.player, hand);
        } else {
            // Native 模式下我们使用基础时间，不加 tolerance
            if (mc.player.getTicksUsingItem() >= getPullTime(stack)) {
                mc.gameMode.releaseUsingItem(mc.player);
                mc.options.keyUse.setDown(false);
                timer = delay.get(); // 触发射击，进入冷却
            }
        }
    }

    private void handleBowControl(ItemStack stack, InteractionHand hand) {
        if (!mc.player.isUsingItem()) {
            mc.gameMode.useItem(mc.player, hand);
            mc.options.keyUse.setDown(true);
        } else {
            int requiredTime = getPullTime(stack) + tolerance.get();
            if (mc.player.getTicksUsingItem() >= requiredTime) {
                mc.gameMode.releaseUsingItem(mc.player);
                timer = delay.get(); // 触发射击，进入冷却
            }
        }
    }

    private void handleBowPacket(ItemStack stack, InteractionHand hand) {
        // 弓的 Packet 逻辑与 Control 类似，但是尝试更快地响应
        if (!mc.player.isUsingItem()) {
            mc.gameMode.useItem(mc.player, hand);
            mc.options.keyUse.setDown(true);
        } 
        
        int requiredTime = getPullTime(stack) + tolerance.get();
        if (mc.player.isUsingItem() && mc.player.getTicksUsingItem() >= requiredTime) {
            mc.gameMode.releaseUsingItem(mc.player);
            timer = delay.get();
        }
    }

    // ================= 通用工具方法 =================

    // 计算装填/拉弓时间
    private int getPullTime(ItemStack stack) {
        // 1. 如果是弓，原版满蓄力固定为 20 ticks (1秒)
        if (stack.getItem() instanceof BowItem) {
            return 20; 
        }
        
        // 2. 如果是弩，计算快速装填 (1.21.4 适配)
        try {
            var registry = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            var quickChargeEntry = registry.getOrThrow(Enchantments.QUICK_CHARGE);
            int level = EnchantmentHelper.getItemEnchantmentLevel(quickChargeEntry, stack);
            return Math.max(0, 25 - 5 * level);
        } catch (Exception e) {
            return 25; // 获取附魔失败时回退到默认的 25 ticks
        }
    }
}