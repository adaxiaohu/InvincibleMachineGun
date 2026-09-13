package com.codigohasta.addon.mixin;

import com.codigohasta.addon.events.TotemParticleEvent;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.particle.TotemParticle;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.Color;

@Mixin(TotemParticle.class)
public abstract class MixinIMGTotemParticle {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void onTotemParticleInit(ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, SpriteProvider spriteProvider, CallbackInfo ci) {
        TotemParticleEvent event = TotemParticleEvent.get(velocityX, velocityY, velocityZ);
        MeteorClient.EVENT_BUS.post(event);
        if (event.isCancelled()) {
            ((ParticleVelocityAccessor) this).setVelocityX(event.velocityX);
            ((ParticleVelocityAccessor) this).setVelocityY(event.velocityY);
            ((ParticleVelocityAccessor) this).setVelocityZ(event.velocityZ);
            Color color = event.color;
            if (color != null) {
                ((BillboardParticleInvoker) this).invokeSetColor(color.getRed() / 255.0F, color.getGreen() / 255.0F, color.getBlue() / 255.0F);
                ((BillboardParticleInvoker) this).invokeSetAlpha(color.getAlpha() / 255.0F);
            }
        }
    }
}
