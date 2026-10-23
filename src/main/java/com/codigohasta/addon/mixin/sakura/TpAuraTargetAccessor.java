package com.codigohasta.addon.mixin.sakura;

import com.codigohasta.addon.modules.TpAura;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = TpAura.class, remap = false)
public interface TpAuraTargetAccessor {
    @Accessor(value = "currentTarget", remap = false)
    Entity img$currentTarget();
}
