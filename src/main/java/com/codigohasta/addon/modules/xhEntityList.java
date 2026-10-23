package com.codigohasta.addon.modules;

import net.minecraft.util.math.Vec3d;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Names;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

import java.util.*;

public class xhEntityList extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup itemGroup = settings.createGroup("物品设置");
    private final SettingGroup ui = settings.createGroup("界面设置");

    // --- General ---
    private final Setting<Set<EntityType<?>>> overworldEntities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("主世界实体")
        .description("仅在主世界显示的实体。")
        .defaultValue(
            EntityType.PLAYER,
            EntityType.CREEPER,
            // 新增默认
            EntityType.EXPERIENCE_ORB,
            EntityType.ZOMBIFIED_PIGLIN
        )
        .build()
    );

    private final Setting<Set<EntityType<?>>> netherEntities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("下界实体")
        .description("仅在下界显示的实体。")
        .defaultValue(
            // 原有默认
            EntityType.GHAST, EntityType.BLAZE, EntityType.WITHER_SKELETON, EntityType.PIGLIN,
            // 你要求的新增默认 (虽然很多是主世界生物，但按你要求加在这里)
            EntityType.COW, EntityType.HORSE, EntityType.PIG, EntityType.SHEEP,
            EntityType.BOGGED, // 沼骸 (1.21新生物)
            EntityType.CAVE_SPIDER, EntityType.DROWNED, EntityType.CREEPER,
            EntityType.HUSK, EntityType.SLIME, EntityType.SPIDER,
            EntityType.ZOMBIE, EntityType.ZOMBIE_VILLAGER,
            EntityType.EXPERIENCE_ORB, EntityType.VILLAGER
        )
        .build()
    );

    private final Setting<Set<EntityType<?>>> endEntities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("末地实体")
        .description("仅在末地显示的实体。")
        .defaultValue(EntityType.ENDERMAN, EntityType.SHULKER)
        .build()
    );

    private final Setting<SettingColor> entityColor = sgGeneral.add(new ColorSetting.Builder()
        .name("实体颜色")
        .defaultValue(new SettingColor(255, 0, 255, 255))
        .build()
    );

    private final Setting<SettingColor> playerColor = sgGeneral.add(new ColorSetting.Builder()
        .name("玩家颜色")
        .defaultValue(new SettingColor(255, 100, 100, 255))
        .build()
    );

    private final Setting<Boolean> entityLog = sgGeneral.add(new BoolSetting.Builder()
        .name("实体日志")
        .description("发现实体时在聊天栏提示。")
        .defaultValue(false)
        .build()
    );

    // --- Items ---
    private final Setting<List<Item>> items1 = itemGroup.add(new ItemListSetting.Builder()
        .name("物品列表 1")
        .description("重点关注的物品。")
        .defaultValue(
            // 鞘翅
            Items.ELYTRA,
            // 潜影盒 (全色)
            Items.SHULKER_BOX, Items.WHITE_SHULKER_BOX, Items.ORANGE_SHULKER_BOX, Items.MAGENTA_SHULKER_BOX,
            Items.LIGHT_BLUE_SHULKER_BOX, Items.YELLOW_SHULKER_BOX, Items.LIME_SHULKER_BOX, Items.PINK_SHULKER_BOX,
            Items.GRAY_SHULKER_BOX, Items.LIGHT_GRAY_SHULKER_BOX, Items.CYAN_SHULKER_BOX, Items.PURPLE_SHULKER_BOX,
            Items.BLUE_SHULKER_BOX, Items.BROWN_SHULKER_BOX, Items.GREEN_SHULKER_BOX, Items.RED_SHULKER_BOX, Items.BLACK_SHULKER_BOX,
            // 收纳袋 (1.21.4 全色)
            Items.BUNDLE, Items.WHITE_BUNDLE, Items.ORANGE_BUNDLE, Items.MAGENTA_BUNDLE,
            Items.LIGHT_BLUE_BUNDLE, Items.YELLOW_BUNDLE, Items.LIME_BUNDLE, Items.PINK_BUNDLE,
            Items.GRAY_BUNDLE, Items.LIGHT_GRAY_BUNDLE, Items.CYAN_BUNDLE, Items.PURPLE_BUNDLE,
            Items.BLUE_BUNDLE, Items.BROWN_BUNDLE, Items.GREEN_BUNDLE, Items.RED_BUNDLE, Items.BLACK_BUNDLE,
            // 下界合金系列
            Items.ANCIENT_DEBRIS, Items.NETHERITE_SCRAP, Items.NETHERITE_INGOT, Items.NETHERITE_BLOCK,
            Items.NETHERITE_SWORD, Items.NETHERITE_AXE, Items.NETHERITE_HOE,
            Items.NETHERITE_PICKAXE, Items.NETHERITE_SHOVEL,
            Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE,
            Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS,
            // 附魔金苹果
            Items.ENCHANTED_GOLDEN_APPLE
        )
        .build()
    );

    private final Setting<SettingColor> items1Color = itemGroup.add(new ColorSetting.Builder()
        .name("物品1 颜色")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .build()
    );

    private final Setting<Boolean> item1Log = itemGroup.add(new BoolSetting.Builder()
        .name("物品1 日志")
        .description("发现列表1的物品时在聊天栏提示。")
        .defaultValue(false)
        .build()
    );

    private final Setting<List<Item>> items2 = itemGroup.add(new ItemListSetting.Builder()
        .name("物品列表 2")
        .description("次要关注的物品。")
        .defaultValue(Items.GOLD_INGOT, Items.IRON_INGOT, Items.DIAMOND)
        .build()
    );

    private final Setting<SettingColor> items2Color = itemGroup.add(new ColorSetting.Builder()
        .name("物品2 颜色")
        .defaultValue(new SettingColor(0, 255, 255, 255))
        .build()
    );

    private final Setting<SettingColor> defaultItemColor = itemGroup.add(new ColorSetting.Builder()
        .name("默认物品颜色")
        .description("未在列表中的其他掉落物颜色。")
        .defaultValue(new SettingColor(255, 255, 0, 255))
        .build()
    );

    private final Setting<List<Item>> blackList = itemGroup.add(new ItemListSetting.Builder()
        .name("物品黑名单")
        .description("不显示的垃圾物品。")
        .defaultValue(Items.COBBLESTONE, Items.DIRT, Items.NETHERRACK)
        .build()
    );

    // --- UI ---
    private final Setting<Integer> xOffset = ui.add(new IntSetting.Builder()
        .name("X 偏移")
        .defaultValue(10)
        .min(0)
        .sliderMax(1000)
        .build()
    );

    private final Setting<Integer> yOffset = ui.add(new IntSetting.Builder()
        .name("Y 偏移")
        .defaultValue(528)
        .min(0)
        .sliderMax(1000)
        .build()
    );

    public enum DisplaySide {
        Left, Right
    }

    private final Setting<DisplaySide> displaySide = ui.add(new EnumSetting.Builder<DisplaySide>()
        .name("对齐方式")
        .defaultValue(DisplaySide.Left)
        .build()
    );

    private final Setting<Double> scale = ui.add(new DoubleSetting.Builder()
        .name("缩放大小")
        .defaultValue(1.0)
        .min(0.5)
        .sliderMax(4.0)
        .build()
    );

    private final Setting<Integer> lineHeight = ui.add(new IntSetting.Builder()
        .name("行高")
        .defaultValue(18)
        .min(5)
        .sliderMax(50)
        .build()
    );

    private final Setting<Boolean> showDistance = ui.add(new BoolSetting.Builder()
        .name("显示距离")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showItemIcons = ui.add(new BoolSetting.Builder()
        .name("物品图标")
        .description("在每行显示该物品的图标与数量。图标排在列表靠屏幕内侧的一边。")
        .defaultValue(false)
        .build()
    );

    /** 图标与文字之间的间距（与文字同一套坐标）。 */
    private static final double ICON_GAP = 2.0;

    private final Set<Integer> loggedEntities = new HashSet<>();

    public xhEntityList() {
        super(AddonTemplate.CATEGORY, "实体列表栏", "在屏幕上显示周围实体和掉落物的统计列表。抄袭的lotus的模块，并且在基础上做了一些更改");
    }

    @Override
    public void onActivate() {
        loggedEntities.clear();
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.world == null || mc.player == null) return;

        Map<Item, ItemStat> mapItems1 = new HashMap<>();
        Map<Item, ItemStat> mapItems2 = new HashMap<>();
        Map<Item, ItemStat> mapItemsOther = new HashMap<>();
        Map<EntityType<?>, EntityStat> mapEntities = new HashMap<>();
        List<PlayerStat> listPlayers = new ArrayList<>();

        Set<Item> set1 = new HashSet<>(items1.get());
        Set<Item> set2 = new HashSet<>(items2.get());
        Set<Item> setBlack = new HashSet<>(blackList.get());

        RegistryKey<World> dimension = mc.world.getRegistryKey();
        
        Set<EntityType<?>> allowedTypes;
        if (dimension == World.OVERWORLD) {
            allowedTypes = overworldEntities.get();
        } else if (dimension == World.NETHER) {
            allowedTypes = netherEntities.get();
        } else {
            allowedTypes = endEntities.get();
        }

        for (Entity entity : mc.world.getEntities()) {
            if (!entity.isAlive()) continue;
            if (entity == mc.player) continue;

            // 1. 物品
            if (entity instanceof ItemEntity itemEntity) {
                ItemStack stack = itemEntity.getStack();
                Item item = stack.getItem();

                if (setBlack.contains(item)) continue;

                Map<Item, ItemStat> targetMap;
                if (set1.contains(item)) {
                    targetMap = mapItems1;
                    if (item1Log.get() && !loggedEntities.contains(entity.getId())) {
                        info("发现物品: " + Names.get(item) + " 坐标: " + entity.getBlockPos().toShortString());
                        loggedEntities.add(entity.getId());
                    }
                } else if (set2.contains(item)) {
                    targetMap = mapItems2;
                } else {
                    targetMap = mapItemsOther;
                }

                ItemStat stat = targetMap.computeIfAbsent(item, k -> new ItemStat(item));
                stat.count += stack.getCount();
                
                double dist = mc.player.distanceTo(entity);
                if (dist < stat.minDistance) {
                    stat.minDistance = dist;
                }
            } 
            // 2. 实体
            else {
                EntityType<?> type = entity.getType();

                if (allowedTypes.contains(type)) {
                    if (entity instanceof PlayerEntity player) {
                        double dist = mc.player.distanceTo(player);
                        listPlayers.add(new PlayerStat(player.getName().getString(), dist));
                        
                        if (entityLog.get() && !loggedEntities.contains(entity.getId())) {
                            info("发现玩家: " + player.getName().getString() + " 坐标: " + entity.getBlockPos().toShortString());
                            loggedEntities.add(entity.getId());
                        }
                    } else {
                        EntityStat stat = mapEntities.computeIfAbsent(type, k -> new EntityStat(type));
                        stat.count++;
                        
                        double dist = mc.player.distanceTo(entity);
                        if (dist < stat.minDistance) {
                            stat.minDistance = dist;
                        }

                        if (entityLog.get() && !loggedEntities.contains(entity.getId())) {
                            info("发现实体: " + Names.get(type) + " 坐标: " + entity.getBlockPos().toShortString());
                            loggedEntities.add(entity.getId());
                        }
                    }
                }
            }
        }

        listPlayers.sort(Comparator.comparingDouble(p -> p.distance));

        int screenWidth = mc.getWindow().getScaledWidth();
        double currentY = yOffset.get();
        double scaleVal = scale.get();
        double scaledLineHeight = lineHeight.get() * scaleVal;

        currentY = drawItems(event, mapItems1, currentY, items1Color.get(), screenWidth, scaleVal, scaledLineHeight);
        currentY = drawItems(event, mapItems2, currentY, items2Color.get(), screenWidth, scaleVal, scaledLineHeight);
        currentY = drawItems(event, mapItemsOther, currentY, defaultItemColor.get(), screenWidth, scaleVal, scaledLineHeight);
        
        currentY = drawPlayers(listPlayers, currentY, playerColor.get(), screenWidth, scaleVal, scaledLineHeight);
        drawEntities(mapEntities, currentY, entityColor.get(), screenWidth, scaleVal, scaledLineHeight);
    }

    private double drawItems(Render2DEvent event, Map<Item, ItemStat> map, double y, Color color, int width, double scale, double scaledLineHeight) {
        if (map.isEmpty()) return y;

        boolean icons = showItemIcons.get();
        boolean right = displaySide.get() == DisplaySide.Right;
        // 图标大小跟着「行高」设置走
        double iconSize = scaledLineHeight;
        // disableGuiScale=true 时 drawItem 收的是帧缓冲像素，与文字同一套坐标；
        // 该空间里物品方块占 16 个单位，所以缩放 = 目标大小 / 16。
        float iconScale = (float) (iconSize / 16.0);

        TextRenderer renderer = TextRenderer.get();
        renderer.begin(scale);

        // 先把每行文字量出来：图标要排成整齐的一列、贴着列表的外缘，
        // 而不是跟着每行文字宽度参差不齐。
        double[] widths = new double[map.size()];
        double maxWidth = 0.0;
        int index = 0;
        for (ItemStat stat : map.values()) {
            widths[index] = renderer.getWidth(lineText(stat)) * scale;
            maxWidth = Math.max(maxWidth, widths[index]);
            index++;
        }

        // 列表外缘：右对齐时是右边缘，左对齐时是左边缘（与改动前的文字位置一致）
        double edge = right ? width - xOffset.get() : xOffset.get();
        // 图标列贴在列表的屏幕内侧那一边：右对齐在左，左对齐在右
        double iconX = right
            ? edge - maxWidth - ICON_GAP - iconSize
            : edge + maxWidth + ICON_GAP;

        index = 0;
        for (ItemStat stat : map.values()) {
            String text = lineText(stat);
            double textWidth = widths[index++];
            double textX = right ? edge - textWidth : edge;

            if (icons) {
                // 不用原版角标：drawStackOverlay 是在 drawItem 的 scale 变换内部绘制的，
                // 数字会跟着图标一起缩小。图标只有行高那么大时，数字就小到看不清了。
                RenderUtils.drawItem(event.drawContext, stat.item.getDefaultStack(),
                    (int) iconX, (int) y, iconScale, false, null, true);

                // 数量改用与文字同一个 renderer、同一字号，画在图标背向文字的一侧，
                // 既不遮图标，也不会像原版角标那样被缩小。
                String count = String.valueOf(stat.count);
                double countWidth = renderer.getWidth(count) * scale;
                double countX = right
                    ? iconX - ICON_GAP - countWidth
                    : iconX + iconSize + ICON_GAP;
                renderer.render(count, countX, y, color, true);
            }

            renderer.render(text, textX, y, color, true);
            y += scaledLineHeight;
        }

        renderer.end();
        return y;
    }

    /** 一行物品文字：名称 x数量 (距离)。 */
    private String lineText(ItemStat stat) {
        String name = Names.get(stat.item);
        return showDistance.get()
            ? String.format("%s x%d (%.1fm)", name, stat.count, stat.minDistance)
            : String.format("%s x%d", name, stat.count);
    }

    private double drawPlayers(List<PlayerStat> players, double y, Color color, int width, double scale, double scaledLineHeight) {
        if (players.isEmpty()) return y;

        TextRenderer renderer = TextRenderer.get();
        renderer.begin(scale);

        for (PlayerStat p : players) {
            String text;
            if (showDistance.get()) {
                text = String.format("%s (%.1fm)", p.name, p.distance);
            } else {
                text = p.name;
            }
            drawTextInternal(renderer, text, y, color, width, scale);
            y += scaledLineHeight;
        }

        renderer.end();
        return y;
    }

    private void drawEntities(Map<EntityType<?>, EntityStat> map, double y, Color color, int width, double scale, double scaledLineHeight) {
        if (map.isEmpty()) return;

        TextRenderer renderer = TextRenderer.get();
        renderer.begin(scale);

        for (EntityStat stat : map.values()) {
            String name = Names.get(stat.type);
            String text;
            if (showDistance.get()) {
                text = String.format("%s x%d (%.1fm)", name, stat.count, stat.minDistance);
            } else {
                text = String.format("%s x%d", name, stat.count);
            }

            drawTextInternal(renderer, text, y, color, width, scale);
            y += scaledLineHeight;
        }

        renderer.end();
    }

    private void drawTextInternal(TextRenderer renderer, String text, double y, Color color, int screenWidth, double scale) {
        double textWidth = renderer.getWidth(text) * scale;
        double x;

        if (displaySide.get() == DisplaySide.Right) {
            x = screenWidth - textWidth - xOffset.get();
        } else {
            x = xOffset.get();
        }
        
        renderer.render(text, x, y, color, true);
    }

    private static class ItemStat {
        Item item;
        int count = 0;
        double minDistance = Double.MAX_VALUE;

        public ItemStat(Item item) {
            this.item = item;
        }
    }

    private static class EntityStat {
        EntityType<?> type;
        int count = 0;
        double minDistance = Double.MAX_VALUE;

        public EntityStat(EntityType<?> type) {
            this.type = type;
        }
    }

    private static class PlayerStat {
        String name;
        double distance;

        public PlayerStat(String name, double distance) {
            this.name = name;
            this.distance = distance;
        }
    }
}