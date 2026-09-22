package com.codigohasta.addon.utils.slimefunhelper.events;

import java.util.*;
import com.codigohasta.addon.utils.slimefunhelper.events.packets.PacketStorage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.*;
import net.minecraft.network.packet.c2s.common.*;
import net.minecraft.network.packet.c2s.play.*;
import io.netty.channel.ChannelFutureListener;
import com.codigohasta.addon.utils.slimefunhelper.accessors.access.ClientConnectionAccess;
/** Ordered outbound queue and per-packet completion callbacks. */
public final class PacketManager {
    public static void schedulePostSendPacket(Packet<?> post, Packet<?> packet) {
        if (packet == null) return;
        var connection = MinecraftClient.getInstance().getNetworkHandler().getConnection();
        schedulePostCallback(post, com.codigohasta.addon.utils.slimefunhelper.hacks.PlacementTasks.guard(() -> connection.send(packet)));
    }
    static {
        Listener.getPacketPostSendPoint().add(event->afterSend(event.context()),0);
        Listener.getPacketPostScheduleSendPoint().add(event->afterSchedule(event.context()),0);
    }
    private static final Channels<PacketStorage> queueEvent=new Channels<>();
    private static final Channel<Void> shutdown=new Channel<>();
    private static final Deque<Packet<?>> queue=new java.util.concurrent.ConcurrentLinkedDeque<>();
    private static final Deque<PacketStorageImpl> inbound=new java.util.concurrent.ConcurrentLinkedDeque<>();
    private static final ThreadLocal<Boolean> flushingIn=ThreadLocal.withInitial(()->false);
    private static volatile long inboundGeneration;
    private static final Map<Packet<?>,List<Runnable>> callbacks=new WeakHashMap<>();
    private static final Map<Packet<?>,List<Runnable>> scheduledCallbacks=new WeakHashMap<>();
    private static final ThreadLocal<Boolean> flushing=ThreadLocal.withInitial(()->false);
    public static Channels<PacketStorage> getPacketQueueEvent(){return queueEvent;}
    public static Channel<Void> getQueueShutdownEvent(){return shutdown;}
    public static boolean isAsyncOrNotTransactionC2SPacket(PacketType<?> type){return type==CommonPackets.KEEP_ALIVE_C2S||type==PlayPackets.CHAT_COMMAND_SIGNED||type==PlayPackets.CHAT_COMMAND||type==PlayPackets.CHAT||type==PlayPackets.COMMAND_SUGGESTION;}
    public static boolean enqueue(Packet<?> packet){if(!flushing.get()&&queueEvent.getChannel(NetworkSide.SERVERBOUND).fire(new PacketStorage(packet)).isCancelled()){queue.add(packet);return true;}return false;}
    public static void flushOutBound(){flushOutBound(p->FlushAction.FLUSH);}
    public static synchronized void clear(){inboundGeneration++;inbound.clear();queue.clear();callbacks.clear();scheduledCallbacks.clear();Listener.clearPacketCatchers();shutdown.broadcast(null);}
    public static void sendBypass(Packet<?> packet){
        var handler=MinecraftClient.getInstance().getNetworkHandler();
        if(handler==null)return;
        // Source Listener.sendPacketNoEvents writes directly: no Send/Sent events,
        // no player-state update, no queue or completion callbacks.
        sendBypass(handler.getConnection(),packet);
    }
    public static void sendBypass(net.minecraft.network.ClientConnection targetConnection,Packet<?> packet){
        targetConnection.submit(connection->{
            var channel=((ClientConnectionAccess)connection).sf$getChannel();
            Runnable write=()->channel.writeAndFlush(packet).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            if(channel.eventLoop().inEventLoop())write.run();else channel.eventLoop().execute(write);
        });
    }
    public static synchronized void schedulePostScheduleCallback(Packet<?> packet,Runnable action){if(action!=null)scheduledCallbacks.computeIfAbsent(packet,p->new ArrayList<>()).add(action);}
    public static synchronized void transferCallbacks(Packet<?> original, Packet<?> replacement) {
        if (original == replacement) return;
        var pending = callbacks.remove(original);
        if (pending != null) callbacks.computeIfAbsent(replacement, p -> new ArrayList<>()).addAll(pending);
        pending=scheduledCallbacks.remove(original);
        if(pending!=null)scheduledCallbacks.computeIfAbsent(replacement,p->new ArrayList<>()).addAll(pending);
    }
    private static void runCallbacks(Map<Packet<?>,List<Runnable>> store,Packet<?> packet){List<Runnable> pending;synchronized(PacketManager.class){pending=store.remove(packet);}if(pending!=null)pending.forEach(Runnable::run);}
    public static void afterSend(Packet<?> packet){runCallbacks(callbacks,packet);}
    public static void afterSchedule(Packet<?> packet){runCallbacks(scheduledCallbacks,packet);}
public enum FlushAction{FLUSH,DROP,QUEUE}
public static final class PacketStorageImpl extends PacketStorage {
    private final net.minecraft.network.ClientConnection connection;
    public PacketStorageImpl(Packet<?> packet,long time,net.minecraft.network.ClientConnection connection){super(packet,time);this.connection=connection;}
    public net.minecraft.network.ClientConnection connection(){return connection;}
    public void handle(){
        var access=(ClientConnectionAccess)connection;
        access.sf$replayPacket(access.sf$getChannel().pipeline().context(connection),packet());
    }
}
public static boolean isAsyncOrNotTransactionS2CPacket(Packet<?> packet){
    return packet instanceof net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket
        || packet instanceof net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket
        || packet instanceof net.minecraft.network.packet.s2c.play.GameMessageS2CPacket
        || packet instanceof net.minecraft.network.packet.s2c.play.CloseScreenS2CPacket
        || packet instanceof net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
}
public static boolean enqueueIncoming(Packet<?> packet,net.minecraft.network.ClientConnection connection){
    if(flushingIn.get()||!com.codigohasta.addon.utils.slimefunhelper.SlimefunRuntime.modifying()
        ||!(connection.getPacketListener() instanceof net.minecraft.network.listener.ClientPlayPacketListener))return false;
    if(packet instanceof net.minecraft.network.packet.s2c.common.DisconnectS2CPacket
        ||packet instanceof net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket
        ||packet instanceof net.minecraft.network.packet.s2c.play.EnterReconfigurationS2CPacket
        ||(packet instanceof net.minecraft.network.packet.s2c.play.HealthUpdateS2CPacket health&&health.getHealth()<=0)){
        shutdown.broadcast(null);flushInBound();return false;
    }
    var storage=new PacketStorageImpl(packet,System.currentTimeMillis(),connection);
    if(queueEvent.getChannel(NetworkSide.CLIENTBOUND).fire(storage).isCancelled()){
        inbound.addLast(storage);return true;
    }
    return false;
}
public static void flushInBound(){flushInBound(packet->FlushAction.FLUSH);}
public static void flushInBound(java.util.function.Function<PacketStorage,FlushAction> filter){
    var first=inbound.peekFirst();
    if(first==null)return;
    var connection=first.connection();
    var channel=((ClientConnectionAccess)connection).sf$getChannel();
    long generation=inboundGeneration;
    Runnable drain=()->{
        if(generation!=inboundGeneration)return;
        boolean previous=flushingIn.get();flushingIn.set(true);
        try{
            for(var iterator=inbound.iterator();iterator.hasNext();){
                if(generation!=inboundGeneration)break;
                var entry=iterator.next();
                var action=filter.apply(entry);
                if(generation!=inboundGeneration)break;
                if(action==FlushAction.QUEUE)continue;
                iterator.remove();
                if(generation==inboundGeneration&&action==FlushAction.FLUSH&&entry.connection()==connection&&connection.isOpen())entry.handle();
            }
        }finally{flushingIn.set(previous);}
    };
    if(channel.eventLoop().inEventLoop())drain.run();else channel.eventLoop().execute(drain);
}
public static boolean isInventoryPacket(PacketType<?> type){return type==PlayPackets.CONTAINER_CLICK||type==PlayPackets.CONTAINER_CLOSE_C2S;}
public static void flushOutBound(java.util.function.Function<PacketStorage,FlushAction> filter){
    var handler=MinecraftClient.getInstance().getNetworkHandler();
    boolean old=flushing.get();flushing.set(true);
    try{Packet<?> packet;while((packet=queue.pollFirst())!=null){
        if(handler!=null&&filter.apply(new PacketStorage(packet))==FlushAction.FLUSH)handler.sendPacket(packet);
        else synchronized(PacketManager.class){callbacks.remove(packet);scheduledCallbacks.remove(packet);}
    }}finally{flushing.set(old);}
}

public static synchronized void schedulePostCallback(Packet<?> p,Runnable action){if(action!=null)callbacks.computeIfAbsent(p,k->new ArrayList<>()).add(action);}
}
