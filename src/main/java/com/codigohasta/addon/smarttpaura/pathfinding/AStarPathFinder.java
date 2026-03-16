package com.codigohasta.addon.smarttpaura.pathfinding;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.*;

public class AStarPathFinder {
    private final World world;
    private final CollisionHelper collisionHelper;
    private boolean airPath = true;
    private static final int MAX_ITERATIONS = 15000;

    // [核心设定] 移动代价权重
    private static final double COST_AIR = 1.0;     // 空气是甜美的
    private static final double COST_GROUND = 2.5;  // 地面是熔岩 (加重惩罚迫使起飞)
    private static final double COST_VCLIP = 0.8;   // 垂直飞行是最高效的

    public AStarPathFinder(World world) {
        this.world = world;
        this.collisionHelper = new CollisionHelper(world);
    }

    public void setAirPath(boolean b) { this.airPath = b; }

    public List<Vec3d> findPath(Vec3d start, Vec3d target) {
        Map<NodeKey, Node> allNodes = new HashMap<>(); 
        PriorityQueue<Node> openSet = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));

        // 强制居中对齐，使用胖子判定的中心点
        Vec3d startCentered = new Vec3d(start.x, start.y, start.z);
        Node startNode = new Node(startCentered, null, 0, startCentered.distanceTo(target));
        
        openSet.add(startNode);
        allNodes.put(new NodeKey(startCentered), startNode);

        Node best = startNode;
        int iterations = 0;

        while (!openSet.isEmpty() && iterations++ < MAX_ITERATIONS) {
            Node curr = openSet.poll();

            // 终点判定
            if (curr.pos.distanceTo(target) < 1.0) {
                if (collisionHelper.canSweep(curr.pos, target)) {
                    best = new Node(target, curr, 0, 0);
                    break;
                }
            }
            if (curr.pos.distanceTo(target) < best.pos.distanceTo(target)) best = curr;

            for (Vec3d neighborPos : getNeighbors(curr.pos)) {
                // 计算基础距离
                double dist = curr.pos.distanceTo(neighborPos);
                
                // [核心设定] 动态代价计算
                double penalty = getCostPenalty(curr.pos, neighborPos);
                double moveCost = dist * penalty;

                double newG = curr.g + moveCost;
                NodeKey nKey = new NodeKey(neighborPos);
                
                Node existing = allNodes.get(nKey);
                if (existing != null && existing.g <= newG) continue;
                if (!collisionHelper.canSweep(curr.pos, neighborPos)) continue;

                Node newNode = new Node(neighborPos, curr, newG, newG + neighborPos.distanceTo(target));
                allNodes.put(nKey, newNode);
                openSet.add(newNode);
            }
        }
        return buildPath(best);
    }

    /**
     * 判断移动类型并返回代价倍率
     */
    private double getCostPenalty(Vec3d from, Vec3d to) {
        // 垂直移动：奖励
        if (Math.abs(from.x - to.x) < 0.1 && Math.abs(from.z - to.z) < 0.1) {
            return COST_VCLIP;
        }
        
        // 检查脚下是否有方块
        boolean fromGround = isSolidGround(from);
        boolean toGround = isSolidGround(to);

        // 如果两点都在空中，或者是在空中平移 -> 便宜
        if (!fromGround && !toGround) return COST_AIR;

        // 只要沾了地 -> 贵
        return COST_GROUND;
    }

    private boolean isSolidGround(Vec3d p) {
        // 检查脚下 0.1 米是否有碰撞
        return !collisionHelper.isSafe(p.add(0, -0.1, 0)); 
    }

    private List<Vec3d> getNeighbors(Vec3d currentPos) {
        List<Vec3d> res = new ArrayList<>();
        BlockPos centerBP = BlockPos.ofFloored(currentPos);

        // 1. 平面移动
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue;
                BlockPos targetBase = centerBP.add(x, 0, z);

                // 严格对角线检查
                if (x != 0 && z != 0) {
                    if (!collisionHelper.isStrictDiagonalSafe(centerBP, targetBase)) continue;
                }

                Vec3d validFloor = findValidGround(currentPos, targetBase);
                if (validFloor != null) res.add(validFloor);
            }
        }

        // 2. 垂直飞行 (V-Clip)
        if (airPath) {
            Vec3d up = currentPos.add(0, 1.0, 0);
            Vec3d down = currentPos.add(0, -1.0, 0);
            if (collisionHelper.isSafe(up)) res.add(up);
            if (collisionHelper.isSafe(down)) res.add(down);
        }
        return res;
    }

    private Vec3d findValidGround(Vec3d currentPos, BlockPos targetXZ) {
        int[] yOffsets = {0, 1, -1};
        for (int yOffset : yOffsets) {
            BlockPos targetBlock = targetXZ.up(yOffset);
            double floorHeight = collisionHelper.getFloorHeight(targetBlock);
            
            // 强制 +0.5 居中
            double destX = targetBlock.getX() + 0.5;
            double destY = targetBlock.getY() + floorHeight;
            double destZ = targetBlock.getZ() + 0.5;
            
            Vec3d candidate = new Vec3d(destX, destY, destZ);

            // 胖子安全检查
            if (!collisionHelper.isSafe(candidate)) continue;

            // 高度差检查
            double heightDiff = destY - currentPos.y;
            if (heightDiff > 1.25 || heightDiff < -2.0) continue;

            if (collisionHelper.canRaycast(currentPos, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private List<Vec3d> buildPath(Node node) {
        if (node == null) return new ArrayList<>();
        List<Vec3d> path = new ArrayList<>();
        while (node != null) {
            path.add(node.pos);
            node = node.parent;
        }
        Collections.reverse(path);
        return simplify(path);
    }

    private List<Vec3d> simplify(List<Vec3d> path) {
        if (path.size() <= 2) return path;
        List<Vec3d> simple = new ArrayList<>();
        simple.add(path.get(0));
        Vec3d last = path.get(0);
        for (int i = 1; i < path.size() - 1; i++) {
            Vec3d next = path.get(i + 1);
            if (!collisionHelper.canSweep(last, next)) {
                simple.add(path.get(i));
                last = path.get(i);
            }
        }
        simple.add(path.get(path.size() - 1));
        return simple;
    }

    private static class Node {
        Vec3d pos; Node parent; double g, f;
        Node(Vec3d p, Node pr, double g, double f) { this.pos = p; this.parent = pr; this.g = g; this.f = f; }
    }
    private static class NodeKey {
        private final int x, yKey, z;
        public NodeKey(Vec3d v) {
            this.x = (int) Math.floor(v.x);
            this.yKey = (int) Math.round(v.y * 4); // Y轴精度保留
            this.z = (int) Math.floor(v.z);
        }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            NodeKey k = (NodeKey) o;
            return x == k.x && yKey == k.yKey && z == k.z;
        }
        @Override public int hashCode() { return Objects.hash(x, yKey, z); }
    }
}