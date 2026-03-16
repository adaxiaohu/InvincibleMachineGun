package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
// Baritone 依赖
import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
// Minecraft 依赖
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class OreVeinESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBaritone = settings.createGroup("Baritone 自动化");
    private final SettingGroup sgRender = settings.createGroup("渲染设置");

    // --- 通用设置 ---
    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("模式")
        .description("安全模式只扫描暴露在表面的矿石。")
        .defaultValue(Mode.Legit)
        .build()
    );

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("目标方块")
        .description("选择需要扫描的矿石。")
        .defaultValue(
            Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
            Blocks.ANCIENT_DEBRIS,
            Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE,
            Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE, Blocks.RAW_IRON_BLOCK,
            Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE, Blocks.RAW_COPPER_BLOCK,
            Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
            Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
            Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
            Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE,
            Blocks.NETHER_QUARTZ_ORE
        )
        .build()
    );

    private final Setting<Integer> radius = sgGeneral.add(new IntSetting.Builder()
        .name("扫描半径")
        .description("水平扫描半径 (Chunk)。")
        .defaultValue(6)
        .min(2)
        .sliderMax(16)
        .build()
    );

    private final Setting<Integer> yMin = sgGeneral.add(new IntSetting.Builder()
        .name("最低高度")
        .description("扫描的最低 Y 轴高度。")
        .defaultValue(-64)
        .sliderMin(-64)
        .sliderMax(320)
        .build()
    );

    private final Setting<Integer> yMax = sgGeneral.add(new IntSetting.Builder()
        .name("最高高度")
        .description("扫描的最高 Y 轴高度。")
        .defaultValue(120)
        .sliderMin(-64)
        .sliderMax(320)
        .build()
    );

    private final Setting<Integer> scanDelay = sgGeneral.add(new IntSetting.Builder()
        .name("扫描延迟 (Tick)")
        .description("后台扫描的时间间隔。")
        .defaultValue(20)
        .min(5)
        .build()
    );

    // --- Baritone 自动化设置 ---
    private final Setting<Boolean> autoMine = sgBaritone.add(new BoolSetting.Builder()
        .name("自动挖掘暴露矿石")
        .description("让 Baritone 自动去挖扫描到的表面矿石。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> autoMineRange = sgBaritone.add(new IntSetting.Builder()
        .name("自动挖掘距离")
        .description("只挖掘距离玩家多少格以内的矿石（太远的不要抢）。")
        .defaultValue(16) // 稍微调小一点，优先挖身边的
        .min(5)
        .sliderMax(64)
        .visible(autoMine::get)
        .build()
    );

    // --- 渲染设置 ---
    private final Setting<Integer> renderLimit = sgRender.add(new IntSetting.Builder()
        .name("渲染上限")
        .description("防止渲染过多导致 FPS 下降。")
        .defaultValue(10000)
        .min(100)
        .sliderMax(20000)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("渲染形状")
        .description("方块的渲染方式。")
        .defaultValue(ShapeMode.Lines)
        .build()
    );

    private final Setting<Boolean> useAutoColor = sgRender.add(new BoolSetting.Builder()
        .name("自动颜色")
        .description("根据矿石类型自动显示颜色（钻石蓝、黄金黄等）。")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> customColor = sgRender.add(new ColorSetting.Builder()
        .name("统一颜色")
        .description("关闭自动颜色后，所有方块显示的统一颜色。")
        .defaultValue(new SettingColor(255, 255, 255))
        .visible(() -> !useAutoColor.get())
        .build()
    );

    // --- 变量 ---
    private final Map<ChunkPos, List<RenderBlock>> cachedChunks = new ConcurrentHashMap<>();
    private final Map<Block, Color> colorCache = new HashMap<>();
    private int timer = 0;
    private int baritoneTimer = 0; 

    public OreVeinESP() {
        super(AddonTemplate.CATEGORY, "ore-vein-esp-pro", "高性能矿脉扫描 & Baritone联动");
    }

    @Override
    public void onDeactivate() {
        cachedChunks.clear();
        colorCache.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        // 1. 扫描逻辑
        timer++;
        if (timer >= scanDelay.get()) {
            timer = 0;
            MeteorExecutor.execute(this::scanSurroundings);
        }

        // 2. Baritone 自动化逻辑
        if (autoMine.get() && BaritoneUtils.IS_AVAILABLE) {
            if (baritoneTimer > 0) {
                baritoneTimer--;
            } else {
                baritoneTimer = 10; // 每0.5秒决策一次
                handleBaritoneLogic();
            }
        }
    }

    // --- Baritone 核心联动逻辑 (智能抢单版) ---
    private void handleBaritoneLogic() {
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos closestVisiblePos = null;
        double closestVisibleDistSq = Double.MAX_VALUE;
        double maxDistSq = Math.pow(autoMineRange.get(), 2);

        // 1. 寻找最近的【可见】矿石
        for (List<RenderBlock> list : cachedChunks.values()) {
            if (list == null || list.isEmpty()) continue;
            
            for (RenderBlock rb : list) {
                double distSq = rb.pos.getSquaredDistance(playerPos);
                if (distSq <= maxDistSq && distSq < closestVisibleDistSq) {
                    closestVisibleDistSq = distSq;
                    closestVisiblePos = rb.pos;
                }
            }
        }

        if (closestVisiblePos == null) return;

        // 2. 决策：是否抢单？
        boolean shouldOverride = false;
        
        // 获取 Baritone 当前正在干什么
        if (!BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()) {
            // 情况A：Baritone 闲着 -> 抢！
            shouldOverride = true;
        } else {
            // 情况B：Baritone 正在干活 (可能在挖隧道，也可能在挖 AntiAntiXray 发现的矿)
            Goal currentGoal = BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().getGoal();
            
            // 如果是在挖隧道 (Goal 不是具体的方块)，直接抢！
            if (currentGoal == null || !(currentGoal instanceof GoalBlock)) {
                shouldOverride = true;
            } 
            // 如果是在挖别的矿 (GoalBlock)
            else {
                BlockPos currentTarget = ((GoalBlock) currentGoal).getGoalPos();
                double currentTargetDistSq = currentTarget.getSquaredDistance(playerPos);
                
                // 比较距离：如果我发现的可见矿 比 他正在挖的矿 更近 -> 抢！
                // (加个 2.0 的缓冲，避免两块矿距离差不多时来回抽搐)
                if (closestVisibleDistSq < currentTargetDistSq - 2.0) {
                    shouldOverride = true;
                }
            }
        }

        // 3. 执行
        if (shouldOverride) {
            // 防止重复设置同一个目标
            if (!isMiningTarget(closestVisiblePos)) {
                BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(closestVisiblePos));
            }
        }
    }

    private boolean isMiningTarget(BlockPos target) {
        if (!BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()) return false;
        
        Goal goal = BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().getGoal();
        if (goal == null) return false;

        if (goal instanceof GoalBlock goalBlock) {
            return goalBlock.getGoalPos().equals(target);
        }
        return false;
    }

    @EventHandler
    private void onBlockUpdate(PacketEvent.Receive event) {
        if (event.packet instanceof BlockUpdateS2CPacket packet) {
            removeBlockFromCache(packet.getPos());
        } else if (event.packet instanceof ChunkDeltaUpdateS2CPacket packet) {
            packet.visitUpdates((pos, state) -> {
                if (!blocks.get().contains(state.getBlock())) {
                    removeBlockFromCache(pos);
                }
            });
        }
    }

    private void scanSurroundings() {
        if (mc.player == null || mc.world == null) return;

        ChunkPos center = new ChunkPos(mc.player.getBlockPos());
        int r = radius.get();
        int minY = yMin.get();
        int maxY = yMax.get();

        Set<Block> targetBlocks = new HashSet<>(blocks.get());
        if (targetBlocks.isEmpty()) return;

        cachedChunks.keySet().removeIf(pos -> 
            Math.abs(pos.x - center.x) > r + 2 || Math.abs(pos.z - center.z) > r + 2
        );

        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                ChunkPos chunkPos = new ChunkPos(center.x + x, center.z + z);

                if (cachedChunks.containsKey(chunkPos)) continue;
                if (!isChunkAndNeighborsLoaded(chunkPos)) continue;

                WorldChunk chunk = mc.world.getChunk(chunkPos.x, chunkPos.z);
                if (chunk == null) continue;

                List<RenderBlock> found = new ArrayList<>();
                int startX = chunkPos.getStartX();
                int startZ = chunkPos.getStartZ();

                int chunkTopY = chunk.getBottomY() + chunk.getHeight();
                int actualMinY = Math.max(minY, chunk.getBottomY());
                int actualMaxY = Math.min(maxY, chunkTopY);

                for (int bx = 0; bx < 16; bx++) {
                    for (int bz = 0; bz < 16; bz++) {
                        for (int by = actualMinY; by < actualMaxY; by++) {
                            BlockPos localPos = new BlockPos(startX + bx, by, startZ + bz);
                            Block block = chunk.getBlockState(localPos).getBlock();

                            if (targetBlocks.contains(block)) {
                                if (mode.get() == Mode.Legit && !isExposed(localPos)) {
                                    continue;
                                }
                                found.add(new RenderBlock(localPos, block));
                            }
                        }
                    }
                }

                if (!found.isEmpty()) {
                    cachedChunks.put(chunkPos, found);
                } else {
                    cachedChunks.put(chunkPos, Collections.emptyList());
                }
            }
        }
    }

    private boolean isChunkAndNeighborsLoaded(ChunkPos center) {
        return mc.world.getChunkManager().isChunkLoaded(center.x, center.z) &&
               mc.world.getChunkManager().isChunkLoaded(center.x + 1, center.z) &&
               mc.world.getChunkManager().isChunkLoaded(center.x - 1, center.z) &&
               mc.world.getChunkManager().isChunkLoaded(center.x, center.z + 1) &&
               mc.world.getChunkManager().isChunkLoaded(center.x, center.z - 1);
    }

    private boolean isExposed(BlockPos pos) {
        int bottomY = mc.world.getBottomY();
        int topY = bottomY + mc.world.getHeight();

        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.offset(dir);
            if (neighbor.getY() < bottomY || neighbor.getY() >= topY) continue;
            if (!mc.world.getBlockState(neighbor).isSideSolidFullSquare(mc.world, neighbor, dir.getOpposite())) {
                return true;
            }
        }
        return false;
    }

    private void removeBlockFromCache(BlockPos pos) {
        ChunkPos chunkPos = new ChunkPos(pos);
        List<RenderBlock> blocks = cachedChunks.get(chunkPos);
        if (blocks != null && !blocks.isEmpty()) {
            try {
                List<RenderBlock> newBlocks = new ArrayList<>(blocks);
                if (newBlocks.removeIf(rb -> rb.pos.equals(pos))) {
                    cachedChunks.put(chunkPos, newBlocks);
                }
            } catch (Exception ignored) {}
        }
    }

    private Color getOreColor(Block block) {
        return colorCache.computeIfAbsent(block, b -> {
            if (b == Blocks.DIAMOND_ORE || b == Blocks.DEEPSLATE_DIAMOND_ORE) return new Color(0, 255, 255);
            if (b == Blocks.GOLD_ORE || b == Blocks.DEEPSLATE_GOLD_ORE || b == Blocks.NETHER_GOLD_ORE || b == Blocks.RAW_GOLD_BLOCK) return new Color(255, 215, 0);
            if (b == Blocks.IRON_ORE || b == Blocks.DEEPSLATE_IRON_ORE || b == Blocks.RAW_IRON_BLOCK) return new Color(210, 180, 160);
            if (b == Blocks.COPPER_ORE || b == Blocks.DEEPSLATE_COPPER_ORE || b == Blocks.RAW_COPPER_BLOCK) return new Color(255, 100, 0);
            if (b == Blocks.EMERALD_ORE || b == Blocks.DEEPSLATE_EMERALD_ORE) return new Color(0, 255, 0);
            if (b == Blocks.LAPIS_ORE || b == Blocks.DEEPSLATE_LAPIS_ORE || b == Blocks.LAPIS_BLOCK) return new Color(0, 0, 255);
            if (b == Blocks.REDSTONE_ORE || b == Blocks.DEEPSLATE_REDSTONE_ORE) return new Color(255, 0, 0);
            if (b == Blocks.ANCIENT_DEBRIS) return new Color(160, 32, 240);
            if (b == Blocks.COAL_ORE || b == Blocks.DEEPSLATE_COAL_ORE) return new Color(30, 30, 30);
            if (b == Blocks.NETHER_QUARTZ_ORE) return new Color(220, 220, 220);

            int mapColor = b.getDefaultMapColor().color;
            if (mapColor == 0) return new Color(255, 0, 255);
            return new Color((mapColor >> 16) & 0xFF, (mapColor >> 8) & 0xFF, mapColor & 0xFF, 255);
        });
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        int count = 0;
        int limit = renderLimit.get();

        for (List<RenderBlock> list : cachedChunks.values()) {
            if (list == null || list.isEmpty()) continue;

            for (RenderBlock rb : list) {
                if (count >= limit) return;

                Color color;
                if (useAutoColor.get()) {
                    color = getOreColor(rb.block);
                } else {
                    color = customColor.get();
                }

                event.renderer.box(rb.pos, color, color, shapeMode.get(), 0);
                count++;
            }
        }
    }

    public enum Mode {
        Legit("安全模式"),
        Blatant("暴力模式");
        private final String title;
        Mode(String title) { this.title = title; }
        @Override public String toString() { return title; }
    }

    private record RenderBlock(BlockPos pos, Block block) {}
}