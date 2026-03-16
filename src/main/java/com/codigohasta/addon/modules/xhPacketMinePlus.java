package com.codigohasta.addon.modules;

import net.minecraft.util.math.Vec3d;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.client.network.SequencedPacketCreator;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.lang.reflect.Method;
import java.util.*;

public class xhPacketMinePlus extends Module {
    public enum SpeedmineMode { PACKET, DAMAGE }
    public enum Swap { NORMAL, SILENT, OFF }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAutoMine = settings.createGroup("自动挖掘");
    private final SettingGroup sgRender = settings.createGroup("渲染");

    // --- General Settings ---
    private final Setting<SpeedmineMode> modeConfig = sgGeneral.add(new EnumSetting.Builder<SpeedmineMode>().name("模式").defaultValue(SpeedmineMode.PACKET).build());
    private final Setting<Boolean> multitaskConfig = sgGeneral.add(new BoolSetting.Builder().name("多任务").defaultValue(false).visible(() -> modeConfig.get() == SpeedmineMode.PACKET).build());
    private final Setting<Boolean> doubleBreakConfig = sgGeneral.add(new BoolSetting.Builder().name("队列缓存(双挖)").description("允许缓存多个挖掘任务").defaultValue(true).visible(() -> modeConfig.get() == SpeedmineMode.PACKET).build());
    private final Setting<Double> rangeConfig = sgGeneral.add(new DoubleSetting.Builder().name("范围").defaultValue(5.0).min(0.1).sliderRange(0.1, 6.0).visible(() -> modeConfig.get() == SpeedmineMode.PACKET).build());
    private final Setting<Double> speedConfig = sgGeneral.add(new DoubleSetting.Builder().name("速度").defaultValue(1.0).min(0.1).sliderRange(0.1, 1.0).build());
    
    // --- New Bypass Settings ---
    private final Setting<Boolean> bypassGround = sgGeneral.add(new BoolSetting.Builder().name("Bypass Ground").description("在水中或空中全速挖掘 (Grim/NCP)").defaultValue(true).build());
    
    private final Setting<Boolean> instantConfig = sgGeneral.add(new BoolSetting.Builder().name("瞬间挖掘").defaultValue(true).build());
    private final Setting<Keybind> instantToggleKey = sgGeneral.add(new KeybindSetting.Builder().name("瞬间挖掘切换键").defaultValue(Keybind.none()).build());
    private final Setting<Boolean> persistentConfig = sgGeneral.add(new BoolSetting.Builder().name("持久模式").defaultValue(false).visible(() -> modeConfig.get() == SpeedmineMode.PACKET).build());
    private final Setting<Swap> swapConfig = sgGeneral.add(new EnumSetting.Builder<Swap>().name("自动切换").defaultValue(Swap.SILENT).visible(() -> modeConfig.get() == SpeedmineMode.PACKET).build());
    private final Setting<Boolean> rotateConfig = sgGeneral.add(new BoolSetting.Builder().name("旋转").defaultValue(false).visible(() -> modeConfig.get() == SpeedmineMode.PACKET).build());
    private final Setting<Boolean> grimConfig = sgGeneral.add(new BoolSetting.Builder().name("Grim绕过").defaultValue(true).build());
    private final Setting<Boolean> grimNewConfig = sgGeneral.add(new BoolSetting.Builder().name("Grim-v3").defaultValue(true).visible(grimConfig::get).build());
    private final Setting<Boolean> miningFix = sgGeneral.add(new BoolSetting.Builder().name("挖掘修复").defaultValue(false).visible(() -> grimConfig.get() && grimNewConfig.get()).build());

    // --- Auto Mine Settings ---
    private final Setting<Keybind> autoMineKey = sgAutoMine.add(new KeybindSetting.Builder().name("自动挖掘键").defaultValue(Keybind.none()).build());
    private final Setting<Boolean> autoMine = sgAutoMine.add(new BoolSetting.Builder().name("自动挖掘").defaultValue(false).build());
    private final Setting<Double> enemyRange = sgAutoMine.add(new DoubleSetting.Builder().name("敌人范围").defaultValue(5.0).build());
    private final Setting<Boolean> strictDirection = sgAutoMine.add(new BoolSetting.Builder().name("严格方向").defaultValue(false).visible(autoMine::get).build());
    private final Setting<Boolean> targetHead = sgAutoMine.add(new BoolSetting.Builder().name("目标头部").defaultValue(false).visible(autoMine::get).build());
    private final Setting<Boolean> autoRotate = sgAutoMine.add(new BoolSetting.Builder().name("自动旋转").defaultValue(true).visible(autoMine::get).build());
    private final Setting<Boolean> antiCrawl = sgAutoMine.add(new BoolSetting.Builder().name("防爬行").defaultValue(true).visible(autoMine::get).build());

    // --- Render Settings ---
    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder().name("渲染").defaultValue(true).build());
    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>().name("形状模式").defaultValue(ShapeMode.Both).build());
    private final Setting<SettingColor> colorConfig = sgRender.add(new ColorSetting.Builder().name("挖掘中颜色").defaultValue(new SettingColor(0, 0, 255)).visible(() -> modeConfig.get() == SpeedmineMode.PACKET).build());
    private final Setting<SettingColor> colorDoneConfig = sgRender.add(new ColorSetting.Builder().name("完成颜色").defaultValue(new SettingColor(0, 255, 255)).visible(() -> modeConfig.get() == SpeedmineMode.PACKET).build());
    private final Setting<Integer> fadeTimeConfig = sgRender.add(new IntSetting.Builder().name("淡出时间").defaultValue(250).visible(() -> false).build());

    private final Map<BlockPos, Animation> fadeList = new HashMap<>();
    private final ArrayList<MiningData> miningQueue = new ArrayList<>();
    private boolean instantTogglePressed = false;
    private boolean autoMineTogglePressed = false;
    private PlayerEntity currentTarget = null;
    private BlockPos lastAutoMineBlock = null;
    private long lastAutoMineTime = 0L;
    private BlockPos lastAntiCrawlBlock = null;
    private long lastAntiCrawlTime = 0L;
    
    private int serverSideSlot = -1;

    public xhPacketMinePlus() {
        super(AddonTemplate.CATEGORY, "packet-mine", "抄袭的bep的发包挖掘模块，没有用了");
    }

    @Override
    public void onActivate() {
        this.miningQueue.clear();
        this.lastAutoMineBlock = null;
        this.lastAutoMineTime = 0L;
        if (mc.player != null) {
            serverSideSlot = ((com.codigohasta.addon.mixin.InventoryAccessor) mc.player.getInventory()).getSelectedSlot();
        }
    }

    @Override
    public void onDeactivate() {
        if (!persistentConfig.get() || mc.getNetworkHandler() == null) {
            miningQueue.clear();
            fadeList.clear();
            syncSlot();
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        miningQueue.clear();
        fadeList.clear();
        serverSideSlot = -1;
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        
        if (serverSideSlot == -1) serverSideSlot = ((com.codigohasta.addon.mixin.InventoryAccessor) mc.player.getInventory()).getSelectedSlot();

        handleKeybinds();

        if (modeConfig.get() != SpeedmineMode.DAMAGE) {
            if (autoMine.get() && modeConfig.get() == SpeedmineMode.PACKET) {
                handleAutoMine();
            }
            processMiningQueue();
        }
    }

    private void syncSlot() {
        if (mc.player == null) return;
        int clientSlot = ((com.codigohasta.addon.mixin.InventoryAccessor) mc.player.getInventory()).getSelectedSlot();
        
        if (serverSideSlot != clientSlot) {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(clientSlot));
            serverSideSlot = clientSlot;
        }
    }

    private void processMiningQueue() {
        if (mc.options.useKey.isPressed() || mc.player.isUsingItem()) {
            syncSlot();
            return;
        }

        if (miningQueue.isEmpty()) {
            syncSlot();
            return;
        }

        Iterator<MiningData> it = miningQueue.iterator();
        while (it.hasNext()) {
            MiningData data = it.next();
            
            if (data.getState().isAir()) {
                data.resetBreakTime();
                if (!instantConfig.get()) {
                    it.remove();
                }
                continue;
            }
            
            double distSq = mc.player.getEyePos().squaredDistanceTo(data.getPos().toCenterPos());
            if (distSq > rangeConfig.get() * rangeConfig.get()) continue;

            if (!data.isStarted()) startMining(data);

            // 使用自定义的计算方法，支持 Bypass Ground
            float damageDelta = getBreakDelta(data.getSlot(), data.getState(), data.getPos());
            data.damage(damageDelta);
        }

        Iterator<MiningData> breakIt = miningQueue.iterator();
        while(breakIt.hasNext()) {
            MiningData data = breakIt.next();
            
            if (data.getState().isAir()) continue;

            // 检查挖掘进度
            if (data.getBlockDamage() >= speedConfig.get()) {
                if (!multitaskConfig.get() && mc.player.isUsingItem()) continue;

                int bestSlot = data.getSlot();
                if (bestSlot != -1) {
                    boolean forceSwap = data.hasAttemptedBreak(); 
                    
                    if (serverSideSlot != bestSlot || forceSwap) {
                        if (swapConfig.get() == Swap.SILENT) {
                            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(bestSlot));
                            serverSideSlot = bestSlot;
                        } else if (swapConfig.get() == Swap.NORMAL) {
                            ((com.codigohasta.addon.mixin.InventoryAccessor) mc.player.getInventory()).setSelectedSlot(bestSlot);
                            serverSideSlot = bestSlot;
                        }
                    }

                    if (rotateConfig.get()) {
                        Rotations.rotate(Rotations.getYaw(data.getPos()), Rotations.getPitch(data.getPos()));
                    }

                    // --- Bypass Ground 核心逻辑 (修复版) ---
                    // 修复: 使用字符串检查避免编译错误 (EntityPose.FALL_FLYING)
                    boolean isFallFlying = mc.player.getPose().name().equals("FALL_FLYING");
                    
                    if (bypassGround.get() && !isFallFlying && !data.getState().isAir()) {
                        // 发送一个位置包，标记 onGround = true
                        // 使用微小的偏移(1.0e-9)来确保服务器处理这个包，但视觉上不动
                        // 1.21.4 适配: Full 构造器增加 false (horizontalCollision)
                        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(
                            mc.player.getX(), 
                            mc.player.getY() + 1.0e-9, 
                            mc.player.getZ(), 
                            mc.player.getYaw(), 
                            mc.player.getPitch(), 
                            true, // onGround = true (关键)
                            false // horizontalCollision = false (1.21.4 新增)
                        ));
                        // 客户端也更新一下状态，防止回弹
                        mc.player.onLanding();
                    }

                    // 发送带序列号的停止挖掘包 (1.21.4 必需)
                    sendSequenced(id -> new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, data.getPos(), data.getDirection(), id));
                    mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

                    data.setAttemptedBreak(true);
                    
                    if (!instantConfig.get()) {
                        breakIt.remove();
                    }
                }
            }
        }
    }

    public void clickMine(MiningData miningData) {
        for (MiningData d : miningQueue) {
            if (d.getPos().equals(miningData.getPos())) return;
        }

        int max = doubleBreakConfig.get() ? 2 : 1;
        if (miningQueue.size() >= max) {
            miningQueue.remove(miningQueue.size() - 1);
        }
        
        miningQueue.add(0, miningData);
    }

    @EventHandler
    public void onSendPacket(PacketEvent.Send event) {
        if (event.packet instanceof PlayerActionC2SPacket packet 
            && packet.getAction() == PlayerActionC2SPacket.Action.START_DESTROY_BLOCK
            && modeConfig.get() == SpeedmineMode.DAMAGE 
            && grimConfig.get()) {
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, packet.getPos(), packet.getDirection()));
        }

        if (event.packet instanceof UpdateSelectedSlotC2SPacket packet) {
            serverSideSlot = packet.getSelectedSlot();
        }
    }

    @EventHandler
    public void onReceivePacket(PacketEvent.Receive event) {
        if (mc.player == null || modeConfig.get() != SpeedmineMode.PACKET) return;
        if (event.packet instanceof BlockUpdateS2CPacket packet) handleBlockUpdate(packet.getPos(), packet.getState());
        else if (event.packet instanceof BundleS2CPacket bundle) {
            for (var p : bundle.getPackets()) {
                if (p instanceof BlockUpdateS2CPacket packet) handleBlockUpdate(packet.getPos(), packet.getState());
            }
        }
    }

    private void handleBlockUpdate(BlockPos pos, BlockState newState) {
        if (newState.isAir()) {
            Iterator<MiningData> it = miningQueue.iterator();
            while(it.hasNext()) {
                MiningData data = it.next();
                if (data.getPos().equals(pos)) {
                    data.setAttemptedBreak(false);
                }
            }
        }
    }

    // --- 核心修复: 手动计算挖掘速度，移除水下/空中惩罚 ---
    // 1.21.4 适配
    private float getBreakDelta(int slot, BlockState state, BlockPos pos) {
        float hardness = state.getHardness(mc.world, pos);
        if (hardness == -1.0F) return 0.0F;
        
        float speed = getBlockBreakingSpeed(slot, state);
        
        // 关键逻辑: 如果开启 BypassGround，我们就不进行原本的 5倍减速惩罚
        if (!bypassGround.get()) {
            // 原版逻辑：如果在水下且没有亲水附魔
            // 修复: 1.21.4 附魔检查逻辑更新
            boolean hasAquaAffinity = false;
            try {
                // 使用 RegistryEntry 检查 AQUA_AFFINITY
                RegistryEntry<Enchantment> aquaAffinityEntry = mc.world.getRegistryManager()
                        .getOrThrow(RegistryKeys.ENCHANTMENT)
                        .getOrThrow(Enchantments.AQUA_AFFINITY);
                hasAquaAffinity = EnchantmentHelper.getEquipmentLevel(aquaAffinityEntry, mc.player) > 0;
            } catch (Exception ignored) {
                // 兼容性Fallback，如果registry查找失败
            }

            if (mc.player.isSubmergedIn(FluidTags.WATER) && !hasAquaAffinity) {
                speed /= 5.0F;
            }
            // 原版逻辑：如果不在地面且不在飞 -> /5
            if (!mc.player.isOnGround()) {
                speed /= 5.0F;
            }
        }
        
        // 计算能否掉落
        boolean canHarvest = true;
        if (state.isToolRequired()) {
             ItemStack stack = mc.player.getInventory().getStack(slot);
             canHarvest = stack.isSuitableFor(state);
        }
        
        return speed / hardness / (canHarvest ? 30.0F : 100.0F);
    }

    private float getBlockBreakingSpeed(int slot, BlockState state) {
        try {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            float f = stack.getMiningSpeedMultiplier(state);

            if (f > 1.0F) {
                // 1.21.4 写法: 使用 Registry 获取附魔
                try {
                   RegistryEntry<Enchantment> effEntry = mc.world.getRegistryManager()
                           .getOrThrow(RegistryKeys.ENCHANTMENT)
                           .getOrThrow(Enchantments.EFFICIENCY);
                   int i = EnchantmentHelper.getLevel(effEntry, stack);
                   if (i > 0 && !stack.isEmpty()) f += (float) (i * i + 1);
                } catch (Exception ignored) {}
            }

            if (mc.player.hasStatusEffect(StatusEffects.HASTE)) {
                f *= 1.0F + (float) (mc.player.getStatusEffect(StatusEffects.HASTE).getAmplifier() + 1) * 0.2F;
            }
            // 挖掘疲劳
            if (mc.player.hasStatusEffect(StatusEffects.MINING_FATIGUE)) {
                float k;
                switch (mc.player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier()) {
                    case 0 -> k = 0.3F;
                    case 1 -> k = 0.09F;
                    case 2 -> k = 0.0027F;
                    default -> k = 8.1E-4F;
                }
                f *= k;
            }
            return f;
        } catch (Exception e) {
            return 1.0f;
        }
    }

    // --- Helper: 反射获取 PendingUpdateManager (修复 access error) ---
    private PendingUpdateManager getPendingManager() {
        try {
            // 尝试直接访问 (如果有AccessWidener或Mixin)
            // return ((ClientWorld) mc.world).getPendingUpdateManager(); 
            
            // 否则使用反射
            Method m = ClientWorld.class.getDeclaredMethod("getPendingUpdateManager"); 
            // 在 Fabric mapping (Yarn) 中通常是 getPendingUpdateManager，Intermediary 是 method_41925
            m.setAccessible(true);
            return (PendingUpdateManager) m.invoke(mc.world);
        } catch (Exception e) {
            // 如果反射名字不对，尝试 intermediary name
             try {
                Method m2 = ClientWorld.class.getDeclaredMethod("method_41925");
                m2.setAccessible(true);
                return (PendingUpdateManager) m2.invoke(mc.world);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    // --- Helper: 1.21+ 序列化发包 ---
    // 为了防止鬼方块，必须使用 Sequence ID
    private void sendSequenced(SequencedPacketCreator packetCreator) {
        if (mc.world == null || mc.getNetworkHandler() == null) return;
        
        PendingUpdateManager pendingUpdateManager = getPendingManager();
        if (pendingUpdateManager != null) {
            try (PendingUpdateManager manager = pendingUpdateManager.incrementSequence()) {
                int i = manager.getSequence();
                mc.getNetworkHandler().sendPacket(packetCreator.predict(i));
            }
        } else {
            // Fallback: 如果反射失败，尝试直接发包 (虽然可能不稳)
            // 但在大多数开发环境中，反射应该能工作
            mc.getNetworkHandler().sendPacket(packetCreator.predict(0));
        }
    }
    
    // --- 兼容旧方法的重载，用于非序列化包 ---
    private void sendAction(PlayerActionC2SPacket.Action action, MiningData data) {
        // 大多数时候 START/ABORT 包也建议序列化，但为了保持原代码风格，这里暂时使用 sendSequenced 封装最关键的 STOP
        // 这里对 START 也做一次增强，使用序列化发送，提高稳定性
        sendSequenced(id -> new PlayerActionC2SPacket(action, data.getPos(), data.getDirection(), id));
    }

    private void handleKeybinds() {
        if (autoMineKey.get().isPressed()) {
            if (!autoMineTogglePressed && mc.currentScreen == null) {
                autoMineTogglePressed = true;
                autoMine.set(!autoMine.get());
                info("Auto-mine " + (autoMine.get() ? "§aenabled" : "§cdisabled"));
            }
        } else {
            autoMineTogglePressed = false;
        }

        if (instantToggleKey.get().isPressed()) {
            if (!instantTogglePressed && mc.currentScreen == null) {
                instantTogglePressed = true;
                instantConfig.set(!instantConfig.get());
                if (!instantConfig.get()) miningQueue.clear();
                info("Instant mining " + (instantConfig.get() ? "§aenabled" : "§cdisabled"));
            }
        } else {
            instantTogglePressed = false;
        }
    }

    private void handleAutoMine() {
        int maxQueue = doubleBreakConfig.get() ? 2 : 1;
        long now = System.currentTimeMillis();

        // 修复: 字符串检查防止 EntityPose.SWIMMING 符号丢失 (虽然 Swimming 通常都存在)
        boolean isSwimming = mc.player.getPose().name().equals("SWIMMING");

        if (antiCrawl.get() && isSwimming && miningQueue.size() < maxQueue && now - lastAntiCrawlTime >= 100L) {
            BlockPos crawlBlock = getAntiCrawlBlock();
            if (crawlBlock != null && !isMiningBlock(crawlBlock)) {
                addAutoMineTask(crawlBlock, Direction.UP);
                lastAntiCrawlBlock = crawlBlock;
                lastAntiCrawlTime = now;
                if (!miningQueue.isEmpty()) return;
            }
        }

        currentTarget = getClosestEnemy();
        if (currentTarget != null) {
            if (miningQueue.size() < maxQueue && now - lastAutoMineTime >= 250L) {
                BlockPos targetBlock = findBestEnemyBlock(currentTarget);
                if (targetBlock != null && !isMiningBlock(targetBlock)) {
                    Direction dir = getInteractDirection(targetBlock);
                    if (dir != null || !strictDirection.get()) {
                        addAutoMineTask(targetBlock, dir == null ? Direction.DOWN : dir);
                        lastAutoMineBlock = targetBlock;
                        lastAutoMineTime = now;
                    }
                }
            }
        } else {
            lastAutoMineBlock = null;
        }
    }

    private void addAutoMineTask(BlockPos pos, Direction dir) {
        if (autoRotate.get() && rotateConfig.get()) {
            Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos));
        }
        queueMiningData(new MiningData(pos, dir));
    }
    
    public void queueMiningData(MiningData data) {
        if (!data.getState().isAir()) {
            if (miningQueue.stream().anyMatch(p1 -> data.getPos().equals(p1.getPos()))) return;
            miningQueue.add(0, data);
        }
    }

    private boolean startMining(MiningData data) {
        if (data.isStarted()) return false;
        data.setStarted();

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
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        } else {
            sendAction(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, data);
            sendAction(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, data);
            sendAction(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, data);
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        }
        return true;
    }

    private int getBestTool(BlockState state) {
        int bestSlot = -1;
        float bestSpeed = 0.0F;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            float speed = stack.getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = i;
            }
        }
        return bestSlot == -1 ? ((com.codigohasta.addon.mixin.InventoryAccessor) mc.player.getInventory()).getSelectedSlot() : bestSlot;
    }
    
    private boolean isMiningBlock(BlockPos pos) {
        for(MiningData data : miningQueue) {
            if(data.getPos().equals(pos)) return true;
        }
        return false;
    }
    
    private PlayerEntity getClosestEnemy() {
        if (mc.world == null) return null;
        return mc.world.getPlayers().stream()
            .filter(p -> p != mc.player && !p.isDead() && !Friends.get().isFriend(p))
            .min(Comparator.comparingDouble(p -> mc.player.distanceTo(p)))
            .filter(p -> mc.player.distanceTo(p) <= enemyRange.get())
            .orElse(null);
    }
    
    private BlockPos findBestEnemyBlock(PlayerEntity enemy) {
        if (enemy == null) return null;
        BlockPos enemyPos = enemy.getBlockPos();
        if (isValidBlock(enemyPos)) return enemyPos;
        BlockPos[] surround = { enemyPos.north(), enemyPos.south(), enemyPos.east(), enemyPos.west() };
        for (BlockPos pos : surround) if (isValidBlock(pos)) return pos;
        if (targetHead.get()) {
            BlockPos head = enemyPos.up(2);
            if (isValidBlock(head)) return head;
        }
        return null;
    }

    private boolean isValidBlock(BlockPos pos) {
        if (isMiningBlock(pos)) return false;
        if (mc.player.getEyePos().squaredDistanceTo(pos.toCenterPos()) > rangeConfig.get() * rangeConfig.get()) return false;
        BlockState state = mc.world.getBlockState(pos);
        if (state.isAir() || state.getHardness(mc.world, pos) == -1) return false;
        return state.getBlock() == Blocks.OBSIDIAN || state.getBlock() == Blocks.ENDER_CHEST || state.getBlock() == Blocks.NETHERITE_BLOCK || state.getBlock() == Blocks.ANVIL;
    }
    
    private BlockPos getAntiCrawlBlock() {
        BlockPos head = mc.player.getBlockPos().up();
        return isValidBlock(head) ? head : null;
    }

    private Direction getInteractDirection(BlockPos pos) {
        return Direction.DOWN; 
    }

    @EventHandler
    public void onStartBreaking(StartBreakingBlockEvent event) {
        if (modeConfig.get() == SpeedmineMode.PACKET) {
            event.cancel();
            BlockState state = mc.world.getBlockState(event.blockPos);
            if (state.getHardness(mc.world, event.blockPos) != -1.0F && !state.isAir()) {
                clickMine(new MiningData(event.blockPos, event.direction));
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    @EventHandler
    public void onRender(Render3DEvent event) {
        if (modeConfig.get() != SpeedmineMode.PACKET || !render.get()) return;

        for (MiningData data : miningQueue) {
            if (!fadeList.containsKey(data.getPos())) {
                fadeList.put(data.getPos(), new Animation(true, fadeTimeConfig.get().longValue()));
            }
        }

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

        for (MiningData data : miningQueue) {
            if (data.getState().isAir()) continue;

            Animation anim = fadeList.get(data.getPos());
            if (anim == null) continue;
            float factor = anim.getFactor();
            boolean done = data.getBlockDamage() >= 0.95f;
            
            SettingColor c = done ? colorDoneConfig.get() : colorConfig.get();
            int boxColor = new SettingColor(c.r, c.g, c.b, (int)(40 * factor)).getPacked();
            int lineColor = new SettingColor(c.r, c.g, c.b, (int)(100 * factor)).getPacked();

            BlockPos pos = data.getPos();
            VoxelShape shape = data.getState().getOutlineShape(mc.world, pos);
            if (shape.isEmpty()) shape = VoxelShapes.fullCube();
            Box box = shape.getBoundingBox().offset(pos);
            
            float total = 1.0F; 
            float progress = data.getState().isAir() ? 1.0f : MathHelper.clamp(data.getBlockDamage() / total, 0f, 1f);
            
            if (progress == 0.0f) progress = 0.01f;

            double centerX = box.minX + (box.maxX - box.minX) / 2;
            double centerY = box.minY + (box.maxY - box.minY) / 2;
            double centerZ = box.minZ + (box.maxZ - box.minZ) / 2;
            double scale = progress;
            double dx = (box.maxX - box.minX) / 2 * scale;
            double dy = (box.maxY - box.minY) / 2 * scale;
            double dz = (box.maxZ - box.minZ) / 2 * scale;
            
            event.renderer.box(new Box(centerX - dx, centerY - dy, centerZ - dz, centerX + dx, centerY + dy, centerZ + dz), new SettingColor(boxColor), new SettingColor(lineColor), shapeMode.get(), 0);
        }
    }

    private class MiningData {
        private final BlockPos pos;
        private final Direction direction;
        private boolean attemptedBreak;
        private long breakTime;
        private float blockDamage;
        private boolean started;

        public MiningData(BlockPos pos, Direction direction) {
            this.pos = pos;
            this.direction = direction;
        }
        
        public BlockPos getPos() { return pos; }
        public Direction getDirection() { return direction; }
        public boolean isStarted() { return started; }
        public void setStarted() { this.started = true; }
        public boolean hasAttemptedBreak() { return attemptedBreak; }
        public void setAttemptedBreak(boolean b) { this.attemptedBreak = b; if(b) resetBreakTime(); }
        public void resetBreakTime() { this.breakTime = System.currentTimeMillis(); }
        public float damage(float dmg) { this.blockDamage += dmg; return this.blockDamage; }
        public void resetDamage() { this.started = false; this.blockDamage = 0; }
        public float getBlockDamage() { return blockDamage; }
        public BlockState getState() { return mc.world.getBlockState(pos); }
        public int getSlot() { return getBestTool(getState()); }
    }
    
    private static class Animation {
        private boolean state;
        private long time;
        private final long duration;
        public Animation(boolean state, long duration) { this.state = state; this.duration = duration; this.time = System.currentTimeMillis(); }
        public void setState(boolean state) { if (this.state != state) { this.state = state; this.time = System.currentTimeMillis(); } }
        public float getFactor() {
            if (state) return 1.0f;
            long elapsed = System.currentTimeMillis() - time;
            return Math.max(0f, 1.0f - (float)elapsed / duration);
        }
    }
}