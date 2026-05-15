package com.codigohasta.addon.utils.alien;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public class AlienPlayerUtil {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static boolean isInWeb(PlayerEntity player) {
        if (mc.world == null) return false;
        for (float x : new float[]{0.0F, 0.3F, -0.3F}) {
            for (float z : new float[]{0.0F, 0.3F, -0.3F}) {
                for (int y : new int[]{-1, 0, 1, 2}) {
                    BlockPos pos = BlockPos.ofFloored(player.getX() + (double) x, player.getY(), player.getZ() + (double) z).up(y);
                    Box box = new Box(pos);
                    if (box.intersects(player.getBoundingBox()) && mc.world.getBlockState(pos).getBlock() == Blocks.COBWEB) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
