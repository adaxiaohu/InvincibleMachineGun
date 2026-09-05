package com.codigohasta.addon.utils;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.Vec3;

public class MovementUtils {
    // 调整安全步长，可以尝试设置为 4.0 - 6.0 之间
    private static final double MAX_STEP = 6.0;

    /**
     * 安全的分段位移，并在每段位移后插入零位移包进行Tick同步，防止服务器检测到瞬时超速。
     * @param player 玩家实体
     * @param start 起始位置
     * @param end 目标位置
     * @param onGround 移动时的 onGround 状态
     */
    public static void tpMove(LocalPlayer player, Vec3 start, Vec3 end, boolean onGround) {
        if (player == null) return;
        
        Vec3 diff = end.subtract(start);
        double distance = diff.length();
        
        // 步数，确保每一步都不超过 MAX_STEP
        int steps = (int) Math.ceil(distance / MAX_STEP);
        if (steps < 1) steps = 1;

        // 步长向量
        Vec3 stepVector = diff.scale(1.0 / steps);
        
        Vec3 currentPos = start;

        for (int i = 0; i < steps; i++) {
            Vec3 nextPos = currentPos.add(stepVector);

            // 1. 发送位移包到下一跳 (isMoving=true)
            // 修复：添加第五个参数 `isMoving` (设为 true)
            player.connection.send(
                new ServerboundMovePlayerPacket.Pos(
                    nextPos.x(),
                    nextPos.y(),
                    nextPos.z(),
                    onGround,
                    true // 关键修复：isMoving 设为 true
                )
            );
            
            // 2. 关键优化：强制发送零位移包 (强制同步 Tick)
            // 修复：添加第五个参数 `isMoving` (设为 true)
            player.connection.send(
                new ServerboundMovePlayerPacket.Pos(
                    nextPos.x(),
                    nextPos.y(),
                    nextPos.z(),
                    onGround,
                    true // 关键修复：isMoving 设为 true
                )
            );

            currentPos = nextPos;
        }
        
        // 强制更新客户端玩家位置到终点，避免客户端看到画面回弹。
        player.setPos(end.x, end.y, end.z);
    }
}