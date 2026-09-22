package com.codigohasta.addon.mixin.slimefunhelper;

import com.codigohasta.addon.modules.slimefunhelper.BadPacketsFix;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class BadPacketsPlayerListMixin {
    @Inject(method="onPlayerList",at=@At(value="INVOKE",target="Lorg/slf4j/Logger;warn(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V",remap=false),cancellable=true)
    private void sf$invalidEntry(PlayerListS2CPacket packet, CallbackInfo ci) {
        var module=BadPacketsFix.INSTANCE;
        if(module!=null&&module.isActive()&&module.fixInvalidPlayerEntryUpdate.get())ci.cancel();
    }
}
