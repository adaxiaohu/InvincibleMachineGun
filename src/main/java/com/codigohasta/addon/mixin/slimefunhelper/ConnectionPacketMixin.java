package com.codigohasta.addon.mixin.slimefunhelper;

import com.codigohasta.addon.utils.slimefunhelper.SlimefunRuntime;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.codigohasta.addon.utils.slimefunhelper.accessors.access.ClientConnectionAccess;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConnection.class)
public abstract class ConnectionPacketMixin implements ClientConnectionAccess {
    @Shadow @Final private NetworkSide side;
    @Shadow private int packetsSentCounter;
    @Accessor("channel") public abstract Channel sf$getChannel();
    @org.spongepowered.asm.mixin.gen.Invoker("channelRead0")
    public abstract void sf$replayPacket(ChannelHandlerContext context, Packet<?> packet);
    @org.spongepowered.asm.mixin.Unique private net.minecraft.network.state.NetworkState<?> sf$outbound;
    public net.minecraft.network.state.NetworkState<?> getOutboundState(){return sf$outbound;}
    @Inject(method="transitionOutbound",at=@At("HEAD"))
    private void sf$outboundState(net.minecraft.network.state.NetworkState<?> state,CallbackInfo ci){sf$outbound=state;}
    @Inject(method="connect(Ljava/lang/String;ILnet/minecraft/network/state/NetworkState;Lnet/minecraft/network/state/NetworkState;Lnet/minecraft/network/listener/ClientPacketListener;Lnet/minecraft/network/packet/c2s/handshake/ConnectionIntent;)V",at=@At("HEAD"))
    private void sf$initialOutbound(String address,int port,net.minecraft.network.state.NetworkState<?> outbound,net.minecraft.network.state.NetworkState<?> inbound,net.minecraft.network.listener.ClientPacketListener listener,net.minecraft.network.packet.c2s.handshake.ConnectionIntent intent,CallbackInfo ci){sf$outbound=outbound;}
    @Override public void sendByteBuf(io.netty.buffer.ByteBuf buffer){
        ++packetsSentCounter;
        ClientConnectionAccess.super.sendByteBuf(buffer);
    }

    // Source ClientConnectionEvents.acceptPacket runs before vanilla enqueues packet handling.
    // Damage confirmations and inventory revisions must already be visible to the next attack.
    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;)V",
            at = @At("HEAD"), cancellable = true, order = -999)
    private void sf$receive(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci,
                            @Local(argsOnly = true) LocalRef<Packet<?>> packetRef) {
        if (side != NetworkSide.CLIENTBOUND) return;
        if (com.codigohasta.addon.utils.slimefunhelper.events.PacketManager.enqueueIncoming(packet, (ClientConnection) (Object) this)) {
            ci.cancel();
            return;
        }
        Packet<?> updated = SlimefunRuntime.acceptIncoming(packet, (ClientConnection) (Object) this);
        if (updated == null) ci.cancel();
        else if (updated != packet) packetRef.set(updated);
    }

    // Same overload and side guard as the source ClientConnectionEvents.
    @WrapMethod(method = "send(Lnet/minecraft/network/packet/Packet;Lio/netty/channel/ChannelFutureListener;Z)V")
    private void sf$route(Packet<?> packet, ChannelFutureListener listener, boolean flush, Operation<Void> original) {
        Packet<?> updated = side == NetworkSide.CLIENTBOUND ? SlimefunRuntime.prepareOutgoing(packet,(ClientConnection)(Object)this) : packet;
        if (updated != null) original.call(updated, listener, flush);
    }

    @Inject(method = "sendImmediately", at = @At("RETURN"))
    private void sf$scheduled(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        if (side == NetworkSide.CLIENTBOUND) SlimefunRuntime.scheduled(packet,(ClientConnection)(Object)this);
    }

    @Inject(method = "sendInternal", at = @At("RETURN"))
    private void sf$sent(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        if (side == NetworkSide.CLIENTBOUND) SlimefunRuntime.sent(packet,(ClientConnection)(Object)this);
    }
}
