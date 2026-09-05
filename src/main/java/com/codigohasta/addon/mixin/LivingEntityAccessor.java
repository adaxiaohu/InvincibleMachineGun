package com.codigohasta.addon.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("swimAmount")
    float getLeaningPitch();

    @Accessor("swimAmount")
    void setLeaningPitch(float value);

    @Accessor("swimAmountO")
    void setLastLeaningPitch(float value);

    @Accessor("noJumpDelay")
    void setJumpingCooldown(int value);
}
