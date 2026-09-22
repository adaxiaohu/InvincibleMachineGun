package com.codigohasta.addon.utils.slimefunhelper.accessors.access;

import io.netty.channel.Channel;

public interface ClientConnectionAccess {
    Channel sf$getChannel();
    void sf$replayPacket(io.netty.channel.ChannelHandlerContext context, net.minecraft.network.packet.Packet<?> packet);
    net.minecraft.network.state.NetworkState<?> getOutboundState();
    default void sendByteBuf(io.netty.buffer.ByteBuf buffer) {
        var channel=sf$getChannel();
        if(channel.eventLoop().inEventLoop())channel.writeAndFlush(buffer);
        else channel.eventLoop().execute(()->channel.writeAndFlush(buffer));
    }
}
