package com.codigohasta.addon.mixin.slimefunhelper;
import com.codigohasta.addon.modules.slimefunhelper.Flight;
import net.minecraft.entity.player.PlayerAbilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(PlayerAbilities.class)
public abstract class FlightAbilitiesMixin {
    @Inject(method="getFlySpeed", at=@At("HEAD"), cancellable=true)
    private void sf$flightSpeed(CallbackInfoReturnable<Float> cir) {
        var flight = Flight.INSTANCE;
        if (flight != null && flight.controlsAbilities((PlayerAbilities)(Object)this) && flight.overrideFlySpeed.get())
            cir.setReturnValue((float)flight.getOverridingFlySpeed());
    }
}
