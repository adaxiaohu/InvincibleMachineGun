package com.codigohasta.addon.utils.slimefunhelper.accessors.access;

import io.netty.channel.Channel;

public interface ClientConnectionAccess {
    Channel sf$getChannel();
    void sf$replayPacket(io.netty.channel.ChannelHandlerContext context, net.minecraft.network.packet.Packet<?> packet);
    net.minecraft.network.state.NetworkState<?> getOutboundState();

    /**
     * Ported from upstream 1.1.7.2 ClientConnectionAccess: replays a packet through the
     * connection's inbound pipeline (used by PacketStorageImpl.handle()).
     */
    default void handlePacket(net.minecraft.network.packet.Packet<?> packet) {
        sf$replayPacket(sf$getChannel().pipeline().context("packet_handler"), packet);
    }

    static ClientConnectionAccess of(net.minecraft.network.ClientConnection connection) {
        return (ClientConnectionAccess) connection;
    }

    default void sendByteBuf(io.netty.buffer.ByteBuf buffer) {
        var channel=sf$getChannel();
        if(channel.eventLoop().inEventLoop())channel.writeAndFlush(buffer);
        else channel.eventLoop().execute(()->channel.writeAndFlush(buffer));
    }
}
