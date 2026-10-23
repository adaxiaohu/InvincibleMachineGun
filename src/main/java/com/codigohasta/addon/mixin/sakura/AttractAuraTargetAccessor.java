package com.codigohasta.addon.mixin.sakura;

import com.codigohasta.addon.modules.AttractAura;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = AttractAura.class, remap = false)
public interface AttractAuraTargetAccessor {
    @Accessor(value = "target", remap = false)
    PlayerEntity img$target();
}
