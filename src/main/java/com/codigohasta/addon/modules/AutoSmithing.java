package com.codigohasta.addon.modules;

import net.minecraft.util.math.Vec3d;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.SmithingScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

import java.util.List;
import java.util.function.Predicate;

public class AutoSmithing extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgUpgrade = settings.createGroup("升级模式 (合金)");
    private final SettingGroup sgTrim = settings.createGroup("纹饰模式 (Trim)");

    public enum Mode {
        Upgrade, // 升级合金
        Trim     // 添加纹饰
    }

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("模式")
        .description("选择是进行装备升级还是添加纹饰。")
        .defaultValue(Mode.Upgrade)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("操作延迟")
        .defaultValue(3)
        .min(1)
        .sliderMax(10)
        .build()
    );

    private final Setting<Boolean> autoDrop = sgGeneral.add(new BoolSetting.Builder()
        .name("自动丢弃成品")
        .defaultValue(false)
        .build()
    );

    // --- 升级模式设置 ---
    private final Setting<List<Item>> upgradeTargets = sgUpgrade.add(new ItemListSetting.Builder()
        .name("升级目标")
        .description("选择要升级为合金的钻石装备。")
        .defaultValue(
            Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.DIAMOND_PICKAXE,
            Items.DIAMOND_SHOVEL, Items.DIAMOND_HOE,
            Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE,
            Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS
        )
        .visible(() -> mode.get() == Mode.Upgrade)
        .filter(this::isDiamondGear)
        .build()
    );

    // --- 纹饰模式设置 ---
    private final Setting<List<Item>> trimTargets = sgTrim.add(new ItemListSetting.Builder()
        .name("纹饰目标")
        .description("选择要添加纹饰的盔甲。")
        .defaultValue(Items.NETHERITE_CHESTPLATE, Items.DIAMOND_CHESTPLATE)
        .visible(() -> mode.get() == Mode.Trim)
        .filter(item -> (item.toString().contains("helmet") || item.toString().contains("chestplate") || item.toString().contains("leggings") || item.toString().contains("boots"))) // 只显示盔甲
        .build()
    );

    private final Setting<List<Item>> trimTemplates = sgTrim.add(new ItemListSetting.Builder()
        .name("使用的纹饰")
        .description("只使用选中的纹饰模板 (Template)。")
        .defaultValue(
            Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, Items.VEX_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.WILD_ARMOR_TRIM_SMITHING_TEMPLATE, Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE, Items.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE, Items.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.HOST_ARMOR_TRIM_SMITHING_TEMPLATE, Items.WARD_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE, Items.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE, Items.RIB_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.EYE_ARMOR_TRIM_SMITHING_TEMPLATE, Items.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE
        )
        .visible(() -> mode.get() == Mode.Trim)
        .filter(item -> item.toString().contains("template") && item != Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE)
        .build()
    );

    private final Setting<List<Item>> trimMaterials = sgTrim.add(new ItemListSetting.Builder()
        .name("使用的材料")
        .description("只使用选中的材料 (颜色)。")
        .defaultValue(
            Items.IRON_INGOT, Items.COPPER_INGOT, Items.GOLD_INGOT, Items.LAPIS_LAZULI,
            Items.EMERALD, Items.DIAMOND, Items.NETHERITE_INGOT, Items.REDSTONE,
            Items.AMETHYST_SHARD, Items.QUARTZ
        )
        .visible(() -> mode.get() == Mode.Trim)
        .build()
    );

    private int timer = 0;

    public AutoSmithing() {
        super(AddonTemplate.CATEGORY, "自动升级和纹饰", "自动升级合金装备或添加盔甲纹饰。");
    }

    @Override
    public void onActivate() {
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (!(mc.player.currentScreenHandler instanceof SmithingScreenHandler handler)) {
            return;
        }

        if (timer > 0) {
            timer--;
            return;
        }

        // Slot 3: 取走成品
        if (handler.getSlot(3).hasStack()) {
            if (autoDrop.get()) {
                mc.interactionManager.clickSlot(handler.syncId, 3, 1, SlotActionType.THROW, mc.player);
            } else {
                mc.interactionManager.clickSlot(handler.syncId, 3, 0, SlotActionType.QUICK_MOVE, mc.player);
            }
            timer = delay.get();
            return;
        }

        if (mode.get() == Mode.Upgrade) {
            handleUpgrade(handler);
        } else {
            handleTrim(handler);
        }
    }

    private void handleUpgrade(SmithingScreenHandler handler) {
        // Slot 0: 升级模板
        if (!handler.getSlot(0).hasStack()) {
            int slot = findItem(item -> item == Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
            if (slot != -1) {
                moveToSlot(slot, 0);
                timer = delay.get();
                return;
            }
        }

        // Slot 2: 升级材料 (合金锭)
        if (!handler.getSlot(2).hasStack()) {
            int slot = findItem(item -> item == Items.NETHERITE_INGOT);
            if (slot != -1) {
                moveToSlot(slot, 2);
                timer = delay.get();
                return;
            }
        }

        // Slot 1: 目标装备
        if (!handler.getSlot(1).hasStack()) {
            int slot = findItem(item -> upgradeTargets.get().contains(item));
            if (slot != -1) {
                moveToSlot(slot, 1);
                timer = delay.get();
            }
        }
    }

    private void handleTrim(SmithingScreenHandler handler) {
        // Slot 0: 纹饰模板 (从列表中选)
        if (!handler.getSlot(0).hasStack()) {
            int slot = findItem(item -> trimTemplates.get().contains(item));
            if (slot != -1) {
                moveToSlot(slot, 0);
                timer = delay.get();
                return;
            }
        }

        // Slot 2: 纹饰材料 (颜色，从列表中选)
        if (!handler.getSlot(2).hasStack()) {
            int slot = findItem(item -> trimMaterials.get().contains(item));
            if (slot != -1) {
                moveToSlot(slot, 2);
                timer = delay.get();
                return;
            }
        }

        // Slot 1: 目标盔甲
        if (!handler.getSlot(1).hasStack()) {
            int slot = findItem(item -> trimTargets.get().contains(item));
            if (slot != -1) {
                moveToSlot(slot, 1);
                timer = delay.get();
            }
        }
    }

    private int findItem(Predicate<Item> predicate) {
        ScreenHandler handler = mc.player.currentScreenHandler;
        for (int i = 4; i < handler.slots.size(); i++) {
            if (handler.getSlot(i).hasStack() && predicate.test(handler.getSlot(i).getStack().getItem())) {
                return i;
            }
        }
        return -1;
    }

    private void moveToSlot(int sourceSlot, int targetSlot) {
        InvUtils.move().fromId(sourceSlot).toId(targetSlot);
    }

    private boolean isDiamondGear(Item item) {
        String name = item.toString();
        return name.contains("diamond_");
    }
}