package com.codigohasta.addon.utils.slimefunhelper.events.catchers;

import java.util.function.Predicate;
import com.codigohasta.addon.utils.slimefunhelper.events.Event;
import net.minecraft.network.packet.Packet;

public class PacketCatcherImpl<T extends Packet<?>> extends AbstractTypedPacketCatcher<T> {
    private final com.codigohasta.addon.utils.slimefunhelper.hacks.PlacementTasks.Scope owner = com.codigohasta.addon.utils.slimefunhelper.hacks.PlacementTasks.capture();
    Predicate<Event<T>> predicate;

    public PacketCatcherImpl(Class<T> packetClass, Predicate<Event<T>> predicate) {
        super(packetClass);
        this.predicate = predicate;
    }

    @Override
    public boolean onEvent(Event<T> packet) {
        if (!com.codigohasta.addon.utils.slimefunhelper.hacks.PlacementTasks.valid(owner)) return true;
        return predicate.test(packet);
    }
}
