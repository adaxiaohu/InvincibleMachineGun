package com.codigohasta.addon.utils.slimefunhelper.events;

import java.util.*;
import net.minecraft.network.packet.Packet;
public class PacketChannels extends Channel<Packet<?>> {
    public Packet<?> routeOutgoing(Packet<?> packet, java.util.function.Predicate<Packet<?>> enqueue, Object... args) {
        // Source ClientConnectionEvents queues before running packet listeners.
        if (packet == null || enqueue.test(packet)) return null;
        var event = fire(packet,args);
        if (event.isCancelled()) return null;
        Packet<?> updated = (Packet<?>) event.context();
        return updated;
    }
    public void broadcast(Packet<?> packet,Object...args) { dispatch(new Event<>(packet,false,false,args)); }
    @SuppressWarnings("unchecked") public void handleValue(Event<?> event) { dispatch((Event<Packet<?>>)event); }
    public boolean preHandle(Packet<?> packet,Object...args) {var event=new Event<Packet<?>>(packet,true,false,args);dispatch(event);return !event.isCancelled();}
    private final Map<Class<?>,Channel<?>> channels=new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<Class<?>,Class<?>> mapped=new java.util.concurrent.ConcurrentHashMap<>();
    private final Channel<Packet<?>> send=new Channel<>(),receive=new Channel<>();
    public Channel<Packet<?>> getPacketSendChannel(){return send;}
    public Channel<Packet<?>> getPacketReceiveChannel(){return receive;}
    @SuppressWarnings("unchecked") public <T extends Packet<?>> Channel<T> getChannel(Class<T> key){return (Channel<T>)channels.computeIfAbsent(key,k->new Channel<>());}
    @Override public Event<Packet<?>> fire(Packet<?> packet,Object...args){
        Event<Packet<?>> event=new Event<>(packet,true,true,args);
        dispatch(event);
        return event;
    }
    @Override @SuppressWarnings({"unchecked","rawtypes"}) public void dispatch(Event<Packet<?>> event){
        // Source PacketEventChannel -> EventChannelDispatcher: direction, root,
        // then the top Packet class of the possibly replaced context.
        var id=event.context().getPacketType();
        if(id!=null)(id.side()==net.minecraft.network.NetworkSide.CLIENTBOUND?receive:send).dispatch(event);
        super.dispatch(event);
        Class<?> type=mapped.computeIfAbsent(event.context().getClass(),key->{
            while(Packet.class.isAssignableFrom(key.getSuperclass()))key=key.getSuperclass();
            return key;
        });
        Channel channel=channels.get(type);
        if(channel!=null)channel.dispatch(event);
    }
}
