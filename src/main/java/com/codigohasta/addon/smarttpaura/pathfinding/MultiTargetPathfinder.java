package com.codigohasta.addon.smarttpaura.pathfinding;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import java.util.List;

public class MultiTargetPathfinder {
    private final World world;
    private final AStarPathFinder pathFinder;
    
    public MultiTargetPathfinder(World world, AStarPathFinder pathFinder) {
        this.world = world;
        this.pathFinder = pathFinder;
    }
    
    public TargetResult findBestTarget(List<LivingEntity> targets, Vec3d startPos) {
        if (targets.isEmpty()) return null;
        
        TargetResult bestResult = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        
        for (LivingEntity target : targets) {
            if (!isValidTarget(target)) continue;
            
            
    List<Vec3d> path = pathFinder.findPath(startPos, target.getPos(), 0.5);
            
            if (path != null && !path.isEmpty()) {
                double score = 1000 - path.size() * 10;
                if (score > bestScore) {
                    bestScore = score;
                    bestResult = new TargetResult(target, path, score);
                }
            }
        }
        return bestResult;
    }
    
    private boolean isValidTarget(LivingEntity target) {
        return target != null && target.isAlive();
    }
    
    public static class TargetResult {
        public final LivingEntity target;
        public final List<Vec3d> path;
        public final double score;
        public TargetResult(LivingEntity t, List<Vec3d> p, double s) {
            this.target = t; this.path = p; this.score = s;
        }
    }
}