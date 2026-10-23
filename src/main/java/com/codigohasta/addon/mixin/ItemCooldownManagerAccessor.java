package com.codigohasta.addon.mixin;

import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

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
