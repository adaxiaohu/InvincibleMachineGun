package com.codigohasta.addon.utils.slimefunhelper.events.catchers;

import com.codigohasta.addon.utils.slimefunhelper.events.Event;

public interface PacketCatcher {
    boolean catchEvent(Event<?> packet);
}
