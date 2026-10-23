package com.codigohasta.addon.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.entity.player.ItemCooldownManager$Entry")
public interface CooldownInstanceAccessor {

    /** 冷却结束 tick（{@code Entry.endTick}）。 */
    @Accessor("endTick")
    int getEndTime();
}
