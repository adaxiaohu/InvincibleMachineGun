package com.codigohasta.addon.utils.slimefunhelper.events.packets;

import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.PacketType;
public class PacketStorage {
    private final Packet<?> packet;
    private final long timestamp;
    public PacketStorage(Packet<?> packet) { this(packet, System.currentTimeMillis()); }
    public PacketStorage(Packet<?> packet, long timestamp) { this.packet = packet; this.timestamp = timestamp; }
    public Packet<?> packet() { return packet; }
    public long timestampMS() { return timestamp; }
    public PacketType<?> packetType() { return packet.getPacketType(); }
}
