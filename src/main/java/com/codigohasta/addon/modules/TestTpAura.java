package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.*;

public class TestTpAura extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPath = settings.createGroup("Pathfinding");

    // --- 设置 ---
    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Attack range.")
        .defaultValue(50)
        .min(0)
        .sliderMax(100)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Tick delay between attacks.")
        .defaultValue(10)
        .min(0)
        .build()
    );

    private final Setting<Integer> pathLimit = sgPath.add(new IntSetting.Builder()
        .name("compute-limit")
        .description("Max steps for A* pathfinding.")
        .defaultValue(10000)
        .min(10)
        .max(99999000)
        .build()
    );

    private final Setting<Boolean> throughWalls = sgPath.add(new BoolSetting.Builder()
        .name("through-walls")
        .description("Ignore walls during pathfinding (Use with caution).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> renderPath = sgPath.add(new BoolSetting.Builder()
        .name("render-path")
        .description("Visualizes the calculated path.")
        .defaultValue(true)
        .build()
    );

    // --- 内部变量 ---
    private int timer = 0;
    private List<BlockPos> currentPath = new ArrayList<>();

    public TestTpAura() {
        super(AddonTemplate.CATEGORY, "test-tp-aura", "A port of LiquidBounce TeleportAura A* logic.");
    }

    @Override
    public void onActivate() {
        timer = 0;
        currentPath.clear();
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (mc.world == null || mc.player == null) return;

        timer++;
        if (timer < delay.get()) return;

        // 1. 寻找目标 (使用 Meteor 自带工具，比手写循环更高效)
        Entity target = TargetUtils.get(entity -> {
            if (!(entity instanceof LivingEntity)) return false;
            if (entity == mc.player) return false;
            if (mc.player.distanceTo(entity) > range.get()) return false;
            if (((LivingEntity) entity).isDead()) return false;
            return true;
        }, SortPriority.LowestDistance);

        if (target != null) {
            // 2. 执行 A* 寻路
            BlockPos startPos = mc.player.getBlockPos();
            BlockPos endPos = target.getBlockPos();

            List<BlockPos> path = computePath(startPos, endPos);

            if (path != null && !path.isEmpty()) {
                currentPath = path; // 用于渲染
                executeAttack(target, path);
                timer = 0;
            } else {
                currentPath.clear();
            }
        } else {
            currentPath.clear();
        }
    }

    // --- 核心攻击逻辑 (TP -> Hit -> TP Back) ---
    private void executeAttack(Entity target, List<BlockPos> path) {
        // 1. 瞬移过去
        for (BlockPos pos : path) {
            sendTpPacket(pos);
        }

        // 2. 攻击
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(mc.player.getActiveHand());

        // 3. 瞬移回来 (反转路径)
        for (int i = path.size() - 2; i >= 0; i--) { // -2 是为了不重复发最后一个包
            sendTpPacket(path.get(i));
        }
    }

   private void sendTpPacket(BlockPos pos) {
        // 将方块坐标转换为中心坐标 (x + 0.5, y, z + 0.5)
        double x = pos.getX() + 0.5;
        double y = pos.getY();
        double z = pos.getZ() + 0.5;
        
        // 修复 1.21.4 的构造函数：增加了第 5 个参数 boolean horizontalCollision
        // 参数顺序：x, y, z, onGround, horizontalCollision
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
            x, y, z, true, false
        ));
    }

    // ==========================================
    // 核心 A* (A-Star) 寻路算法实现
    // 对应 LiquidBounce 脚本中的 computePath / _0x5e9583
    // ==========================================
    private List<BlockPos> computePath(BlockPos start, BlockPos end) {
        // OpenList: 使用优先队列，按 fCost (g + h) 排序
        PriorityQueue<Node> openSet = new PriorityQueue<>(Comparator.comparingDouble(Node::getFCost));
        // ClosedList: 使用 HashSet 存储已访问的坐标 (比 JS 中的位运算 HashMap 更适合 Java)
        Set<BlockPos> closedSet = new HashSet<>();

        Node startNode = new Node(start, null, 0, start.getSquaredDistance(end));
        openSet.add(startNode);

        // 防止无限计算
        int loops = 0;
        int limit = pathLimit.get();

        while (!openSet.isEmpty() && loops < limit) {
            Node current = openSet.poll();
            BlockPos currentPos = current.pos;

            // 检查是否到达终点 (距离小于 1.5 格)
            if (Math.sqrt(currentPos.getSquaredDistance(end)) < 1.5) {
                return retracePath(current);
            }

            closedSet.add(currentPos);

            // 扩展邻居 (前后左右上下)
            // 对应 JS 代码中的手动展开 neighbor logic
            BlockPos[] neighbors = {
                currentPos.north(), currentPos.south(),
                currentPos.east(), currentPos.west(),
                currentPos.up(), currentPos.down()
            };

            for (BlockPos neighbor : neighbors) {
                if (closedSet.contains(neighbor)) continue;

                // 碰撞检测 (isBlockSafe)
                if (!isValidPosition(neighbor)) continue;

                double newGCost = current.gCost + 1; // 距离 + 1
                // 启发式函数 (Euclidean Distance)
                double newHCost = Math.sqrt(neighbor.getSquaredDistance(end)); 
                
                Node neighborNode = new Node(neighbor, current, newGCost, newHCost);
                
                // 这里简化了 update 逻辑，直接添加 (对于 Minecraft 这种网格，通常足够)
                openSet.add(neighborNode);
            }
            loops++;
        }

        return null; // 未找到路径
    }

    // 路径回溯
    private List<BlockPos> retracePath(Node endNode) {
        List<BlockPos> path = new ArrayList<>();
        Node current = endNode;
        while (current != null) {
            path.add(current.pos);
            current = current.parent;
        }
        Collections.reverse(path);
        return path;
    }

    // 碰撞检测逻辑 (对应 JS 中的 isValidPosition / isBlockSafe)
    private boolean isValidPosition(BlockPos pos) {
        if (throughWalls.get()) return true;

        // 检查脚下是否有方块 (防止掉落虚空，或者确保是地面)
        // 注意：如果你想让它能飞行寻路，可以注释掉这行
        // if (mc.world.getBlockState(pos.down()).isAir()) return false; 

        // 检查目标位置是否是固体 (如果是固体则不能进入 -> 除非是穿墙)
        BlockState state = mc.world.getBlockState(pos);
        if (state.isSolidBlock(mc.world, pos)) return false;

        // 检查头顶防止卡住 (简化的 2格高 检查)
        BlockState upState = mc.world.getBlockState(pos.up());
        if (upState.isSolidBlock(mc.world, pos.up())) return false;

        return true;
    }

    // 内部节点类
    private static class Node {
        public final BlockPos pos;
        public final Node parent;
        public final double gCost; // 离起点距离
        public final double hCost; // 离终点估算
        
        public Node(BlockPos pos, Node parent, double gCost, double hCost) {
            this.pos = pos;
            this.parent = parent;
            this.gCost = gCost;
            this.hCost = hCost;
        }

        public double getFCost() {
            return gCost + hCost;
        }
    }

    // --- 渲染 (Debug) ---
    @EventHandler
    public void onRender(Render3DEvent event) {
        if (!renderPath.get() || currentPath.isEmpty()) return;

        // 绘制路径线
        for (int i = 0; i < currentPath.size() - 1; i++) {
            BlockPos pos1 = currentPath.get(i);
            BlockPos pos2 = currentPath.get(i + 1);

            double x1 = pos1.getX() + 0.5;
            double y1 = pos1.getY();
            double z1 = pos1.getZ() + 0.5;

            double x2 = pos2.getX() + 0.5;
            double y2 = pos2.getY();
            double z2 = pos2.getZ() + 0.5;

            event.renderer.line(x1, y1, z1, x2, y2, z2, Color.GREEN);
        }
        
        // 绘制每个节点
        for (BlockPos pos : currentPath) {
             event.renderer.box(pos, new Color(0, 255, 0, 50), new Color(0, 255, 0, 255), ShapeMode.Lines, 0);
        }
    }
}