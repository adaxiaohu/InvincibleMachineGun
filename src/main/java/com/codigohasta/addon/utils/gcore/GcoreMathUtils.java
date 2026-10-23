package com.codigohasta.addon.utils.gcore;

/**
 * 数学遍历工具。
 *
 * <p>移植自 {@code dev.mumy.utils.math.MathUtils}（GCore）。
 * 源是 Kotlin `object`，Java 里改为静态方法。
 *
 * <p>TP 锚路径上只有 {@code GcoreExplosionUtils.exposureAmount} 用到
 * {@link #forEachPoint}（爆炸采样点遍历）。
 */
public final class GcoreMathUtils {

    private GcoreMathUtils() {
    }

    /**
     * 在 [0,1]³ 单位立方体上按给定步长遍历采样点，回调每个 (x, y, z)。
     *
     * <p><b>源码用 double 累加</b>（不是 {@code forEachPointFloat} 的 float 版本），
     * 所以精度保持 double。注意累加写法必须与源一致
     * （{@code xt += xStep}），不要改成乘法索引 —— 浮点累加误差会影响
     * 采样点数量，进而影响爆炸伤害的 exposure 比例。
     *
     * <p>源的回调是 {@code Function3<Double,Double,Double,Unit>}，
     * Java 里用 {@link GcoreTriConsumer}<Double,Double,Double> 表达。
     */
    public static void forEachPoint(double xStep, double yStep, double zStep,
                                    GcoreTriConsumer<Double, Double, Double> block) {
        for (double xt = 0.0; xt <= 1.0; xt += xStep) {
            for (double yt = 0.0; yt <= 1.0; yt += yStep) {
                for (double zt = 0.0; zt <= 1.0; zt += zStep) {
                    block.accept(xt, yt, zt);
                }
            }
        }
    }
}
