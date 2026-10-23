package com.codigohasta.addon.utils.slimefunhelper.events.channels;

/** Ported from upstream 1.1.7.2 {@code me.matl114.events.channels.ListenerPoint}. */
public class ListenerPoint<W> extends EntryPoint<W> {
    @Override
    public boolean handleValue(W express) {
        var iter = this.handlers.iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            if (!entry.test(express)) {
                iter.remove();
            }
        }
        return true;
    }
}
