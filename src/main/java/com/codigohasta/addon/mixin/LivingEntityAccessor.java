package com.codigohasta.addon.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("leaningPitch")
    float getLeaningPitch();

    @Accessor("leaningPitch")
    void setLeaningPitch(float value);

    @Accessor("lastLeaningPitch")
    void setLastLeaningPitch(float value);
}
