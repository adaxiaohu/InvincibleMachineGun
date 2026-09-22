package com.codigohasta.addon.utils.slimefunhelper.hacks;
import net.minecraft.util.math.*;
import com.codigohasta.addon.modules.slimefunhelper.InteractExtra;
import com.codigohasta.addon.utils.slimefunhelper.utils.MathUtils;
public final class MineTasks {
public static boolean distanceOutOfReach(BlockPos pos1, Vec3d playerPos) {
        if (pos1 == null || playerPos == null) {
            return true;
        }
        return new Box(pos1).squaredMagnitude(playerPos) > MathUtils.s2(InteractExtra.INSTANCE.getBlockReachDistance());
    }
}
