package com.codigohasta.addon.utils.slimefunhelper.accessors.access;

import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
public interface PlayerInteractEntityC2SPacketAccess {
    static PlayerInteractEntityC2SPacketAccess of(PlayerInteractEntityC2SPacket p){return (PlayerInteractEntityC2SPacketAccess)p;}
    int getEntityId();boolean isAttack();
void setPlayerSneaking(boolean value);
}