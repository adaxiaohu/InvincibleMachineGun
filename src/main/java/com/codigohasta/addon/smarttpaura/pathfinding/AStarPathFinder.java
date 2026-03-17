package com.codigohasta.addon.smarttpaura.pathfinding;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.*;

public class AStarPathFinder {
    private final World world;
    private final CollisionHelper collisionHelper;
    private boolean airPath = true;
    private static final int MAX_ITERATIONS = 15000;

    public AStarPathFinder(World world) {
        this.world = world;
        this.collisionHelper = new CollisionHelper(world);
    }

    public void setAirPath(boolean b) { this.airPath = b; }

    // 兼容方法重载
    public List<Vec3d> findPath(Vec3d start, Vec3d target) {
        return findPath(start, target, 9.0);
    }

    public List<Vec3d> findPath(Vec3d start, Vec3d target, double maxStep) {
        BlockPos startBP = BlockPos.ofFloored(start);
        BlockPos targetBP = BlockPos.ofFloored(target);

        Map<BlockPos, Node> allNodes = new HashMap<>();
        PriorityQueue<Node> openSet = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));

        Node startNode = new Node(startBP, null, 0, getHeuristic(startBP, targetBP));
        openSet.add(startNode);
        allNodes.put(startBP, startNode);

        Node best = startNode;
        int iterations = 0;

        while (!openSet.isEmpty() && iterations++ < MAX_ITERATIONS) {
            Node curr = openSet.poll();

            // 终点判定
            if (curr.pos.isWithinDistance(targetBP, 1.5)) {
                if (collisionHelper.canSweep(toVec3d(curr.pos), target)) {
                    best = new Node(targetBP, curr, 0, 0);
                    break;
                }
            }

            // 启发式保存最近点
            if (curr.pos.getSquaredDistance(targetBP) < best.pos.getSquaredDistance(targetBP)) {
                best = curr;
            }

            // 六向正交寻路 & VClip 探测
            for (BlockPos neighbor : getNeighbors(curr.pos)) {
                
                // [核心修改]：使用严格的“两格高”检测，拒绝钻入 1.8 格的极限缝隙
                if (!isTwoBlocksHighSafe(neighbor)) continue;

                double cost = curr.g + Math.sqrt(curr.pos.getSquaredDistance(neighbor));

                Node existing = allNodes.get(neighbor);
                if (existing != null && existing.g <= cost) continue;

                Node newNode = new Node(neighbor, curr, cost, cost + getHeuristic(neighbor, targetBP));
                allNodes.put(neighbor, newNode);
                openSet.add(newNode);
            }
        }
        
        return buildPath(best, start, target, maxStep);
    }

    private List<BlockPos> getNeighbors(BlockPos p) {
        List<BlockPos> res = new ArrayList<>();

        // 六向正交
        res.add(p.add(1, 0, 0));
        res.add(p.add(-1, 0, 0));
        res.add(p.add(0, 0, 1));
        res.add(p.add(0, 0, -1));
        res.add(p.up());
        res.add(p.down());

        // V-Clip 穿墙机制
        if (airPath) {
            for (int i = 2; i <= 10; i++) {
                BlockPos up = p.up(i);
                if (isTwoBlocksHighSafe(up)) { // [核心修改]
                    res.add(up); 
                    break;
                }
            }
            for (int i = 2; i <= 10; i++) {
                BlockPos down = p.down(i);
                if (isTwoBlocksHighSafe(down)) { // [核心修改]
                    res.add(down);
                    break;
                }
            }
        }
        return res;
    }

    /**
  
     */
    private boolean isTwoBlocksHighSafe(BlockPos pos) {
        Vec3d exactPos = toVec3d(pos);
        
        // 1. 基础的胖子碰撞箱检查 (确保脚下、周围不蹭墙)
        if (!collisionHelper.isSafe(exactPos)) return false;
        
        // 2. 严格两格高检测：检查头顶上方的方块 (pos.up())
        BlockPos headPos = pos.up();
        BlockState headState = world.getBlockState(headPos);
        
        // 如果头顶这格不是完全空的空气
        if (!headState.getCollisionShape(world, headPos).isEmpty()) {
            // 获取头顶方块向下的最低延伸点 (比如上半砖的底部是 0.5)
            double headBlockMinY = headPos.getY() + headState.getCollisionShape(world, headPos).getMin(Direction.Axis.Y);
            
            // 玩家脚底到头顶方块最低点的净空高度，必须 >= 1.95 格（留出绝对防回弹余量）
            // 如果只有 1.8 格（刚好塞进玩家），瞬移时必定被发包检测判定卡墙
            if (headBlockMinY - exactPos.y < 1.95) {
                return false;
            }
        }
        
        return true;
    }

    private double getHeuristic(BlockPos a, BlockPos b) {
        double dx = Math.abs(a.getX() - b.getX());
        double dy = Math.abs(a.getY() - b.getY());
        double dz = Math.abs(a.getZ() - b.getZ());
        return Math.sqrt(dx * dx + dy * dy + dz * dz) + dx + dy + dz;
    }

    private Vec3d toVec3d(BlockPos bp) {
        double floorHeight = collisionHelper.getFloorHeight(bp);
        return new Vec3d(bp.getX() + 0.5, bp.getY() + floorHeight, bp.getZ() + 0.5);
    }

    private List<Vec3d> buildPath(Node node, Vec3d realStart, Vec3d realTarget, double maxStep) {
        if (node == null) return new ArrayList<>();

        List<BlockPos> blockPath = new ArrayList<>();
        while (node != null) {
            blockPath.add(node.pos);
            node = node.parent;
        }
        Collections.reverse(blockPath);

        List<Vec3d> vecPath = new ArrayList<>();
        vecPath.add(realStart); 
        for (int i = 1; i < blockPath.size() - 1; i++) {
            vecPath.add(toVec3d(blockPath.get(i)));
        }
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
        BlockPos pos;
        Node parent;
        double g, f;

        Node(BlockPos p, Node pr, double g, double f) {
            this.pos = p;
            this.parent = pr;
            this.g = g;
            this.f = f;
        }
    }
}
