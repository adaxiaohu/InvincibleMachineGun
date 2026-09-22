package com.codigohasta.addon.utils.slimefunhelper.events;

import java.util.*;
import com.codigohasta.addon.utils.slimefunhelper.events.impl.EventContainer;
public class CustomChannels {
    private final Map<Class<?>,Channel<?>> channels=new HashMap<>();
    @SuppressWarnings("unchecked") public <T> Channel<EventContainer<T>> getChannel(Class<T> key){return (Channel<EventContainer<T>>)channels.computeIfAbsent(key,k->new Channel<>());}
    public <T> void broadcast(EventContainer<T> event){getChannel(event.type()).fire(event);}
}
