package com.codigohasta.addon.modules;

import net.minecraft.util.math.Vec3d;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes; // 必须导入这个
import net.minecraft.item.Item;

import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;


import java.util.List;
import java.util.function.Predicate;

public class SwordGap extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // 允许用户自定义吃的食物列表
    private final Setting<List<Item>> allowedFoods = sgGeneral.add(new ItemListSetting.Builder()
        .name("allowed-foods")
        .description("Select which items are allowed to be eaten.")
        .defaultValue(Items.ENCHANTED_GOLDEN_APPLE, Items.GOLDEN_APPLE)
        // 1.21.4 修复：使用数据组件检查物品是否包含 FOOD 组件
        .filter(item -> item.getComponents().contains(DataComponentTypes.FOOD))
        .build()
    );

    private boolean isEating = false;
    private int lastFoodSlot = -1;

    public SwordGap() {
        super(AddonTemplate.CATEGORY, "sword-gap", "剑吃，原版meteor就有了。有点没用");
    }

    @Override
    public void onDeactivate() {
        if (isEating && mc.player != null) {
            restoreOffhand();
        }
        isEating = false;
        lastFoodSlot = -1;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        boolean holdingSword = mc.player.getMainHandStack().getItem().toString().contains("sword");
        boolean pressingUse = mc.options.useKey.isPressed();

        if (holdingSword && pressingUse) {
            if (!isEating) {
                startEating();
            }
        } else {
            if (isEating) {
                restoreOffhand();
            }
        }
    }

    private void startEating() {
        int foodSlot = findFoodSlot();

        if (foodSlot == -1) return;

        if (allowedFoods.get().contains(mc.player.getOffHandStack().getItem())) {
            isEating = true;
            return;
        }

        lastFoodSlot = foodSlot;
        InvUtils.move().from(foodSlot).toOffhand();
        isEating = true;
    }

    private void restoreOffhand() {
        if (lastFoodSlot != -1) {
            InvUtils.move().from(lastFoodSlot).toOffhand();
        }
        isEating = false;
        lastFoodSlot = -1;
    }

    private int findFoodSlot() {
        Predicate<ItemStack> predicate = itemStack -> allowedFoods.get().contains(itemStack.getItem());

        var result = InvUtils.find(predicate);

        if (result.found()) {
            return result.slot();
        }

        return -1;
    }
}