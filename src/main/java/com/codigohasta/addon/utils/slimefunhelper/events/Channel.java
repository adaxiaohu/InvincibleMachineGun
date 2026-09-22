package com.codigohasta.addon.utils.slimefunhelper.events;

import java.util.*;
import java.util.function.Consumer;
public class Channel<T> {
    private record Entry<T>(int priority, Consumer<Event<T>> consumer) {}
    private final List<Entry<T>> listeners=new ArrayList<>();
    public boolean isEmpty(){return listeners.isEmpty();}
    public void add(Consumer<Event<T>> listener,int priority){listeners.add(new Entry<>(priority,listener));listeners.sort(Comparator.comparingInt(Entry::priority));}
    public Event<T> fire(T value,Object...args){Event<T> e=new Event<>(value,true,true,args);dispatch(e);return e;}
    public void dispatch(Event<T> event){for(var l:List.copyOf(listeners))l.consumer.accept(event);}
    public void broadcast(T value,Object... args){dispatch(new Event<>(value,false,false,args));}
}
