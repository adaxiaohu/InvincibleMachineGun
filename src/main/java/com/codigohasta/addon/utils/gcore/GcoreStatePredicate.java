package com.codigohasta.addon.utils.gcore;

import net.minecraft.block.BlockState;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;

/**
 * 方块状态判定，替代 Kotlin 的
 * {@code Function3<BlockState, FluidState, BlockPos, Boolean>}。
 *
 * <p>GCore 的爆炸伤害计算用它来判断「这一格是否阻挡射线」。
 * 默认实现是 {@code !state.isAir()}（见 {@code GcoreExplosionUtils}）。
 *
 * <p>注意源的调用点传的 {@code fluidState} 实际是 {@code null}
 * （源自己的字节码里就是 {@code fluidState$iv = null}），
 * 所以实现方不应依赖该参数非空。
 */
@FunctionalInterface
public interface GcoreStatePredicate {
    boolean test(BlockState state, FluidState fluid, BlockPos pos);
}
