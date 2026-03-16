package com.codigohasta.addon.smarttpaura.pathfinding;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

public class CollisionHelper {
    private final World world;
    
    // [核心设定] 胖子模式：0.85 宽
    // 玩家实际宽 0.6。设为 0.85 意味着左右各留出 0.125 的安全余量。
    // 这能确保绝对不会蹭到楼梯的侧面（楼梯缺口宽0.5，0.85绝对进不去，会被视为墙）
    private static final double FAT_WIDTH = 0.85; 
    private static final double PLAYER_HEIGHT = 1.8;

    public CollisionHelper(World world) {
        this.world = world;
    }

    public boolean canRaycast(Vec3d start, Vec3d end) {
        return canSweep(start, end);
    }

    public boolean isSafe(Vec3d pos) {
        return isSafeBox(pos.x, pos.y, pos.z);
    }

    public double getFloorHeight(BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        VoxelShape shape = state.getCollisionShape(world, pos);
        if (shape.isEmpty()) return 0.0;
        return shape.getMax(net.minecraft.util.math.Direction.Axis.Y);
    }

    /**
     * 判断该位置是否足够容纳一个 "0.85宽" 的胖子
     */
    private boolean isSafeBox(double x, double y, double z) {
        // 稍微抬高底部 0.01 防止地板误判
        Box box = new Box(
            x - FAT_WIDTH / 2, y + 0.01, z - FAT_WIDTH / 2,
            x + FAT_WIDTH / 2, y + PLAYER_HEIGHT, z + FAT_WIDTH / 2
        );
        return !world.getBlockCollisions(null, box).iterator().hasNext();
    }

    /**
     * 高精度扫掠：使用 0.05 步长 + 胖子宽度
     */
    public boolean canSweep(Vec3d start, Vec3d end) {
        double dist = start.distanceTo(end);
        if (dist < 0.001) return true;

        int steps = (int) Math.ceil(dist / 0.05); // 5cm 极高精度
        Vec3d dir = end.subtract(start).multiply(1.0 / steps);
        
        Vec3d current = start;
        for (int i = 1; i <= steps; i++) {
            current = current.add(dir);
            if (!isSafe(current)) return false;
        }
        return true;
    }

    /**
     * 严格对角线检查
     * 确保转角处不仅要是空的，而且要足够宽敞
     */
    public boolean isStrictDiagonalSafe(BlockPos start, BlockPos end) {
        BlockPos c1 = new BlockPos(start.getX(), start.getY(), end.getZ());
        BlockPos c2 = new BlockPos(end.getX(), start.getY(), start.getZ());
        // 只要转角处有任何碰撞箱，直接禁止斜走
        return !isBlockSolid(c1) && !isBlockSolid(c2);
    }

    private boolean isBlockSolid(BlockPos pos) {
        return !world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
    }
}