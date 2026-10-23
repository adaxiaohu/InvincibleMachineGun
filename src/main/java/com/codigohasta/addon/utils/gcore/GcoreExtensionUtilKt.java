package com.codigohasta.addon.utils.gcore;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Box / BlockPos / BlockState 小工具。
 *
 * <p>移植自 {@code dev.godden.core.Utils.ExtensionUtilKt}（GCore）。
 * Kotlin 扩展函数改为静态方法。
 */
public final class GcoreExtensionUtilKt {

    private GcoreExtensionUtilKt() {
    }

    /** 方块状态的 raw id（等价于 {@code Block.getRawIdFromState}）。 */
    public static int getBlockId(BlockState state) {
        return Block.getRawIdFromState(state);
    }

    /** 该坐标方块的 raw id。 */
    public static int getBlockId(BlockPos pos) {
        return Block.getRawIdFromState(MinecraftClient.getInstance().world.getBlockState(pos));
    }

    /** 方块坐标 + 偏移，得到世界坐标。 */
    public static Vec3d toVec3d(BlockPos pos, double x, double y, double z) {
        return new Vec3d(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
    }

    /** 见 {@link #toVec3d}，默认偏移全 0。 */
    public static Vec3d toVec3d(BlockPos pos) {
        return toVec3d(pos, 0.0, 0.0, 0.0);
    }

    /** 对 Box 的六个边界分别加值，构造新 Box。 */
    public static Box add(Box box, double addToMinX, double addToMinY, double addToMinZ,
                          double addToMaxX, double addToMaxY, double addToMaxZ) {
        return new Box(
            box.minX + addToMinX, box.minY + addToMinY, box.minZ + addToMinZ,
            box.maxX + addToMaxX, box.maxY + addToMaxY, box.maxZ + addToMaxZ);
    }

    /** 两个 Box 逐分量相加（不做 min/max 归一化）。 */
    public static Box plus(Box box, Box other) {
        return new Box(
            box.minX + other.minX, box.minY + other.minY, box.minZ + other.minZ,
            box.maxX + other.maxX, box.maxY + other.maxY, box.maxZ + other.maxZ);
    }
}
