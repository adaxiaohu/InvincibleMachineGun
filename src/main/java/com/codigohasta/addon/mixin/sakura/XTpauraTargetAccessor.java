package com.codigohasta.addon.mixin.sakura;

import com.codigohasta.addon.modules.XTpaura;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = XTpaura.class, remap = false)
public interface XTpauraTargetAccessor {
    @Accessor(value = "target", remap = false)
    Entity img$target();
}
