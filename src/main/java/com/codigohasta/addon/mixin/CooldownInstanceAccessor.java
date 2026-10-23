package com.codigohasta.addon.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 {@code ItemCooldownManager$Entry} 的 {@code endTick}（冷却结束 tick）。
 *
 * <h2>为什么需要它（连踩两个坑，记录清楚）</h2>
 *
 * <p>{@code Entry} 是 <b>record</b>，而且是<b>包私有</b>类 —— 两者叠加导致
 * 「裸反射」这条路走不通：
 *
 * <ol>
 *   <li><b>包私有</b>：{@code Method.invoke} 会抛 {@code IllegalAccessException}。</li>
 *   <li><b>record 访问器的名字</b>（<b>这才是倒计时恒为 0:00 的真正根因</b>）：
 *       {@code Entry} 的组件在 <b>intermediary（生产运行时）</b>里叫
 *       {@code comp_3083}/{@code comp_3084}，而 {@code startTick}/{@code endTick}
 *       只是 <b>yarn（开发）名</b>。反射用<b>字符串字面量，loom 不会重映射</b>
 *       → {@code getMethod("endTick")} 抛 {@code NoSuchMethodException} → 被 catch
 *       吞掉 → 恒为 0。</li>
 * </ol>
 *
 * <p>映射表原文（{@code tiny 2 0 intermediary named}，第 1 列 intermediary、第 2 列 yarn）：
 * <pre>
 *   c  net/minecraft/class_1796$class_1797  net/minecraft/entity/player/ItemCooldownManager$Entry
 *   f  I  comp_3084  endTick
 *   m  ()I  comp_3084  endTick
 * </pre>
 *
 * <h2>为什么这里写【yarn 名】而不是 intermediary 名</h2>
 *
 * <p>mixin {@code @Accessor} 的价值就在于<b>走 refmap</b>：写 yarn 名，loom 在编译期
 * 把它翻译成 intermediary 并记入 refmap（可在 jar 里核对
 * {@code endTick -> comp_3084}）。若直接写 intermediary 名 {@code comp_3084}，
 * loom 认为无需重映射、<b>不产生 refmap 条目</b> —— 那就失去了可验证性，
 * 出问题时无法从构建产物判断是否生效。
 *
 * <p>{@code targets} 同理写 yarn 名，loom 会把它重映射成
 * {@code net/minecraft/class_1796$class_1797}。
 *
 * <p>（CatLean 的 {@code CooldownInstanceAccessor} 写的是 intermediary 名，
 * 那是它自己的构建流程决定的；本项目用 yarn 名以保留 refmap 验证能力。）
 */
@Mixin(targets = "net.minecraft.entity.player.ItemCooldownManager$Entry")
public interface CooldownInstanceAccessor {

    /** 冷却结束 tick（{@code Entry.endTick}）。 */
    @Accessor("endTick")
    int getEndTime();
}
