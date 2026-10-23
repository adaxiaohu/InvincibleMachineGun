package com.codigohasta.addon.utils.slimefunhelper.events;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Function;
import com.codigohasta.addon.utils.slimefunhelper.events.channels.EventChannel;
import com.codigohasta.addon.utils.slimefunhelper.events.channels.ListenerPoint;
import com.codigohasta.addon.utils.slimefunhelper.events.packets.PacketStorage;
import com.codigohasta.addon.utils.slimefunhelper.events.packets.PacketStorageImpl;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.*;
import net.minecraft.network.packet.c2s.common.*;
import net.minecraft.network.packet.c2s.play.*;
import io.netty.channel.ChannelFutureListener;
import com.codigohasta.addon.utils.slimefunhelper.accessors.access.ClientConnectionAccess;

/**
 * Ordered packet queues and per-packet completion callbacks.
 *
 * <p>Ported from upstream 1.1.7.2 {@code me.matl114.events.PacketManager}: the inbound/outbound
 * queues are now {@link EventChannel}s fired both when a packet arrives and when the queue is
 * flushed, so listeners such as {@code ElytraExtra.onFireworkRemoval} can cancel a packet and
 * re-queue it. IMG's original {@code Channels}-based accessors are kept as aliases so the
 * existing callers (BackTrack / Blink / ElytraJump) keep working.
 */
public final class PacketManager {
    public static void schedulePostSendPacket(Packet<?> post, Packet<?> packet) {
        if (packet == null) return;
        var connection = MinecraftClient.getInstance().getNetworkHandler().getConnection();
        schedulePostCallback(post, com.codigohasta.addon.utils.slimefunhelper.hacks.PlacementTasks.guard(() -> connection.send(packet)));
    }
    static {
        Listener.getPacketPostSendPoint().add(event->afterSend(event.context()),0);
        Listener.getPacketPostScheduleSendPoint().add(event->afterSchedule(event.context()),0);
        Listener.getServerLeavePoint().add(event -> onDisconnect(event), 0);
        Listener.getWorldSwitchPoint().add(event -> onWorldSwitch(event), 0);
    }
    // this queue should be accessed only in event loop
    public static ConcurrentLinkedQueue<PacketStorage> packetQueueIn = new ConcurrentLinkedQueue<>();
    // this queue can be accessed async
    public static final ConcurrentLinkedQueue<PacketStorage> packetQueueOut = new ConcurrentLinkedQueue<>();

    private static final Channels<PacketStorage> queueEvent=new Channels<>();
    private static final Channel<Void> shutdown=new Channel<>();
    private static final Deque<PacketStorageImpl> inbound=new java.util.concurrent.ConcurrentLinkedDeque<>();
    private static final ThreadLocal<Boolean> flushingIn=ThreadLocal.withInitial(()->false);
    private static volatile long inboundGeneration;
    private static final Map<Packet<?>,List<Runnable>> callbacks=new WeakHashMap<>();
    private static final Map<Packet<?>,List<Runnable>> scheduledCallbacks=new WeakHashMap<>();
    private static final ThreadLocal<Boolean> flushing=ThreadLocal.withInitial(()->false);

    /** IMG alias: the outbound queue channel. */
    public static Channels<PacketStorage> getPacketQueueEvent(){return queueEvent;}
    /** IMG alias: broadcast on shutdown. */
    public static Channel<Void> getQueueShutdownEvent(){return shutdown;}

    // must visit in eventLoop
    public static boolean startFlushIn = false;
    public static boolean startFlushOut = false;

    public static EventChannel<PacketStorage> packetQueueInEvent = new EventChannel<>();

    public static EventChannel<PacketStorage> packetQueueOutEvent = new EventChannel<>();

    public static EventChannel<PacketStorage> getPacketQueueInEvent(){return packetQueueInEvent;}
    public static EventChannel<PacketStorage> getPacketQueueOutEvent(){return packetQueueOutEvent;}

    public static void handleQueueIn(PacketStorage packet) {
        packetQueueIn.add(packet);
    }

    public static void handleQueueOut(PacketStorage packet) {
        packetQueueOut.add(packet);
    }

    public static boolean handleQueueInPacket(Packet<?> packet, ClientConnection connection) {
        // do not handle flushing packets
        if (startFlushIn) {
            return false;
        }
        checkImmediatelyFlush();
        if (connection.getPacketListener() instanceof ClientPlayPacketListener play) {
            if (packet instanceof net.minecraft.network.packet.s2c.common.DisconnectS2CPacket
                    || (packet instanceof net.minecraft.network.packet.s2c.play.HealthUpdateS2CPacket hl && hl.getHealth() <= 0.0)
                    || packet instanceof net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket
                    || packet instanceof net.minecraft.network.packet.s2c.play.EnterReconfigurationS2CPacket) {
                // clear all
                clearAndShutdown();
            } else {
                Event<PacketStorage> queueEvent = new Event<>(
                        new PacketStorageImpl(packet, System.currentTimeMillis(), connection),
                        true,
                        false,
                        connection,
                        true);
                packetQueueInEvent.handleValue(queueEvent);
                if (queueEvent.isCancelled()) {
                    handleQueueIn(queueEvent.context());
                    return true;
                }
            }
        }
        return false;
    }

    public static void clearAndShutdown() {
        shutdown.broadcast(null);
        flushOutBound();
    }

    static boolean immediatelyFlush;

    public static void scheduleImmediateFlush() {
        immediatelyFlush = true;
    }

    private static void checkImmediatelyFlush() {
        if (immediatelyFlush) {
            immediatelyFlush = false;
            flushInBoundInternal(false);
        }
    }

    public static boolean handleQueueOutPacket(Packet<?> packet, ClientConnection connection) {
        if (startFlushOut) {
            return false;
        }
        if (connection.getPacketListener() instanceof ClientPlayPacketListener play) {
            if (packet instanceof AcknowledgeReconfigurationC2SPacket) {
                clearAndShutdown();
            } else {
                Event<PacketStorage> queueEvent = new Event<>(
                        new PacketStorageImpl(packet, System.currentTimeMillis(), connection), true, false, connection);
                packetQueueOutEvent.handleValue(queueEvent);
                if (queueEvent.isCancelled()) {
                    handleQueueOut(queueEvent.context());
                    return true;
                }
            }
        }
        return false;
    }

    private static void flushInBoundInternal(boolean escapePipeline) {
        if (MinecraftClient.getInstance().getNetworkHandler() != null) {
            Runnable task = () -> {
                try {
                    if (startFlushIn) {
                        return;
                    }
                    if (MinecraftClient.getInstance().getNetworkHandler() != null
                            && MinecraftClient.getInstance().getNetworkHandler().getConnection().isOpen()) {
                        // flush
                        // do not trigger recursive call
                        startFlushIn = true;
                        immediatelyFlush = false;
                        try {
                            var oldQueue = packetQueueIn;
                            packetQueueIn = new ConcurrentLinkedQueue<>();
                            for (var packet : oldQueue) {
                                // abort if need a immediate flush
                                if (immediatelyFlush) {
                                    packetQueueIn.add(packet);
                                    continue;
                                }
                                if (!escapePipeline) {
                                    Event<PacketStorage> queueEvent = new Event<>(
                                            packet,
                                            true,
                                            false,
                                            MinecraftClient.getInstance().getNetworkHandler().getConnection(),
                                            false);
                                    packetQueueInEvent.handleValue(queueEvent);
                                    if (queueEvent.isCancelled()) {
                                        packetQueueIn.add(packet);
                                        continue;
                                    }
                                }
                                packet.handle();
                            }
                        } finally {
                            startFlushIn = false;
                        }
                    } else {
                        packetQueueIn.clear();
                    }
                } catch (Throwable e) {
                    packetQueueIn.clear();
                }
            };
            if (MinecraftClient.getInstance().getNetworkHandler().getConnection().channel.eventLoop().inEventLoop(Thread.currentThread())) {
                task.run();
            } else {
                MinecraftClient.getInstance().getNetworkHandler().getConnection().channel.eventLoop().execute(task);
            }
        } else {
            packetQueueIn.clear();
        }
    }

    public static void flushOutBound() {
        try {
            if (MinecraftClient.getInstance().getNetworkHandler() != null
                    && MinecraftClient.getInstance().getNetworkHandler().getConnection().isOpen()) {
                // flush
                startFlushOut = true;
                try {
                    for (var packet : packetQueueOut) {
                        packet.send();
                    }
                } finally {
                    startFlushOut = false;
                }
            }
        } finally {
            packetQueueOut.clear();
        }
    }

    public static void flushOutBound(Function<PacketStorage, FlushAction> pdd) {
        if (MinecraftClient.getInstance().getNetworkHandler() != null
                && MinecraftClient.getInstance().getNetworkHandler().getConnection().isOpen()) {
            // flush
            startFlushOut = true;
            var iter = packetQueueOut.iterator();
            try {
                while (iter.hasNext()) {
                    var packet = iter.next();
                    switch (pdd.apply(packet)) {
                        case FLUSH -> {
                            packet.send();
                            iter.remove();
                        }
                        case DROP -> {
                            iter.remove();
                        }
                        case QUEUE -> {}
                    }
                }
            } finally {
                startFlushOut = false;
            }
        } else {
            packetQueueOut.removeIf((v) -> pdd.apply(v) != FlushAction.QUEUE);
        }
    }

    public static boolean isAsyncOrNotTransactionC2SPacket(PacketType<?> type){return type==CommonPackets.KEEP_ALIVE_C2S||type==PlayPackets.CHAT_COMMAND_SIGNED||type==PlayPackets.CHAT_COMMAND||type==PlayPackets.CHAT||type==PlayPackets.COMMAND_SUGGESTION;}

    public static boolean isAsyncOrNotTransactionC2SPacket(Packet<?> pkt) {
        if (pkt instanceof KeepAliveC2SPacket
                || pkt instanceof ChatCommandSignedC2SPacket
                || pkt instanceof ChatMessageC2SPacket
                || pkt instanceof CommandExecutionC2SPacket
                || pkt instanceof RequestCommandCompletionsC2SPacket) return true;
        return false;
    }

    public static boolean enqueue(Packet<?> packet){if(!flushing.get()&&queueEvent.getChannel(NetworkSide.SERVERBOUND).fire(new PacketStorage() {
        public long timestampMS(){return System.currentTimeMillis();}
        public PacketType<?> packetType(){return packet.getPacketType();}
        public NetworkSide side(){return NetworkSide.SERVERBOUND;}
        public void send(){}
        public void handle(){}
    }).isCancelled()){queue.add(packet);return true;}return false;}

    private static final Deque<Packet<?>> queue=new java.util.concurrent.ConcurrentLinkedDeque<>();

    public static synchronized void clear(){inboundGeneration++;inbound.clear();queue.clear();packetQueueIn.clear();packetQueueOut.clear();callbacks.clear();scheduledCallbacks.clear();Listener.clearPacketCatchers();shutdown.broadcast(null);}

    public static void sendCompat(Packet<?> packet){
        var handler=MinecraftClient.getInstance().getNetworkHandler();
        if(handler==null)return;
        // Source Listener.sendPacketNoEvents writes directly: no Send/Sent events,
        // no player-state update, no queue or completion callbacks.
        sendCompat(handler.getConnection(),packet);
    }

    public static void sendCompat(net.minecraft.network.ClientConnection targetConnection,Packet<?> packet){
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

    public static boolean isAsyncOrNotTransactionS2CPacket(Packet<?> packet){
        return packet instanceof net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket
            || packet instanceof net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket
            || packet instanceof net.minecraft.network.packet.s2c.play.GameMessageS2CPacket
            || packet instanceof net.minecraft.network.packet.s2c.play.CloseScreenS2CPacket
            || packet instanceof net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
    }

    public static boolean isAsyncOrNotTransactionS2CPacket(PacketType<?> pkt) {
        return pkt != null && (pkt == CommonPackets.KEEP_ALIVE_S2C
                || pkt == PlayPackets.PLAYER_CHAT
                || pkt == PlayPackets.SYSTEM_CHAT
                || pkt == PlayPackets.CONTAINER_CLOSE_S2C);
    }

    public static boolean enqueueIncoming(Packet<?> packet,net.minecraft.network.ClientConnection connection){
        return handleQueueInPacket(packet, connection);
    }

    public static void flushInBound(){flushInBoundInternal(false);}

    public static void flushInBound(Function<PacketStorage, FlushAction> filter){
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

    public static boolean isInventoryPacket(Packet<?> pkt) {
        return pkt instanceof ClickSlotC2SPacket || pkt instanceof CloseHandledScreenC2SPacket;
    }

    public static synchronized void schedulePostCallback(Packet<?> p,Runnable action){if(action!=null)callbacks.computeIfAbsent(p,k->new ArrayList<>()).add(action);}

    protected static <W> void registerListener(ListenerPoint<W> listener, Consumer<W> handler) {
        listener.registerHandler(handler);
    }

    public static void onDisconnect(Event<Void> disconnect) {
        clearAndShutdown();
    }

    public static void onWorldSwitch(Event<net.minecraft.world.World> event) {
        clearAndShutdown();
    }
}
