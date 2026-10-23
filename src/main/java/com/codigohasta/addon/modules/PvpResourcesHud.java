package com.codigohasta.addon.modules;

import com.codigohasta.addon.utils.sakura.render.AbstractPanelHud;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class PvpResourcesHud extends AbstractPanelHud {

    private final SettingGroup sgResources = settings.createGroup("资源");

    private final Setting<Boolean> totems = sgResources.add(new BoolSetting.Builder()
        .name("图腾").description("统计不死图腾。").defaultValue(true).build());
    private final Setting<Boolean> crystals = sgResources.add(new BoolSetting.Builder()
        .name("末地水晶").description("统计末地水晶。").defaultValue(true).build());
    private final Setting<Boolean> xp = sgResources.add(new BoolSetting.Builder()
        .name("经验瓶").description("统计附魔之瓶（经验瓶）。").defaultValue(true).build());
    private final Setting<Boolean> crapples = sgResources.add(new BoolSetting.Builder()
        .name("附魔金苹果").description("统计附魔金苹果。").defaultValue(true).build());
    private final Setting<Boolean> gapples = sgResources.add(new BoolSetting.Builder()
        .name("金苹果").description("统计金苹果。").defaultValue(true).build());
    private final Setting<Boolean> fireworks = sgResources.add(new BoolSetting.Builder()
        .name("烟花").description("统计烟花火箭。").defaultValue(true).build());
    private final Setting<Boolean> obby = sgResources.add(new BoolSetting.Builder()
        .name("黑曜石").description("统计黑曜石。").defaultValue(true).build());

    private static final float PANEL_H = 29f;
    private static final float CELL_W = 20f;
    private static final float WIDTH_BASE = 1f;
    private static final float ICON_X0 = 2f;
    private static final float ICON_Y = 2f;
    private static final float NUM_Y = 18f;
    private static final float NUM_CENTER_X = 8f;
    private static final float ICON_SIZE = 16f;

    private static final float DIV_X = 17.5f;
    private static final float DIV_Y = 7f;
    private static final float DIV_W = 0.3f;
    private static final float DIV_H = 8.5f;

    private final Setting<Boolean> showDividers = sgResources.add(new BoolSetting.Builder()
        .name("格间分割线")
        .description("每个物品右侧的细竖线。")
        .defaultValue(true).build());

    private final Setting<Boolean> includeShulkers = sgResources.add(new BoolSetting.Builder()
        .name("统计潜影盒内")
        .description("把潜影盒里的同类物品也算进来")
        .defaultValue(true).build());

    public PvpResourcesHud() {
        super("pvp-resources-hud", "在你屏幕上画个面板，让你知道剩多少武器",
            700, 300, 1.0);
    }

    @Override
    protected void render(float x, float y, float scale) {
        java.util.List<Item> order = new java.util.ArrayList<>();
        if (totems.get())    order.add(Items.TOTEM_OF_UNDYING);
        if (crystals.get())  order.add(Items.END_CRYSTAL);
        if (xp.get())        order.add(Items.EXPERIENCE_BOTTLE);
        if (crapples.get())  order.add(Items.ENCHANTED_GOLDEN_APPLE);
        if (gapples.get())   order.add(Items.GOLDEN_APPLE);
        if (fireworks.get()) order.add(Items.FIREWORK_ROCKET);
        if (obby.get())      order.add(Items.OBSIDIAN);
        if (order.isEmpty()) return;

        float panelW = WIDTH_BASE + CELL_W * order.size();
        float panelH = PANEL_H;

        queuePanel(x, y, panelW * scale, panelH * scale, 1f);

        float fontPx = currentFontPx();

        for (int i = 0; i < order.size(); i++) {
            Item item = order.get(i);
            int count = countItem(item);
            float cx = x + (ICON_X0 + CELL_W * i) * scale;

            if (showDividers.get() && i < order.size() - 1) {
                var dc = dividerColorSetting();
                queueShape(
                    cx + DIV_X * scale,
                    y + DIV_Y * scale,
                    DIV_W * scale,
                    DIV_H * scale,
                    0f, dc, 1f);
            }

            queueItem(new ItemStack(item), cx, y + ICON_Y * scale, ICON_SIZE * scale, 1f);

            String num = count > 0 ? String.valueOf(count) : "-";
            float numW = (float) com.codigohasta.addon.utils.sakura.render.SakuraTextRenderer
                .getWidth(num, fontPx);
            float numX = cx + (NUM_CENTER_X * scale) - numW / 2f;
            queueText(num, numX, y + NUM_Y * scale, 1f);
        }
    }

    private int countItem(Item item) {
        if (mc.player == null) return 0;
        int total = 0;
        var inv = mc.player.getInventory();

        for (int i = 0; i < inv.size(); i++) {
            total += countIn(inv.getStack(i), item);
        }
        total += countIn(mc.player.getOffHandStack(), item);
        return total;
    }

    private int countIn(ItemStack stack, Item item) {
        if (stack == null || stack.isEmpty()) return 0;

        if (stack.isOf(item)) return stack.getCount();

        if (includeShulkers.get() && isShulkerBox(stack)) {
            int sum = 0;
            var contents = stack.get(net.minecraft.component.DataComponentTypes.CONTAINER);
            if (contents != null) {
                for (ItemStack inner : contents.stream().toList()) {
                    if (inner.isOf(item)) sum += inner.getCount();
                }
            }
            return sum;
        }
        return 0;
    }

    private static boolean isShulkerBox(ItemStack stack) {
        var id = net.minecraft.registry.Registries.ITEM.getId(stack.getItem());
        return id != null && id.getPath().endsWith("shulker_box");
    }
}
