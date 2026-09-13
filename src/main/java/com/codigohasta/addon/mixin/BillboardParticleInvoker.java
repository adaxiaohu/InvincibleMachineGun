package com.codigohasta.addon.mixin;

import net.minecraft.client.particle.BillboardParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(BillboardParticle.class)
public interface BillboardParticleInvoker {
    @Invoker("setColor")
    void invokeSetColor(float r, float g, float b);

    @Invoker("setAlpha")
    void invokeSetAlpha(float alpha);
}
