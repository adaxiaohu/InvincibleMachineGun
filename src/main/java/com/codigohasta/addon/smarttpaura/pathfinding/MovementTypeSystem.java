package com.codigohasta.addon.smarttpaura.pathfinding;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import static meteordevelopment.meteorclient.MeteorClient.mc;

public class MovementTypeSystem {
    public enum MovementType {
        WALKING, FLYING, CREATIVE_FLY, BOAT, ELYTRA, SWIMMING
    }
    
    private MovementType currentType = MovementType.WALKING;
    private final World world;
    
    public MovementTypeSystem(World world) {
        this.world = world;
        detectCurrentType();
    }
    
    private void detectCurrentType() {
        if (mc.player == null) return;
        
        // 1. 检查创造/飞行
        if (mc.player.getAbilities().flying) {
            currentType = mc.player.getAbilities().creativeMode ? 
                MovementType.CREATIVE_FLY : MovementType.FLYING;
        } 
        // 2. 检查鞘翅 (使用字符串匹配绕过编译错误)
        else if (mc.player.getPose().name().contains("FLYING") || mc.player.getPose().name().contains("GLIDING")) { 
            currentType = MovementType.ELYTRA;
        } 
        // 3. 检查游泳
        else if (mc.player.isTouchingWater()) {
            currentType = MovementType.SWIMMING;
        } 
        // 4. 默认步行
        else {
            currentType = MovementType.WALKING;
        }
    }
    
    public boolean isPassable(BlockPos pos) {
        if (world == null) return false;
        
        switch (currentType) {
            case CREATIVE_FLY:
            case FLYING:
                return world.isAir(pos); // 飞行只需要当前格子是空气
            case ELYTRA:
                return world.isAir(pos) && world.isAir(pos.up()); // 鞘翅需要两格
            case WALKING:
                // 步行：脚下有方块，头顶两格是空气
                return world.isAir(pos) && world.isAir(pos.up()) && !world.isAir(pos.down());
            default:
                return world.isAir(pos);
        }
    }
}