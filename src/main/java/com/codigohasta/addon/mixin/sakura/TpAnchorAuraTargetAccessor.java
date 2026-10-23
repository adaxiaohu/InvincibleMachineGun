package com.codigohasta.addon.mixin.sakura;

import com.codigohasta.addon.modules.TpAnchorAura;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = TpAnchorAura.class, remap = false)
public interface TpAnchorAuraTargetAccessor {
    @Accessor(value = "target", remap = false)
    Entity img$target();
}
