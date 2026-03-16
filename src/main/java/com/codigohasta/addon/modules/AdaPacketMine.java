package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.modules.AdaPacketMine.SpeedmineMode;

import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class AdaPacketMine extends Module {
    
    // --- 配置组 ---
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAutoMine = settings.createGroup("自动挖掘");
    private final SettingGroup sgRender = settings.createGroup("渲染");

    // --- 通用设置 ---
    private final Setting<SpeedmineMode> modeConfig = sgGeneral.add(new EnumSetting.Builder<SpeedmineMode>()
        .name("模式").description("挖掘模式").defaultValue(SpeedmineMode.PACKET).build());

    private final Setting<Boolean> multitaskConfig = sgGeneral.add(new BoolSetting.Builder()
        .name("多任务").description("允许在使用物品(如吃金苹果)时挖掘").defaultValue(false)
        .visible(() -> modeConfig.get() == SpeedmineMode.PACKET).build());

    private final Setting<Boolean> doubleBreakConfig = sgGeneral.add(new BoolSetting.Builder()
        .name("双重破坏").description("允许同时挖掘两个方块").defaultValue(true)
        .visible(() -> modeConfig.get() == SpeedmineMode.PACKET).build());

    private final Setting<Double> rangeConfig = sgGeneral.add(new DoubleSetting.Builder()
        .name("挖掘距离").defaultValue(4.5).min(0.1).sliderRange(0.1, 6.0)
        .visible(() -> modeConfig.get() == SpeedmineMode.PACKET).build());

    private final Setting<Double> speedConfig = sgGeneral.add(new DoubleSetting.Builder()
        .name("速度倍率").description("调整挖掘进度的速度").defaultValue(1.0).min(0.1).sliderRange(0.1, 1.0).build());

    // --- 地面绕过 (Bypass Ground) ---
    private final Setting<Boolean> bypassGround = sgGeneral.add(new BoolSetting.Builder()
        .name("地面绕过 (Bypass)").description("在水中或空中全速挖掘 (Grim/NCP)").defaultValue(true)
        .visible(() -> modeConfig.get() == SpeedmineMode.PACKET).build());

    private final Setting<Boolean> instantConfig = sgGeneral.add(new BoolSetting.Builder()
        .name("瞬间挖掘(Instant)").defaultValue(true).build());

    private final Setting<Keybind> instantToggleKey = sgGeneral.add(new KeybindSetting.Builder()
        .name("瞬挖切换键").defaultValue(Keybind.none()).build());

    private final Setting<Swap> swapConfig = sgGeneral.add(new EnumSetting.Builder<Swap>()
        .name("自动切换工具").description("挖掘完成后切换工具的方式").defaultValue(Swap.SILENT)
        .visible(() -> modeConfig.get() == SpeedmineMode.PACKET).build());

    private final Setting<Boolean> rotateConfig = sgGeneral.add(new BoolSetting.Builder()
        .name("自动旋转").description("挖掘时旋转视角朝向方块").defaultValue(false)
        .visible(() -> modeConfig.get() == SpeedmineMode.PACKET).build());

    // --- Grim 反作弊设置 ---
    private final Setting<Boolean> grimConfig = sgGeneral.add(new BoolSetting.Builder()
        .name("Grim绕过").description("针对Grim反作弊优化发包顺序").defaultValue(true).build());

    private final Setting<Boolean> grimNewConfig = sgGeneral.add(new BoolSetting.Builder()
        .name("Grim-V3").description("针对新版Grim (V3/V4) 优化").defaultValue(true)
        .visible(grimConfig::get).build());

    private final Setting<Boolean> miningFix = sgGeneral.add(new BoolSetting.Builder()
        .name("卡死修复").description("防止挖掘进度卡死").defaultValue(false)
        .visible(() -> grimConfig.get() && grimNewConfig.get()).build());

    // --- 自动挖掘设置 ---
    private final Setting<Boolean> autoMine = sgAutoMine.add(new BoolSetting.Builder()
        .name("启用自动挖掘").defaultValue(false).build());
    private final Setting<Double> enemyRange = sgAutoMine.add(new DoubleSetting.Builder()
        .name("敌人搜索范围").defaultValue(5.0).min(1.0).sliderRange(1.0, 10.0).visible(autoMine::get).build());
    private final Setting<Boolean> strictDirection = sgAutoMine.add(new BoolSetting.Builder()
        .name("严格方向检查").defaultValue(false).visible(autoMine::get).build());

    // --- 渲染设置 ---
    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("启用渲染").defaultValue(true).build());
    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("渲染模式").defaultValue(ShapeMode.Both).build());
    private final Setting<SettingColor> colorConfig = sgRender.add(new ColorSetting.Builder()
        .name("挖掘中颜色").defaultValue(new SettingColor(255, 0, 0, 80)).build());
    private final Setting<SettingColor> colorDoneConfig = sgRender.add(new ColorSetting.Builder()
        .name("就绪颜色").defaultValue(new SettingColor(0, 255, 255, 80)).build());

    // --- 内部状态变量 ---
    private final Map<BlockPos, Animation> fadeList = new ConcurrentHashMap<>();
    private FirstOutQueue<MiningData> miningQueue;
    private long lastBreak;
    private boolean instantTogglePressed = false;
    private BlockPos lastAutoMineBlock = null;
    private long lastAutoMineTime = 0L;
    
    // 工具切换状态
    private int internalSwappedSlot = -1;
    private int internalOriginalSlot = -1;
    private int swapBackTicks = 0;

    // 反射缓存字段
    private Field cachedSlotField = null;

    public AdaPacketMine() {
        super(AddonTemplate.CATEGORY, "ada-packet-mine", "抄的bep的包挖，ai抄过来效果不好");
    }

    @Override
    public void onActivate() {
        int queueSize = doubleBreakConfig.get() ? 2 : 1;
        this.miningQueue = new FirstOutQueue<>(queueSize);
        resetInternalState();
    }

    @Override
    public void onDeactivate() {
        if (miningQueue != null) miningQueue.clear();
        fadeList.clear();
        resetInternalState();
        if (mc.player != null && internalOriginalSlot != -1) {
             setInvSlot(internalOriginalSlot);
             mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(internalOriginalSlot));
        }
    }

    private void resetInternalState() {
        internalSwappedSlot = -1;
        internalOriginalSlot = -1;
        swapBackTicks = 0;
        lastAutoMineBlock = null;
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        onDeactivate();
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // 1. 静默切换回退逻辑
        if (swapBackTicks > 0) {
            swapBackTicks--;
            if (swapBackTicks <= 0 && internalSwappedSlot != -1 && internalOriginalSlot != -1) {
                setInvSlot(internalOriginalSlot);
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(internalOriginalSlot));
                internalSwappedSlot = -1;
                internalOriginalSlot = -1;
            }
        }

        // 2. 按键切换处理
        handleKeyToggles();

        if (modeConfig.get() == SpeedmineMode.DAMAGE) return;

        // 3. 自动挖掘逻辑
        handleAutoMine();

        // 4. 挖掘队列处理核心逻辑
        if (!miningQueue.isEmpty()) {
            List<MiningData> toRemove = new ArrayList<>();
            
            for (MiningData data : miningQueue) {
                if (data.isAir()) {
                    data.resetBreakTime(); 
                    if (!instantConfig.get()) toRemove.add(data);
                    continue;
                }

                // 距离检查 (使用 squaredDistanceTo)
                double distSq = mc.player.squaredDistanceTo(data.getCenterPos());
                if (distSq > rangeConfig.get() * rangeConfig.get()) {
                    toRemove.add(data);
                    continue;
                }

                // 启动挖掘
                if (!data.isStarted()) {
                    startMining(data);
                }

                // 伤害计算 (集成 Bypass Ground - 水下不减速)
                float damageDelta = calcBlockBreakingDelta(data.getState(), data.getPos());
                data.damage(damageDelta);

                // 如果方块破坏进度 >= 1.0 (100%)
                if (data.getBlockDamage() >= speedConfig.get()) {
                    if (mc.player.isUsingItem() && !multitaskConfig.get()) return;
                    
                    // 防卡死逻辑：如果已经完成挖掘但方块还没消失，且超过一定时间，重新发包
                    long now = System.currentTimeMillis();
                    boolean needsResend = data.hasAttemptedBreak() && (now - data.lastStopPacket > 500L);

                    if (!data.hasAttemptedBreak() || needsResend) {
                        stopMining(data);
                        data.setAttemptedBreak(true);
                        data.lastStopPacket = now;
                    }
                    
                    if (!instantConfig.get()) {
                        // 如果是非瞬挖模式，我们只有在确认方块消失后才从队列移除
                        // 这里不做移除，等待 BlockUpdate 移除
                        // 但为了防止永久卡住，增加一个超时移除
                        if (data.passedAttemptedBreakTime(2000L)) { // 2秒后强制移除
                             toRemove.add(data);
                        }
                    }
                }
                
                // 异常中止
                if (data.hasAttemptedBreak() && data.passedAttemptedBreakTime(5000L)) {
                     abortMining(data);
                     toRemove.add(data);
                }
            }
            miningQueue.removeAll(toRemove);
        }
    }

    private void handleKeyToggles() {
        if (instantToggleKey.get().isPressed() && !instantTogglePressed) {
            instantTogglePressed = true;
            instantConfig.set(!instantConfig.get());
            info("瞬间挖掘: " + (instantConfig.get() ? "开启" : "关闭"));
            if (!instantConfig.get()) miningQueue.clear();
        } else if (!instantToggleKey.get().isPressed()) {
            instantTogglePressed = false;
        }
    }

    private void handleAutoMine() {
        if (!autoMine.get()) return;
        
        long currentTime = System.currentTimeMillis();
        int maxQueue = doubleBreakConfig.get() ? 2 : 1;

        if (miningQueue.size() < maxQueue && currentTime - lastAutoMineTime >= 250L) {
            PlayerEntity target = getClosestEnemy();
            if (target != null) {
                BlockPos targetBlock = findBestEnemyBlock(target);
                if (targetBlock != null && !isMiningBlock(targetBlock)) {
                    Direction dir = getInteractDirection(targetBlock);
                    if (dir == null && strictDirection.get()) return;
                    if (dir == null) dir = Direction.UP;

                    if (rotateConfig.get()) {
                        performRotation(targetBlock.toCenterPos());
                    }

                    MiningData data = new MiningData(targetBlock, dir);
                    queueMiningData(data);
                    lastAutoMineBlock = targetBlock;
                    lastAutoMineTime = currentTime;
                }
            }
        }
    }

    @EventHandler
    public void onStartBreakingBlock(StartBreakingBlockEvent event) {
        if (!mc.player.isCreative() && modeConfig.get() == SpeedmineMode.PACKET) {
            event.cancel();
            BlockState state = mc.world.getBlockState(event.blockPos);
            if (!state.isAir() && state.getHardness(mc.world, event.blockPos) != -1.0f) {
                clickMine(new MiningData(event.blockPos, event.direction));
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    @EventHandler
    public void onPacketSend(PacketEvent.Send event) {
        if (event.packet instanceof UpdateSelectedSlotC2SPacket && modeConfig.get() == SpeedmineMode.PACKET) {
            // Passive sync
        }
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive event) {
        if (mc.player == null) return;
        if (event.packet instanceof BlockUpdateS2CPacket packet) {
            handleBlockUpdate(packet.getPos(), packet.getState());
        } else if (event.packet instanceof ChunkDeltaUpdateS2CPacket packet) {
            packet.visitUpdates(this::handleBlockUpdate);
        }
    }

    private void handleBlockUpdate(BlockPos pos, BlockState state) {
        if (state.isAir()) {
            for (MiningData data : miningQueue) {
                if (data.getPos().equals(pos)) {
                    data.setAttemptedBreak(false);
                    // 如果不是瞬挖，或者虽然是瞬挖但方块已经消失，可以考虑重置或移除
                    if (!instantConfig.get()) {
                        // 在 tick 循环中会被移除
                    } else {
                        // 瞬挖模式下，方块消失后我们重置状态，等待下一次放置
                        data.blockDamage = 0;
                        data.started = false;
                    }
                }
            }
        }
    }

    // --- 渲染逻辑 (完全保留原始代码) ---
    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (modeConfig.get() != SpeedmineMode.PACKET || !render.get()) return;

        // 更新动画状态
        for (MiningData data : miningQueue) {
            if (!fadeList.containsKey(data.getPos())) {
                fadeList.put(data.getPos(), new Animation(true, 250));
            }
        }

        // 清理无效动画
        fadeList.entrySet().removeIf(e -> {
            boolean active = false;
            for(MiningData d : miningQueue) {
                if(d.getPos().equals(e.getKey()) && !d.getState().isAir()) {
                    active = true;
                    break;
                }
            }
            e.getValue().setState(active);
            return e.getValue().getFactor() == 0.0f;
        });

        // 绘制
        for (MiningData data : miningQueue) {
            if (data.getState().isAir()) continue;

            Animation anim = fadeList.get(data.getPos());
            if (anim == null) continue;
            
            float factor = anim.getFactor();
            boolean done = data.getBlockDamage() >= 0.95f;
            
            // 颜色选择
            SettingColor c = done ? colorDoneConfig.get() : colorConfig.get();
            // 动态透明度
            int boxAlpha = (int)(c.a * 0.5 * factor);
            int lineAlpha = (int)(c.a * factor);
            
            Color boxColor = new Color(c.r, c.g, c.b, boxAlpha);
            Color lineColor = new Color(c.r, c.g, c.b, lineAlpha);

            BlockPos pos = data.getPos();
            VoxelShape shape = data.getState().getOutlineShape(mc.world, pos);
            if (shape.isEmpty()) shape = VoxelShapes.fullCube();
            Box box = shape.getBoundingBox().offset(pos);
            
            // 进度条缩放
            float total = 1.0F; 
            float progress = MathHelper.clamp(data.getBlockDamage() / total, 0.01f, 1f);
            
            double centerX = box.minX + (box.maxX - box.minX) / 2;
            double centerY = box.minY + (box.maxY - box.minY) / 2;
            double centerZ = box.minZ + (box.maxZ - box.minZ) / 2;
            
            double scale = progress;
            
            double dx = (box.maxX - box.minX) / 2 * scale;
            double dy = (box.maxY - box.minY) / 2 * scale;
            double dz = (box.maxZ - box.minZ) / 2 * scale;
            
            Box renderBox = new Box(centerX - dx, centerY - dy, centerZ - dz, centerX + dx, centerY + dy, centerZ + dz);
            
            event.renderer.box(renderBox, boxColor, lineColor, shapeMode.get(), 0);
        }
    }

    // --- 核心操作方法 (集成 Bypass) ---

    public void clickMine(MiningData data) {
        if (miningQueue.size() <= (doubleBreakConfig.get() ? 2 : 1)) {
            queueMiningData(data);
        }
    }

    private void queueMiningData(MiningData data) {
        if (!data.isAir()) {
            boolean exists = miningQueue.stream().anyMatch(d -> d.getPos().equals(data.getPos()));
            if (!exists) miningQueue.addFirst(data);
        }
    }

    private boolean startMining(MiningData data) {
        if (data.isStarted()) return false;
        data.setStarted();

        if (grimConfig.get()) {
            if (grimNewConfig.get()) {
                if (!miningFix.get()) {
                    sendAction(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, data);
                    sendAction(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, data);
                    sendAction(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, data);
                } else {
                    sendAction(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, data);
                }
                sendAction(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, data);
                mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            } else {
                sendAction(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, data);
                sendAction(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, data);
                sendAction(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, data);
                sendAction(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, data);
            }
        } else {
            sendAction(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, data);
            sendAction(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, data);
        }
        return true;
    }

    private void stopMining(MiningData data) {
        if (!data.isStarted() || data.isAir()) return;

        if (rotateConfig.get()) performRotation(data.getCenterPos());

        int bestSlot = getBestTool(data.getState());
        int currentSlot = getInvSlot();
        boolean needsSwap = bestSlot != -1 && bestSlot != currentSlot;

        if (needsSwap) {
            if (internalOriginalSlot == -1) internalOriginalSlot = currentSlot;
            
            if (swapConfig.get() == Swap.SILENT) {
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(bestSlot));
                internalSwappedSlot = bestSlot;
                swapBackTicks = 3; 
            } else if (swapConfig.get() == Swap.NORMAL) {
                setInvSlot(bestSlot);
            }
        } else if (internalSwappedSlot != -1) {
            swapBackTicks = 3;
        }

        // --- Bypass Ground 关键逻辑 ---
        // 核心思路：发送一个 PlayerMoveC2SPacket.Full 包，告诉服务器我们在地面上 (onGround=true)
        boolean isFallFlying = mc.player.getPose().toString().equals("FALL_FLYING");
        
        // 只有当 Bypass 开启，且我们可能受到惩罚时(水下或空中)，才强制发送
        if (bypassGround.get() && !isFallFlying && !data.getState().isAir()) {
            boolean inWater = mc.player.isSubmergedIn(FluidTags.WATER);
            boolean inAir = !mc.player.isOnGround();
            
            if (inWater || inAir) {
                // 构造 1.21.11 兼容的 Full 包
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(
                    mc.player.getX(), 
                    mc.player.getY() + 1.0e-9, 
                    mc.player.getZ(), 
                    mc.player.getYaw(), 
                    mc.player.getPitch(), 
                    true,  // onGround = true (欺骗核心)
                    false  // horizontalCollision
                ));
                
                // 客户端状态更新，防止本地逻辑回弹
                mc.player.onLanding();
            }
        }

        sendAction(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, data);
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        lastBreak = System.currentTimeMillis();
    }

    private void abortMining(MiningData data) {
        if (data.isStarted() && !data.isAir()) {
            sendAction(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, data);
        }
    }

    private void sendAction(PlayerActionC2SPacket.Action action, MiningData data) {
        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(action, data.getPos(), data.getDirection()));
    }

    // --- 反射辅助方法 ---
    
    private void setInvSlot(int slot) {
        try {
            Field f = getSlotField();
            if (f != null) f.setInt(mc.player.getInventory(), slot);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int getInvSlot() {
        try {
            Field f = getSlotField();
            if (f != null) return f.getInt(mc.player.getInventory());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    private Field getSlotField() {
        if (cachedSlotField != null) return cachedSlotField;
        Class<?> clazz = PlayerInventory.class;
        String[] possibleNames = {"selectedSlot", "field_7545", "c"};
        for (String name : possibleNames) {
            try {
                Field f = clazz.getDeclaredField(name);
                f.setAccessible(true);
                cachedSlotField = f;
                return f;
            } catch (NoSuchFieldException ignored) {}
        }
        return null;
    }

    // --- 伤害计算 (集成 Bypass Ground - 水下修复核心) ---

    private float calcBlockBreakingDelta(BlockState state, BlockPos pos) {
        if (swapConfig.get() == Swap.OFF) {
            return state.calcBlockBreakingDelta(mc.player, mc.world, pos);
        }
        
        float hardness = state.getHardness(mc.world, pos);
        if (hardness == -1.0f) return 0.0f;
        
        int bestSlot = getBestTool(state);
        ItemStack stack = mc.player.getInventory().getStack(bestSlot);
        
        float speed = stack.getMiningSpeedMultiplier(state);
        
        // 1.21.11 效率附魔检测修复
        int efficiencyLevel = getEfficiencyLevel(stack);
        if (efficiencyLevel > 0 && !stack.isEmpty()) {
            speed += efficiencyLevel * efficiencyLevel + 1;
        }

        if (mc.player.hasStatusEffect(StatusEffects.HASTE)) {
             int amplifier = mc.player.getStatusEffect(StatusEffects.HASTE).getAmplifier();
             speed *= 1.0f + (amplifier + 1) * 0.2f;
        }

        if (mc.player.hasStatusEffect(StatusEffects.MINING_FATIGUE)) {
             // 简化疲劳计算
             speed *= 0.3f;
        }

        // --- Bypass Ground 逻辑核心 ---
        // 如果开启了 Bypass Ground，我们跳过所有的环境检查 (水下、空中)，
        // 直接使用全速计算。这配合 stopMining 中的发包欺骗，能实现水下瞬挖。
        if (!bypassGround.get()) {
            if (mc.player.isSubmergedIn(FluidTags.WATER) && !hasAquaAffinity()) {
                speed /= 5.0f;
            }
            if (!mc.player.isOnGround()) {
                speed /= 5.0f;
            }
        }
        // -----------------------------

        float damage = speed / hardness;
        boolean canHarvest = !state.isToolRequired() || stack.isSuitableFor(state);
        
        return damage / (canHarvest ? 30f : 100f);
    }
    
    // 1.21.11 兼容: 安全获取效率等级 (修复 NPE 崩溃)
    private int getEfficiencyLevel(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        
        try {
            // 获取物品的附魔组件
            var enchantments = stack.getEnchantments();
            if (enchantments == null) return 0;

            // 遍历所有附魔条目
            for (var entry : enchantments.getEnchantmentEntries()) {
                // 防御性检查: entry 或其 Key 即使在遍历中也可能为 null
                if (entry == null || entry.getKey() == null) continue;

                // 方案 A: 尝试通过 Identifier 字符串匹配 (最安全)
                // 1.21+ 的 RegistryEntry.toString() 通常格式为 "Reference{ResourceKey[minecraft:efficiency]...}"
                // 或者直接包含 id
                String idStr = entry.getKey().toString().toLowerCase();
                
                // 方案 B: 如果能安全获取 Key (更精准，但需多重判空)
                if (entry.getKey().getKey().isPresent()) {
                    idStr = entry.getKey().getKey().get().getValue().toString();
                }

                // 只要 ID 中包含 efficiency 即可判定
                if (idStr.contains("efficiency")) {
                    return entry.getIntValue();
                }
            }
        } catch (Exception ignored) {
            // 捕获所有潜在的 Registry 解析错误，防止踢出游戏
            return 0;
        }
        return 0;
    }
    
    // 1.21.11 兼容: 检测水下速掘
    private boolean hasAquaAffinity() {
        // 修复: 使用 getEquippedStack 替代 armor.get
        ItemStack helmet = mc.player.getEquippedStack(EquipmentSlot.HEAD);
        if (helmet.isEmpty()) return false;
        try {
            for (var entry : helmet.getEnchantments().getEnchantmentEntries()) {
                 String id = entry.getKey().getKey().map(k -> k.getValue().toString()).orElse("");
                 if (id.contains("aqua_affinity")) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private int getBestTool(BlockState state) {
        int bestSlot = -1;
        float bestSpeed = 0.0f;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            float speed = stack.getMiningSpeedMultiplier(state);
            // 简单估算效率加成
            if (speed > 1.0f) {
    int effLevel = getEfficiencyLevel(stack); // 只获取一次
    if (effLevel > 0) {
        speed += effLevel * effLevel + 1;
    }
}
            
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = i;
            }
        }
        return bestSlot == -1 ? getInvSlot() : bestSlot;
    }

    // --- 旋转与辅助逻辑 ---
    private void performRotation(Vec3d targetPos) {
        if (mc.player == null) return;
        
        double diffX = targetPos.x - mc.player.getX();
        double diffY = targetPos.y - mc.player.getEyeY();
        double diffZ = targetPos.z - mc.player.getZ();
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, diffXZ));

        if (grimConfig.get()) {
            Rotations.rotate(yaw, pitch, 100, null); 
        } else {
            mc.player.setYaw(yaw);
            mc.player.setPitch(pitch);
        }
    }

    private PlayerEntity getClosestEnemy() {
        PlayerEntity closest = null;
        double closestDist = enemyRange.get() * enemyRange.get();

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || Friends.get().isFriend(player)) continue;
            double dist = mc.player.squaredDistanceTo(player);
            if (dist < closestDist) {
                closestDist = dist;
                closest = player;
            }
        }
        return closest;
    }

    private BlockPos findBestEnemyBlock(PlayerEntity enemy) {
        BlockPos feet = enemy.getBlockPos();
        if (!mc.world.getBlockState(feet).isAir()) return feet; 
        
        BlockPos[] offsets = {feet.north(), feet.south(), feet.east(), feet.west()};
        for (BlockPos p : offsets) {
            if (!mc.world.getBlockState(p).isAir() && mc.world.getBlockState(p).getHardness(mc.world, p) != -1) {
                return p;
            }
        }
        return null;
    }
    
    private Direction getInteractDirection(BlockPos pos) {
        return Direction.UP; 
    }
    
    private boolean isMiningBlock(BlockPos pos) {
        return miningQueue.stream().anyMatch(d -> d.getPos().equals(pos));
    }

    // --- 内部数据类 (保留原始定义) ---

    private static class MiningData {
        private final BlockPos pos;
        private final Direction direction;
        private float blockDamage = 0;
        private long breakTime;
        private boolean attemptedBreak;
        private boolean started;
        private long lastStopPacket = 0; // 新增：防卡死时间戳

        public MiningData(BlockPos pos, Direction direction) {
            this.pos = pos;
            this.direction = direction;
            this.breakTime = System.currentTimeMillis();
        }

        public BlockPos getPos() { return pos; }
        public Direction getDirection() { return direction; }
        public Vec3d getCenterPos() { return pos.toCenterPos(); }
        
        public boolean isAir() {
            if (meteordevelopment.meteorclient.MeteorClient.mc.world == null) return true;
            return meteordevelopment.meteorclient.MeteorClient.mc.world.getBlockState(pos).isAir();
        }
        
        public BlockState getState() {
            return meteordevelopment.meteorclient.MeteorClient.mc.world.getBlockState(pos);
        }

        public void damage(float amount) { blockDamage += amount; }
        public float getBlockDamage() { return blockDamage; }
        
        public void setAttemptedBreak(boolean b) { 
            attemptedBreak = b; 
            if (b) breakTime = System.currentTimeMillis(); 
        }
        public boolean hasAttemptedBreak() { return attemptedBreak; }
        
        public void resetBreakTime() { breakTime = System.currentTimeMillis(); }
        public boolean passedAttemptedBreakTime(long ms) { return System.currentTimeMillis() - breakTime >= ms; }
        
        public boolean isStarted() { return started; }
        public void setStarted() { started = true; }
        
        public boolean isPacketMine() { return true; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MiningData that = (MiningData) o;
            return Objects.equals(pos, that.pos);
        }

        @Override
        public int hashCode() {
            return Objects.hash(pos);
        }
    }

    private static class FirstOutQueue<T> extends ArrayList<T> {
        private final int maxSize;
        public FirstOutQueue(int maxSize) { this.maxSize = maxSize; }
        
        public void addFirst(T t) {
            super.add(0, t);
            while (size() > maxSize) remove(size() - 1);
        }
        
        @Override
        public T getFirst() { return isEmpty() ? null : get(0); }
    }

    private static class Animation {
        private boolean state;
        private long time;
        private final long duration;

        public Animation(boolean state, long duration) {
            this.state = state;
            this.duration = duration;
            this.time = System.currentTimeMillis();
        }

        public void setState(boolean state) {
            if (this.state != state) {
                this.state = state;
                this.time = System.currentTimeMillis();
            }
        }

        public float getFactor() {
            long elapsed = System.currentTimeMillis() - time;
            float progress = Math.min(1.0f, (float) elapsed / duration);
            return state ? 1.0f : 1.0f - progress;
        }
    }

    public enum SpeedmineMode { PACKET, DAMAGE }
    public enum Swap { NORMAL, SILENT, OFF }
}