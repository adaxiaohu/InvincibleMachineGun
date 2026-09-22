package com.codigohasta.addon.mixin.slimefunhelper;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import com.codigohasta.addon.utils.slimefunhelper.accessors.access.PlayerInteractEntityC2SPacketAccess;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
@Mixin(PlayerInteractEntityC2SPacket.class)
public abstract class InteractEntityPacketMixin implements PlayerInteractEntityC2SPacketAccess {
    @Shadow @Final private int entityId;
    public int getEntityId(){return entityId;}
    public boolean isAttack(){boolean[] attack={false};((PlayerInteractEntityC2SPacket)(Object)this).handle(new PlayerInteractEntityC2SPacket.Handler(){public void interact(Hand hand){}public void interactAt(Hand hand,Vec3d pos){}public void attack(){attack[0]=true;}});return attack[0];}
@org.spongepowered.asm.mixin.gen.Accessor("playerSneaking") public abstract void setPlayerSneaking(boolean value);
}