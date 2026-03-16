package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class adaAutoHotbar extends Module {
    
    public enum Preset {
        预设_1, 预设_2, 预设_3, 预设_4, 预设_5
    }

    public enum Mode {
        固定模式, // 每次开启都整理当前选中的那个预设
        循环模式  // 每次开启自动切换到下一个允许循环的预设
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgConfig = settings.createGroup("控制台");
    private final SettingGroup sgCycle = settings.createGroup("循环设置");
    
    // 我们动态创建5个分组，分别对应5套预设
    private final List<SettingGroup> presetGroups = new ArrayList<>();
    // 存储所有预设的所有槽位设置 [预设索引][槽位索引] -> Setting
    private final List<List<Setting<List<Item>>>> allPresetsSettings = new ArrayList<>();

    // --- 常规设置 ---
    private final Setting<Integer> tickDelay = sgGeneral.add(new IntSetting.Builder()
        .name("整理延迟 (Tick)")
        .description("物品移动间隔。Grim服务器建议设为 2。")
        .defaultValue(2)
        .min(0)
        .sliderMax(10)
        .build()
    );

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("切换模式")
        .defaultValue(Mode.固定模式)
        .build()
    );

    private final Setting<Boolean> fuzzyMatch = sgGeneral.add(new BoolSetting.Builder()
        .name("宽容匹配")
        .description("如果没有保存的物品，尝试使用同类型的次级替代品。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoDisable = sgGeneral.add(new BoolSetting.Builder()
        .name("整理完自动关闭")
        .defaultValue(true)
        .build()
    );

    // --- 控制台 ---
    private final Setting<Preset> activePreset = sgConfig.add(new EnumSetting.Builder<Preset>()
        .name("目标预设")
        .description("当前操作的预设对象。")
        .defaultValue(Preset.预设_1)
        .build()
    );

    private final Setting<Boolean> saveNow = sgConfig.add(new BoolSetting.Builder()
        .name("保存当前快捷栏 -> 目标预设")
        .description("点击保存：将你现在手上的 9 个物品覆盖保存到上方选中的预设中。")
        .defaultValue(false)
        .onChanged(v -> { if (v) saveHotbarToPreset(); })
        .build()
    );

    private final Setting<Boolean> previewNow = sgConfig.add(new BoolSetting.Builder()
        .name("聊天栏预览 -> 目标预设")
        .description("在聊天栏打印出目标预设里保存了什么。")
        .defaultValue(false)
        .onChanged(v -> { if (v) previewPreset(); })
        .build()
    );

    // --- 循环设置 ---
    private final List<Setting<Boolean>> cycleEnables = new ArrayList<>();

    public adaAutoHotbar() {
        super(AddonTemplate.CATEGORY, "a形态", "变换战术形态，快捷换物品栏，应该不能绕过grimac，只能站着不动等切换完毕");
        
        // 1. 初始化循环开关
        for (int i = 1; i <= 5; i++) {
            cycleEnables.add(sgCycle.add(new BoolSetting.Builder()
                .name("循环包含: 预设 " + i)
                .defaultValue(i <= 2) // 默认开启 1 和 2
                .build()
            ));
        }

        // 2. 初始化 5 套预设的 SettingGroup 和 ItemListSetting
        // 这样 Meteor 就会自动把它们保存到配置文件里了
        for (int i = 0; i < 5; i++) {
            int presetNum = i + 1;
            SettingGroup pg = settings.createGroup("数据: 预设 " + presetNum);
            presetGroups.add(pg);
            
            List<Setting<List<Item>>> currentSlots = new ArrayList<>();
            for (int slot = 1; slot <= 9; slot++) {
                currentSlots.add(pg.add(new ItemListSetting.Builder()
                    .name("槽位 " + slot)
                    .description("预设 " + presetNum + " 的第 " + slot + " 格物品。")
                    .defaultValue(new ArrayList<>()) // 默认为空
                    .build()
                ));
            }
            allPresetsSettings.add(currentSlots);
        }
    }

    private int timer = 0;
    private int currentSlotIndex = 0;
    private boolean isSorting = false;

    @Override
    public void onActivate() {
        if (mc.player == null) { toggle(); return; }

        if (mode.get() == Mode.循环模式) {
            cycleNextPreset();
        }

        info("正在应用: " + activePreset.get().name());
        isSorting = true;
        currentSlotIndex = 0;
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        // 按钮复位逻辑
        if (saveNow.get()) saveNow.set(false);
        if (previewNow.get()) previewNow.set(false);

        if (mc.player == null || !isSorting) return;

        if (timer > 0) {
            timer--;
            return;
        }

        // 获取当前预设的所有槽位设置
        int presetIndex = activePreset.get().ordinal();
        List<Setting<List<Item>>> targetSettings = allPresetsSettings.get(presetIndex);

        // 遍历处理槽位
        while (currentSlotIndex < 9) {
            // 从设置里读取期望的物品列表
            List<Item> preferredItems = targetSettings.get(currentSlotIndex).get();
            
            // 如果列表为空或者第一个是空气，视为该槽位不需要整理
            if (preferredItems.isEmpty() || preferredItems.get(0) == Items.AIR) {
                currentSlotIndex++;
                continue;
            }

            Item targetItem = preferredItems.get(0);

            // 检查是否已经满足（精确或模糊）
            if (isSlotCorrectExact(currentSlotIndex, targetItem)) {
                currentSlotIndex++;
                continue;
            }
            if (fuzzyMatch.get() && isSlotCorrectFuzzy(currentSlotIndex, targetItem)) {
                currentSlotIndex++;
                continue;
            }

            // 执行查找和移动
            if (findAndMoveItem(currentSlotIndex, targetItem)) {
                timer = tickDelay.get();
                currentSlotIndex++;
                return; // 移动了一个，等待下一刻
            }
            
            currentSlotIndex++;
        }

        if (autoDisable.get()) toggle();
        else isSorting = false;
    }

    // --- 核心功能 ---

    private void saveHotbarToPreset() {
        if (mc.player == null) return;
        int presetIndex = activePreset.get().ordinal();
        List<Setting<List<Item>>> targetSlotSettings = allPresetsSettings.get(presetIndex);

        StringBuilder sb = new StringBuilder();
        sb.append("已保存 [").append(activePreset.get().name()).append("] 到配置文件: ");

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            Item item = stack.getItem();
            
            // 将当前物品写入 Setting
            Setting<List<Item>> setting = targetSlotSettings.get(i);
            if (item == Items.AIR) {
                setting.set(new ArrayList<>()); // 空气存为空列表
                sb.append("空, ");
            } else {
                setting.set(Collections.singletonList(item)); // 存入物品
                sb.append(item.getName().getString()).append(", ");
            }
        }
        info(sb.toString());
    }

    private void previewPreset() {
        int presetIndex = activePreset.get().ordinal();
        List<Setting<List<Item>>> targetSlotSettings = allPresetsSettings.get(presetIndex);
        
        info("--- 预览 " + activePreset.get().name() + " ---");
        for (int i = 0; i < 9; i++) {
            List<Item> items = targetSlotSettings.get(i).get();
            String itemName = (items.isEmpty() || items.get(0) == Items.AIR) ? "空" : items.get(0).getName().getString();
            info("槽位 " + (i + 1) + ": " + itemName);
        }
    }

    private void cycleNextPreset() {
        int current = activePreset.get().ordinal();
        for (int i = 1; i <= 5; i++) {
            int nextIndex = (current + i) % 5;
            if (cycleEnables.get(nextIndex).get()) {
                activePreset.set(Preset.values()[nextIndex]);
                return;
            }
        }
    }

    // --- 匹配逻辑 ---

    private boolean isSlotCorrectExact(int slot, Item targetItem) {
        return mc.player.getInventory().getStack(slot).getItem() == targetItem;
    }

    private boolean isSlotCorrectFuzzy(int slot, Item targetItem) {
        ItemStack stack = mc.player.getInventory().getStack(slot);
        if (stack.isEmpty()) return false;
        String currentName = stack.getItem().toString();
        String targetName = targetItem.toString();
        
        if (targetName.contains("enchanted_golden_apple") && currentName.contains("golden_apple")) return true;
        
        String targetType = getMediaType(targetName);
        return targetType != null && currentName.contains(targetType);
    }

    private boolean findAndMoveItem(int targetSlot, Item targetItem) {
        // 1. 精确查找
        FindItemResult result = InvUtils.find(stack -> stack.getItem() == targetItem);

        // 2. 宽容查找
        if (!result.found() && fuzzyMatch.get()) {
            result = findFuzzyReplacement(targetItem);
        }

        if (result.found()) {
            if (result.slot() == targetSlot) return false;
            InvUtils.move().from(result.slot()).toHotbar(targetSlot);
            return true;
        }
        return false;
    }

    private FindItemResult findFuzzyReplacement(Item target) {
        String name = target.toString().toLowerCase();
        if (name.contains("enchanted_golden_apple")) {
            return InvUtils.find(s -> s.getItem().toString().contains("golden_apple") && !s.getItem().toString().contains("enchanted"));
        }
        String type = getMediaType(name);
        if (type != null) {
            String[] tiers = {"netherite", "diamond", "iron", "golden", "stone", "wooden", "chainmail"};
            for (String tier : tiers) {
                FindItemResult res = InvUtils.find(s -> s.getItem().toString().contains(tier + type));
                if (res.found()) return res;
            }
        }
        return new FindItemResult(-1, 0);
    }
    
    private String getMediaType(String name) {
        if (name.contains("_sword")) return "_sword";
        if (name.contains("_pickaxe")) return "_pickaxe";
        if (name.contains("_axe")) return "_axe";
        if (name.contains("_shovel")) return "_shovel";
        if (name.contains("_hoe")) return "_hoe";
        if (name.contains("_helmet")) return "_helmet";
        if (name.contains("_chestplate")) return "_chestplate";
        if (name.contains("_leggings")) return "_leggings";
        if (name.contains("_boots")) return "_boots";
        return null;
    }

    @Override
    public void onDeactivate() {
        isSorting = false;
    }
}