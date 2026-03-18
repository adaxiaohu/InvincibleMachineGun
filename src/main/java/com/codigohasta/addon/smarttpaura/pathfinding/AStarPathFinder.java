package com.codigohasta.addon.smarttpaura.pathfinding;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.*;

/**
 * 终极复刻：1.8.9 LiquidBounce 核心寻路算法 (mumyHackAura) 
 * 适配：Minecraft 1.21.4 (VoxelShape 严格检测)
 *
 * 【新集成的 4 大核心特性】：
 * 1. 逆向寻路 (Reverse Pathfinding)：从目标散发向玩家寻路，速度成倍提升。
 * 2. 隔墙起手 (Through-walls Initiation)：扫描目标周围 3 格攻击半径起手，支持隔墙/隔天花板刺杀。
 * 3. 瞬移穿薄墙 (H-Clip)：遇到 1 格厚的墙壁，直接生成跨越节点，无视障碍。
 * 4. 极致 GC 优化 (Zero-GC Loop)：使用 FastUtil 的 Long2ObjectOpenHashMap 与 int 坐标系，消灭寻路途中的对象分配掉帧。
 */
public class AStarPathFinder {
    private final World world;
    private final CollisionHelper collisionHelper;
    
    // 功能开关配置
    private boolean airPath = true; // 开启 V-Clip
    private boolean hClip = true;   // 【新特性 3】：开启 H-Clip 水平穿墙
    private double attackRange = 3.0; // 【新特性 2】：杀戮光环攻击范围 (隔墙起手半径)
    private static final int MAX_ITERATIONS = 15000;

    // 预先分配 Mutable 以防在安全检测中产生大量 GC 垃圾
    private final BlockPos.Mutable checkPos = new BlockPos.Mutable();
    private final BlockPos.Mutable headPos = new BlockPos.Mutable();

    public AStarPathFinder(World world) {
        this.world = world;
        this.collisionHelper = new CollisionHelper(world);
    }

    public void setAirPath(boolean b) { this.airPath = b; }
    public void setHClip(boolean b) { this.hClip = b; }
    public void setAttackRange(double range) { this.attackRange = range; }

    public List<Vec3d> findPath(Vec3d start, Vec3d target, double maxStep) {
        // 【新特性 1】：逆向寻路。将真实玩家定为 targetPos (终点)，将真实敌人定为 startPos (起点)
        BlockPos playerBP = BlockPos.ofFloored(start);
        BlockPos enemyBP = BlockPos.ofFloored(target);

        // 【新特性 4】：彻底消除 GC 压力。使用 long 作为 Key，拒绝生成上万个 HashMap.Entry 和 BlockPos 对象
        Long2ObjectOpenHashMap<Node> allNodes = new Long2ObjectOpenHashMap<>();
        PriorityQueue<Node> openSet = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));

        Node best = null;

        // 【新特性 2】：隔墙起手。不从敌人脚下开始，而是把敌人周围可攻击范围内的安全方块全塞进开放列表
        int ex = enemyBP.getX(), ey = enemyBP.getY(), ez = enemyBP.getZ();
        int range = (int) Math.ceil(attackRange);
        
        for (int x = ex - range; x <= ex + range; x++) {
            for (int y = ey - range; y <= ey + range; y++) {
                for (int z = ez - range; z <= ez + range; z++) {
                    double distSq = (x - ex)*(x - ex) + (y - ey)*(y - ey) + (z - ez)*(z - ez);
                    if (distSq <= attackRange * attackRange) {
                        if (isTwoBlocksHighSafe(x, y, z)) {
                            long posLong = BlockPos.asLong(x, y, z);
                            double h = getHeuristic(x, y, z, playerBP.getX(), playerBP.getY(), playerBP.getZ());
                            Node startNode = new Node(posLong, null, 0, h);
                            openSet.add(startNode);
                            allNodes.put(posLong, startNode);
                            
                            if (best == null || startNode.f < best.f) best = startNode;
                        }
                    }
                }
            }
        }

        if (openSet.isEmpty()) return new ArrayList<>(); // 目标被彻底封死，完全不可达

        int px = playerBP.getX(), py = playerBP.getY(), pz = playerBP.getZ();
        int iterations = 0;

        // 核心寻路循环 (所有计算降维到 int 与 long，实现近乎零 GC)
        while (!openSet.isEmpty() && iterations++ < MAX_ITERATIONS) {
            Node curr = openSet.poll();

            int cx = BlockPos.unpackLongX(curr.posLong);
            int cy = BlockPos.unpackLongY(curr.posLong);
            int cz = BlockPos.unpackLongZ(curr.posLong);

            // 终点判定：是否靠近玩家，且能直接发包移动
            if (Math.abs(cx - px) <= 1 && Math.abs(cy - py) <= 1 && Math.abs(cz - pz) <= 1) {
                if (collisionHelper.canSweep(toVec3d(cx, cy, cz), start)) {
                    best = new Node(BlockPos.asLong(px, py, pz), curr, 0, 0);
                    break;
                }
            }

            // 更新最近点（启发式）
            double currentDistToPlayer = getDistanceSq(cx, cy, cz, px, py, pz);
            int bx = BlockPos.unpackLongX(best.posLong);
            int by = BlockPos.unpackLongY(best.posLong);
            int bz = BlockPos.unpackLongZ(best.posLong);
            if (currentDistToPlayer < getDistanceSq(bx, by, bz, px, py, pz)) {
                best = curr;
            }

            // 展开相邻节点（全内联，零对象分配）
            expandNode(cx, cy, cz, curr, px, py, pz, openSet, allNodes);
        }

        return buildPath(best, start, target, maxStep);
    }

    private void expandNode(int cx, int cy, int cz, Node curr, int px, int py, int pz, PriorityQueue<Node> openSet, Long2ObjectOpenHashMap<Node> allNodes) {
        // 1. 六向正交寻路 (代价为 1.0)
        processNeighbor(cx + 1, cy, cz, curr, px, py, pz, 1.0, openSet, allNodes);
        processNeighbor(cx - 1, cy, cz, curr, px, py, pz, 1.0, openSet, allNodes);
        processNeighbor(cx, cy, cz + 1, curr, px, py, pz, 1.0, openSet, allNodes);
        processNeighbor(cx, cy, cz - 1, curr, px, py, pz, 1.0, openSet, allNodes);
        processNeighbor(cx, cy + 1, cz, curr, px, py, pz, 1.0, openSet, allNodes);
        processNeighbor(cx, cy - 1, cz, curr, px, py, pz, 1.0, openSet, allNodes);

        // 2. V-Clip 穿透寻路 (向上/向下穿过天花板/地板)
        if (airPath) {
            for (int i = 2; i <= 10; i++) {
                if (isTwoBlocksHighSafe(cx, cy + i, cz)) {
                    processNeighbor(cx, cy + i, cz, curr, px, py, pz, i, openSet, allNodes);
                    break; // 找到第一层安全楼层即停止向上搜索
                }
            }
            for (int i = 2; i <= 10; i++) {
                if (isTwoBlocksHighSafe(cx, cy - i, cz)) {
                    processNeighbor(cx, cy - i, cz, curr, px, py, pz, i, openSet, allNodes);
                    break;
                }
            }
        }

        // 3. 【新特性 3】：H-Clip 水平穿透薄墙 (代价视为正常移动)
        if (hClip) {
            // +X 方向
            if (!isTwoBlocksHighSafe(cx + 1, cy, cz) && isTwoBlocksHighSafe(cx + 2, cy, cz)) 
                processNeighbor(cx + 2, cy, cz, curr, px, py, pz, 2.0, openSet, allNodes);
            // -X 方向
            if (!isTwoBlocksHighSafe(cx - 1, cy, cz) && isTwoBlocksHighSafe(cx - 2, cy, cz)) 
                processNeighbor(cx - 2, cy, cz, curr, px, py, pz, 2.0, openSet, allNodes);
            // +Z 方向
            if (!isTwoBlocksHighSafe(cx, cy, cz + 1) && isTwoBlocksHighSafe(cx, cy, cz + 2)) 
                processNeighbor(cx, cy, cz + 2, curr, px, py, pz, 2.0, openSet, allNodes);
            // -Z 方向
            if (!isTwoBlocksHighSafe(cx, cy, cz - 1) && isTwoBlocksHighSafe(cx, cy, cz - 2)) 
                processNeighbor(cx, cy, cz - 2, curr, px, py, pz, 2.0, openSet, allNodes);
        }
    }

    private void processNeighbor(int x, int y, int z, Node curr, int targetX, int targetY, int targetZ, double costAdd, PriorityQueue<Node> openSet, Long2ObjectOpenHashMap<Node> allNodes) {
        if (!isTwoBlocksHighSafe(x, y, z)) return;

        long posLong = BlockPos.asLong(x, y, z);
        double cost = curr.g + costAdd;

        Node existing = allNodes.get(posLong);
        if (existing != null && existing.g <= cost) return;

        double f = cost + getHeuristic(x, y, z, targetX, targetY, targetZ);
        Node newNode = new Node(posLong, curr, cost, f);
        allNodes.put(posLong, newNode);
        openSet.add(newNode);
    }

    /**
     * 优化后的：绝对两格高防卡墙检测 (支持 Mutable 无分配操作)
     */
    private boolean isTwoBlocksHighSafe(int x, int y, int z) {
        checkPos.set(x, y, z);
        Vec3d exactPos = toVec3d(x, y, z);
        
        if (!collisionHelper.isSafe(exactPos)) return false;
        
        headPos.set(x, y + 1, z);
        BlockState headState = world.getBlockState(headPos);
        
        if (!headState.getCollisionShape(world, headPos).isEmpty()) {
            double headBlockMinY = headPos.getY() + headState.getCollisionShape(world, headPos).getMin(Direction.Axis.Y);
            if (headBlockMinY - exactPos.y < 1.95) {
                return false;
            }
        }
        return true;
    }

    private double getHeuristic(int x1, int y1, int z1, int x2, int y2, int z2) {
        double dx = Math.abs(x1 - x2);
        double dy = Math.abs(y1 - y2);
        double dz = Math.abs(z1 - z2);
        return Math.sqrt(dx * dx + dy * dy + dz * dz) + dx + dy + dz;
    }

    private double getDistanceSq(int x1, int y1, int z1, int x2, int y2, int z2) {
        return (x1 - x2)*(x1 - x2) + (y1 - y2)*(y1 - y2) + (z1 - z2)*(z1 - z2);
    }

    private Vec3d toVec3d(int x, int y, int z) {
        BlockPos bp = new BlockPos(x, y, z); // 仅在安全检测与重构路径时分配，频率极低
        double floorHeight = collisionHelper.getFloorHeight(bp);
        return new Vec3d(x + 0.5, y + floorHeight, z + 0.5);
    }

    private List<Vec3d> buildPath(Node bestNodeNearPlayer, Vec3d realStart, Vec3d realTarget, double maxStep) {
        if (bestNodeNearPlayer == null) return new ArrayList<>();

        // 注意：因为我们是从敌人寻路到玩家，所以 Node 的链条是：【玩家附近】 -> ... -> 【敌人附近起手点】
        // 顺着 parent 往回找，列表自带天然的【从玩家向敌人前进】的顺序！所以不需要 Collections.reverse() 了！
        List<Long> blockPath = new ArrayList<>();
        Node node = bestNodeNearPlayer;
        while (node != null) {
            blockPath.add(node.posLong);
            node = node.parent;
        }

        List<Vec3d> vecPath = new ArrayList<>();
        vecPath.add(realStart); // 玩家真实坐标
        
        // 跳过两端可能与真实坐标重叠的格子
        for (int i = 1; i < blockPath.size() - 1; i++) {
            long posLong = blockPath.get(i);
            vecPath.add(toVec3d(BlockPos.unpackLongX(posLong), BlockPos.unpackLongY(posLong), BlockPos.unpackLongZ(posLong)));
        }
        
        // 如果你需要走到贴脸，可以加 realTarget。但对于光环来说，走到起手点其实就够了。这里保留添加以确保逻辑完整。
        vecPath.add(realTarget); 

        return simplify(vecPath, maxStep);
    }

    private List<Vec3d> simplify(List<Vec3d> path, double maxStep) {
        if (path.size() <= 2) return path;

        List<Vec3d> simple = new ArrayList<>();
        Vec3d lastPos = path.get(0);
        simple.add(lastPos);

        for (int i = 1; i < path.size(); i++) {
            Vec3d current = path.get(i);
            
            if (i < path.size() - 1) {
                Vec3d next = path.get(i + 1);
                double distance = lastPos.distanceTo(next);
                
                if (distance > maxStep || !collisionHelper.canSweep(lastPos, next)) {
                    simple.add(current);
                    lastPos = current;
                }
            }
        }
        
        simple.add(path.get(path.size() - 1));
        return simple;
    }

    private static class Node {
        long posLong; // 使用 long 代替 BlockPos，大幅减少内存占用
        Node parent;
        double g, f;

        Node(long posLong, Node pr, double g, double f) {
            this.posLong = posLong;
            this.parent = pr;
            this.g = g;
            this.f = f;
        }
    }
}
