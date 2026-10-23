package com.codigohasta.addon.mixin.sakura;

import com.codigohasta.addon.modules.PistonCrystal;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = PistonCrystal.class, remap = false)
public interface PistonCrystalTargetAccessor {
    @Accessor(value = "target", remap = false)
    PlayerEntity img$target();
}
