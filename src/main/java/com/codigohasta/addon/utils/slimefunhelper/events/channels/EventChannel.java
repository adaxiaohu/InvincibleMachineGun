package com.codigohasta.addon.utils.slimefunhelper.events.channels;

import com.codigohasta.addon.utils.slimefunhelper.events.Event;

/** Ported from upstream 1.1.7.2 {@code me.matl114.events.channels.EventChannel}. */
public class EventChannel<T> extends ListenerPoint<Event<T>> {
    private static final Object[] VALUES = new Object[0];

    public void broadcast(T val) {
        broadcast(val, VALUES);
    }

    public void broadcast(T val, Object... val2) {
        if (!isEmpty()) {
            handleValue(new Event<>(val, false, false, val2));
        }
    }

    public boolean fireEvent(T val) {
        if (isEmpty()) {
            return true;
        } else {
            Event<T> event = new Event<>(val, true, false);
            handleValue(event);
            if (event.isCancelled()) {
                return false;
            }
            return true;
        }
    }
}
