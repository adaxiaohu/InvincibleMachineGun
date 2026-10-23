package com.codigohasta.addon.utils.gcore;

/**
 * 整数区间，替代 Kotlin 的 {@code kotlin.ranges.IntRange}。
 *
 * <p>GCore 里 {@code IntRange(start, endInclusive)} 被用在两处：
 * <ul>
 *     <li>{@code GcoreMathUtil.getRandomIntFromRange(range)} —— 用 {@code getStart()}/{@code getStep()}</li>
 *     <li>{@code GcoreEnumType.ItemMode.getFindIntRange(slot)} —— 返回槽位范围</li>
 * </ul>
 *
 * <p><b>为什么名字叫 step 而不是 end</b>：Kotlin 的 {@code IntRange} 继承自
 * {@code IntProgression}，第二个字段语义是「步长方向上的结束值」，
 * 反编译后 getter 是 {@code getStep()}。GCore 的代码直接用
 * {@code getStart()} 和 {@code getStep()}，这里保持同名以免调用点出错。
 *
 * <p>{@code last} 是闭区间结束值（Kotlin 的 {@code IntRange} 是闭区间）。
 */
public final class GcoreIntRange {

    private final int start;
    /** Kotlin 里这个字段同时充当「结束值」和「步长」，GCore 把它当结束值用。 */
    private final int step;

    public GcoreIntRange(int start, int step) {
        this.start = start;
        this.step = step;
    }

    public int getStart() {
        return this.start;
    }

    public int getStep() {
        return this.step;
    }

    /** 闭区间结束值（与 {@link #getStep()} 同义，便于阅读）。 */
    public int getLast() {
        return this.step;
    }

    /** 区间内元素个数（闭区间）。 */
    public int size() {
        return Math.abs(this.step - this.start) + 1;
    }

    @Override
    public String toString() {
        return this.start + ".." + this.step;
    }
}
