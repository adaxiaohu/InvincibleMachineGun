package com.codigohasta.addon.utils.gcore;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;

import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * 体素射线遍历（DDA）。
 *
 * <p>移植自 {@code dev.mumy.utils.raytrace.RaytraceUtils}（GCore）。
 *
 * <p><b>★ 为什么单独成类而不是照抄 {@code inline fun}</b>：
 * 源里 {@code raytrace} 是 Kotlin 的 <b>{@code inline fun}</b>，
 * 被 {@code AimUtil.checkCanSee} <b>内联展开</b>成 134 行
 * （反编译产物里全是 {@code $iv$iv} 后缀的变量）。
 * 这里还原成独立静态方法，与**原始源码结构**一致，也更可读。
 *
 * <p><b>与 MC 原生 {@code world.raycast} 的区别</b>：
 * 源需要一个<b>可自定义的方块状态查询器</b>（{@code blockStateProvider}）——
 * 用来模拟「某个方块已经被放了 / 已经被炸了」这类情况。
 * 原版 {@code raycast} 只能读真实世界，所以源自己写了 DDA 遍历。
 *
 * <h2>源的 4 个默认参数（从字节码逐一还原）</h2>
 * <pre>
 * mutable              = true
 * blockStateProvider   = world::getBlockState      （RaytraceUtils$raytrace$1）
 * fluidStateProvider   = world::getFluidState      （RaytraceUtils$raytrace$2）
 * statePredicate       = (b, f, p) -&gt; b != null &amp;&amp; !b.isAir()   （RaytraceUtils$raytrace$3）
 * resultPredicate      = (r, p) -&gt; r != null                     （RaytraceUtils$raytrace$4）
 * </pre>
 *
 * <p><b>★ 两个默认谓词的字节码证据</b>：
 * <pre>
 * // $raytrace$3（statePredicate）
 *   aload_1 ; dup ; ifnull 25
 *   invokevirtual class_2680.method_26215:()Z    // isAir
 *   ifne 21 ; iconst_1 ; goto 27 ; 21: iconst_0 ; goto 27
 *   25: pop ; iconst_0 ; 27: Boolean.valueOf
 *   =&gt; blockState != null &amp;&amp; !blockState.isAir()
 *
 * // $raytrace$4（resultPredicate）
 *   aload_1 ; ifnull 14 ; iconst_1 ; goto 15 ; 14: iconst_0 ; 15: Boolean.valueOf
 *   =&gt; hitResult != null
 * </pre>
 */
public final class GcoreRaytraceUtils {

    private GcoreRaytraceUtils() {
    }

    /** 源的 {@code statePredicate} 默认值：{@code (b, f, p) -> b != null && !b.isAir()}。 */
    public static final GcoreTriPredicate DEFAULT_STATE_PREDICATE =
        (blockState, fluidState, pos) -> blockState != null && !blockState.isAir();

    /** 源的 {@code resultPredicate} 默认值：{@code (r, p) -> r != null}。 */
    public static final BiPredicate<BlockHitResult, BlockPos> DEFAULT_RESULT_PREDICATE =
        (result, pos) -> result != null;

    /**
     * 源的 {@code blockStateProvider} 默认值：{@code world::getBlockState}。
     */
    public static Function<BlockPos, BlockState> defaultBlockStateProvider() {
        return pos -> MinecraftClient.getInstance().world.getBlockState(pos);
    }

    /**
     * 源的 {@code fluidStateProvider} 默认值：{@code world::getFluidState}。
     */
    public static Function<BlockPos, FluidState> defaultFluidStateProvider() {
        return pos -> MinecraftClient.getInstance().world.getFluidState(pos);
    }

    // ─────────────────────────── 重载（展开源的默认参数） ───────────────────────────

    /** 见 {@link #raytrace(Vec3d, Vec3d, RaycastContext, boolean, Function, Function, GcoreTriPredicate, BiPredicate)}，全默认。 */
    public static BlockHitResult raytrace(Vec3d start, Vec3d end, RaycastContext context) {
        return raytrace(start, end, context, true,
            defaultBlockStateProvider(), defaultFluidStateProvider(),
            DEFAULT_STATE_PREDICATE, DEFAULT_RESULT_PREDICATE);
    }

    /** 见 {@link #raytrace(Vec3d, Vec3d, RaycastContext, boolean, Function, Function, GcoreTriPredicate, BiPredicate)}，指定方块查询器。 */
    public static BlockHitResult raytrace(Vec3d start, Vec3d end, RaycastContext context,
                                          Function<BlockPos, BlockState> blockStateProvider) {
        return raytrace(start, end, context, true,
            blockStateProvider, defaultFluidStateProvider(),
            DEFAULT_STATE_PREDICATE, DEFAULT_RESULT_PREDICATE);
    }

    /**
     * 体素射线遍历。
     *
     * <p>逐行对应源的 {@code RaytraceUtils.raytrace}（源 25-165 行）。
     *
     * <p><b>★ 三个容易抄错的点</b>：
     * <ol>
     *     <li><b>起点微调用 {@code -1.0E-7} 而不是 0</b>：
     *         {@code MathHelper.lerp(-1.0E-7, start, end)} —— 把起点往终点方向
     *         挪一个极小的量，避免起点恰好落在方块边界上时判错。
     *         源对 start 和 end <b>各做一次</b>（方向相反）。</li>
     *     <li><b>{@code delta == 0} 时 {@code step = Double.MAX_VALUE}</b>：
     *         该轴永不前进（因为 {@code progress += MAX_VALUE} 立刻 &gt; 1.0）。</li>
     *     <li><b>循环条件是与 {@code > 1.0} 比较</b>，且 <b>三轴都 &gt; 1.0 才退出</b>：
     *         {@code while (!(px > 1.0) || !(py > 1.0) || !(pz > 1.0))}。</li>
     * </ol>
     *
     * @param mutable 源的 {@code mutable} 参数。为 {@code true} 时复用同一个
     *                {@link BlockPos.Mutable} 对象（省内存）；为 {@code false}
     *                时每次新建 {@link BlockPos}。**默认 true**。
     * @return 命中结果；未命中或谓词判否时返回 {@code null}
     */
    public static BlockHitResult raytrace(Vec3d start, Vec3d end, RaycastContext context,
                                          boolean mutable,
                                          Function<BlockPos, BlockState> blockStateProvider,
                                          Function<BlockPos, FluidState> fluidStateProvider,
                                          GcoreTriPredicate statePredicate,
                                          BiPredicate<BlockHitResult, BlockPos> resultPredicate) {
        ClientWorld world = MinecraftClient.getInstance().world;

        // 源：if (start == end) → null（引用比较，Kotlin 的 == 对 data class 是 equals，
        // 但 Vec3d 是普通类，== 即引用比较；源反编译为 if_acmpne，见字节码）
        if (start == end) {
            return null;
        }

        // ★ 起点微调（-1.0E-7），start 与 end 各一次、方向相反
        double startX = MathHelper.lerp(-1.0E-7, start.x, end.x);
        double startY = MathHelper.lerp(-1.0E-7, start.y, end.y);
        double startZ = MathHelper.lerp(-1.0E-7, start.z, end.z);
        double endX = MathHelper.lerp(-1.0E-7, end.x, start.x);
        double endY = MathHelper.lerp(-1.0E-7, end.y, start.y);
        double endZ = MathHelper.lerp(-1.0E-7, end.z, start.z);

        int currentX = MathHelper.floor(startX);
        int currentY = MathHelper.floor(startY);
        int currentZ = MathHelper.floor(startZ);

        BlockPos.Mutable mutablePos = new BlockPos.Mutable(currentX, currentY, currentZ);

        // ── 首个格子：先判一次（源在进循环前就检查起点格） ──
        BlockPos pos = mutablePos;
        BlockState blockState = blockStateProvider.apply(mutablePos);
        FluidState fluidState = fluidStateProvider.apply(mutablePos);
        Boolean shouldCheck = statePredicate.test(blockState, fluidState, pos);
        if (shouldCheck == null) {
            return null;
        }
        if (shouldCheck) {
            BlockHitResult hit = clip(world, context, start, end, pos, blockState, fluidState);
            if (resultPredicate.test(hit, pos)) {
                return hit;
            }
        }

        // ── DDA 步进 ──
        double deltaX = endX - startX;
        double deltaY = endY - startY;
        double deltaZ = endZ - startZ;
        int signX = (int) Math.signum(deltaX);
        int signY = (int) Math.signum(deltaY);
        int signZ = (int) Math.signum(deltaZ);
        double stepX = (signX == 0) ? Double.MAX_VALUE : (double) signX / deltaX;
        double stepY = (signY == 0) ? Double.MAX_VALUE : (double) signY / deltaY;
        double stepZ = (signZ == 0) ? Double.MAX_VALUE : (double) signZ / deltaZ;
        double progressX = stepX * (signX > 0 ? 1.0 - MathHelper.fractionalPart(startX) : MathHelper.fractionalPart(startX));
        double progressY = stepY * (signY > 0 ? 1.0 - MathHelper.fractionalPart(startY) : MathHelper.fractionalPart(startY));
        double progressZ = stepZ * (signZ > 0 ? 1.0 - MathHelper.fractionalPart(startZ) : MathHelper.fractionalPart(startZ));

        while (!(progressX > 1.0) || !(progressY > 1.0) || !(progressZ > 1.0)) {
            if (progressX < progressY) {
                if (progressX < progressZ) {
                    currentX += signX;
                    progressX += stepX;
                } else {
                    currentZ += signZ;
                    progressZ += stepZ;
                }
            } else if (progressY < progressZ) {
                currentY += signY;
                progressY += stepY;
            } else {
                currentZ += signZ;
                progressZ += stepZ;
            }

            mutablePos.set(currentX, currentY, currentZ);
            BlockPos posX = mutable ? mutablePos : new BlockPos(currentX, currentY, currentZ);
            BlockState blockStateX = blockStateProvider.apply(posX);
            FluidState fluidStateX = fluidStateProvider.apply(posX);
            shouldCheck = statePredicate.test(blockStateX, fluidStateX, posX);
            if (shouldCheck == null) {
                return null;
            }
            if (shouldCheck) {
                BlockHitResult hit = clip(world, context, start, end, posX, blockStateX, fluidStateX);
                if (resultPredicate.test(hit, posX)) {
                    return hit;
                }
            }
        }

        return null;
    }

    /**
     * 单格命中：分别对<b>方块形状</b>与<b>流体形状</b>求交，取更近的那个。
     *
     * <p>逐行对应源里重复两次的那段（源 58-83 / 129-154 行）。
     *
     * <pre>
     * blockHit = world.clipWithInteractionOverride(start, end, pos,
     *                context.getBlockShape(blockState, world, pos), blockState)
     * fluidHit = context.getFluidShape(fluidState, world, pos).clip(start, end, pos)
     * // 取「start 到 blockHit.location」与「end 到 fluidHit.location」更近者
     * return (blockHit 距离 &lt;= fluidHit 距离) ? blockHit : fluidHit
     * </pre>
     *
     * <p><b>★ 注意源的比较不对称</b>（字节码已核，非笔误）：
     * 方块侧用 {@code start.distanceToSqr(blockHit.getLocation())}，
     * 流体侧用 <b>{@code end}</b>{@code .distanceToSqr(fluidHit.getLocation())}。
     * 这是源的真实写法，**照抄不改**。
     */
    private static BlockHitResult clip(ClientWorld world, RaycastContext context,
                                       Vec3d start, Vec3d end, BlockPos pos,
                                       BlockState blockState, FluidState fluidState) {
        BlockHitResult blockHit = null;
        if (blockState != null) {
            VoxelShape blockShape = context.getBlockShape(blockState, world, pos);
            blockHit = world.raycastBlock(start, end, pos, blockShape, blockState);
        }

        BlockHitResult fluidHit = null;
        if (fluidState != null) {
            VoxelShape fluidShape = context.getFluidShape(fluidState, world, pos);
            fluidHit = fluidShape.raycast(start, end, pos);
        }

        double blockDist = (blockHit != null) ? start.squaredDistanceTo(blockHit.getPos()) : Double.MAX_VALUE;
        double fluidDist = (fluidHit != null) ? end.squaredDistanceTo(fluidHit.getPos()) : Double.MAX_VALUE;

        return (blockDist <= fluidDist) ? blockHit : fluidHit;
    }
}
