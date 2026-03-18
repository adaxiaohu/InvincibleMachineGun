package com.codigohasta.addon.smarttpaura;

import com.codigohasta.addon.smarttpaura.pathfinding.AStarPathFinder;
import com.codigohasta.addon.smarttpaura.pathfinding.CollisionHelper;
import com.codigohasta.addon.smarttpaura.rendering.PathRenderer;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class SmartTPAuraCore {
    private final AStarPathFinder pathFinder;
    private final CollisionHelper collisionHelper;
    private final PathRenderer pathRenderer = new PathRenderer();
    private final ExecutorService pool = Executors.newFixedThreadPool(1);
    
    private volatile List<Vec3d> currentPath = new ArrayList<>();
    public Vec3d desyncPos = null;
    private final AtomicBoolean isCalculating = new AtomicBoolean(false);
    private final PlayerEntity localPlayer;

    public SmartTPAuraCore(World world, PlayerEntity player) {
        this.localPlayer = player;
        this.pathFinder = new AStarPathFinder(world);
        this.collisionHelper = new CollisionHelper(world);
    }

    /**
     * 计算最佳攻击位，避免卡墙
     */
    public Vec3d getBestAttackPos(Entity target, double reach) {
        Vec3d targetPos = target.getPos();
        if (collisionHelper.isSafe(targetPos)) return targetPos;

        Vec3d playerPos = localPlayer.getPos();
        Vec3d dir = playerPos.subtract(targetPos).normalize();
        
        for (double d = 0.5; d < reach - 0.5; d += 0.5) {
            Vec3d testPos = targetPos.add(dir.multiply(d));
            double floorY = collisionHelper.getFloorHeight(net.minecraft.util.math.BlockPos.ofFloored(testPos));
            Vec3d fixedTestPos = new Vec3d(testPos.x, Math.floor(testPos.y) + floorY, testPos.z);

            if (collisionHelper.isSafe(fixedTestPos)) {
                return fixedTestPos;
            }
        }
        return targetPos;
    }

    /**
     * 模式 A: 针对实体的攻击寻路 (包含起飞逻辑)
     */
    public void updatePathfinding(Vec3d start, Entity target) {
        if (isCalculating.get()) return;
        
        // 1. 计算终点
        Vec3d finalDest = getBestAttackPos(target, 4.0);

        // 2. [强制起飞逻辑]
        List<Vec3d> takeoffPath = new ArrayList<>();
        Vec3d aStarStart = start;

        // 向上探测安全起飞高度
        for (double h = 1.0; h <= 3.0; h += 1.0) {
            Vec3d checkPos = start.add(0, h, 0);
            if (collisionHelper.isSafe(checkPos)) {
                takeoffPath.add(checkPos);
                aStarStart = checkPos; 
            } else {
                break;
            }
        }

        final Vec3d actualStart = aStarStart;
        final List<Vec3d> prefix = new ArrayList<>(takeoffPath);

        isCalculating.set(true);
        // 异步计算 A* (默认步长 9.0)
        CompletableFuture.supplyAsync(() -> pathFinder.findPath(actualStart, finalDest, 9.0), pool)
            .thenAccept(path -> {
                if (path != null) {
                    List<Vec3d> fullPath = new ArrayList<>();
                    fullPath.add(start); // 必须包含原始起点
                    fullPath.addAll(prefix);
                    fullPath.addAll(path);
                    this.currentPath = fullPath;
                }
                isCalculating.set(false);
            });
    }

    /**
     * 模式 B: [新增] 针对坐标的紧急回传寻路 (解决编译报错)
     * 用于 S08 处理：updatePathfinding(Vec3d, Vec3d, Double)
     */
    public void updatePathfinding(Vec3d start, Vec3d targetPos, double maxStep) {
        if (isCalculating.get()) return;

        isCalculating.set(true);
        CompletableFuture.supplyAsync(() -> pathFinder.findPath(start, targetPos, maxStep), pool)
            .thenAccept(path -> {
                if (path != null) {
                    this.currentPath = new ArrayList<>(path);
                }
                isCalculating.set(false);
            });
    }

    /**
     * 提供给 Module 使用的路径压缩方法
     */
    public List<Vec3d> getEfficientPath(double maxStep) {
        return getChunkedFromSnapshot(this.currentPath, maxStep);
    }
    
    /**
     * 贪心步长拆解与路径拉直逻辑
     */
    public List<Vec3d> getChunkedFromSnapshot(List<Vec3d> inputPath, double maxStep) {
        if (inputPath == null || inputPath.isEmpty()) return new ArrayList<>();
        
        List<Vec3d> raw = new ArrayList<>(inputPath);
        List<Vec3d> corners = new ArrayList<>();
        corners.add(raw.get(0));

        // 提取拐点
        for (int i = 1; i < raw.size() - 1; i++) {
            Vec3d prev = raw.get(i - 1);
            Vec3d curr = raw.get(i);
            Vec3d next = raw.get(i + 1);
            Vec3d dir1 = curr.subtract(prev).normalize();
            Vec3d dir2 = next.subtract(curr).normalize();
            if (dir1.distanceTo(dir2) > 0.01) corners.add(curr);
        }
        if (raw.size() > 1) corners.add(raw.get(raw.size() - 1));

        List<Vec3d> finalPath = new ArrayList<>();
        if (corners.isEmpty()) return finalPath;
        
        finalPath.add(corners.get(0));
        int i = 0;
        
        // 贪心合并直线，直到超出步长或撞墙
        while (i < corners.size() - 1) {
            int furthest = i + 1;
            for (int j = i + 1; j < corners.size(); j++) {
                Vec3d p1 = corners.get(i);
                Vec3d p2 = corners.get(j);
                if (p1.distanceTo(p2) > maxStep - 0.05) break;
                if (!collisionHelper.canSweep(p1, p2)) break; 
                furthest = j;
            }
            finalPath.add(corners.get(furthest));
            i = furthest;
        }
        return finalPath;
    }

    public double getFloorHeightAt(Vec3d pos) {
        if (collisionHelper == null) return 0.0;
        return collisionHelper.getFloorHeight(net.minecraft.util.math.BlockPos.ofFloored(pos));
    }
    
    public void setAirPath(boolean b) { pathFinder.setAirPath(b); }
    public List<Vec3d> getCurrentPath() { return new ArrayList<>(currentPath); }
    public void renderFixedSnapshot(Render3DEvent event, List<Vec3d> path, Color pathColor, double step) {
        pathRenderer.renderFixedSnapshot(event, path, pathColor, step, this);
    }
    public void cleanup() { pool.shutdownNow(); }
    public List<Vec3d> getChunkedPath(double maxStep) { return getEfficientPath(maxStep); }
    public void renderPersistent(Render3DEvent event, List<Vec3d> path, Color pathColor, double step) {
        renderFixedSnapshot(event, path, pathColor, step);
    }
}