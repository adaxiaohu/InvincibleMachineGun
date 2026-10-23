package com.codigohasta.addon.utils.gcore;

import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * 一个「起点终点都是零向量」的射线上下文。
 *
 * <p>移植自 {@code dev.mumy.utils.raytrace.RaytraceUtils.SimpleRaycastContext}（GCore）。
 *
 * <p><b>为什么单独成类而不是整个移植 RaytraceUtils</b>：
 * 源 {@code RaytraceUtils} 有 691 行，但 TP 锚路径**只用它的这个内部类**
 * （{@code GcoreExplosionUtils} 与 {@code GcoreAnchorUtils} 用它构造
 * {@link RaycastContext} 传给 {@code world.raycastBlock}）。
 * 那个 691 行的 {@code raytrace(...)} 方法（自己实现的体素 DDA）在 TP 锚路径上
 * **从未被调用** —— 用的是 MC 原生的 {@code world.raycastBlock}。
 * 按「按需裁剪」原则只保留这个内部类。
 *
 * <p><b>源的行为</b>：构造时把 start/end 都设成 {@code Vec3d.ZERO}，
 * 后续调用方通过 {@code getBlockShape(...)} / {@code getFluidShape(...)}
 * 使用（真正决定「起点终点」的是 {@code world.raycastBlock(start, end, ...)}
 * 的参数，而不是 context 里存的那对）。
 */
public class GcoreSimpleRaycastContext extends RaycastContext {

    public GcoreSimpleRaycastContext(ShapeType shapeType, FluidHandling fluidHandling,
                                     ShapeContext shapeContext) {
        super(Vec3d.ZERO, Vec3d.ZERO, shapeType, fluidHandling, shapeContext);
    }

    public GcoreSimpleRaycastContext(ShapeType shapeType, FluidHandling fluidHandling,
                                     Entity entity) {
        this(shapeType, fluidHandling, ShapeContext.of(entity));
    }
}
