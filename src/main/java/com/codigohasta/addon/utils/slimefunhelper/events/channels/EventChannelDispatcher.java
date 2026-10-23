package com.codigohasta.addon.utils.slimefunhelper.events.channels;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import com.codigohasta.addon.utils.slimefunhelper.events.Event;

public class EventChannelDispatcher<T> extends EventChannel<T> {
    public Function<Event<T>, ?> dispatcher;

    public Map<?, EventChannel<? extends T>> channels;

    public EventChannelDispatcher(Function<T, ?> dispatcher) {
        this.dispatcher = (event) -> dispatcher.apply(event.context());
        // avoid async events error
        this.channels = new ConcurrentHashMap<>();
    }

    public EventChannelDispatcher(Function<Event<T>, ?> dispatcher, boolean second) {
        this.dispatcher = dispatcher;
        // avoid async events error
        this.channels = new ConcurrentHashMap<>();
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public boolean handleValue(Event<T> express) {
        boolean val = super.handleValue(express);
        Object typeDispatch = dispatcher.apply(express);
        var channel = channels.get(typeDispatch);
        if (channel != null) {
            if (!channel.isEmpty()) {
                channel.handleValue((Event) express);
            }
        }
        return val;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <W extends T> EventChannel<W> getChannel(Object val) {
        return (EventChannel<W>) ((Map) this.channels).computeIfAbsent(val, (v) -> new EventChannel<>());
    }

    public boolean isEmpty() {
        return super.isEmpty() && channels.isEmpty();
    }
}
