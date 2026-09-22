package com.codigohasta.addon.mixin.slimefunhelper;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import com.codigohasta.addon.utils.slimefunhelper.accessors.access.PlayerMoveC2SPacketAccess;
@Mixin(PlayerMoveC2SPacket.class)
public abstract class PlayerMovePacketMixin implements PlayerMoveC2SPacketAccess {
    @Unique private Cause sf$cause;
    public Cause getCause(){return sf$cause;}
    public void setCause(Cause value){sf$cause=value;}
    @Mutable @Accessor("onGround") public abstract void setOnGround(boolean value);
    @Mutable @Accessor("pitch") public abstract void setPitch(float value);
    @Mutable @Accessor("yaw") public abstract void setYaw(float value);
}
