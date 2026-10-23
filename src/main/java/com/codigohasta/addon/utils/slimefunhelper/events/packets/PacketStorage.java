package com.codigohasta.addon.utils.slimefunhelper.events.packets;

import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.PacketType;

/** Ported from upstream 1.1.7.2 {@code me.matl114.events.packets.PacketStorage} (interface). */
public interface PacketStorage {
    long timestampMS();

    PacketType<?> packetType();

    NetworkSide side();

    void send();

    void handle();
}
