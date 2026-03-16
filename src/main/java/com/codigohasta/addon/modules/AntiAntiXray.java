package com.codigohasta.addon.modules;

import net.minecraft.util.math.Vec3d;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
// Baritone 依赖
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
// Minecraft 依赖
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem; 
import net.minecraft.item.FireworkRocketItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack; // 新增
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.*;

public class AntiAntiXray extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBaritone = settings.createGroup("Baritone 自动化");
    private final SettingGroup sgPerformance = settings.createGroup("性能与逻辑");
    private final SettingGroup sgColors = settings.createGroup("矿物颜色(高亮版)");
    private final SettingGroup sgRender = settings.createGroup("渲染设置");

    // --- 通用设置 ---
    private final Setting<TriggerMode> triggerMode = sgGeneral.add(new EnumSetting.Builder<TriggerMode>()
        .name("触发模式")
        .description("选择自动扫描还是按键手动扫描。")
        .defaultValue(TriggerMode.Automatic)
        .build()
    );

    private final Setting<Keybind> forceScanKey = sgGeneral.add(new KeybindSetting.Builder()
        .name("扫描按键")
        .description("按下此按键开始扫描周围。")
        .defaultValue(Keybind.fromKey(-1))
        .visible(() -> triggerMode.get() == TriggerMode.Keybind)
        .build()
    );

    private final Setting<Integer> radius = sgGeneral.add(new IntSetting.Builder()
        .name("扫描半径")
        .description("扫描范围半径。")
        .defaultValue(5)
        .min(1)
        .max(7)
        .build()
    );

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("交互模式")
        .description("发包方式。")
        .defaultValue(Mode.PacketMine)
        .build()
    );

    // --- Baritone 设置 ---
    private final Setting<Boolean> autoMine = sgBaritone.add(new BoolSetting.Builder()
        .name("启用自动挖掘")
        .description("当扫描出真矿时，让Baritone自动去挖掘。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> mineThreshold = sgBaritone.add(new IntSetting.Builder()
        .name("中断挖掘阈值")
        .description("只有当发现至少N个真矿时，才停止挖隧道去挖矿。")
        .defaultValue(1)
        .min(1)
        .sliderMax(10)
        .visible(autoMine::get)
        .build()
    );

    private final Setting<Boolean> tunnelMode = sgBaritone.add(new BoolSetting.Builder()
        .name("空闲时挖隧道")
        .description("如果没有发现真矿(或数量不足)，让Baritone自动向前挖隧道(盾构机)。")
        .defaultValue(true)
        .visible(autoMine::get)
        .build()
    );
    
    // --- 新增：工具保护设置 ---
    private final Setting<Integer> minDurability = sgBaritone.add(new IntSetting.Builder()
        .name("工具保护阈值")
        .description("当主手工具耐久低于此值时，强制停止Baritone。")
        .defaultValue(15)
        .min(0)
        .sliderMax(100)
        .visible(autoMine::get)
        .build()
    );

    // --- 性能设置 ---
    private final Setting<Integer> threadCount = sgPerformance.add(new IntSetting.Builder()
        .name("线程数量")
        .defaultValue(2)
        .min(1)
        .max(8)
        .build()
    );

    private final Setting<Integer> blocksPerTick = sgPerformance.add(new IntSetting.Builder()
        .name("双线速率")
        .defaultValue(1)
        .min(1)
        .max(10)
        .build()
    );

    private final Setting<Integer> delay = sgPerformance.add(new IntSetting.Builder()
        .name("发包延迟(Tick)")
        .defaultValue(1)
        .min(1)
        .build()
    );

    private final Setting<Boolean> dynamicReset = sgPerformance.add(new BoolSetting.Builder()
        .name("移动时重置队列")
        .defaultValue(true)
        .build()
    );

    // --- 颜色设置 ---
    private final Setting<SettingColor> diamondColor = sgColors.add(new ColorSetting.Builder().name("钻石颜色").defaultValue(new SettingColor(0, 255, 255, 200)).build());
    private final Setting<SettingColor> debrisColor = sgColors.add(new ColorSetting.Builder().name("残骸颜色").defaultValue(new SettingColor(160, 32, 240, 220)).build());
    private final Setting<SettingColor> goldColor = sgColors.add(new ColorSetting.Builder().name("金矿颜色").defaultValue(new SettingColor(255, 215, 0, 200)).build());
    private final Setting<SettingColor> ironColor = sgColors.add(new ColorSetting.Builder().name("铁矿颜色").defaultValue(new SettingColor(210, 180, 140, 200)).build());
    private final Setting<SettingColor> emeraldColor = sgColors.add(new ColorSetting.Builder().name("绿宝石颜色").defaultValue(new SettingColor(0, 255, 0, 200)).build());
    private final Setting<SettingColor> lapisColor = sgColors.add(new ColorSetting.Builder().name("青金石颜色").defaultValue(new SettingColor(0, 0, 170, 220)).build());
    private final Setting<SettingColor> redstoneColor = sgColors.add(new ColorSetting.Builder().name("红石颜色").defaultValue(new SettingColor(255, 0, 0, 200)).build());
    private final Setting<SettingColor> coalColor = sgColors.add(new ColorSetting.Builder().name("煤矿颜色").defaultValue(new SettingColor(20, 20, 20, 200)).build());
    private final Setting<SettingColor> copperColor = sgColors.add(new ColorSetting.Builder().name("铜矿颜色").defaultValue(new SettingColor(230, 115, 0, 200)).build());
    private final Setting<SettingColor> quartzColor = sgColors.add(new ColorSetting.Builder().name("石英颜色").defaultValue(new SettingColor(230, 230, 230, 180)).build());
    private final Setting<SettingColor> otherColor = sgColors.add(new ColorSetting.Builder().name("其他颜色").defaultValue(new SettingColor(255, 255, 255, 200)).build());

    // --- 渲染设置 ---
    private final Setting<List<Block>> targetBlocks = sgRender.add(new BlockListSetting.Builder()
        .name("目标方块列表")
        .defaultValue(
            Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
            Blocks.ANCIENT_DEBRIS,
            Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE,
            Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE,
            Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE,
            Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
            Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
            Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
            Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE,
            Blocks.NETHER_QUARTZ_ORE
        )
        .build()
    );

    private final Setting<Boolean> renderTracers = sgRender.add(new BoolSetting.Builder()
        .name("启用追踪线")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showProgress = sgRender.add(new BoolSetting.Builder()
        .name("显示进度条")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> renderScan = sgRender.add(new BoolSetting.Builder()
        .name("渲染扫描光标")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> scanColor = sgRender.add(new ColorSetting.Builder()
        .name("光标颜色")
        .defaultValue(new SettingColor(255, 255, 255, 100))
        .visible(renderScan::get)
        .build()
    );

    // --- 内部变量 ---
    private final Queue<BlockPos> queueA = new ConcurrentLinkedQueue<>();
    private final Queue<BlockPos> queueB = new ConcurrentLinkedQueue<>();
    private final Set<BlockPos> scannedPositions = Collections.synchronizedSet(new HashSet<>());
    private final Map<BlockPos, Block> foundOres = new ConcurrentHashMap<>();
    private final Map<BlockPos, Long> activeScanningRender = new ConcurrentHashMap<>();
    
    private ExecutorService executor;
    private int timer = 0;
    private int baritoneTimer = 0;
    
    private int totalBlocksInCurrentScan = 0;
    private boolean isScanning = false;
    private boolean lastKeyPressedState = false;
    private BlockPos lastRefillPos = null;

    public AntiAntiXray() {
        super(AddonTemplate.CATEGORY, "反反矿透", "可以通过点击附近方块来获得真实的矿物信息，实现矿透。好像有些假矿反作弊插件修复了这种方法。有些服务器可以有用");
    }

    @Override
    public void onActivate() {
        clearAll();
        executor = Executors.newFixedThreadPool(threadCount.get());
        lastKeyPressedState = false;
        lastRefillPos = null;
        baritoneTimer = 0;
        
        if (triggerMode.get() == TriggerMode.Automatic) {
            refillQueue();
        }
    }

    @Override
    public void onDeactivate() {
        clearAll();
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        if (BaritoneUtils.IS_AVAILABLE && BaritoneAPI.getProvider().getPrimaryBaritone() != null) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
        }
    }

    private void clearAll() {
        queueA.clear();
        queueB.clear();
        scannedPositions.clear();
        foundOres.clear();
        activeScanningRender.clear();
        totalBlocksInCurrentScan = 0;
        isScanning = false;
        timer = 0;
        baritoneTimer = 0;
        lastRefillPos = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.world == null || mc.player == null) return;

        BlockPos playerPos = mc.player.getBlockPos();

        // 1. 自动清理失效矿石
        if (mc.player.age % 5 == 0 && !foundOres.isEmpty()) {
            foundOres.entrySet().removeIf(entry -> {
                BlockPos pos = entry.getKey();
                Block storedBlock = entry.getValue();
                Block currentBlock = mc.world.getBlockState(pos).getBlock();
                return currentBlock != storedBlock;
            });
        }

        // 2. 扫描队列逻辑
        if (triggerMode.get() == TriggerMode.Automatic) {
            boolean movedSignificantly = lastRefillPos == null || lastRefillPos.getSquaredDistance(playerPos) > 2.25; 
            
            if (movedSignificantly) {
                if (dynamicReset.get()) {
                    queueA.clear(); queueB.clear();
                }
                lastRefillPos = playerPos;
                refillQueue();
            } 
            else if (isQueuesEmpty() && mc.player.age % 10 == 0) {
                if (isScanning) finishScan();
                refillQueue();
            }
        } else {
            boolean isPressed = forceScanKey.get().isPressed();
            if (isPressed && !lastKeyPressedState) {
                if (!isQueuesEmpty()) {
                    mc.inGameHud.setOverlayMessage(Text.of("§c扫描正在进行中..."), false);
                } else {
                    mc.inGameHud.setOverlayMessage(Text.of("§a[手动] 开始扫描..."), false);
                    queueA.clear(); queueB.clear();
                    int clearRadius = radius.get() + 5;
                    synchronized (scannedPositions) {
                        scannedPositions.removeIf(p -> p.isWithinDistance(playerPos, clearRadius));
                    }
                    if (executor == null || executor.isShutdown()) executor = Executors.newFixedThreadPool(threadCount.get());
                    refillQueue();
                }
            }
            lastKeyPressedState = isPressed;
            if (isScanning && isQueuesEmpty()) finishScan();
        }
        
        if (isScanning) updateProgress();

        // 3. Baritone 自动化逻辑 (包含耐久检测)
        if (autoMine.get() && BaritoneUtils.IS_AVAILABLE) {
            // --- 工具耐久保护逻辑 ---
            if (checkToolHealth()) {
                if (baritoneTimer > 0) {
                    baritoneTimer--;
                } else {
                    baritoneTimer = 5; 
                    handleBaritoneLogic(playerPos);
                }
            }
        }

        // 4. 缓存清理
        if (mc.player.age % 20 == 0) {
            int cleanupThreshold = radius.get() + 8;
            synchronized (scannedPositions) {
                scannedPositions.removeIf(pos -> !pos.isWithinDistance(playerPos, cleanupThreshold));
            }
            foundOres.keySet().removeIf(pos -> !pos.isWithinDistance(playerPos, 100));
        }

        if (isQueuesEmpty()) return;

        // 5. 发包处理
        if (timer < delay.get()) {
            timer++;
            return;
        }
        timer = 0;
        int count = blocksPerTick.get();
        submitTasks(queueA, count);
        submitTasks(queueB, count);
    }
    
    // --- 新增：检查工具耐久 ---
    private boolean checkToolHealth() {
        if (!isPathing()) return true; // 如果 Baritone 没在干活，就不管
        
        ItemStack stack = mc.player.getMainHandStack();
        if (stack.isEmpty() || !stack.isDamageable()) return true; // 手里没拿工具或物品无耐久
        
        // 计算剩余耐久：最大耐久 - 已损耗耐久
        int remainingDurability = stack.getMaxDamage() - stack.getDamage();
        
        if (remainingDurability <= minDurability.get()) {
            // 紧急停止！
            BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
            mc.inGameHud.setOverlayMessage(Text.of("§c[警告] 工具耐久过低 (" + remainingDurability + ")，已停止自动挖掘！"), true);
            
            // 可选：在这里自动关闭模块，防止它下一秒又开始
            // toggle(); 
            return false; // 返回 false 表示本次 Tick 停止后续 Baritone 逻辑
        }
        return true;
    }

    // --- Baritone 智能调度 ---
    private void handleBaritoneLogic(BlockPos playerPos) {
        int threshold = mineThreshold.get();
        int oreCount = foundOres.size();

        // 场景一：发现的矿石数量 >= 阈值
        if (oreCount >= threshold) {
            // 找到最近的真矿
            BlockPos closest = foundOres.keySet().stream()
                .min(Comparator.comparingDouble(pos -> pos.getSquaredDistance(playerPos)))
                .orElse(null);

            if (closest != null) {
                if (!isMiningTarget(closest)) {
                    BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(closest));
                }
            }
        } 
        // 场景二：矿石不足阈值，且开启了盾构模式
        else if (tunnelMode.get()) {
            if (!isPathing()) {
                BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("tunnel");
            }
        }
    }

    private boolean isMiningTarget(BlockPos target) {
        if (!isPathing()) return false;
        Goal goal = BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().getGoal();
        if (goal == null) return false;

        if (goal instanceof GoalBlock goalBlock) {
            return goalBlock.getGoalPos().equals(target);
        }
        return false;
    }

    private boolean isPathing() {
        return BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing();
    }
    
    private void finishScan() {
        isScanning = false;
        if (showProgress.get()) {
            mc.inGameHud.setOverlayMessage(Text.of("§a扫描完成!"), false);
        }
    }

    private void submitTasks(Queue<BlockPos> queue, int count) {
        for (int i = 0; i < count; i++) {
            BlockPos pos = queue.poll();
            if (pos == null) break;
            scannedPositions.add(pos);
            if (executor != null && !executor.isShutdown()) executor.submit(() -> processBlock(pos));
        }
    }

    private void processBlock(BlockPos pos) {
        if (mc.getNetworkHandler() == null || mc.player == null) return;
        activeScanningRender.put(pos, System.currentTimeMillis());
        try {
            boolean usePacketMine = mode.get() == Mode.PacketMine;
            if (!usePacketMine) {
                Item mainItem = mc.player.getMainHandStack().getItem();
                if (mainItem instanceof BlockItem || mainItem instanceof FireworkRocketItem) usePacketMine = true;
            }

            if (usePacketMine) {
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, Direction.UP));
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, pos, Direction.UP));
            } else {
                BlockHitResult hitResult = new BlockHitResult(new Vec3d(pos.getX(), pos.getY(), pos.getZ()), Direction.UP, pos, false);
                mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hitResult, 0));
            }
        } catch (Exception ignored) {}
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof BlockUpdateS2CPacket packet) {
            BlockPos pos = packet.getPos();
            Block block = packet.getState().getBlock();
            if (targetBlocks.get().contains(block)) {
                foundOres.put(pos, block);
            } else {
                foundOres.remove(pos);
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (renderScan.get()) {
            long now = System.currentTimeMillis();
            activeScanningRender.forEach((pos, time) -> {
                if (now - time > 150) {
                    activeScanningRender.remove(pos); return;
                }
                event.renderer.box(pos, scanColor.get(), scanColor.get(), ShapeMode.Lines, 0);
            });
        }
        for (Map.Entry<BlockPos, Block> entry : foundOres.entrySet()) {
            BlockPos pos = entry.getKey();
            Block block = entry.getValue();
            SettingColor color = getColorForBlock(block);
            event.renderer.box(pos, color, color, ShapeMode.Lines, 0); 
            if (renderTracers.get()) {
                event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, color);
            }
        }
    }

    private void updateProgress() {
        if (!showProgress.get() || totalBlocksInCurrentScan == 0) return;
        int remaining = queueA.size() + queueB.size();
        int completed = totalBlocksInCurrentScan - remaining;
        float percent = (float) completed / totalBlocksInCurrentScan * 100f;
        StringBuilder bar = new StringBuilder("[");
        int barLength = 10;
        int filled = (int) (percent / 100.0f * barLength);
        for (int i = 0; i < barLength; i++) {
            if (i < filled) bar.append("§a█"); else bar.append("§7-");
        }
        bar.append("§r]");
        String msg = String.format("扫描进度: %s §e%.2f%% §7(剩余: %d)", bar, percent, remaining);
        mc.inGameHud.setOverlayMessage(Text.of(msg), false);
    }

    private boolean isQueuesEmpty() {
        return queueA.isEmpty() && queueB.isEmpty();
    }

    private void refillQueue() {
        if (mc.player == null) return;
        int r = radius.get();
        BlockPos pPos = mc.player.getBlockPos();
        List<BlockPos> allCandidates = new ArrayList<>();
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos target = pPos.add(x, y, z);
                    if (!target.isWithinDistance(pPos, r)) continue;
                    if (mc.world.getBlockState(target).isAir()) continue;
                    synchronized (scannedPositions) { if (scannedPositions.contains(target)) continue; }
                    if (foundOres.containsKey(target)) continue;
                    if (queueA.contains(target) || queueB.contains(target)) continue;
                    allCandidates.add(target);
                }
            }
        }
        if (allCandidates.isEmpty()) return;
        allCandidates.sort(Comparator.comparingDouble(pos -> pos.getSquaredDistance(pPos)));
        for (int i = 0; i < allCandidates.size(); i++) {
            if (i % 2 == 0) queueA.add(allCandidates.get(i)); else queueB.add(allCandidates.get(i));
        }
        totalBlocksInCurrentScan = queueA.size() + queueB.size();
        if (totalBlocksInCurrentScan > 0) isScanning = true;
    }
    
    private SettingColor getColorForBlock(Block block) {
        if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE) return diamondColor.get();
        if (block == Blocks.ANCIENT_DEBRIS) return debrisColor.get();
        if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE || block == Blocks.NETHER_GOLD_ORE) return goldColor.get();
        if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE) return ironColor.get();
        if (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE) return emeraldColor.get();
        if (block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE) return lapisColor.get();
        if (block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE) return redstoneColor.get();
        if (block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE) return coalColor.get();
        if (block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE) return copperColor.get();
        if (block == Blocks.NETHER_QUARTZ_ORE) return quartzColor.get();
        return otherColor.get();
    }
    
    @Override
    public String getInfoString() {
        if (isScanning) return "扫描中";
        return triggerMode.get() == TriggerMode.Automatic ? "自动" : "按键";
    }

    public enum Mode { PacketMine, RightClick }
    public enum TriggerMode { Automatic, Keybind }
}