package com.codigohasta.addon.utils.slimefunhelper.events;

public class Event<T> {
    public T context;
    private boolean cancelled;
    private final Object[] args;
    public final boolean canCancel;
    public final boolean canModifyContext;
    public Event(T context, boolean canCancel, boolean canModifyContext, Object... args){this.context=context;this.canCancel=canCancel;this.canModifyContext=canModifyContext;this.args=args;}
    public T context(){return context;}
    public Event<T> context(T value){if(!canModifyContext)throw new IllegalArgumentException("Can not modify");context=value;return this;}
    public Event<T> cancel(){if(!canCancel)throw new IllegalArgumentException("Can not cancel!");cancelled=true;return this;}
    public boolean isCancelled(){return cancelled;}
    @SuppressWarnings("unchecked") public <A>A getArgs(int i){return (A)args[i];}
    public Object[] extraArgs(){return args;}
}
