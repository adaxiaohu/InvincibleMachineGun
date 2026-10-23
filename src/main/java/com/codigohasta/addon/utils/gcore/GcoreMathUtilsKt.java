package com.codigohasta.addon.utils.gcore;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Box / BlockPos 扩展工具。
 *
 * <p>移植自 {@code dev.mumy.utils.math.MathUtilsKt}（GCore）。
 * 源是 Kotlin 扩展函数（`Box.position(...)`），Java 里改为**静态方法**，
 * 第一个参数是原来的接收者 `Box`。
 *
 * <p>TP 锚实际用到的只有 {@link #position(Box, double, double, double)}
 * （`util.position()` 调用点），其余方法一并移植以保持闭包完整。
 */
public final class GcoreMathUtilsKt {

    private GcoreMathUtilsKt() {
    }

    /** Box 中心的 X。 */
    public static double getX(Box box) {
        return MathHelper.lerp(0.5, box.minX, box.maxX);
    }

    /** Box 的 Y（顶还是底取决于坐标轴方向，源就是这么写的）。 */
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

    /**
     * 把 Box 平移到以 {@code (x, y, z)} 为中心的位置。
     *
     * <p>X 与 Z 用「中心对齐」（减去 Box 中心），Y 用「底部对齐」（减去 minY）。
     * 这是源实现的语义，不要改。
     */
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
