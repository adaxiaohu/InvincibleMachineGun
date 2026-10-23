package com.codigohasta.addon.utils.gcore;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class GcoreMathUtilsKt {

    private GcoreMathUtilsKt() {
    }

    /** Box 中心的 X。 */
    public static double getX(Box box) {
        return MathHelper.lerp(0.5, box.minX, box.maxX);
    }

    public static double getY(Box box) {
        return box.minY;
    }

    /** Box 中心的 Z。 */
    public static double getZ(Box box) {
        return MathHelper.lerp(0.5, box.minZ, box.maxZ);
    }

    /** Box 的体积。 */
    public static double getSize(Box box) {
        return (box.maxX - box.minX) * (box.maxY - box.minY) * (box.maxZ - box.minZ);
    }

    /**
     * 把 Box 平移到以 {@code pos} 为中心的位置。
     *
     * <p>等价于 Kotlin 的 {@code box.position(pos)}。
     */
    public static Box position(Box box, Vec3d pos) {
        return position(box, pos.x, pos.y, pos.z);
    }

    public static Box position(Box box, double x, double y, double z) {
        return box.offset(
            x - MathHelper.lerp(0.5, box.minX, box.maxX),
            y - box.minY,
            z - MathHelper.lerp(0.5, box.minZ, box.maxZ));
    }

    /** 该坐标是否为空气。 */
    public static boolean isAir(BlockPos pos) {
        ClientWorld world = MinecraftClient.getInstance().world;
        return world != null && world.isAir(pos);
    }

    /** 取该坐标的方块状态。 */
    public static BlockState getBlockState(BlockPos pos) {
        return MinecraftClient.getInstance().world.getBlockState(pos);
    }

    /** 取该坐标的流体状态。 */
    public static FluidState getFluidState(BlockPos pos) {
        return MinecraftClient.getInstance().world.getFluidState(pos);
    }

    /** 方块坐标的 1×1×1 盒子。 */
    public static Box getBox(BlockPos pos) {
        return new Box(
            pos.getX(), pos.getY(), pos.getZ(),
            pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
    }
}
