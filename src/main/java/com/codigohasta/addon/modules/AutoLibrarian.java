package com.codigohasta.addon.modules;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.event.events.PathEvent;
import baritone.api.event.listener.AbstractGameEventListener;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.process.ICustomGoalProcess;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.modules.villager.EnchantSortConfig;
import com.codigohasta.addon.modules.villager.LibrarianStep;
import com.codigohasta.addon.modules.villager.LibrarianWarp;
import com.codigohasta.addon.modules.villager.LibrarianWarp.LibrarianOffer;
import com.codigohasta.addon.modules.villager.ShulkerManager;
import com.codigohasta.addon.utils.heutil.HeBlockUtils;
import com.codigohasta.addon.utils.heutil.HeInvUtils;
import com.codigohasta.addon.utils.heutil.HeRotationUtils;
import com.codigohasta.addon.modules.villager.VillagerMode;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.SelectMerchantTradeC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.VillagerProfession;

import java.util.ArrayList;
import java.util.List;

/**
 * 终极自动化图书管理员交易系统 (Minecraft 1.21.11 专属)
 * 融合了 VillagerTrader 的高鲁棒性状态机、精准锚定寻路、网络延迟重试与原版冷却机制。
 * 已修复 Java Switch 语法混用问题与 Entity.getPos() 映射丢失问题。
 */
public class AutoLibrarian extends Module implements AbstractGameEventListener {

    // ==========================================
    // 界面设置 (UI Settings)
    // ==========================================
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTarget = settings.createGroup("目标附魔");
    private final SettingGroup sgVillager = settings.createGroup("村民机制");
    private final SettingGroup sgDelay = settings.createGroup("延迟控制");
    private final SettingGroup sgSupply = settings.createGroup("补给设置");
    private final SettingGroup sgMemory = settings.createGroup("记忆系统");

    // 基础寻路与扫描
    private final Setting<Integer> minDistance = sgGeneral.add(new IntSetting.Builder().name("操作范围").description("操作容器和村民的触发距离").min(2).sliderMax(4).defaultValue(2).build());
    private final Setting<Integer> searchRange = sgGeneral.add(new IntSetting.Builder().name("搜索容器范围").description("启动时扫描容器的半径").min(6).sliderMax(30).defaultValue(15).build());

    // 容器类型配置
    private final Setting<List<BlockEntityType<?>>> emeraldStorage = sgGeneral.add(new StorageBlockListSetting.Builder().name("绿宝石容器").defaultValue(new BlockEntityType[]{BlockEntityType.BARREL}).build());
    private final Setting<List<BlockEntityType<?>>> bookStorage = sgGeneral.add(new StorageBlockListSetting.Builder().name("普通书容器").defaultValue(new BlockEntityType[]{BlockEntityType.CHEST}).build());
    private final Setting<List<BlockEntityType<?>>> dumpStorage = sgGeneral.add(new StorageBlockListSetting.Builder().name("满盒回收容器").defaultValue(new BlockEntityType[]{BlockEntityType.TRAPPED_CHEST}).build());
    private final Setting<List<BlockEntityType<?>>> emptyBoxStorage = sgGeneral.add(new StorageBlockListSetting.Builder().name("空盒补给容器").defaultValue(new BlockEntityType[]{BlockEntityType.DROPPER}).build());

    // 目标配置
    private final Setting<java.util.Set<net.minecraft.registry.RegistryKey<net.minecraft.enchantment.Enchantment>>> targets = sgTarget.add(new EnchantmentListSetting.Builder()
        .name("目标附魔清单")
        .description("勾选你想要交易的所有附魔书")
        .defaultValue(java.util.Collections.emptySet())
        .build()
    );
    private final Setting<VillagerMode> mode = sgTarget.add(new EnumSetting.Builder<VillagerMode>()
        .name("交易模式")
        .description("控制对村民涨价/售价的容忍度")
        .defaultValue(VillagerMode.仅不溢价)
        .build()
    );
    private final Setting<Integer> maxPrice = sgTarget.add(new IntSetting.Builder().name("最高可接受价格").description("单本书超过多少绿宝石就不买").min(1).sliderMax(64).defaultValue(30).build());

    // 村民机制 (移植自 VillagerTrader)
    private final Setting<Integer> checkCooldown = sgVillager.add(new IntSetting.Builder().name("缺货冷却(秒)").description("村民缺货后，等待多久再次找他").min(10).sliderMax(120).defaultValue(30).build());
    private final Setting<Integer> maxExhaustions = sgVillager.add(new IntSetting.Builder().name("每日交易上限").description("一个村民每天最多被买空几次(原版通常为3次)").min(1).sliderMax(4).defaultValue(3).build());
    private final Setting<Integer> workEndTick = sgVillager.add(new IntSetting.Builder().name("下班时间(Tick)").description("村民停止工作的时间").min(8000).sliderMax(12000).defaultValue(9000).build());

    // 延迟控制
    private final Setting<Integer> windowDelay = sgDelay.add(new IntSetting.Builder().name("界面延迟(Tick)").description("打开或关闭容器时的等待时间").min(1).sliderMax(20).defaultValue(5).build());
    private final Setting<Integer> clickDelay = sgDelay.add(new IntSetting.Builder().name("点击延迟(Tick)").description("在界面中拿取、交易物品的间隔").min(1).sliderMax(10).defaultValue(2).build());
 private final Setting<Integer> supplyEmeraldStacks = sgSupply.add(new IntSetting.Builder().name("绿宝石补给量(组)").description("每次去木桶拿多少组绿宝石").min(1).sliderMax(27).defaultValue(9).build());
    private final Setting<Integer> supplyBookStacks = sgSupply.add(new IntSetting.Builder().name("普通书补给量(组)").description("每次去箱子拿多少组普通书").min(1).sliderMax(27).defaultValue(4).build());
   
    private final Setting<Boolean> useMemory = sgMemory.add(new BoolSetting.Builder()
        .name("启用村民记忆")
        .description("开启后，关闭再打开模块不会重新扫描已知村民，省去摸底时间。")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> forceReset = sgMemory.add(new BoolSetting.Builder()
        .name("强制重新扫描")
        .description("开启此项后再开启模块，将丢弃记忆重新摸底。扫描启动后该开关会自动关闭。")
        .defaultValue(false)
        .build()
    );
    // ==========================================
    // 核心状态变量
    // ==========================================
    private final IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
    private final ICustomGoalProcess customGoalProcess = baritone.getCustomGoalProcess();
    private final Settings baritoneSettings = BaritoneAPI.getSettings();

    private LibrarianStep step = LibrarianStep.NONE;
    private LibrarianStep nextStep = null; 
    private LibrarianStep closeScreenNextStep = null;
    
    // 辅助系统
    private final EnchantSortConfig sortConfig = new EnchantSortConfig();
    private final ShulkerManager shulkerManager = new ShulkerManager();
    
    // 实体与容器坐标
    private List<LibrarianWarp> librarianList = new ArrayList<>();
    private LibrarianWarp currentTarget = null;
    private LibrarianOffer currentOffer = null;
    
    private BlockPos emeraldPos, bookPos, dumpPos, emptyBoxPos, sortAreaCenter;
    
    private int timer = 0; 
    private int requiredEmeralds = 0; 
    private int requiredBooks = 0;
    private boolean wait = false; // 挂机待机标志
    private boolean tradedThisSession = false; // 记录当前交互是否成功交易过
    
    // 分类系统的临时记忆
    private int currentSortItemSlot = -1; 
    private BlockPos currentSortBoxPos = null;

    // 🌟新增：记录当前世界的标识（防止去别的维度或服务器串台）
    private String memoryWorld = "";

    public AutoLibrarian() {
        super(AddonTemplate.CATEGORY, "自动交易附魔书", "自动找村民买附魔书，自动放进潜影盒里满盒自动换盒。这个模块当前没完善，只能勉强跑起来，有机会完善一下，或者等xiaohe666的lotus更新，看看他怎么做的。");
        baritone.getGameEventHandler().registerEventListener(this);
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) {
            toggle();
            return;
        }

        // 获取当前维度的唯一标识符 (例如：minecraft:overworld)
        String currentWorld = mc.world.getRegistryKey().getValue().toString();
        boolean worldChanged = !currentWorld.equals(memoryWorld);

        // ==========================================
        // 核心逻辑：是否需要重新初始化摸底？
        // 条件：强制重置开关打开 OR 世界维度变了 OR 未开启记忆功能 OR 记忆列表为空
        // ==========================================
        if (forceReset.get() || worldChanged || !useMemory.get() || librarianList.isEmpty()) {
            info("🔄 初始化模式：正在扫描附近村民并丢弃旧记忆...");
            librarianList.clear();
            memoryWorld = currentWorld;

            for (Entity entity : mc.world.getEntities()) {
                if (entity instanceof VillagerEntity villager) {
                    if (villager.getVillagerData().profession().matchesKey(VillagerProfession.LIBRARIAN)) {
                        BlockPos lecternPos = getOperatePos(villager);
                        if (lecternPos != null) {
                            librarianList.add(new LibrarianWarp(villager.getUuid(), lecternPos));
                        }
                    }
                }
            }
            
            // 扫描完成后，自动将 UI 上的“强制重置”开关弹回关闭状态，方便下次直接使用记忆
            if (forceReset.get()) forceReset.set(false);
            
            if (librarianList.isEmpty()) {
                warning("搜索完毕。附近没有找到具有有效站立位的图书管理员！");
                toggle();
                return;
            }
            info("锁定 " + librarianList.size() + " 名图书管理员，准备开始摸底勘探！");
            this.step = LibrarianStep.INIT_SCAN;

        } else {
            // ==========================================
            // 触发记忆系统
            // ==========================================
            info("🧠 触发记忆系统：已加载 " + librarianList.size() + " 个村民数据，跳过摸底直接交易！");
            
            // 记忆模式下，仅清理“缺货状态”和“每日交易次数”，保留他们的附魔书数据 (Offers)
            librarianList.forEach(warp -> {
                warp.setTradeTimes(0L);
                warp.setLastTradeTime(0L);
                // 强制解锁所有记录好的书本的缺货状态
                warp.getOffers().forEach(offer -> offer.setOutOfStock(false));
            });
            
            // 确保摸底阶段被安全跳过
            this.step = LibrarianStep.CHECK_SUPPLY;
        }

        // 统一执行容器扫描
        scanContainers();
        if (emeraldPos == null || bookPos == null) {
            warning("缺少必要的补给容器(绿宝石/普通书)，模块关闭！");
            toggle();
            return;
        }
        
        sortAreaCenter = mc.player.getBlockPos();
        baritoneSettings.allowBreak.value = false;
        baritoneSettings.allowPlace.value = false;
        
        this.wait = false;
        this.timer = 0;
        this.tradedThisSession = false;
    }

    @Override
    public void onDeactivate() {
        this.step = LibrarianStep.NONE;
        this.nextStep = null;
        this.baritone.getCommandManager().execute("cancel");
        // 🌟 删除 this.librarianList.clear(); 让数据保留在内存中！
    }

    // ==========================================
    // 核心 Tick 循环与 Timer 延迟系统
    // ==========================================
    @EventHandler
    private void onTick(Pre event) {
        if (!checkAndDecrement()) return;

        switch (step) {
            case INIT_SCAN -> doInitScan();
            case OPEN_FOR_DISCOVER -> doDiscoverTrade();

            case CHECK_SUPPLY -> checkSupplyAndTarget();
            case GOTO_EMERALD -> gotoIfNeed(emeraldPos, "前往补给绿宝石", LibrarianStep.TAKE_EMERALD);
            // 🌟核心修复：进货时，拿取你设置的组数，而不是刚刚好够用的数量
            // 🌟修复：使用 Math.max 确保拿取的数量至少能满足当前村民单次爆买的需求
case TAKE_EMERALD -> takeItems(emeraldPos, Items.EMERALD, Math.max(requiredEmeralds, supplyEmeraldStacks.get() * 64), LibrarianStep.CHECK_SUPPLY);

case GOTO_BASE_BOOK -> gotoIfNeed(bookPos, "前往补给普通书", LibrarianStep.TAKE_BASE_BOOK);
case TAKE_BASE_BOOK -> takeItems(bookPos, Items.BOOK, Math.max(requiredBooks, supplyBookStacks.get() * 64), LibrarianStep.CHECK_SUPPLY);

            case NEXT_LIBRARIAN -> nextLibrarian();
            case OPEN_TRADE -> openTradeGUI();
            case EXECUTE_TRADE -> executeTrade();
            case CLOSE_SCREEN_AND_NEXT -> closeScreenAndNext();

            case GOTO_SORT_AREA -> gotoIfNeed(sortAreaCenter, "前往分类区", LibrarianStep.SORT_BOOKS);
            case SORT_BOOKS -> sortBooksLogic();

            case HANDLE_FULL_BOX -> prepareShulkerReplacement();
            case BREAK_FULL_BOX -> { if (shulkerManager.tickBreakFullBox()) step = LibrarianStep.GOTO_DUMP_CHEST; }
            case GOTO_DUMP_CHEST -> gotoIfNeed(dumpPos, "前往满盒回收箱", LibrarianStep.DUMP_FULL_BOX);
            case DUMP_FULL_BOX -> { if (shulkerManager.tickDumpFullBox(dumpPos)) step = LibrarianStep.GOTO_EMPTY_BOX_CHEST; }
            case GOTO_EMPTY_BOX_CHEST -> gotoIfNeed(emptyBoxPos, "前往空盒补给箱", LibrarianStep.TAKE_EMPTY_BOX);
            case TAKE_EMPTY_BOX -> { if (shulkerManager.tickTakeEmptyBox(emptyBoxPos)) step = LibrarianStep.GOTO_PLACE_POS; }
            case GOTO_PLACE_POS -> gotoIfNeed(currentSortBoxPos, "回到被挖掉的潜影盒位置", LibrarianStep.PLACE_NEW_BOX);
            case PLACE_NEW_BOX -> { if (shulkerManager.tickPlaceNewBox()) step = LibrarianStep.SORT_BOOKS; }
            
            case WAIT -> handleWait();
            
            // 下列状态都是交给 Baritone 走路，Tick 中什么都不做
            // 修复: 统一使用 Java 14 的箭头语法 -> 避免编译错误
            case WALK_TO_UNDISCOVERED, WALKING_TO_VILLAGER -> none();
                
            default -> {}
        }
    }

    private boolean checkAndDecrement() {
        if (this.timer > 0) {
            this.timer--;
            return false;
        }
        return true;
    }

    private void setDelay(int d) {
        this.timer = d;
    }

    private void none() {
        // 交给 Baritone 和 onPathEvent 处理，Tick 内不干扰
    }

    // ==========================================
    // 阶段 1：摸底勘探逻辑 (带容错)
    // ==========================================
    private void doInitScan() {
        for (LibrarianWarp warp : librarianList) {
            if (!warp.isDiscovered()) {
                currentTarget = warp;
                gotoIfNeed(warp.getOperatePos(), "摸底勘探村民", LibrarianStep.OPEN_FOR_DISCOVER);
                return;
            }
        }
        info("全部村民摸底完毕，开始交易分拣循环！");
        step = LibrarianStep.CHECK_SUPPLY;
    }

    private void doDiscoverTrade() {
        VillagerEntity villager = currentTarget.getVillager();
        if (villager == null || !villager.isAlive()) {
            currentTarget.setDiscovered(true); // 死了就跳过
            step = LibrarianStep.INIT_SCAN;
            return;
        }

        if (!(mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.MerchantScreen)) {
            // 打开界面重试机制
            openEntityGUI(villager, LibrarianStep.OPEN_FOR_DISCOVER);
            return;
        }

        MerchantScreenHandler handler = (MerchantScreenHandler) mc.player.currentScreenHandler;
        TradeOfferList offers = handler.getRecipes();
        currentTarget.clearOffers();

        for (int i = 0; i < offers.size(); i++) {
            TradeOffer trade = offers.get(i);
            ItemStack sellItem = trade.getSellItem();
            
            if (sellItem.getItem().toString().contains("enchanted_book")) {
                var mainEnchant = sortConfig.getMainEnchantment(sellItem);
                if (mainEnchant != null) {
                    int cPrice = trade.getDisplayedFirstBuyItem().getCount();
                    int oPrice = trade.getOriginalFirstBuyItem().getCount();
                    int level = sortConfig.getEnchantmentLevel(sellItem, mainEnchant);

                    currentTarget.addOffer(new LibrarianOffer(i, mainEnchant, level, cPrice, oPrice, trade.isDisabled()));
                    info("摸底发现: " + mainEnchant.getValue().getPath() + " Lv." + level + " | 原价:" + oPrice + " 现价:" + cPrice);
                }
            }
        }
        
        currentTarget.setDiscovered(true);
        setDelayCloseScreenAndNext(LibrarianStep.INIT_SCAN);
    }

    // ==========================================
    // 阶段 2：智能调度与进货算法
    // ==========================================
   private void checkSupplyAndTarget() {
        if (InvUtils.find(item -> item.toString().contains("enchanted_book")).found()) {
            step = LibrarianStep.GOTO_SORT_AREA;
            return;
        }

        int emptySlots = 0;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) emptySlots++;
        }
        if (emptySlots < 2) {
            warning("背包空间严重不足，无法进货，请清理！");
            this.wait = true;
            step = LibrarianStep.WAIT;
            return;
        }

        currentTarget = null;
        currentOffer = null;
        long currentTime = System.currentTimeMillis();
        long cooldownMs = this.checkCooldown.get() * 1000L;
        
        for (LibrarianWarp warp : librarianList) {
            if (warp.getTradeTimes() >= this.maxExhaustions.get() || currentTime - warp.getLastTradeTime() < cooldownMs) continue; 
            
            for (LibrarianOffer offer : warp.getOffers()) {
                if (offer.isOutOfStock()) continue;

                int currentPrice = offer.getEmeraldPrice();
                int originalPrice = offer.getOriginalPrice();

                if (mode.get() == VillagerMode.仅一块钱 && currentPrice > 1) continue;
                if (mode.get() == VillagerMode.仅不溢价 && currentPrice > originalPrice) continue;
                if (currentPrice > maxPrice.get()) continue; 
                
                 boolean isWanted = false;
                for (net.minecraft.registry.RegistryKey<net.minecraft.enchantment.Enchantment> targetKey : targets.get()) {
                    if (targetKey.equals(offer.getEnchantment())) {
                        isWanted = true;
                        break;
                    }
                }

                if (isWanted) {
                    currentTarget = warp;
                    currentOffer = offer;
                    break;
                }
            }
            if (currentTarget != null) break;
        }

        if (currentTarget == null) {
            // 🌟核心修复1：拦截日志刷屏，仅在首次切入待机时打印
            if (!this.wait) {
                long time = mc.world.getTimeOfDay() % 24000L;
                if (time >= this.workEndTick.get()) {
                    info("村民已下班且所有目标缺货，进入待机模式...");
                } else {
                    info("当前暂无合适的交易目标（可能都在冷却或未补货），原地稍作等待...");
                }
                this.wait = true;
            }
            step = LibrarianStep.WAIT;
            return;
        }

        this.wait = false; 
        int maxTradeUses = Math.min(12, emptySlots - 1); 
        requiredBooks = maxTradeUses;
        requiredEmeralds = maxTradeUses * currentOffer.getEmeraldPrice();
        
        int hasEmeralds = InvUtils.find(Items.EMERALD).count();
        int hasBooks = InvUtils.find(Items.BOOK).count();

        if (hasEmeralds < requiredEmeralds) {
            step = LibrarianStep.GOTO_EMERALD;
        } else if (hasBooks < requiredBooks) {
            step = LibrarianStep.GOTO_BASE_BOOK;
        } else {
            step = LibrarianStep.NEXT_LIBRARIAN;
        }
    }

    private void takeItems(BlockPos containerPos, net.minecraft.item.Item targetItem, int amountNeeded, LibrarianStep nextStep) {
        var handler = mc.player.currentScreenHandler;

        if (handler instanceof PlayerScreenHandler) {
            HeBlockUtils.open(containerPos);
            setDelay(windowDelay.get());
            return;
        }

        if (handler instanceof GenericContainerScreenHandler chestHandler) {
            int currentCount = InvUtils.find(targetItem).count();
            if (currentCount >= amountNeeded) {
                setDelayCloseScreenAndNext(nextStep);
                return;
            }

            for (int i = 0; i < chestHandler.getInventory().size(); i++) {
                if (chestHandler.getSlot(i).getStack().getItem() == targetItem) {
                    InvUtils.shiftClick().slotId(i);
                    setDelay(clickDelay.get());
                    return; 
                }
            }
            
            warning("容器内 " + targetItem.getName().getString() + " 不足！需要人工干预。");
            toggle();
        } else {
            HeInvUtils.closeCurScreen();
            setDelay(windowDelay.get());
        }
    }

    // ==========================================
    // 阶段 3：村民交易执行逻辑
    // ==========================================
    private void nextLibrarian() {
        if (currentTarget != null) {
            gotoIfNeed(currentTarget.getOperatePos(), "前往村民讲台站位", LibrarianStep.OPEN_TRADE);
        } else {
            step = LibrarianStep.CHECK_SUPPLY;
        }
    }

    private void openTradeGUI() {
        VillagerEntity villager = currentTarget.getVillager();
        if (villager == null || !villager.isAlive()) {
            currentTarget.setTradeTimes((long) this.maxExhaustions.get());
            step = LibrarianStep.CHECK_SUPPLY;
            return;
        }
        openEntityGUI(villager, LibrarianStep.EXECUTE_TRADE);
    }

    private void executeTrade() {
        if (!(mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.MerchantScreen)) {
            setDelay(windowDelay.get());
            step = LibrarianStep.OPEN_TRADE;
            return;
        }

        MerchantScreenHandler handler = (MerchantScreenHandler) mc.player.currentScreenHandler;
        TradeOfferList tradeOffers = handler.getRecipes();
        
        if (currentOffer.getTradeIndex() >= tradeOffers.size()) {
            setDelayCloseScreenAndNext(LibrarianStep.CHECK_SUPPLY);
            return;
        }

        TradeOffer actualTrade = tradeOffers.get(currentOffer.getTradeIndex());

        // ==========================================
        // 🌟核心修复1：买书前强制“验货”，防止盲买买错！
        // ==========================================
        ItemStack sellItem = actualTrade.getSellItem();
        if (sellItem.getItem().toString().contains("enchanted_book")) {
            var actualEnchant = sortConfig.getMainEnchantment(sellItem);
            if (actualEnchant == null || !actualEnchant.equals(currentOffer.getEnchantment())) {
                warning("⚠️ 警告：村民记忆与实际出售的附魔不符！(可能村民被替换或升级了)。正在重置该村民记忆...");
                currentTarget.setDiscovered(false); // 标记为未摸底
                currentTarget.clearOffers(); // 清除错误记忆
                setDelayCloseScreenAndNext(LibrarianStep.INIT_SCAN); // 强行回退到摸底状态
                return;
            }
        }

        // 缺货与上限判断
        if (actualTrade.isDisabled() || actualTrade.getUses() >= actualTrade.getMaxUses()) {
            long currentExhaustions = currentTarget.getTradeTimes();
            if (this.tradedThisSession) {
                currentTarget.setTradeTimes(currentExhaustions + 1L);
                info("此书售罄，今日累计交易次数: " + (currentExhaustions + 1) + "/" + maxExhaustions.get());
            } else {
                long time = mc.world.getTimeOfDay() % 24000L;
                if (time >= workEndTick.get()) {
                    currentTarget.setTradeTimes((long) maxExhaustions.get());
                }
                info("该附魔书暂时缺货，进入冷却...");
            }
            
            currentOffer.setOutOfStock(true);
            currentTarget.setLastTradeTime(System.currentTimeMillis());
            setDelayCloseScreenAndNext(LibrarianStep.CHECK_SUPPLY);
            return;
        }

        // 背包与材料检查
        int emptySlots = 0;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) emptySlots++;
        }
        if (emptySlots == 0) {
            info("背包已满，停止进货，准备去卸货。");
            setDelayCloseScreenAndNext(LibrarianStep.GOTO_SORT_AREA); 
            return;
        }

        int emeralds = InvUtils.find(Items.EMERALD).count();
        if (handler.getSlot(0).getStack().isOf(Items.EMERALD)) emeralds += handler.getSlot(0).getStack().getCount();
        if (handler.getSlot(1).getStack().isOf(Items.EMERALD)) emeralds += handler.getSlot(1).getStack().getCount();

        int books = InvUtils.find(Items.BOOK).count();
        if (handler.getSlot(0).getStack().isOf(Items.BOOK)) books += handler.getSlot(0).getStack().getCount();
        if (handler.getSlot(1).getStack().isOf(Items.BOOK)) books += handler.getSlot(1).getStack().getCount();

        if (emeralds < currentOffer.getEmeraldPrice() || books < 1) {
            info("身上材料不足(绿宝石或书耗尽)，关闭界面回去补给。");
            setDelayCloseScreenAndNext(LibrarianStep.CHECK_SUPPLY);
            return;
        }
        
        // 执行交易
        this.tradedThisSession = true;
        handler.setRecipeIndex(currentOffer.getTradeIndex());
        mc.getNetworkHandler().sendPacket(new SelectMerchantTradeC2SPacket(currentOffer.getTradeIndex()));
        InvUtils.shiftClick().slotId(2);
        
        setDelay(clickDelay.get()); 
    }

    // ==========================================
    // 阶段 4：智能分类系统
    // ==========================================
   private void sortBooksLogic() {
        var handler = mc.player.currentScreenHandler;
        
        if (!(handler instanceof PlayerScreenHandler)) {
            // 在潜影盒 GUI 内... (保持你原本代码不变)
            ItemStack itemInSlot = mc.player.getInventory().getStack(currentSortItemSlot);
            
            if (itemInSlot.isEmpty() || !itemInSlot.getItem().toString().contains("enchanted_book")) {
                setDelayCloseScreenAndNext(LibrarianStep.SORT_BOOKS);
                return;
            }

            InvUtils.shiftClick().slot(currentSortItemSlot);
            setDelay(clickDelay.get());

            ItemStack afterClick = mc.player.getInventory().getStack(currentSortItemSlot);
            if (!afterClick.isEmpty() && afterClick.getCount() == itemInSlot.getCount()) {
                warning("该颜色潜影盒已满！触发自动换盒程序...");
                setDelayCloseScreenAndNext(LibrarianStep.HANDLE_FULL_BOX);
            }
            return;
        }

        // 寻找身上需要分类的附魔书
        FindItemResult book = InvUtils.find(item -> item.toString().contains("enchanted_book"));
        if (!book.found()) {
            info("所有附魔书分类完毕！");
            step = LibrarianStep.CHECK_SUPPLY;
            return;
        }

        currentSortItemSlot = book.slot();
        net.minecraft.block.Block targetColor = sortConfig.getTargetBoxType(mc.player.getInventory().getStack(book.slot()));
        
        // ==========================================
        // 🌟核心修复2：遍历所有容器，计算最短距离，就近存放！
        // ==========================================
        BlockPos nearestBoxPos = null;
        double minDistance = Double.MAX_VALUE;
        Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());

        for (BlockEntity be : Utils.blockEntities()) {
            if (mc.world.getBlockState(be.getPos()).getBlock() == targetColor) {
                // 计算当前找到的这个潜影盒到玩家的距离平方 (性能更好)
                double dist = be.getPos().toCenterPos().squaredDistanceTo(playerPos);
                if (dist < minDistance) {
                    minDistance = dist;
                    nearestBoxPos = be.getPos();
                }
            }
        }
        
        if (nearestBoxPos != null) {
            currentSortBoxPos = nearestBoxPos;
            gotoIfNeed(currentSortBoxPos, "前往最近的分类潜影盒: " + targetColor.getName().getString(), LibrarianStep.SORT_BOOKS);
            
            if (step == LibrarianStep.SORT_BOOKS) { // 意味着距离足够近
                HeBlockUtils.open(currentSortBoxPos);
                setDelay(windowDelay.get());
            }
            return;
        }
        
        warning("附近找不到接收方潜影盒 (" + targetColor.getName().getString() + ")！请检查阵列。");
        toggle();
    }

    // ==========================================
    // 阶段 5：满盒置换微操包装
    // ==========================================
    private void prepareShulkerReplacement() {
        if (dumpPos == null || emptyBoxPos == null) {
            warning("未设置满盒回收箱或空盒补给箱，无法自动换盒！");
            toggle();
            return;
        }
        
        BlockState boxState = mc.world.getBlockState(currentSortBoxPos);
        // 🌟核心升级：直接获取物理物品对象，而不是名字字符串！
        net.minecraft.item.Item boxItem = boxState.getBlock().asItem(); 
        
        shulkerManager.initReplacement(currentSortBoxPos, boxItem);
        step = LibrarianStep.BREAK_FULL_BOX;
    }

    // ==========================================
    // 通用机制与辅助方法 (完全移植)
    // ==========================================
    private void gotoIfNeed(BlockPos targetPos, String msg, LibrarianStep nextStep) {
        // 修复：使用 new Vec3d(...) 构建当前坐标，避免映射问题
        Vec3d currentPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        double dist = currentPos.distanceTo(targetPos.toCenterPos());
        
        if (dist > this.minDistance.get() + 1.5) {
            info(msg);
            this.nextStep = nextStep;
            this.gotoTarget(targetPos);
        } else {
            this.step = nextStep;
        }
    }

    private void gotoTarget(BlockPos targetPos) {
        this.customGoalProcess.setGoalAndPath(new GoalNear(targetPos, this.minDistance.get()));
        this.step = LibrarianStep.WALKING_TO_VILLAGER;
    }

    @Override
    public void onPathEvent(PathEvent event) {
        if ((event == PathEvent.CANCELED || event == PathEvent.AT_GOAL) && this.nextStep != null) {
            this.step = this.nextStep;
            this.nextStep = null;
        }
    }

    private void openEntityGUI(Entity entity, LibrarianStep nextStep) {
        // 修复：使用 new Vec3d(...) 避免不同映射版本对 Entity.getPos() 的兼容性问题
        Vec3d entityPos = new Vec3d(entity.getX(), entity.getY(), entity.getZ());
        Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        double distance = entityPos.distanceTo(playerPos);
        
        if (distance > this.minDistance.get() + 1.5) {
            // 如果意外被挤开，重新寻路，不强行交互
            gotoIfNeed(entity.getBlockPos(), "靠近村民", nextStep);
            return;
        }

        EntityHitResult hit = ProjectileUtil.raycast(mc.player, playerPos, entityPos, entity.getBoundingBox(), Entity::canHit, distance * distance);
        HeRotationUtils.rotate(entity.getEyePos());
        
        if (hit == null) {
            mc.interactionManager.interactEntity(mc.player, entity, Hand.MAIN_HAND);
        } else {
            ActionResult res = mc.interactionManager.interactEntityAtLocation(mc.player, entity, hit, Hand.MAIN_HAND);
            if (!res.isAccepted()) {
                mc.interactionManager.interactEntity(mc.player, entity, Hand.MAIN_HAND);
            }
        }
        
        this.tradedThisSession = false;
        setDelay(windowDelay.get());
        this.step = nextStep;
    }

    private void setDelayCloseScreenAndNext(LibrarianStep closeScreenNextStep) {
        this.setDelay(this.windowDelay.get());
        this.step = LibrarianStep.CLOSE_SCREEN_AND_NEXT;
        this.closeScreenNextStep = closeScreenNextStep;
    }

    private void closeScreenAndNext() {
        if (mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen) {
            HeInvUtils.closeCurScreen();
        }
        this.step = this.closeScreenNextStep;
    }

    // 核心寻路锚定点：三点一线寻找讲台正确站位
    private BlockPos getOperatePos(VillagerEntity villager) {
        Direction[] dirs = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        for (Direction dir : dirs) {
            BlockPos blockPos = villager.getBlockPos().offset(dir);
            BlockState blockState = mc.world.getBlockState(blockPos);
            // 匹配讲台
            if (blockState.isOf(Blocks.LECTERN)) {
                BlockPos pos = blockPos.offset(dir);
                if (mc.world.getBlockState(pos).isAir() || mc.world.getBlockState(pos).isReplaceable()) {
                    return pos;
                }
            }
        }
        return null;
    }

    private void scanContainers() {
        emeraldPos = bookPos = dumpPos = emptyBoxPos = null;
        // 修复：使用 new Vec3d(...) 避免不同映射版本的冲突
        Vec3d pPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        for (BlockEntity be : Utils.blockEntities()) {
            if (pPos.distanceTo(be.getPos().toCenterPos()) > searchRange.get()) continue;
            BlockEntityType<?> type = be.getType();
            if (emeraldStorage.get().contains(type)) emeraldPos = be.getPos();
            else if (bookStorage.get().contains(type)) bookPos = be.getPos();
            else if (dumpStorage.get().contains(type)) dumpPos = be.getPos();
            else if (emptyBoxStorage.get().contains(type)) emptyBoxPos = be.getPos();
        }
    }

    private void handleWait() {
        long time = mc.world.getTimeOfDay() % 24000L;
        
        if (time < 100L) {
            if (this.wait) {
                info("🌞 新的一天，重置所有村民的交易与缺货状态！");
                for (LibrarianWarp warp : librarianList) {
                    warp.setTradeTimes(0L);
                    warp.setLastTradeTime(0L);
                    warp.getOffers().forEach(offer -> offer.setOutOfStock(false));
                }
                this.wait = false;
                step = LibrarianStep.CHECK_SUPPLY;
            }
            return;
        }

        if (time < this.workEndTick.get()) {
            long currentTime = System.currentTimeMillis();
            long cooldownMs = this.checkCooldown.get() * 1000L;
            
            for (LibrarianWarp warp : librarianList) {
                // 如果这个村民冷却到期了，且今天还没被榨干 3 次
                if (warp.getTradeTimes() < this.maxExhaustions.get() && (currentTime - warp.getLastTradeTime() > cooldownMs)) {
                    
                    // 🌟核心修复：检查这个刚刚冷却好的村民，到底有没有我们要买的书？
                    boolean hasWantedOffer = false;
                    for (LibrarianOffer offer : warp.getOffers()) {
                        int currentPrice = offer.getEmeraldPrice();
                        int originalPrice = offer.getOriginalPrice();

                        // 1. 价格过滤器
                        boolean priceValid = true;
                        if (mode.get() == VillagerMode.仅一块钱 && currentPrice > 1) priceValid = false;
                        if (mode.get() == VillagerMode.仅不溢价 && currentPrice > originalPrice) priceValid = false;
                        if (currentPrice > maxPrice.get()) priceValid = false;

                        // 2. 目标附魔过滤器
                        boolean isWanted = false;
                        for (net.minecraft.registry.RegistryKey<net.minecraft.enchantment.Enchantment> targetKey : targets.get()) {
                            if (targetKey.equals(offer.getEnchantment())) {
                                isWanted = true;
                                break;
                            }
                        }

                        // 如果不仅价格合适，而且是我们打勾想要的附魔
                        if (priceValid && isWanted) {
                            hasWantedOffer = true;
                            break;
                        }
                    }

                    // 🌟只有当这个村民身上确实有我们需要的东西时，才唤醒模块！
                    if (hasWantedOffer) {
                        warp.getOffers().forEach(offer -> offer.setOutOfStock(false));
                        this.wait = false;
                        step = LibrarianStep.CHECK_SUPPLY;
                        return;
                    }
                }
            }
        }
    }
}