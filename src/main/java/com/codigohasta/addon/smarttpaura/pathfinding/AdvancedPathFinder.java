package com.codigohasta.addon.smarttpaura.pathfinding;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

import java.util.*;

public class AdvancedPathFinder {
    private final World world;
    private final MovementTypeSystem movementSystem;
    private boolean airPath = true;

    // 搜索限制设置
    private static final int MAX_ITERATIONS = 15000; // 大幅增加迭代上限以支持百米寻路
    private static final int TIMEOUT_MS = 150;       // 毫秒级超时控制

    public AdvancedPathFinder(World world) {
        this.world = world;
        this.movementSystem = new MovementTypeSystem(world);
    }

    /**
     * 使用双向 A* 算法寻找路径
     */
    public List<Vec3d> findPath(Vec3d startVec, Vec3d targetVec) {
        BlockPos start = BlockPos.ofFloored(startVec);
        BlockPos target = BlockPos.ofFloored(targetVec);

        // 1. 快速检查：如果两点间直接可见，无需寻路
        if (isDirectPathClear(startVec, targetVec)) {
            return new ArrayList<>(List.of(startVec, targetVec));
        }

        // 2. 初始化双向搜索
        // 正向搜索 (Start -> Target)
        PriorityQueue<Node> openStart = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
        Map<BlockPos, Node> mapStart = new HashMap<>();
        
        // 反向搜索 (Target -> Start)
        PriorityQueue<Node> openEnd = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
        Map<BlockPos, Node> mapEnd = new HashMap<>();

        Node startNode = new Node(start, null, 0, start.getSquaredDistance(target));
        Node targetNode = new Node(target, null, 0, target.getSquaredDistance(start));

        openStart.add(startNode);
        mapStart.put(start, startNode);

        openEnd.add(targetNode);
        mapEnd.put(target, targetNode);

        long startTime = System.currentTimeMillis();
        int iterations = 0;
        Node intersectNode = null; // 相遇点

        // 3. 双向搜索循环
        while (!openStart.isEmpty() && !openEnd.isEmpty()) {
            // 检查超时或超限
            if (++iterations > MAX_ITERATIONS || (System.currentTimeMillis() - startTime) > TIMEOUT_MS) {
                break;
            }

            // 每次从较小的那一端扩展，保持平衡
            if (openStart.size() <= openEnd.size()) {
                intersectNode = expand(openStart, mapStart, mapEnd, target, false);
            } else {
                intersectNode = expand(openEnd, mapEnd, mapStart, start, true);
            }

            if (intersectNode != null) break; // 路径打通了
        }

        // 4. 构建原始路径
        List<BlockPos> rawPath = reconstructBidirectionalPath(intersectNode, mapStart, mapEnd);
        if (rawPath.isEmpty()) return null;

        // 5. 路径平滑 (Theta* 思想：拉直线)
        return smoothPath(rawPath, startVec, targetVec);
    }

    private Node expand(PriorityQueue<Node> open, Map<BlockPos, Node> currentMap, Map<BlockPos, Node> otherMap, BlockPos target, boolean reverse) {
        Node current = open.poll();
        if (current == null) return null;

        // 检查是否在对方的已访问列表中（相遇）
        if (otherMap.containsKey(current.pos)) {
            return current;
        }

        // 3D 邻居扩展
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) { // 允许 Y 轴
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    
                    // 优化：长距离时减少 Y 轴频繁变向
                    if (!airPath && y != 0 && (x == 0 && z == 0)) continue;

                    BlockPos nextPos = current.pos.add(x, y, z);

                    // 碰撞检查
                    if (!isPassable(nextPos)) continue;

                    double moveCost = Math.sqrt(x * x + y * y + z * z);
                    double newG = current.g + moveCost;

                    Node neighbor = currentMap.getOrDefault(nextPos, new Node(nextPos));

                    if (newG < neighbor.g) {
                        neighbor.parent = current;
                        neighbor.g = newG;
                        // 启发式函数：双向 A* 需要一致的启发式
                        neighbor.f = newG + Math.sqrt(nextPos.getSquaredDistance(target));
                        
                        if (!currentMap.containsKey(nextPos)) {
                            open.add(neighbor);
                            currentMap.put(nextPos, neighbor);
                        }
                    }
                }
            }
        }
        return null;
    }

    // 重组双向路径
    private List<BlockPos> reconstructBidirectionalPath(Node intersect, Map<BlockPos, Node> mapStart, Map<BlockPos, Node> mapEnd) {
        if (intersect == null) return Collections.emptyList();

        List<BlockPos> path = new ArrayList<>();
        
        // 1. 回溯正向路径 (Start -> Intersect)
        Node curr = mapStart.get(intersect.pos);
        while (curr != null) {
            path.add(curr.pos);
            curr = curr.parent;
        }
        Collections.reverse(path); // 翻转变成 Start -> Intersect

        // 2. 回溯反向路径 (Intersect -> Target)
        // 注意：mapEnd 中的 parent 是指向 Target 的，所以直接遍历就是 Intersect -> Target
        curr = mapEnd.get(intersect.pos).parent; // 跳过相遇点，避免重复
        while (curr != null) {
            path.add(curr.pos);
            curr = curr.parent;
        }

        return path;
    }

    // 核心优化：路径平滑 (String Pulling)
    // 将网格状的路径拉直成最少的直线段
    private List<Vec3d> smoothPath(List<BlockPos> gridPath, Vec3d startVec, Vec3d endVec) {
        List<Vec3d> smoothed = new ArrayList<>();
        smoothed.add(startVec);

        Vec3d lastValid = startVec;
        int checkIndex = 0;

        // 类似 Theta* 的视线检查
        for (int i = 1; i < gridPath.size(); i++) {
            Vec3d current = Vec3d.ofBottomCenter(gridPath.get(i)).add(0, 0.1, 0); // 稍微抬高防止蹭地
            
            // 检查从 lastValid 到 current 之间是否有障碍
            if (!isDirectPathClear(lastValid, current)) {
                // 如果有障碍，说明上一个点是必须经过的拐点
                Vec3d waypoint = Vec3d.ofBottomCenter(gridPath.get(i - 1)).add(0, 0.1, 0);
                smoothed.add(waypoint);
                lastValid = waypoint;
            }
        }

        smoothed.add(endVec);
        return smoothed;
    }

    // 射线检测 (Raycast) 判断两点间是否直通
    private boolean isDirectPathClear(Vec3d start, Vec3d end) {
        double dist = start.distanceTo(end);
        int steps = (int) Math.ceil(dist * 2); // 每 0.5 格检查一次
        Vec3d diff = end.subtract(start).normalize().multiply(0.5);

        Vec3d current = start;
        // 考虑玩家碰撞箱高度 (1.8m)
        // 我们检查脚部和头部两个点
        for (int i = 0; i < steps; i++) {
            current = current.add(diff);
            BlockPos pos = BlockPos.ofFloored(current);
            
            if (!isPassable(pos)) return false;
            // 检查头部 (假设高 1.8，这里检查上方一格)
            if (!isPassable(pos.up())) return false;
        }
        return true;
    }

    private boolean isPassable(BlockPos pos) {
        if (airPath) {
            BlockState state = world.getBlockState(pos);
            // 允许穿过草、水、梯子等，只阻挡完整方块
            VoxelShape shape = state.getCollisionShape(world, pos);
            return shape.isEmpty();
        }
        return movementSystem.isPassable(pos);
    }

    public void setAirPath(boolean airPath) { this.airPath = airPath; }

    private static class Node {
        BlockPos pos;
        Node parent;
        double g = Double.MAX_VALUE;
        double f = Double.MAX_VALUE;
        Node(BlockPos pos) { this.pos = pos; }
        Node(BlockPos pos, Node parent, double g, double f) {
            this.pos = pos; this.parent = parent; this.g = g; this.f = f;
        }
    }
}