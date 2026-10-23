package com.codigohasta.addon.utils.gcore;

import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.function.Function;

/**
 * 视线 / 命中判定。
 *
 * <p>移植自 {@code dev.godden.core.Utils.AimUtil}（GCore）。
 *
 * <p><b>裁剪说明</b>：源类 191 行、3 个方法，TP 水晶路径**只用 2 个**：
 * <ul>
 *     <li>{@link #checkCanHit} —— 能否打到（双射程：直接命中 / 穿墙命中）</li>
 *     <li>{@link #checkCanSee} —— 两点之间有无方块遮挡（DDA 射线）</li>
 * </ul>
 * 源里的 {@code getRotationFromAToB}（算 yaw/pitch）**本路径不用**，
 * 按「按需裁剪」原则不移植。
 *
 * <h2>为什么「双射程」</h2>
 * <pre>
 * 能看见目标（无遮挡） -&gt; 用 hitRange          判定
 * 看不见（有方块挡着） -&gt; 用 throughWallRange  判定
 * </pre>
 * 后者通常更小 —— 意思是「隔着墙也能打，但要求站得更近」。
 * 两个阈值都可配（模块里的 {@code HitRange} / {@code ThroughWallRange}）。
 */
public final class GcoreAimUtil {

    /** 源的默认眼高（{@code attackerEyeHeight} 默认参数）。 */
    public static final double DEFAULT_EYE_HEIGHT = 1.62;

    private GcoreAimUtil() {
    }

    // ─────────────────────────── checkCanHit ───────────────────────────

    /**
     * 见 {@link #checkCanHit(Vec3d, double, Vec3d, double, double, double, Function)}，用默认眼高与方块查询器。
     */
    public static boolean checkCanHit(Vec3d attackerVec, Vec3d victimVec, double victimEyeHeight,
                                      double hitRange, double throughWallRange) {
        return checkCanHit(attackerVec, DEFAULT_EYE_HEIGHT, victimVec, victimEyeHeight,
            hitRange, throughWallRange, GcoreRaytraceUtils.defaultBlockStateProvider());
    }

    /**
     * 见 {@link #checkCanHit(Vec3d, double, Vec3d, double, double, double, Function)}，
     * 用默认方块查询器（{@code world::getBlockState}）。
     */
    public static boolean checkCanHit(Vec3d attackerVec, double attackerEyeHeight,
                                      Vec3d victimVec, double victimEyeHeight,
                                      double hitRange, double throughWallRange) {
        return checkCanHit(attackerVec, attackerEyeHeight, victimVec, victimEyeHeight,
            hitRange, throughWallRange, GcoreRaytraceUtils.defaultBlockStateProvider());
    }

    /**
     * 能否打到目标。
     *
     * <p>逐行对应源的 {@code AimUtil.checkCanHit}（源 36-55 行）。
     *
     * <pre>
     * sqDis = attackerVec.squaredDistanceTo(victimVec)
     * if (sqDis &gt;= hitRange²) return false;                       // 超出最大射程，直接否
     * attackerEyeVec = attackerVec + (0, attackerEyeHeight, 0)      // 眼睛
     * victimEyeVec   = victimVec   + (0, victimEyeHeight,   0)      // 目标眼睛
     * return checkCanSee(attackerEyeVec, victimEyeVec, provider)
     *          ? sqDis &lt; hitRange²                                 // 看得见 → 用 hitRange
     *          : sqDis &lt; throughWallRange²;                        // 看不见 → 用 throughWallRange
     * </pre>
     *
     * <p><b>★ 注意两个比较符不一样</b>（字节码已核）：
     * 入口的早退是 {@code >=}（等于也算超程），
     * 返回时是严格 {@code <}。照抄不改。
     *
     * <p><b>字节码证据</b>（{@code AimUtil.checkCanHit} 偏移 89-128）：
     * <pre>
     * 89: checkCanSee → istore 20
     * 96: ifeq 115            // canSee == false 走穿墙分支
     * 99: dload 12 ; dload 14 ; dcmpg ; ifge 111 ; iconst_1 ...   // sqDis &lt; sqHitRange
     * 115: dload 12 ; dload 16 ; dcmpg ; ifge 127 ; iconst_1 ...  // sqDis &lt; sqThroughWallRange
     * </pre>
     */
    public static boolean checkCanHit(Vec3d attackerVec, double attackerEyeHeight,
                                      Vec3d victimVec, double victimEyeHeight,
                                      double hitRange, double throughWallRange,
                                      Function<BlockPos, BlockState> blockStateProvider) {
        double sqDis = attackerVec.squaredDistanceTo(victimVec);
        double sqHitRange = hitRange * hitRange;
        double sqThroughWallRange = throughWallRange * throughWallRange;

        if (sqDis >= sqHitRange) {
            return false;
        }

        Vec3d attackerEyeVec = attackerVec.add(0.0, attackerEyeHeight, 0.0);
        Vec3d victimEyeVec = victimVec.add(0.0, victimEyeHeight, 0.0);

        if (checkCanSee(attackerEyeVec, victimEyeVec, blockStateProvider)) {
            return sqDis < sqHitRange;
        } else {
            return sqDis < sqThroughWallRange;
        }
    }

    // ─────────────────────────── checkCanSee ───────────────────────────

    /** 见 {@link #checkCanSee(Vec3d, Vec3d, Function)}，用默认方块查询器。 */
    public static boolean checkCanSee(Vec3d fromVec, Vec3d toVec) {
        return checkCanSee(fromVec, toVec, GcoreRaytraceUtils.defaultBlockStateProvider());
    }

    /**
     * 两点之间是否**没有**方块遮挡。
     *
     * <p>逐行对应源的 {@code AimUtil.checkCanSee}（源 57-190 行）。
     *
     * <p><b>★ 语义（容易抄反）</b>：返回 {@code true} 表示
     * <b>「能看见」= 射线一路没撞到方块</b>。
     *
     * <p><b>字节码证据</b>（{@code AimUtil.checkCanSee} 尾部 1293-1306）：
     * <pre>
     * 1293: nop
     * 1294: ifnonnull 1301        // hitResult != null ？
     * 1297: iconst_1              //   null     -&gt; 1 (true)
     * 1298: goto 1302
     * 1301: iconst_0              //   非 null  -&gt; 0 (false)
     * 1302: istore 4
     * 1304: iload 4
     * 1306: ireturn
     * </pre>
     * 即 {@code return hitResult == null}。
     *
     * <p><b>★ 源在循环里也有一处提前返回</b>（源 111-113 行）：
     * <pre>
     * if (var103 != null) { return var103 == null; }   // 撞到东西 → 返回 false
     * </pre>
     * 等价于「一旦命中就立刻 false」。本实现通过
     * {@link GcoreRaytraceUtils#raytrace} 返回「命中或 null」再统一判断，
     * **行为完全一致**（raytrace 命中即返回命中点，不会继续走）。
     *
     * <p><b>射线上下文</b>（源 59-64 行，硬编码）：
     * <pre>
     * ShapeType.COLLIDER      // 只对碰撞箱判定
     * FluidHandling.NONE      // 忽略流体
     * ShapeContext.of(player) // 用玩家自己的形状上下文
     * </pre>
     */
    public static boolean checkCanSee(Vec3d fromVec, Vec3d toVec,
                                      Function<BlockPos, BlockState> blockStateProvider) {
        MinecraftClient mc = MinecraftClient.getInstance();

        // 源：new RaytraceUtils.SimpleRaycastContext(COLLIDER, NONE, ShapeContext.of(player))
        RaycastContext context = new GcoreSimpleRaycastContext(
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            ShapeContext.of(mc.player));

        // 源把 RaytraceUtils.raytrace 内联在这里；这里改为调用已移植的等价实现。
        // 默认参数与源一致：statePredicate = blockState != null && !isAir,
        //                   resultPredicate = hit != null,
        //                   fluidStateProvider = world::getFluidState
        BlockHitResult result = GcoreRaytraceUtils.raytrace(
            fromVec, toVec, context, true,
            blockStateProvider,
            GcoreRaytraceUtils.defaultFluidStateProvider(),
            GcoreRaytraceUtils.DEFAULT_STATE_PREDICATE,
            GcoreRaytraceUtils.DEFAULT_RESULT_PREDICATE);

        return result == null;
    }
}
