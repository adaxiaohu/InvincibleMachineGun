package com.codigohasta.addon.mixin.sakura;

import com.codigohasta.addon.modules.MaceDMGPlus;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = MaceDMGPlus.class, remap = false)
public interface MaceDMGPlusTargetAccessor {
    @Accessor(value = "currentTarget", remap = false)
    Entity img$currentTarget();
}
