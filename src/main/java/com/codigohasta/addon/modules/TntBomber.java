package com.codigohasta.addon.modules;

import net.minecraft.util.math.Vec3d;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.FireChargeItem;
import net.minecraft.item.FlintAndSteelItem;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class TntBomber extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("放置TNT的间隔 (ticks)。")
        .defaultValue(4)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> onlyWhenFlying = sgGeneral.add(new BoolSetting.Builder()
        .name("only-when-flying")
        .description("仅在使用鞘翅飞行时激活。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> requireRightClick = sgGeneral.add(new BoolSetting.Builder()
        .name("require-right-click")
        .description("需要按住右键才能激活。")
        .defaultValue(true)
        .build()
    );

    private int timer;

    public TntBomber() {
        super(AddonTemplate.CATEGORY, "B2轰炸机", "飞行时在你下方放置并点燃TNT，点燃的工具  支持打火石，燃烧弹。轰炸机炸死你。娱乐功能");
    }

    @Override
    public void onActivate() {
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (timer > 0) {
            timer--;
            return;
        }

        // 检查是否满足激活条件
        boolean shouldActivate = !requireRightClick.get() || mc.options.useKey.isPressed();
        // 检查玩家是否穿着鞘翅且不在地面上，作为飞行状态的替代判断
        boolean isFlying = mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA && !mc.player.isOnGround();

        if (!shouldActivate || (onlyWhenFlying.get() && !isFlying)) {
            return;
        }

        // 寻找TNT和点火工具
        FindItemResult tnt = InvUtils.findInHotbar(Items.TNT);
        FindItemResult flint = InvUtils.findInHotbar(itemStack -> itemStack.getItem() instanceof FlintAndSteelItem);
        FindItemResult fireCharge = InvUtils.findInHotbar(itemStack -> itemStack.getItem() instanceof FireChargeItem);

        if (!tnt.found() || (!flint.found() && !fireCharge.found())) {
            return;
        }

        // --- 新逻辑：将TNT放置在玩家身后以避免碰撞 ---
        Vec3d playerPos = new net.minecraft.util.math.Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        Vec3d playerVelocity = mc.player.getVelocity();
        // 基于速度计算玩家刚刚飞过的位置
        Vec3d behindPos = playerPos.subtract(playerVelocity.multiply(2.5));
        BlockPos placePos = BlockPos.ofFloored(behindPos.x, playerPos.y - 1, behindPos.z);

        // 如果目标位置不能放置，则放弃本次放置
        if (!BlockUtils.canPlace(placePos)) return;

        // 放置并点燃
        final BlockPos finalPlacePos = placePos;
        final int prevSlot = ((com.codigohasta.addon.mixin.InventoryAccessor) mc.player.getInventory()).getSelectedSlot(); // 在 lambda 外部记录原始槽位

        Rotations.rotate(Rotations.getYaw(finalPlacePos), Rotations.getPitch(finalPlacePos), () -> {
            // 放置TNT
            if (BlockUtils.place(finalPlacePos, tnt, false, 0)) {
                // 成功放置后，立即点燃
                FindItemResult igniter = flint.found() ? flint : fireCharge;

                // 手动创建交互结果
                BlockHitResult hitResult = new BlockHitResult(finalPlacePos.toCenterPos(), Direction.UP, finalPlacePos, false);

                InvUtils.swap(igniter.slot(), false); // 切换到点火工具
                mc.interactionManager.interactBlock(mc.player, igniter.getHand(), hitResult);
                InvUtils.swap(prevSlot, false); // 切换回原来的物品

                timer = delay.get(); // 重置计时器
            }
        });
    }
}
