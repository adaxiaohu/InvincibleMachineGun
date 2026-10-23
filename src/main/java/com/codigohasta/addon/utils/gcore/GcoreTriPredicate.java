package com.codigohasta.addon.utils.gcore;

import net.minecraft.block.BlockState;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;

/**
 * 三参数判定，替代 Kotlin 的
 * {@code Function3<BlockState, FluidState, BlockPos, Boolean?>}。
 *
 * <p>与 {@link GcoreStatePredicate} 的区别：<b>本接口允许返回 {@code null}</b>。
 *
 * <p><b>为什么必须允许 null</b>：源的 {@code RaytraceUtils.raytrace} 里
 * {@code statePredicate} 的返回值有<b>三种</b>语义（字节码已核）：
 * <pre>
 * null  -&gt; 直接 return null（中止整条射线，视为「不可见」）
 * true  -&gt; 这一格要检查碰撞（继续算命中点）
 * false -&gt; 跳过这一格（继续步进）
 * </pre>
 * {@code AimUtil.checkCanSee} 正是靠这个 {@code null} 语义来表达
 * 「射线被挡住了」。若把它压成 {@code boolean}，就丢掉了「中止」这一档。
 */
@FunctionalInterface
public interface GcoreTriPredicate {
    Boolean test(BlockState state, FluidState fluid, BlockPos pos);
}
