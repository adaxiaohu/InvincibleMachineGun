package com.codigohasta.addon.smarttpaura.pathfinding;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import static meteordevelopment.meteorclient.MeteorClient.mc;

import java.util.HashMap;
import java.util.Map;

public class DynamicObstacleDetector {
    private final World world;
    private final Map<Integer, MovingEntity> trackedEntities = new HashMap<>();
    
    public DynamicObstacleDetector(World world) {
        this.world = world;
    }
    
    public void update() {
        trackedEntities.clear();
        if (mc.world == null) return;
        
        // 修正：在 1.21.4 中直接遍历 world.getEntities()
        for (Entity entity : mc.world.getEntities()) {
            if (shouldTrack(entity)) {
                trackedEntities.put(entity.getId(), new MovingEntity(entity));
            }
        }
    }
    
    public boolean isPositionBlocked(BlockPos pos, double timeAhead) {
        for (MovingEntity entity : trackedEntities.values()) {
            if (entity.willBeAt(pos, timeAhead)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean shouldTrack(Entity entity) {
        if (entity == null) return false;
        if (entity.isRemoved()) return false;
        
        if (entity instanceof net.minecraft.entity.player.PlayerEntity) return true;
        if (entity instanceof net.minecraft.entity.mob.HostileEntity) return true;
        if (entity instanceof net.minecraft.entity.projectile.ProjectileEntity) return true;
        if (entity.getBoundingBox().getAverageSideLength() > 0.5) return true;
        return false;
    }
    
    public void shutdown() {
        trackedEntities.clear();
    }
    
    // 内部类
    static class MovingEntity {
        final Entity entity;
        final Box boundingBox;
        
        MovingEntity(Entity entity) {
            this.entity = entity;
            this.boundingBox = entity.getBoundingBox();
        }
        
        boolean willBeAt(BlockPos pos, double timeAhead) {
            // 简单预测：当前位置 + 速度 * 时间
            Vec3d predictedPos = entity.getPos()
                .add(entity.getVelocity().multiply(timeAhead));
            
            Box predictedBox = boundingBox.offset(
                predictedPos.subtract(entity.getPos())
            );
            
            return predictedBox.contains(
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5
            );
        }
    }
}