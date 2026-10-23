package com.codigohasta.addon.mixin;

import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * 暴露 {@link ItemCooldownManager} 的私有冷却表与内部 tick 计数。
 *
 * <p><b>为什么需要它</b>：原版 {@code getCooldownProgress(stack, delta)} 只返回
 * <b>比例</b>（1.0 刚进冷却 → 0.0 结束），拿不到剩余时长。而
 * {@code entries} 这个 Map 的值是 {@code Entry(startTick, endTick)} record，
 * 用 {@code endTick - tick} 就是剩余 tick 数 —— 这正是 CooldownsHud 显示
 * {@code mm:ss} 倒计时需要的数据。
 *
 * <p><b>⚠️ 值类型故意写成 {@code Object}</b>：{@code Entry} 是<b>包私有</b>类
 * （{@code final class} 无 public 修饰），外部包不能引用其类型；
 * 读它的字段用 {@link CooldownInstanceAccessor}（CatLean 同款做法）。
 */
@Mixin(ItemCooldownManager.class)
public interface ItemCooldownManagerAccessor {

    /** 冷却表：组 ID → {@code Entry(startTick, endTick)}。 */
    @Accessor("entries")
    Map<Identifier, Object> getEntries();

    /**
     * 内部 tick 计数（{@code update()} 每 tick +1）。
     *
     * <p>必须用它而不是 {@code mc.world.getTime()} 之类的别的时间源 ——
     * {@code Entry} 的 startTick/endTick 就是基于这个计数器的，
     * 换别的时间源会算错剩余时长。
     */
    @Accessor("tick")
    int getTick();
}
