package com.codigohasta.addon.utils.slimefunhelper.events.packets;

import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.PacketType;

public interface PacketStorage {
    long timestampMS();

    PacketType<?> packetType();

    NetworkSide side();

    void send();

    void handle();
}
