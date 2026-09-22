package com.codigohasta.addon.mixin.slimefunhelper;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(LivingEntity.class)
public interface StepHeightEntityAccessor {
    @Invoker("getJumpVelocity") float sf$getJumpVelocity(float strength);
}
