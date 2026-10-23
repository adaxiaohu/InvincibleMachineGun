package com.codigohasta.addon.utils.gcore;

/**
 * 三参数回调，替代 Kotlin 的 {@code Function3<A, B, C, Unit>}。
 *
 * <p>GCore 的 {@code ExtensionUtil.forEachXYZ} / {@code forEachAToB} 用它遍历坐标。
 */
@FunctionalInterface
public interface GcoreTriConsumer<A, B, C> {
    void accept(A a, B b, C c);
}
