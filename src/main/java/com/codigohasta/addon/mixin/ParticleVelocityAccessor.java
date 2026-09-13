package com.codigohasta.addon.mixin;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Particle.class)
public interface ParticleVelocityAccessor {
    @Accessor("velocityX")
    void setVelocityX(double velocityX);

    @Accessor("velocityY")
    void setVelocityY(double velocityY);

    @Accessor("velocityZ")
    void setVelocityZ(double velocityZ);
}
