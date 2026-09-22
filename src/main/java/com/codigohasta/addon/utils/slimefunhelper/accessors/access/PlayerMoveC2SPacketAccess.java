package com.codigohasta.addon.utils.slimefunhelper.accessors.access;

import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
public interface PlayerMoveC2SPacketAccess {
    enum Cause {SET_BACK,PLAYER_MOVEMENT,HACKING_PACKETS,LEGACY_SNAP,TRIGGER_SIMULATION}
    Cause getCause();void setCause(Cause value);
    static PlayerMoveC2SPacketAccess of(PlayerMoveC2SPacket p){return (PlayerMoveC2SPacketAccess)p;}
    static <T extends PlayerMoveC2SPacket>T setCause(T packet,Cause value){of(packet).setCause(value);return packet;}
    static PlayerMoveC2SPacket setCauseFrom(PlayerMoveC2SPacket packet,PlayerMoveC2SPacket previous){return setCause(packet,of(previous).getCause());}
    void setOnGround(boolean value);
    void setPitch(float value);
    void setYaw(float value);
}
