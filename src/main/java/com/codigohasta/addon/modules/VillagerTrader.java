package com.codigohasta.addon.modules;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.event.events.PathEvent;
import baritone.api.event.listener.AbstractGameEventListener;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.process.ICustomGoalProcess;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.modules.villager.VillagerEntityWarp;
import com.codigohasta.addon.modules.villager.VillagerMode;
import com.codigohasta.addon.modules.villager.VillagerStep;
import com.codigohasta.addon.modules.villager.VillagerType;
import com.codigohasta.addon.utils.heutil.HeBlockUtils;
import com.codigohasta.addon.utils.heutil.HeInvUtils;
import com.codigohasta.addon.utils.heutil.HeRotationUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.StorageBlockListSetting;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.SelectMerchantTradeC2SPacket;
import net.minecraft.screen.Generic3x3ContainerScreenHandler;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.VillagerProfession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VillagerTrader extends Module implements AbstractGameEventListener {
   private static final Logger log = LoggerFactory.getLogger(VillagerTrader.class);
   private static final int INIT_TICK = 100;
   private static final Item[] EMPTY_ITEM_ARR = new Item[0];
   private final SettingGroup sgGeneral = settings.getDefaultGroup();
   
   private final Setting<Boolean> debug = this.sgGeneral.add(new BoolSetting.Builder().name("调试模式").description("调试模式").defaultValue(false).build());
   private final Setting<List<BlockEntityType<?>>> supplyStorage = this.sgGeneral.add(new StorageBlockListSetting.Builder().name("补给容器").description("装有交换物的容器的类型").defaultValue(new BlockEntityType[]{BlockEntityType.BARREL}).visible(() -> false).build());
   public final Setting<Integer> supplyQty = this.sgGeneral.add(new IntSetting.Builder().name("补给数量(组)").description("每次补给的数量").min(1).sliderMax(18).defaultValue(5).build());
   private final Setting<List<BlockEntityType<?>>> putStorage = this.sgGeneral.add(new StorageBlockListSetting.Builder().name("卸货容器").description("用于卸货容器的类型").defaultValue(new BlockEntityType[]{BlockEntityType.CHEST}).visible(() -> false).build());
   public final Setting<Integer> supplyRange = this.sgGeneral.add(new IntSetting.Builder().name("搜索容器范围").description("开启功能时, 搜索容器的范围").min(6).sliderMax(20).defaultValue(8).build());
   public final Setting<VillagerMode> mode = this.sgGeneral.add(new EnumSetting.Builder<VillagerMode>().name("模式").description("交易模式").defaultValue(VillagerMode.仅一块钱).build());
   public final Setting<Integer> checkCooldown = this.sgGeneral.add(new IntSetting.Builder().name("缺货冷却(秒)").description("村民缺货后，等待多久再次检查他是否补货").min(10).sliderMax(120).defaultValue(30).build());
   public final Setting<Integer> maxExhaustions = this.sgGeneral.add(new IntSetting.Builder().name("每日交易上限").description("一个村民每天最多被买空几次(Wiki机制为初始1次+补货2次=3次)").min(1).sliderMax(4).defaultValue(3).build());
   public final Setting<Integer> workEndTick = this.sgGeneral.add(new IntSetting.Builder().name("下班时间(tick)").description("村民停止工作的时间(原版通常是9000)").min(8000).sliderMax(12000).defaultValue(9000).visible(() -> false).build());
   public final Setting<Boolean> lateTrade = this.sgGeneral.add(new BoolSetting.Builder().name("下班后加班交易").description("在村民下班后开启模块时，强制交易所有人一轮").defaultValue(true).build());
   public final Setting<Integer> minDistance = this.sgGeneral.add(new IntSetting.Builder().name("操作范围").description("补给、卸货的距离").min(2).sliderMax(3).defaultValue(2).build());
   public final Setting<Boolean> one = this.sgGeneral.add(new BoolSetting.Builder().name("交易1").description("交易1").defaultValue(true).build());
   public final Setting<VillagerType> type1 = this.sgGeneral.add(new EnumSetting.Builder<VillagerType>().name("村民类型").description("村民类型").defaultValue(VillagerType.牧师).visible(one::get).build());
   public final Setting<List<Item>> buy1 = this.sgGeneral.add(new ItemListSetting.Builder().name("买").description("你需要买的物品").defaultValue(new Item[]{Items.ROTTEN_FLESH}).visible(one::get).build());
   public final Setting<List<Item>> sell1 = this.sgGeneral.add(new ItemListSetting.Builder().name("卖").description("你需要卖的物品").visible(one::get).build());
   public final Setting<Boolean> two = this.sgGeneral.add(new BoolSetting.Builder().name("交易2").description("开启第二种交易").defaultValue(false).build());
   public final Setting<VillagerType> type2 = this.sgGeneral.add(new EnumSetting.Builder<VillagerType>().name("村民类型2").description("村民类型, 暂不支持选择多个").defaultValue(VillagerType.工具匠).visible(two::get).build());
   public final Setting<List<Item>> buy2 = this.sgGeneral.add(new ItemListSetting.Builder().name("买2").description("你需要买的物品").visible(two::get).build());
   public final Setting<List<Item>> sell2 = this.sgGeneral.add(new ItemListSetting.Builder().name("卖2").description("你需要买的物品").defaultValue(new Item[]{Items.DIAMOND_PICKAXE}).visible(two::get).build());
   public final Setting<Integer> delay = this.sgGeneral.add(new IntSetting.Builder().name("延迟").description("操作延迟控制").defaultValue(10).build());
   public final Setting<Integer> clickDelay = this.sgGeneral.add(new IntSetting.Builder().name("点击延迟(tick)").description("在界面中拿取、放入物品的间隔").min(0).sliderMax(10).defaultValue(1).build());
public final Setting<Integer> windowDelay = this.sgGeneral.add(new IntSetting.Builder().name("界面延迟(tick)").description("打开或关闭容器时的等待时间").min(1).sliderMax(20).defaultValue(5).build());

   private final IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
   private final ICustomGoalProcess customGoalProcess = this.baritone.getCustomGoalProcess();
   private final Settings baritoneSettings = BaritoneAPI.getSettings();
   private BlockPos moneyPos;
   private BlockPos putPos;
   private BlockPos goodsPos;
   private Item goodsItem;
   private boolean needBuy;
   private List<VillagerEntityWarp> villagerList = Collections.emptyList();
   private VillagerEntityWarp currentVillager;
   private VillagerStep step = VillagerStep.None;
   private VillagerStep nextStep;
   private VillagerStep closeScreenNextStep;
   private boolean wait;
   private int tradeIndex = 0;
   private volatile int todayTimes = 0;
   private int timer = 0;
   private boolean tradedThisSession = false;
   private boolean doingLateTrade = false; // 记录当前是否处于“加班交易”模式

   public VillagerTrader() {
      super(AddonTemplate.CATEGORY, "自动村民交易者", "自动和村民交易。根据开启时的位置确定最近的容器, 大箱子卸货、木桶补绿宝石、盒子补货物(卖只能支持1种物品)源自lotus，我做了修改");
      this.baritone.getGameEventHandler().registerEventListener(this);
   }

   @Override
   public void onActivate() {
      if (mc.player != null && mc.world != null) {
         List<VillagerEntityWarp> villagerList = this.getVillagerEntity();
         if (villagerList.isEmpty()) {
            warning("附近没有合适村民");
            this.toggle();
         } else {
            BlockPos moneyPos = null;
            BlockPos putPos = null;
            BlockPos goodsPos = null;
            double min1 = Double.MAX_VALUE;
            double min2 = Double.MAX_VALUE;
            double min3 = Double.MAX_VALUE;

            Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
            for (BlockEntity blockEntity : Utils.blockEntities()) {
               BlockEntityType<?> type = blockEntity.getType();
               if (this.supplyStorage.get().contains(type)) {
                  double distanceTo = playerPos.distanceTo(blockEntity.getPos().toCenterPos());
                  if (distanceTo < this.supplyRange.get() && distanceTo < min1) {
                     moneyPos = blockEntity.getPos();
                     min1 = distanceTo;
                  }
               } else if (this.putStorage.get().contains(type)) {
                  double distanceTo = playerPos.distanceTo(blockEntity.getPos().toCenterPos());
                  if (distanceTo < this.supplyRange.get() && distanceTo < min2) {
                     putPos = blockEntity.getPos();
                     min2 = distanceTo;
                  }
               } else if (type == BlockEntityType.SHULKER_BOX) {
                  double distanceTo = playerPos.distanceTo(blockEntity.getPos().toCenterPos());
                  if (distanceTo < this.supplyRange.get() && distanceTo < min3) {
                     goodsPos = blockEntity.getPos();
                     min3 = distanceTo;
                  }
               }
            }

            if (moneyPos == null && goodsPos == null) {
               warning("找不到<补给容器-潜影盒>或<商品容器-潜影盒>");
            } else if (goodsPos == null) {
               warning("找不到<卸货容器-大箱子>");
            } else {
               long time = this.timeOfDay();
               if (time >= this.workEndTick.get() && this.lateTrade.get()) {
                   info("🌙 晚班模式启动：村民已下班，将强制进行一轮加班交易！");
                   this.doingLateTrade = true;
                   // 把村民交易次数设为 最大值-1，这样每个人刚好只会被点1次
                   villagerList.forEach(item -> {
                       item.setTradeTimes((long)(this.maxExhaustions.get() - 1));
                       item.setLastTradeTime(0L);
                   });
               } else {
                   info("🌞 自动交易已启动，正在扫描村民...");
                   this.doingLateTrade = false;
                   villagerList.forEach(item -> {
                       item.setTradeTimes(0L); 
                       item.setLastTradeTime(0L); 
                   });
               }
               this.todayTimes = 0;

               this.baritoneSettings.allowBreak.value = false;
                this.baritoneSettings.allowPlace.value = false;
                this.goodsItem = null;
                this.needBuy = false;

                // 核心修复：正确判定是“赚绿宝石”还是“花绿宝石”
                if (this.one.get()) {
                    if (!this.sell1.get().isEmpty()) {
                        this.goodsItem = this.sell1.get().get(0); // 卖出物（如铁锭）
                    }
                    // 如果你要买的东西【包含非绿宝石物品】（比如买钻石镐），才需要去木桶拿钱
                    if (!this.buy1.get().isEmpty() && !this.buy1.get().contains(Items.EMERALD)) {
                        this.needBuy = true;
                    }
                }

                if (this.two.get()) {
                    if (this.goodsItem == null && !this.sell2.get().isEmpty()) {
                        this.goodsItem = this.sell2.get().get(0);
                    }
                    if (!this.buy2.get().isEmpty() && !this.buy2.get().contains(Items.EMERALD)) {
                        this.needBuy = true;
                    }
                }

                this.tradeIndex = 0;
               this.wait = false;
               this.moneyPos = moneyPos;
               this.putPos = putPos;
               this.goodsPos = goodsPos;
               this.villagerList = villagerList;
               this.step = VillagerStep.Wait;
            }
         }
      } else {
         this.toggle();
      }
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.checkAndDecrement()) {
         switch (this.step) {
            case GoToPut:
               this.gotoPut();
               break;
            case Put:
               this.put();
               break;
            case GotoGoods:
               this.gotoGoodsIfNotFull();
               break;
            case TakeGoods:
               this.takeGoods();
               break;
            case GotoMoney:
               this.gotoMoneyIfNotFull();
               break;
            case TakeMoney:
               this.takeMoney();
               break;
            case NextVillager:
               this.nextVillager();
               break;
            case Walking:
               this.none();
               break;
            case OpenTrade:
               this.openTrade();
               break;
            case ExecuteTrade:
               this.executeTrade();
               break;
            case CloseScreenAndNext:
               this.closeScreenAndNext();
               break;
            case Wait:
               this.waitTrade();
               break;
            default:
               this.disableAuto();
               this.toggle();
         }
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

   private void setDelay() {
      this.timer = this.delay.get();
   }

   private void waitTrade() {
      long time = this.timeOfDay();
      
      // 1. 每天清晨 (0-100刻) 统一重置
      if (time < 100L) {
         if (this.wait || this.doingLateTrade) { 
            info("🌞 新的一天开始了，重置所有村民的交易状态！");
            this.doingLateTrade = false; // 清除加班标记
            this.villagerList.forEach(item -> {
               item.setTradeTimes(0L);     
               item.setLastTradeTime(0L);  
            });
            this.wait = false;
            this.step = VillagerStep.GoToPut;
         }
         return;
      }

      // 2. 检查是否有村民可以交易
      long currentTimeMillis = System.currentTimeMillis();
      long cooldownMs = this.checkCooldown.get() * 1000L;
      boolean hasReadyVillager = false;
      
      for (VillagerEntityWarp warp : this.villagerList) {
         if (warp.getTradeTimes() < this.maxExhaustions.get() && 
            (currentTimeMillis - warp.getLastTradeTime() > cooldownMs)) {
            hasReadyVillager = true;
            break;
         }
      }

      // 3. 根据时间与状态分配行动 (哪怕下班了，只要处于加班模式也继续运行)
      if (time < this.workEndTick.get() || this.doingLateTrade) {
         if (hasReadyVillager) {
            this.wait = false;
            this.step = VillagerStep.GoToPut;
         } else if (time >= this.workEndTick.get() && this.doingLateTrade) {
            // 所有人都被点完一轮了，结束加班
            info("🌙 晚间加班交易结束，准备挂机等待明天...");
            this.doingLateTrade = false;
            this.wait = true;
         }
      } else {
         // 正常下班待机
         if (!this.wait) {
            info("🌙 村民已下班 (过了 " + this.workEndTick.get() + " 刻)，今天不再补货，挂机等待明天...");
            this.wait = true;
         }
      }
   }

   private void closeScreenAndNext() {
    // 检查当前显示的 GUI 是否属于带容器的窗口
    if (mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen) {
        HeInvUtils.closeCurScreen();
    }
    this.step = this.closeScreenNextStep;
}

   private boolean needClean() {
      if (this.goodsItem != null) {
         FindItemResult findItemResult = InvUtils.find(new Item[]{this.goodsItem});
         if (!findItemResult.found() || findItemResult.count() < 64) {
            return true;
         }
      }

      if (this.needBuy) {
         FindItemResult findItemResult = InvUtils.find(new Item[]{Items.EMERALD});
         if (!findItemResult.found()) {
            return true;
         }

         int qty = this.mode.get() == VillagerMode.仅一块钱 ? 12 : 64;
         if (findItemResult.count() < qty) {
            return true;
         }
      }

      PlayerInventory playerInventory = mc.player.getInventory();
      int emptyQty = 0;

      for (int i = 0; i < 36; i++) {
         ItemStack itemStack = playerInventory.getStack(i);
         if (itemStack.isEmpty()) {
            emptyQty++;
         }
      }

      return emptyQty <= 0;
   }

   private void executeTrade() {
        if (!(mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.MerchantScreen)) {
            this.setDelay();
            this.step = VillagerStep.OpenTrade;
            return;
        }

        MerchantScreenHandler handler = (MerchantScreenHandler) mc.player.currentScreenHandler;
        TradeOfferList tradeOfferList = handler.getRecipes();
        boolean foundValidTrade = false;

        // 遍历村民的所有交易项
        for (int i = 0; i < tradeOfferList.size(); i++) {
            TradeOffer trade = tradeOfferList.get(i);
            
            // 1. 如果这个交易项被锁定了，或者次数用光了，直接跳过看下一个
            if (trade.isDisabled() || trade.getUses() >= trade.getMaxUses()) {
                continue;
            }

            Item gives = trade.getSellItem().getItem(); // 村民给你的东西 (比如绿宝石)
            Item wants = trade.getDisplayedFirstBuyItem().getItem(); // 村民要的东西 (比如铁锭)

            // 2. 匹配我们需要的交易
            // 情景 A: 卖货物赚绿宝石 (wants 是我们设定的商品，gives 是绿宝石)
            boolean isSellingGoods = (wants == this.goodsItem && gives == Items.EMERALD);
            // 情景 B: 花绿宝石买东西 (wants 是绿宝石，gives 是我们想买的物品)
            boolean isBuyingGoods1 = (this.one.get() && this.buy1.get().contains(gives) && wants == Items.EMERALD);
            boolean isBuyingGoods2 = (this.two.get() && this.buy2.get().contains(gives) && wants == Items.EMERALD);

            if (isSellingGoods || isBuyingGoods1 || isBuyingGoods2) {
                // 3. 检查奸商溢价 (根据你的模式设置)
                int originCount = trade.getOriginalFirstBuyItem().getCount();
                int currentPrice = trade.getDisplayedFirstBuyItem().getCount();
                
                if (this.mode.get() == VillagerMode.仅一块钱 && currentPrice > 1) {
                    continue; // 太贵了，跳过
                }
                if (this.mode.get() == VillagerMode.仅不溢价 && currentPrice > originCount) {
                    continue; // 涨价了，跳过
                }

                // 4. 找到完美的交易！
                this.tradeIndex = i;
                foundValidTrade = true;
                break;
            }
        }

      
        // 执行操作
        if (!foundValidTrade) {
            long currentExhaustions = this.currentVillager.getTradeTimes();
            
            if (this.tradedThisSession) {
                // 我们成功把他买空了
                this.currentVillager.setTradeTimes(currentExhaustions + 1L); 
                info("该村民已被买空。今日累计交易: " + (currentExhaustions + 1) + "/" + this.maxExhaustions.get() + " 次");
            } else {
                // 一打开发现就是空的
                if (this.timeOfDay() >= this.workEndTick.get()) {
                    // 如果已经下班了，他绝对不会再补货了，直接拉满次数将他拉黑
                    info("该村民已下班且无货，今晚不再补货，跳过他...");
                    this.currentVillager.setTradeTimes((long) this.maxExhaustions.get());
                } else {
                    // 白天没货，才给他冷却机会等补货
                    info("该村民还未补货，不计入交易次数，进入缺货冷却...");
                }
            }
            
            this.currentVillager.setLastTradeTime(System.currentTimeMillis()); 
            this.setDelayCloseScreenAndNext(VillagerStep.NextVillager);
        } else {
                
                this.tradedThisSession = true; // <--- 核心！标记为：我们成功交易过了！
                
                // 点击交易槽
                handler.setRecipeIndex(this.tradeIndex);
                mc.getNetworkHandler().sendPacket(new SelectMerchantTradeC2SPacket(this.tradeIndex));
                InvUtils.shiftClick().slotId(2);
                this.setDelay(this.clickDelay.get()); 
            }
        }
      
   
    

  private void openTrade() {
      VillagerEntity villager = this.currentVillager.getVillager();
      
      // 如果村民丢失或者死了，直接把交易次数拉满，今天彻底无视他
      if (villager == null || !villager.isAlive()) {
         info("村民丢失或嗝屁，剔除出今日交易名单");
         this.currentVillager.setTradeTimes((long) this.maxExhaustions.get()); 
         this.nextVillager();
         return;
      }

      Vec3d villagerPos = new Vec3d(villager.getX(), villager.getY(), villager.getZ());
      Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
      double distance = villagerPos.distanceTo(playerPos);
      
      if (distance > this.minDistance.get() + 0.5) {
         this.printLog("distance false");
         // 距离太远，可能寻路没走到，设一个短冷却(比如1秒)重试，而不是算作一次交易
         this.currentVillager.setLastTradeTime(System.currentTimeMillis() - (this.checkCooldown.get() * 1000L) + 1000L); 
         this.nextVillager();
      } else {
         this.printLog("openTrade");
         EntityHitResult entityHitResult = ProjectileUtil.raycast(
               mc.player, playerPos, villagerPos, villager.getBoundingBox(), Entity::canHit, playerPos.squaredDistanceTo(villagerPos)
            );
            if (entityHitResult == null) {
               if (this.debug.get()) {
                  info("111");
               }

               HeRotationUtils.rotate(villager.getEyePos());
               mc.interactionManager.interactEntity(mc.player, villager, Hand.MAIN_HAND);
               this.tradedThisSession = false;
               this.step = VillagerStep.ExecuteTrade;
            } else {
               if (this.debug.get()) {
                  info("222");
               }

               HeRotationUtils.rotate(entityHitResult.getEntity().getEyePos());
               ActionResult actionResult = mc.interactionManager.interactEntityAtLocation(mc.player, villager, entityHitResult, Hand.MAIN_HAND);
               if (!actionResult.isAccepted()) {
                  mc.interactionManager.interactEntity(mc.player, villager, Hand.MAIN_HAND);
                   this.tradedThisSession = false;
                  this.step = VillagerStep.ExecuteTrade;
               } else {
                  ChatUtils.error("无法打开交易界面，重试中...");
               }
            }

           this.setDelay(this.windowDelay.get()); 
         }
      }
   

   @Override
   public void onPathEvent(PathEvent event) {
      if (event == PathEvent.CANCELED && this.nextStep != null) {
         this.step = this.nextStep;
         this.nextStep = null;
      }
   }

   private void nextVillager() {
      this.printLog("find next villager");
      if (this.needClean()) {
         this.gotoIfNeed(this.putPos, "前往卸货", VillagerStep.Put);
         return;
      }

      VillagerEntityWarp best = null;
      Vec3d pos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
      double minDistance = Double.MAX_VALUE;
      long currentTimeMillis = System.currentTimeMillis();
      long cooldownMs = this.checkCooldown.get() * 1000L;

      for (VillagerEntityWarp warp : this.villagerList) {
         // 寻找目标：今天没被交易满3次，且不在冷却期内
         if (warp.getTradeTimes() < this.maxExhaustions.get() && 
             currentTimeMillis - warp.getLastTradeTime() > cooldownMs) {
             
            double distance = warp.getOperatePosCenter().distanceTo(pos);
            if (distance < minDistance) {
               best = warp;
               minDistance = distance;
            }
         }
      }

      if (best == null) {
         long time = this.timeOfDay();
         if (time >= this.workEndTick.get()) {
            info("所有村民均已交易完毕或已下班，待机至明天...");
         } else {
            info("周围所有村民都在缺货冷却中，原地稍作等待...");
         }
         this.wait = true;
         this.step = VillagerStep.GoToPut; // 去存个货，然后进入 Wait 状态不断轮询
      } else {
         this.printLog("锁定下一个目标 : {}", best.getOperatePosCenter());
         this.currentVillager = best;
         this.gotoVillager();
      }
   }
    private void takeMoney() {
        var handler = mc.player.currentScreenHandler;

        if (handler instanceof PlayerScreenHandler) {
            info("尝试打开绿宝石木桶...");
            HeBlockUtils.open(this.moneyPos);
            this.setDelay(this.windowDelay.get());
            return;
        }

        if (handler instanceof GenericContainerScreenHandler barrelHandler) {
            FindItemResult emeralds = InvUtils.find(Items.EMERALD);
            // 钱够了，关掉，开始交易
            if (emeralds.found() && emeralds.count() >= 64 * this.supplyQty.get()) {
                info("绿宝石充足，开始寻找村民");
                HeInvUtils.closeCurScreen();
                this.step = VillagerStep.NextVillager;
                this.setDelay(this.windowDelay.get());
                return;
            }

            // 木桶 27 格，遍历
            for (int i = 0; i < 27; i++) {
                ItemStack stack = barrelHandler.getSlot(i).getStack();
                if (stack.getItem() == Items.EMERALD && !stack.isEmpty()) {
                    InvUtils.shiftClick().slotId(i);
                    this.setDelay(this.clickDelay.get());
                    return;
                }
            }
            warning("木桶里没钱了！");
            this.toggle();
        } else {
            HeInvUtils.closeCurScreen();
            this.setDelay(this.windowDelay.get());
        }
    }
   private void gotoMoneyIfNotFull() {
        if (this.needBuy) {
            FindItemResult findItemResult = InvUtils.find(Items.EMERALD);
            if (!findItemResult.found() || findItemResult.count() < 64 * this.supplyQty.get()) {
                this.gotoIfNeed(this.moneyPos, "前往绿宝石木桶", VillagerStep.TakeMoney);
                return;
            }
        }
        
        // 如果身上的货和钱都补齐了
        if (this.wait) {
            info("补给完毕，原地待机等待下一轮交易时间");
            this.step = VillagerStep.Wait; // 真正进入待机，不动了
        } else {
            this.step = VillagerStep.NextVillager; // 继续找村民
        }
    }

  private void takeGoods() {
        var handler = mc.player.currentScreenHandler;

        // 状态1：未打开界面，去点潜影盒
        if (handler instanceof PlayerScreenHandler) {
            info("尝试打开商品潜影盒...");
            HeBlockUtils.open(this.goodsPos);
            this.setDelay(this.windowDelay.get());
            return;
        }

        // 状态2：识别潜影盒界面
        if (handler instanceof ShulkerBoxScreenHandler shulkerHandler) {
            FindItemResult currentInv = InvUtils.find(this.goodsItem);
            // 身上货够了，关掉，去补钱
            if (currentInv.found() && currentInv.count() >= 64 * this.supplyQty.get()) {
                info("商品补给充足，关闭潜影盒");
                HeInvUtils.closeCurScreen();
                this.step = VillagerStep.GotoMoney; // 下一步：补钱
                this.setDelay(this.windowDelay.get());
                return;
            }

            // 遍历潜影盒 27 个槽位
            for (int i = 0; i < 27; i++) {
                ItemStack stack = shulkerHandler.getSlot(i).getStack();
                if (stack.getItem() == this.goodsItem && !stack.isEmpty()) {
                    InvUtils.shiftClick().slotId(i);
                     this.setDelay(this.clickDelay.get());
                    return;
                }
            }
            warning("商品潜影盒已空！");
            this.toggle();
        } else {
            // 如果误开了别的界面，关掉重试
            HeInvUtils.closeCurScreen();
             this.setDelay(this.windowDelay.get());
        }
    }

   private void gotoGoodsIfNotFull() {
        if (this.goodsItem != null) {
            FindItemResult findItemResult = InvUtils.find(this.goodsItem);
            if (!findItemResult.found() || findItemResult.count() < 64 * this.supplyQty.get()) {
                this.gotoIfNeed(this.goodsPos, "前往商品箱", VillagerStep.TakeGoods);
                return;
            }
        }
        this.gotoMoneyIfNotFull();
    }

   private void gotoIfNeed(BlockPos targetPos, String msg, VillagerStep nextStep) {
        // 获取当前位置
        Vec3d currentPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        double dist = currentPos.distanceTo(targetPos.toCenterPos());
        
        // 打印调试信息，看为什么不走
        if (debug.get()) info("目标距离: " + dist);

        if (dist > this.minDistance.get() + 1.5) { // 稍微放大一点判定范围
            info(msg);
            this.nextStep = nextStep;
            this.gotoTarget(targetPos);
        } else {
            // 距离已经够近了，直接进入下一步
            this.step = nextStep;
        }
    }

   private void put() {
        var handler = mc.player.currentScreenHandler;

        if (handler instanceof net.minecraft.screen.PlayerScreenHandler) {
            info("尝试打开卸货大箱子...");
            HeBlockUtils.open(this.putPos);
            this.setDelay(this.windowDelay.get());
            return;
        }

        if (handler instanceof GenericContainerScreenHandler || handler instanceof net.minecraft.screen.ShulkerBoxScreenHandler) {
            List<Item> toDump = new ArrayList<>();
            // 正常买进的东西肯定要存
            toDump.addAll(this.buy1.get());
            toDump.addAll(this.buy2.get());
            
            // 核心修复：如果你不需要花钱买东西（纯卖家），就把赚到的绿宝石全存起来！
            if (!this.needBuy) {
                toDump.add(Items.EMERALD);
            }

            FindItemResult result = InvUtils.find(itemStack -> toDump.contains(itemStack.getItem()));

            if (result.found()) {
                InvUtils.shiftClick().slot(result.slot());
                this.setDelay(this.clickDelay.get());
            } else {
                info("卸货完成，准备去检查补给");
                HeInvUtils.closeCurScreen();
                this.step = VillagerStep.GotoGoods;
                 this.setDelay(this.windowDelay.get());
            }
        } else {
            HeInvUtils.closeCurScreen();
             this.setDelay(this.windowDelay.get());
        }
    }

   private void gotoPut() {
      FindItemResult findItemResult = InvUtils.find(
         item -> this.buy1.get().contains(item.getItem()) || this.buy2.get().contains(item.getItem())
      );
      if ((!findItemResult.found() || findItemResult.count() <= 0) && !this.wait) {
         this.gotoGoodsIfNotFull();
      } else {
         this.gotoIfNeed(this.putPos, "前往卸货", VillagerStep.Put);
      }
   }

   private void gotoVillager() {
      BlockPos targetPos = this.currentVillager.getOperatePos();
      this.printLog("gotoVillager : {}", targetPos);
      this.nextStep = VillagerStep.OpenTrade;
      this.gotoTarget(targetPos, 0);
   }

   private void gotoTarget(BlockPos targetPos) {
      this.gotoTarget(targetPos, this.minDistance.get());
   }

   private void gotoTarget(BlockPos targetPos, int distance) {
      this.customGoalProcess.setGoalAndPath(new GoalNear(targetPos, distance));
      this.step = VillagerStep.Walking;
   }

   private void none() {
   }

   private long timeOfDay() {
      return mc.world.getTimeOfDay() % 24000L;
   }

   private List<VillagerEntityWarp> getVillagerEntity() {
      List<VillagerEntityWarp> villagerList = new ArrayList<>();

      for (Entity entity : mc.world.getEntities()) {
         if (entity instanceof VillagerEntity villager) {
            double y = entity.getY() - mc.player.getY();
            if (y >= -2.0 && y <= 2.0) {
               net.minecraft.registry.entry.RegistryEntry<VillagerProfession> profession = villager.getVillagerData().profession();
               VillagerType currentType = VillagerType.valueOf(profession);
               if (currentType != null
                  && (this.one.get() && currentType == this.type1.get() || this.two.get() && currentType == this.type2.get())) {
                  BlockPos pos = this.getOperatePos(villager, currentType, Direction.NORTH);
                  if (pos == null) {
                     pos = this.getOperatePos(villager, currentType, Direction.SOUTH);
                  }

                  if (pos == null) {
                     pos = this.getOperatePos(villager, currentType, Direction.WEST);
                  }

                  if (pos == null) {
                     pos = this.getOperatePos(villager, currentType, Direction.EAST);
                  }

                  if (pos != null) {
                     villagerList.add(new VillagerEntityWarp(villager.getUuid(), pos));
                  }
               }
            }
         }
      }

      return villagerList;
   }

   private BlockPos getOperatePos(VillagerEntity villager, VillagerType currentType, Direction direction) {
      BlockPos blockPos = villager.getBlockPos().offset(direction);
      BlockState blockState = mc.world.getBlockState(blockPos);
      if (blockState.getBlock().asItem() == currentType.getItem()) {
         BlockPos pos = blockPos.offset(direction);
         if (mc.world.getBlockState(pos).isAir()) {
            return pos;
         }
      }

      return null;
   }

   private void setDelayCloseScreenAndNext(VillagerStep closeScreenNextStep) {
       this.setDelay(this.windowDelay.get());
      this.step = VillagerStep.CloseScreenAndNext;
      this.closeScreenNextStep = closeScreenNextStep;
   }

   private void disableAuto() {
      this.step = VillagerStep.None;
      this.nextStep = null;
      this.baritone.getCommandManager().execute("cancel");
      this.moneyPos = null;
      this.putPos = null;
      this.villagerList = Collections.emptyList();
   }

   @Override
   public void onDeactivate() {
      this.disableAuto();
   }

   private void printLog(String str, Object arg) {
      if (this.debug.get()) {
         log.info(str, arg);
      }
   }

   private void printLog(String str, Object... args) {
      if (this.debug.get()) {
         log.info(str, args);
      }
   }
}