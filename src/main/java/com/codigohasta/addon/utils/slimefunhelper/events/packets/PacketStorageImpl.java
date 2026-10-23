package com.codigohasta.addon.utils.slimefunhelper.events.packets;

import com.codigohasta.addon.utils.slimefunhelper.accessors.access.ClientConnectionAccess;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.PacketType;

/** Ported from upstream 1.1.7.2 {@code me.matl114.events.packets.PacketStorageImpl}. */
public record PacketStorageImpl(Packet<?> packet, long timestampMS, ClientConnection connection)
        implements PacketStorage {
    @Override
    public PacketType<?> packetType() {
        return packet.getPacketType();
    }

    @Override
    public NetworkSide side() {
        return packet.getPacketType().side();
    }

    @Override
    public void send() {
        try {
            connection.send(packet);
        } catch (Throwable throwable) {
        }
    }

    @Override
    public void handle() {
        try {
            ClientConnectionAccess.of(connection).handlePacket(packet);
        } catch (Throwable throwable) {
        }
    }
}
