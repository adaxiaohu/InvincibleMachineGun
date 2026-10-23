package com.codigohasta.addon.utils.slimefunhelper.events;

import java.util.*;
import java.util.function.Function;

public class Channels<T> {
    private final Map<Object,Channel<T>> channels=new java.util.concurrent.ConcurrentHashMap<>();
    private final Function<Event<T>,Object> dispatcher;

    public Channels() {
        this.dispatcher = null;
    }

    public Channels(Function<Event<T>,Object> dispatcher) {
        this.dispatcher = dispatcher;
    }

    public Channel<T> getChannel(Object key){return channels.computeIfAbsent(key,k->new Channel<>());}

    /** Routes the event to the channel selected by the dispatcher (or by {@code args(0)}). */
    @SuppressWarnings("unchecked")
    public boolean handleValue(Event<T> event) {
        Object key = dispatcher != null ? dispatcher.apply(event) : event.getArgs(0);
        if (key == null) return true;
        Channel<T> channel = channels.get(key);
        if (channel != null && !channel.isEmpty()) {
            channel.dispatch(event);
        }
        return !event.isCancelled();
    }

    public boolean isEmpty() {
        if (dispatcher == null && channels.isEmpty()) return true;
        for (var c : channels.values()) {
            if (!c.isEmpty()) return false;
        }
        return true;
    }
}
