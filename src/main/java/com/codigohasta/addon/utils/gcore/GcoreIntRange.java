package com.codigohasta.addon.utils.gcore;

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
