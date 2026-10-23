package com.codigohasta.addon.utils.gcore;

/**
 * 双参数布尔谓词，替代 Kotlin 的 {@code Function2<A, B, Boolean>}。
 *
 * <p>GCore 的「放置面检查」用它：{@code (pos, dir) -> Boolean}。
 */
@FunctionalInterface
public interface GcoreBiPredicate<A, B> {
    boolean test(A a, B b);
}
