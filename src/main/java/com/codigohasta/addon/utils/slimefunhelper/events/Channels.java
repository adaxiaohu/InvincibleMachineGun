package com.codigohasta.addon.utils.slimefunhelper.events;

import java.util.*;
public class Channels<T> {
    private final Map<Object,Channel<T>> channels=new java.util.concurrent.ConcurrentHashMap<>();
    public Channel<T> getChannel(Object key){return channels.computeIfAbsent(key,k->new Channel<>());}
}
