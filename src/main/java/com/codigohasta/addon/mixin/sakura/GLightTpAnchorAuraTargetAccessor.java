package com.codigohasta.addon.mixin.sakura;

import com.codigohasta.addon.modules.GLightTpAnchorAura;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = GLightTpAnchorAura.class, remap = false)
public interface GLightTpAnchorAuraTargetAccessor {
    @Accessor(value = "target", remap = false)
    Entity img$target();
}
