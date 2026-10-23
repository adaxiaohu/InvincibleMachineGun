package com.codigohasta.addon.mixin.sakura;

import com.codigohasta.addon.modules.SpearKill;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = SpearKill.class, remap = false)
public interface SpearKillTargetAccessor {
    @Accessor(value = "killtarget", remap = false)
    Entity img$killTarget();
}
