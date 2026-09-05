package com.codigohasta.addon.mixin;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Particle.class)
public interface ParticleVelocityAccessor {
    @Accessor("xd")
    void setVelocityX(double velocityX);

    @Accessor("yd")
    void setVelocityY(double velocityY);

    @Accessor("zd")
    void setVelocityZ(double velocityZ);
}
