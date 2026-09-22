package com.codigohasta.addon.mixin.slimefunhelper;
import com.codigohasta.addon.modules.slimefunhelper.Flight;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.*;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(LivingEntity.class)
public abstract class FlightWalkSpeedMixin {
    @Inject(method="getAttributeValue", at=@At("HEAD"), cancellable=true)
    private void sf$walkSpeed(RegistryEntry<EntityAttribute> attribute, CallbackInfoReturnable<Double> cir) {
        var flight = Flight.INSTANCE;
        if ((Object)this == MinecraftClient.getInstance().player && flight != null && flight.isActive()
                && attribute == EntityAttributes.MOVEMENT_SPEED && flight.overrideWalkSpeed.get())
            cir.setReturnValue(flight.getOverridingWalkSpeed());
    }
}
