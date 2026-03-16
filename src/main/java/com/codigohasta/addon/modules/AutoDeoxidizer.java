package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.Oxidizable;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AutoDeoxidizer extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // 默认开启，确保你进游戏就能用
    private final Setting<Boolean> stripLogs = sgGeneral.add(new BoolSetting.Builder()
        .name("原木去皮")
        .description("是否自动将原木转为去皮原木。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> removeWax = sgGeneral.add(new BoolSetting.Builder()
        .name("去除蜡层")
        .description("是否同时去除铜块的蜡层 (Wax)。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> checkHand = sgGeneral.add(new BoolSetting.Builder()
        .name("检查手持斧头")
        .description("如果你手里已经拿着斧头，则不进行自动切换。")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<Item>> handBlacklist = sgGeneral.add(new ItemListSetting.Builder()
        .name("手持黑名单")
        .description("手持这些物品时不触发功能。")
        .defaultValue(
            Items.HONEYCOMB,
            Items.FLINT_AND_STEEL,
            Items.SHEARS,
            Items.GLOW_INK_SAC,
            Items.INK_SAC,
            Items.BRUSH,
            Items.BONE_MEAL
        )
        .build()
    );

    private final Setting<Boolean> checkInventory = sgGeneral.add(new BoolSetting.Builder()
        .name("检查背包")
        .description("如果快捷栏没有斧头，是否从背包中寻找并临时移动。")
        .defaultValue(true)
        .build()
    );

    // 两个核心手动列表
    private final Map<Block, Block> stripMap = new HashMap<>();
    private final Map<Block, Block> waxMap = new HashMap<>();

    public AutoDeoxidizer() {
        super(AddonTemplate.CATEGORY, "AutoDeoxidizer", "右键铜块或原木时自动切换斧头进行处理。");
    }

    @Override
    public void onActivate() {
        initStripMap();
        initWaxMap();
    }

    @EventHandler
    private void onInteractBlock(InteractBlockEvent event) {
        if (mc.world == null || mc.player == null) return;
        if (event.hand != Hand.MAIN_HAND) return;

        BlockHitResult hitResult = event.result;
        BlockPos pos = hitResult.getBlockPos();
        BlockState state = mc.world.getBlockState(pos);
        Block block = state.getBlock();

        // 1. 检查手持斧头
        if (checkHand.get() && mc.player.getMainHandStack().getItem() instanceof AxeItem) {
            return;
        }

        // 2. 检查黑名单
        if (handBlacklist.get().contains(mc.player.getMainHandStack().getItem())) {
            return;
        }

        boolean shouldScrape = false;

        // A. 检查氧化 (原生接口，这是最稳的，绝对支持所有铜)
        // 注意：Waxed(涂蜡) 的方块在这里会返回 Empty，所以必须下面单独检查 Wax
        if (Oxidizable.getDecreasedOxidationState(state).isPresent()) {
            shouldScrape = true;
        }
        // B. 检查蜡层 (查我们的手动表)
        else if (removeWax.get() && waxMap.containsKey(block)) {
            shouldScrape = true;
        }
        // C. 检查原木去皮 (查我们的手动表)
        else if (stripLogs.get() && stripMap.containsKey(block)) {
            shouldScrape = true;
        }

        if (!shouldScrape) return;

        // 3. 寻找斧头
        FindItemResult axe = InvUtils.find(item -> item.getItem() instanceof AxeItem);

        if (!axe.found()) return;

        // 4. 执行操作
        event.cancel(); // 阻止原版交互(如放置方块)

        int currentSlot = mc.player.getInventory().selectedSlot;
        int axeSlot = axe.slot();

        // 执行切换和点击
        if (axe.isHotbar()) {
            InvUtils.swap(axeSlot, true);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
            mc.player.swingHand(Hand.MAIN_HAND);
            InvUtils.swapBack();
        } else if (checkInventory.get()) {
            InvUtils.move().from(axeSlot).to(currentSlot);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
            mc.player.swingHand(Hand.MAIN_HAND);
            InvUtils.move().from(currentSlot).to(axeSlot);
        }
    }

    // === 手动注册表：原木去皮 ===
    private void initStripMap() {
        stripMap.clear();
        // 橡木
        stripMap.put(Blocks.OAK_LOG, Blocks.STRIPPED_OAK_LOG);
        stripMap.put(Blocks.OAK_WOOD, Blocks.STRIPPED_OAK_WOOD);
        // 云杉
        stripMap.put(Blocks.SPRUCE_LOG, Blocks.STRIPPED_SPRUCE_LOG);
        stripMap.put(Blocks.SPRUCE_WOOD, Blocks.STRIPPED_SPRUCE_WOOD);
        // 白桦
        stripMap.put(Blocks.BIRCH_LOG, Blocks.STRIPPED_BIRCH_LOG);
        stripMap.put(Blocks.BIRCH_WOOD, Blocks.STRIPPED_BIRCH_WOOD);
        // 丛林
        stripMap.put(Blocks.JUNGLE_LOG, Blocks.STRIPPED_JUNGLE_LOG);
        stripMap.put(Blocks.JUNGLE_WOOD, Blocks.STRIPPED_JUNGLE_WOOD);
        // 金合欢
        stripMap.put(Blocks.ACACIA_LOG, Blocks.STRIPPED_ACACIA_LOG);
        stripMap.put(Blocks.ACACIA_WOOD, Blocks.STRIPPED_ACACIA_WOOD);
        // 深色橡木
        stripMap.put(Blocks.DARK_OAK_LOG, Blocks.STRIPPED_DARK_OAK_LOG);
        stripMap.put(Blocks.DARK_OAK_WOOD, Blocks.STRIPPED_DARK_OAK_WOOD);
        // 红树林
        stripMap.put(Blocks.MANGROVE_LOG, Blocks.STRIPPED_MANGROVE_LOG);
        stripMap.put(Blocks.MANGROVE_WOOD, Blocks.STRIPPED_MANGROVE_WOOD);
        // 樱花
        stripMap.put(Blocks.CHERRY_LOG, Blocks.STRIPPED_CHERRY_LOG);
        stripMap.put(Blocks.CHERRY_WOOD, Blocks.STRIPPED_CHERRY_WOOD);
        // 1.21.4 苍白橡木
        stripMap.put(Blocks.PALE_OAK_LOG, Blocks.STRIPPED_PALE_OAK_LOG);
        stripMap.put(Blocks.PALE_OAK_WOOD, Blocks.STRIPPED_PALE_OAK_WOOD);
        // 竹子
        stripMap.put(Blocks.BAMBOO_BLOCK, Blocks.STRIPPED_BAMBOO_BLOCK);
        // 下界菌柄
        stripMap.put(Blocks.CRIMSON_STEM, Blocks.STRIPPED_CRIMSON_STEM);
        stripMap.put(Blocks.CRIMSON_HYPHAE, Blocks.STRIPPED_CRIMSON_HYPHAE);
        stripMap.put(Blocks.WARPED_STEM, Blocks.STRIPPED_WARPED_STEM);
        stripMap.put(Blocks.WARPED_HYPHAE, Blocks.STRIPPED_WARPED_HYPHAE);
    }

    // === 手动注册表：去除蜡层 ===
    // 只有涂蜡的方块才需要手动指定，没涂蜡的走 Oxidizable 接口
    private void initWaxMap() {
        waxMap.clear();
        // 全氧化阶段的 Waxed Block -> Block
        addWaxSet(Blocks.WAXED_COPPER_BLOCK, Blocks.COPPER_BLOCK);
        addWaxSet(Blocks.WAXED_EXPOSED_COPPER, Blocks.EXPOSED_COPPER);
        addWaxSet(Blocks.WAXED_WEATHERED_COPPER, Blocks.WEATHERED_COPPER);
        addWaxSet(Blocks.WAXED_OXIDIZED_COPPER, Blocks.OXIDIZED_COPPER);

        // 切制铜块
        addWaxSet(Blocks.WAXED_CUT_COPPER, Blocks.CUT_COPPER);
        addWaxSet(Blocks.WAXED_EXPOSED_CUT_COPPER, Blocks.EXPOSED_CUT_COPPER);
        addWaxSet(Blocks.WAXED_WEATHERED_CUT_COPPER, Blocks.WEATHERED_CUT_COPPER);
        addWaxSet(Blocks.WAXED_OXIDIZED_CUT_COPPER, Blocks.OXIDIZED_CUT_COPPER);

        // 台阶
        addWaxSet(Blocks.WAXED_CUT_COPPER_SLAB, Blocks.CUT_COPPER_SLAB);
        addWaxSet(Blocks.WAXED_EXPOSED_CUT_COPPER_SLAB, Blocks.EXPOSED_CUT_COPPER_SLAB);
        addWaxSet(Blocks.WAXED_WEATHERED_CUT_COPPER_SLAB, Blocks.WEATHERED_CUT_COPPER_SLAB);
        addWaxSet(Blocks.WAXED_OXIDIZED_CUT_COPPER_SLAB, Blocks.OXIDIZED_CUT_COPPER_SLAB);

        // 楼梯
        addWaxSet(Blocks.WAXED_CUT_COPPER_STAIRS, Blocks.CUT_COPPER_STAIRS);
        addWaxSet(Blocks.WAXED_EXPOSED_CUT_COPPER_STAIRS, Blocks.EXPOSED_CUT_COPPER_STAIRS);
        addWaxSet(Blocks.WAXED_WEATHERED_CUT_COPPER_STAIRS, Blocks.WEATHERED_CUT_COPPER_STAIRS);
        addWaxSet(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, Blocks.OXIDIZED_CUT_COPPER_STAIRS);

        // 1.21 新增：铜门 (Door)
        addWaxSet(Blocks.WAXED_COPPER_DOOR, Blocks.COPPER_DOOR);
        addWaxSet(Blocks.WAXED_EXPOSED_COPPER_DOOR, Blocks.EXPOSED_COPPER_DOOR);
        addWaxSet(Blocks.WAXED_WEATHERED_COPPER_DOOR, Blocks.WEATHERED_COPPER_DOOR);
        addWaxSet(Blocks.WAXED_OXIDIZED_COPPER_DOOR, Blocks.OXIDIZED_COPPER_DOOR);

        // 1.21 新增：铜活板门 (Trapdoor)
        addWaxSet(Blocks.WAXED_COPPER_TRAPDOOR, Blocks.COPPER_TRAPDOOR);
        addWaxSet(Blocks.WAXED_EXPOSED_COPPER_TRAPDOOR, Blocks.EXPOSED_COPPER_TRAPDOOR);
        addWaxSet(Blocks.WAXED_WEATHERED_COPPER_TRAPDOOR, Blocks.WEATHERED_COPPER_TRAPDOOR);
        addWaxSet(Blocks.WAXED_OXIDIZED_COPPER_TRAPDOOR, Blocks.OXIDIZED_COPPER_TRAPDOOR);

        // 1.21 新增：铜格栅 (Grate)
        addWaxSet(Blocks.WAXED_COPPER_GRATE, Blocks.COPPER_GRATE);
        addWaxSet(Blocks.WAXED_EXPOSED_COPPER_GRATE, Blocks.EXPOSED_COPPER_GRATE);
        addWaxSet(Blocks.WAXED_WEATHERED_COPPER_GRATE, Blocks.WEATHERED_COPPER_GRATE);
        addWaxSet(Blocks.WAXED_OXIDIZED_COPPER_GRATE, Blocks.OXIDIZED_COPPER_GRATE);

        // 1.21 新增：铜灯 (Bulb)
        addWaxSet(Blocks.WAXED_COPPER_BULB, Blocks.COPPER_BULB);
        addWaxSet(Blocks.WAXED_EXPOSED_COPPER_BULB, Blocks.EXPOSED_COPPER_BULB);
        addWaxSet(Blocks.WAXED_WEATHERED_COPPER_BULB, Blocks.WEATHERED_COPPER_BULB);
        addWaxSet(Blocks.WAXED_OXIDIZED_COPPER_BULB, Blocks.OXIDIZED_COPPER_BULB);
        
        // 1.21 新增：雕纹铜块 (Chiseled)
        addWaxSet(Blocks.WAXED_CHISELED_COPPER, Blocks.CHISELED_COPPER);
        addWaxSet(Blocks.WAXED_EXPOSED_CHISELED_COPPER, Blocks.EXPOSED_CHISELED_COPPER);
        addWaxSet(Blocks.WAXED_WEATHERED_CHISELED_COPPER, Blocks.WEATHERED_CHISELED_COPPER);
        addWaxSet(Blocks.WAXED_OXIDIZED_CHISELED_COPPER, Blocks.OXIDIZED_CHISELED_COPPER);
    }

    private void addWaxSet(Block waxed, Block unwaxed) {
        if (waxed != null && unwaxed != null) {
            waxMap.put(waxed, unwaxed);
        }
    }
}