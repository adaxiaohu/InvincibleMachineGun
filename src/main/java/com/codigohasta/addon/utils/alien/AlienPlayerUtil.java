package com.codigohasta.addon.utils.alien;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

public class AlienPlayerUtil {
    private static final Minecraft mc = Minecraft.getInstance();

    public static boolean isInWeb(Player player) {
        if (mc.level == null) return false;
        for (float x : new float[]{0.0F, 0.3F, -0.3F}) {
            for (float z : new float[]{0.0F, 0.3F, -0.3F}) {
                for (int y : new int[]{-1, 0, 1, 2}) {
                    BlockPos pos = BlockPos.containing(player.getX() + (double) x, player.getY(), player.getZ() + (double) z).above(y);
                    AABB box = new AABB(pos);
                    if (box.intersects(player.getBoundingBox()) && mc.level.getBlockState(pos).getBlock() == Blocks.COBWEB) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
