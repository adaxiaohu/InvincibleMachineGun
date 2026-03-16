package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import baritone.api.BaritoneAPI; // 导入 Baritone API
import baritone.api.pathing.goals.GoalBlock;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.screen.slot.SlotActionType;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.Item;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Direction;
import net.minecraft.registry.tag.ItemTags;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class AutoMiner extends Module {

    // 状态机
    private enum State {
        IDLE,
        MINING,
        WAITING_FOR_BARITONE_STOP, // 新增：等待 Baritone 停止
        LOOKING_FOR_PLACE_POS,
        PLACING_SHULKER,
        LOADING_ITEMS,
        COLLECTING_SHULKER, // 新增：回收潜影盒的状态
        SWAPPING_TOOLS, // 新增：切换工具的状态
        REPAIRING,      // 新增：修复工具的状态
        BREAKING_SHULKER,
        RESUMING
    }

    private final SettingGroup sgMining = settings.createGroup("Mining");
    private final SettingGroup sgRepair = settings.createGroup("Auto Repair"); // 新增：自动修复设置组
    private final SettingGroup sgShulker = settings.createGroup("Shulker Loader");

    // --- Mining Settings ---
    private final Setting<List<Block>> blocksToMine = sgMining.add(new BlockListSetting.Builder()
        .name("blocks-to-mine")
        .description("选择要挖掘的方块列表。")
        .build()
    );

    private final Setting<Integer> mineRange = sgMining.add(new IntSetting.Builder()
        .name("mine-range")
        .description("Baritone 搜索方块的最大范围。")
        .defaultValue(64)
        .min(16)
        .sliderRange(16, 256)
        .build()
    );

    // --- Auto Repair Settings ---
    private final Setting<Boolean> enableAutoRepair = sgRepair.add(new BoolSetting.Builder()
        .name("enable-auto-repair")
        .description("当主手镐子耐久度过低时，自动切换到备用镐进行修复。")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<Block>> repairBlocks = sgRepair.add(new BlockListSetting.Builder()
        .name("repair-blocks")
        .description("用于修复的矿石列表 (例如：煤矿、红石矿)。")
        .defaultValue(List.of(Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE, Blocks.NETHER_QUARTZ_ORE))
        .visible(enableAutoRepair::get)
        .build()
    );

    private final Setting<Integer> repairThreshold = sgRepair.add(new IntSetting.Builder()
        .name("repair-threshold")
        .description("镐子耐久度低于此百分比时开始修复。")
        .defaultValue(20)
        .min(1).max(99)
        .sliderRange(1, 99)
        .visible(enableAutoRepair::get)
        .build()
    );

    private final Setting<Integer> mineThreshold = sgRepair.add(new IntSetting.Builder()
        .name("mine-threshold")
        .description("镐子耐久度高于此百分比时停止修复。")
        .defaultValue(90)
        .min(1).max(100)
        .sliderRange(1, 100)
        .visible(enableAutoRepair::get)
        .build()
    );

    // --- Shulker Loader Settings ---
    private final Setting<Boolean> enableShulkerLoading = sgShulker.add(new BoolSetting.Builder()
        .name("enable-shulker-loading")
        .description("当背包满时，自动使用潜影盒打包矿物。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> minEmptySlots = sgShulker.add(new IntSetting.Builder()
        .name("min-empty-slots")
        .description("当背包空格子数量少于或等于此值时，开始打包。")
        .defaultValue(4)
        .min(0)
        .sliderRange(0, 10)
        .visible(enableShulkerLoading::get)
        .build()
    );

    private final Setting<Boolean> useEmptyShulkersOnly = sgShulker.add(new BoolSetting.Builder()
        .name("use-empty-shulkers-only")
        .description("只寻找并使用空的潜影盒。")
        .defaultValue(true)
        .visible(enableShulkerLoading::get)
        .build()
    );

    private final Setting<Boolean> rotate = sgShulker.add(new BoolSetting.Builder()
        .name("rotate")
        .description("放置和破坏时自动转头。")
        .defaultValue(true)
        .build()
    );

    private State currentState = State.IDLE;
    private Block currentMiningTarget;
    private BlockPos shulkerPlacePos;
    private int loadingTimer; // 用于等待GUI打开的计时器
    private int collectTimer; // 用于回收潜影盒的计时器
    private Item brokenShulkerItem; // 记录被打破的潜影盒的物品类型
    private int shulkerCountBeforeBreaking; // 记录打破前的数量
    private boolean baritoneWasScanningDroppedItems; // 记录Baritone原始设置
    private State stateBeforeRepair; // 记录进入修复前的状态
    private List<Integer> usedShulkerSlots = new ArrayList<>(); // 记录已经尝试过的满盒子

    public AutoMiner() {
        super(AddonTemplate.CATEGORY, "auto-miner", "使用Baritone自动挖矿，并用潜影盒打包。");
    }

    @Override
    public void onActivate() {
        if (blocksToMine.get().isEmpty()) {
            error("挖掘列表为空，请先选择要挖掘的方块。");
            toggle();
            return;
        }
        currentState = State.MINING;
        info("自动矿机已启动...");
        shulkerPlacePos = null;
        loadingTimer = 0;
        collectTimer = 0;
        brokenShulkerItem = null;
        shulkerCountBeforeBreaking = 0;
        stateBeforeRepair = null;
        baritoneWasScanningDroppedItems = BaritoneAPI.getSettings().mineScanDroppedItems.value;
        usedShulkerSlots.clear();
    }

    @Override
    public void onDeactivate() {
        BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().cancel();
        info("自动矿机已停止。");
        BaritoneAPI.getSettings().mineScanDroppedItems.value = baritoneWasScanningDroppedItems; // 恢复Baritone设置
        currentState = State.IDLE;
        shulkerPlacePos = null;
        loadingTimer = 0;
        collectTimer = 0;
        stateBeforeRepair = null;
        usedShulkerSlots.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        switch (currentState) {
            case MINING:
                // 检查是否需要修复
                if (enableAutoRepair.get() && needsRepair(mc.player.getMainHandStack())) {
                    info("主手镐子耐久度低，准备开始修复...");
                    BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().cancel();
                    stateBeforeRepair = State.MINING; // 记录当前状态
                    currentState = State.SWAPPING_TOOLS;
                    return; // 进入下一轮tick处理
                }


                // 监控背包
                if (enableShulkerLoading.get() && isInventoryFull()) {
                    info("背包已满，暂停挖掘，准备打包...");
                    BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().cancel();
                    currentState = State.WAITING_FOR_BARITONE_STOP; // 先等待 Baritone 停止
                    return; // 关键修复：立即返回，等待下一轮tick来处理新状态
                }

                // 如果Baritone当前没有在挖矿，就给它分配一个新任务
                if (!BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().isActive()) {
                    // 恢复 Baritone 拾取设置，以防在修复后被关闭
                    BaritoneAPI.getSettings().mineScanDroppedItems.value = true;
                    assignNewMiningJob();
                }
                break;

            case WAITING_FOR_BARITONE_STOP:
                if (!BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().isActive()) {
                    info("Baritone 已停止，开始寻找放置点。");
                    usedShulkerSlots.clear(); // 开始新一轮打包时，清空记录
                    currentState = State.LOOKING_FOR_PLACE_POS;
                }
                break;
            // 其他状态的逻辑将在后续实现
            case LOOKING_FOR_PLACE_POS: {
                shulkerPlacePos = findPlacePos();
                if (shulkerPlacePos != null) {
                    info("已找到放置点: " + shulkerPlacePos.toShortString() + "，准备放置潜影盒。");
                    currentState = State.PLACING_SHULKER;
                } else {
                    error("在附近找不到合适的空间来放置潜影盒，请移动到更开阔的地方。");
                    toggle();
                }
                break;
            }

            case PLACING_SHULKER: {
                // 1. 寻找一个可用的潜影盒
                FindItemResult shulker = InvUtils.find(stack -> {
                    // 首先，必须是潜影盒
                    if (!(stack.getItem() instanceof BlockItem && ((BlockItem) stack.getItem()).getBlock() instanceof ShulkerBoxBlock)) {
                        return false;
                    }
                    // 如果设置了只用空盒，则必须是空的
                    if (useEmptyShulkersOnly.get() && !isShulkerEmpty(stack)) {
                        return false;
                    }
                    // 最后，不能是我们在这个周期里已经试过的满盒子
                    // 注意：这里的 usedShulkerSlots 逻辑可能需要根据槽位来判断，但我们暂时保留原逻辑
                    return !usedShulkerSlots.contains(InvUtils.find(stack.getItem()).slot());
                });

                if (!shulker.found()) {
                    error("找不到可用的（或新的）潜影盒！");
                    toggle();
                    return;
                }
 
                // 2. 确保潜影盒在快捷栏上
                if (!shulker.isHotbar()) {
                    // 如果不在快捷栏，就找一个空的快捷栏格子，然后交换过去
                    FindItemResult emptyHotbarSlot = InvUtils.find(ItemStack::isEmpty, 0, 8);
                    if (!emptyHotbarSlot.found()) {
                        error("快捷栏已满，无法调度潜影盒！请清空一个快捷栏格子。");
                        toggle();
                        return;
                    }
                    // 执行交换
                    InvUtils.move().from(shulker.slot()).to(emptyHotbarSlot.slot());
                    // 在下一帧再执行放置，等待交换完成
                    return;
                }
 
                // 3. 放置潜影盒 (此时它一定在快捷栏里)
                // 重新查找一次，确保我们拿到的是快捷栏里的结果
                ItemStack shulkerStack = mc.player.getInventory().getStack(shulker.slot());
                FindItemResult shulkerInHotbar = InvUtils.find(itemStack -> itemStack.getItem() == shulkerStack.getItem(), 0, 8);
                if (BlockUtils.place(shulkerPlacePos, shulkerInHotbar, rotate.get(), 0, true)) {
                    info("潜影盒已放置，准备装载物品...");
                    currentState = State.LOADING_ITEMS;
                }
                break;
            }
            case LOADING_ITEMS: {
                // 如果GUI还没打开
                if (!(mc.currentScreen instanceof ShulkerBoxScreen)) {
                    // 使用计时器防止重复发包
                    if (loadingTimer <= 0) {
                        // 发送右键数据包来打开盒子
                        BlockHitResult hitResult = new BlockHitResult(shulkerPlacePos.toCenterPos(), Direction.UP, shulkerPlacePos, false);
                        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
                        loadingTimer = 5; // 等待5个tick
                    } else {
                        loadingTimer--;
                    }
                    return;
                }

                // 如果光标上有物品，说明上一次的shift-click失败了（盒子满了）
                if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                    info("潜影盒已满，准备更换下一个...");
                    mc.player.currentScreenHandler.setCursorStack(ItemStack.EMPTY); // 清空光标
                    mc.player.closeHandledScreen();
                    usedShulkerSlots.add(InvUtils.find(itemStack -> mc.world.getBlockState(shulkerPlacePos).getBlock().asItem() == itemStack.getItem()).slot()); // 记录这个满盒子
                    currentState = State.BREAKING_SHULKER; // 去挖掉这个满盒子
                    return;
                }


                // GUI已打开，开始转移物品
                boolean hasItemsToMove = false;
                // 遍历玩家主背包 (9-35)
                for (int i = 9; i < 36; i++) {
                    ItemStack stack = mc.player.currentScreenHandler.getSlot(i).getStack();
                    if (!stack.isEmpty() && blocksToMine.get().stream().anyMatch(b -> b.asItem() == stack.getItem())) {
                        hasItemsToMove = true;
                        // 使用Shift-Click (QUICK_MOVE) 来快速移动物品
                        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                        // 每次只移动一个，等待下一帧，防止不同步
                        return;
                    }
                }

                // 如果一轮转移后，背包里已经没有需要移动的物品了
                if (!hasItemsToMove) {
                    info("物品装载完成，准备回收潜影盒...");
                    mc.player.closeHandledScreen();
                    currentState = State.BREAKING_SHULKER;
                }
                break;
            }
            case BREAKING_SHULKER: {
                // 检查潜影盒是否还存在
                if (!(mc.world.getBlockState(shulkerPlacePos).getBlock() instanceof ShulkerBoxBlock)) {
                    info("潜影盒已被破坏，准备前往拾取...");
                    // 记录被打破的潜影盒物品类型和当前数量
                    // this.brokenShulkerItem = mc.world.getBlockState(shulkerPlacePos).getBlock().asItem(); // This line is redundant, brokenShulkerItem is set before breaking
                    this.shulkerCountBeforeBreaking = countItems(brokenShulkerItem);
                    this.collectTimer = 200; // 设置 10 秒的超时保护
                    currentState = State.COLLECTING_SHULKER;
                }

                // 如果Baritone没有在工作，就命令它去挖掉那个盒子
                // 使用 getCustomGoalProcess 来移动到方块旁边，然后 MineProcess 会接管挖掘
                // 最终修复：使用 BlockUtils.breakBlock 来破坏方块，而不是 Baritone
                // 确保 Baritone 能够捡起掉落物
                BaritoneAPI.getSettings().mineScanDroppedItems.value = true;
                // 在破坏之前记录物品信息
                this.brokenShulkerItem = mc.world.getBlockState(shulkerPlacePos).getBlock().asItem();
                this.shulkerCountBeforeBreaking = countItems(brokenShulkerItem);

                info("正在破坏潜影盒: " + brokenShulkerItem.getName().getString());
                if (BlockUtils.breakBlock(shulkerPlacePos, true)) {
                    // breakBlock 返回 true 意味着破坏指令已发出
                    // 状态将在下一轮 tick 中因为方块消失而自动切换到 COLLECTING_SHULKER
                }
                // breakBlock 是一个持续的过程，下一轮tick会检查方块是否已消失
                break;
            }
            case COLLECTING_SHULKER: {
                // 1. 检查是否已经捡到了
                if (countItems(brokenShulkerItem) > shulkerCountBeforeBreaking) {
                    info("成功回收潜影盒！");
                    shulkerPlacePos = null;
                    brokenShulkerItem = null;
                    // 检查背包是否仍然满了
                    if (isInventoryFull()) {
                        currentState = State.LOOKING_FOR_PLACE_POS;
                    } else {
                        currentState = State.RESUMING;
                    }
                    return;
                }

                // 2. 超时检查
                collectTimer--;
                if (collectTimer <= 0) {
                    error("回收潜影盒超时，请手动检查！");
                    toggle();
                    return;
                }

                // 3. 如果没捡到，命令 Baritone 过去
                BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(shulkerPlacePos));
                break;
            }
            case SWAPPING_TOOLS: {
                // 这个状态负责处理工具的切换逻辑
                if (stateBeforeRepair != null) { // --- 进入修复流程 ---
                    // 1. 找到主手的精准采集镐
                    ItemStack silkTouchPick = mc.player.getMainHandStack();
                    if (!isSilkTouchPickaxe(silkTouchPick)) {
                        error("主手不是精准采集镐，无法执行修复流程！");
                        toggle();
                        return;
                    }

                    // 2. 找到背包里可用的修复镐 (有经验修补，无精准采集)
                    FindItemResult repairPickResult = InvUtils.find(this::isRepairPickaxe);
                    if (!repairPickResult.found()) {
                        error("找不到可用的修复镐 (需要带经验修补，且不能有精准采集)！");
                        toggle();
                        return;
                    }

                    // 3. 执行切换
                    info("切换工具：精准采集镐 -> 副手，修复镐 -> 主手");
                    InvUtils.move().from(mc.player.getInventory().selectedSlot).toOffhand(); // 主手到副手
                    InvUtils.move().from(repairPickResult.slot()).to(mc.player.getInventory().selectedSlot); // 修复镐到主手

                    // 4. 切换到修复状态
                    currentState = State.REPAIRING;

                } else { // --- 修复完成，切换回来 ---
                    // 1. 找到副手的精准采集镐
                    if (!isSilkTouchPickaxe(mc.player.getOffHandStack())) {
                        error("修复完成，但在副手找不到精准采集镐！");
                        toggle();
                        return;
                    }
                    // 2. 找到主手的修复镐
                    if (!isRepairPickaxe(mc.player.getMainHandStack())) {
                        error("修复完成，但主手不是修复镐！");
                        toggle();
                        return;
                    }

                    // 3. 执行切换
                    info("切换工具：精准采集镐 -> 主手，修复镐 -> 背包");
                    // 先把主手的修复镐放回背包，防止物品栏操作冲突
                    FindItemResult emptySlot = InvUtils.findEmpty();
                    if (!emptySlot.found()) {
                        error("背包已满，无法将修复镐切换回背包！");
                        toggle();
                        return;
                    }
                    InvUtils.move().from(mc.player.getInventory().selectedSlot).to(emptySlot.slot());
                    InvUtils.move().fromOffhand().to(mc.player.getInventory().selectedSlot); // 副手到主手

                    // 4. 恢复到修复前的状态
                    currentState = State.RESUMING; // 使用RESUMING来统一处理恢复逻辑
                }
                break;
            }
            case REPAIRING: {
                // 检查副手的镐子是否修好了
                if (isRepairComplete(mc.player.getOffHandStack())) {
                    info("镐子已修复，准备切换回来...");
                    BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().cancel();
                    currentState = State.SWAPPING_TOOLS; // 进入工具切换状态
                    return;
                }

                // 如果Baritone没在挖，就命令它去挖修复矿
                if (!BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().isActive()) {
                    info("开始挖掘经验矿石进行修复...");
                    // 关键：关闭物品拾取，只吃经验
                    BaritoneAPI.getSettings().mineScanDroppedItems.value = false;
                    BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().mine(repairBlocks.get().toArray(new Block[0]));
                }
                break;
            }
            case RESUMING: {
                info("恢复自动挖掘作业...");
                // 确保 Baritone 恢复拾取物品的能力
                BaritoneAPI.getSettings().mineScanDroppedItems.value = true;
                currentState = State.MINING;
                break;
            }
        }
    }

    private BlockPos findPlacePos() {
        BlockPos playerPos = mc.player.getBlockPos();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos checkPos = playerPos.add(x, 0, z);
                BlockPos below = checkPos.down();
                BlockPos above = checkPos.up();

                if (mc.world.getBlockState(below).isSideSolidFullSquare(mc.world, below, Direction.UP) && mc.world.isAir(checkPos) && mc.world.isAir(above)) {
                    return checkPos;
                }
            }
        }
        return null;
    }

    private void assignNewMiningJob() {
        // 简单的逻辑：按列表顺序循环挖掘
        if (currentMiningTarget == null) {
            currentMiningTarget = blocksToMine.get().get(0);
        } else {
            int currentIndex = blocksToMine.get().indexOf(currentMiningTarget);
            int nextIndex = (currentIndex + 1) % blocksToMine.get().size();
            currentMiningTarget = blocksToMine.get().get(nextIndex);
        }

        info("开始挖掘: " + currentMiningTarget.getName().getString());
        // 关键修复：创建一个 Baritone 能识别的 BlockOptionalMeta 对象
        // Baritone 的 mine Process 需要一个或多个 Block 对象
        // 我们直接传递 Block 对象数组
        // 将正确的对象传递给 mine 方法
        BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().mine(mineRange.get(), currentMiningTarget);
    }

    private boolean isInventoryFull() {
        // 修复：手动计算空格子数量
        int emptySlots = 0;
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                emptySlots++;
            }
        }
        return emptySlots <= minEmptySlots.get();
    }

    private boolean isShulkerEmpty(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem && ((BlockItem) stack.getItem()).getBlock() instanceof ShulkerBoxBlock) {
            // 最终修复：使用 1.21.4 的标准 Data Components API
            ContainerComponent container = stack.getComponents().get(DataComponentTypes.CONTAINER);
            if (container == null) {
                return true; // 没有容器组件，就是空的
            }
            return container.stream().allMatch(ItemStack::isEmpty); // 1.21.4 修复：检查容器流中的所有物品是否都为空
        }
        return false;
    }

    // --- 修复相关的方法 ---

    private boolean needsRepair(ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamageable()) return false;
        double durabilityPercent = 100.0 * (stack.getMaxDamage() - stack.getDamage()) / stack.getMaxDamage();
        return durabilityPercent <= repairThreshold.get();
    }

    private boolean isRepairComplete(ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamageable()) return true; // 如果不是有效物品，则认为修复完成
        double durabilityPercent = 100.0 * (stack.getMaxDamage() - stack.getDamage()) / stack.getMaxDamage();
        return durabilityPercent >= mineThreshold.get();
    }

    private boolean isSilkTouchPickaxe(ItemStack stack) {
        if (stack.isEmpty() || !stack.isIn(ItemTags.PICKAXES)) return false;
        // 检查是否同时拥有经验修补和精准采集
        // 最终修复：使用 Meteor Client 官方工具类
        boolean hasMending = Utils.hasEnchantment(stack, Enchantments.MENDING);
        boolean hasSilkTouch = Utils.hasEnchantment(stack, Enchantments.SILK_TOUCH);
        return hasMending && hasSilkTouch;
    }

    private boolean isRepairPickaxe(ItemStack stack) {
        if (stack.isEmpty() || !stack.isIn(ItemTags.PICKAXES)) return false;
        // 检查是否拥有经验修补，但没有精准采集
        // 最终修复：使用 Meteor Client 官方工具类
        boolean hasMending = Utils.hasEnchantment(stack, Enchantments.MENDING);
        boolean hasSilkTouch = Utils.hasEnchantment(stack, Enchantments.SILK_TOUCH);
        return hasMending && !hasSilkTouch;
    }

    // 修复：手动实现物品计数方法
    private int countItems(Item item) {
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) {
                count += mc.player.getInventory().getStack(i).getCount();
            }
        }
        return count;
    }

    @Override
    public String getInfoString() {
        if (currentState == State.MINING && currentMiningTarget != null) {
            return currentMiningTarget.getName().getString();
        }
        if (shulkerPlacePos != null) return "Packing...";

        if (currentState == State.REPAIRING) {
            return "Repairing";
        }

        return currentState.name();
    }
}
