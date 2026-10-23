package com.codigohasta.addon.mixin.sakura;

import com.codigohasta.addon.modules.TargetStrafe;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = TargetStrafe.class, remap = false)
public interface TargetStrafeTargetAccessor {
    @Accessor(value = "target", remap = false)
    PlayerEntity img$target();
}
