package com.codigohasta.addon.utils.slimefunhelper.events.catchers;

import com.codigohasta.addon.utils.slimefunhelper.events.Event;
import net.minecraft.network.packet.Packet;

public abstract class AbstractTypedPacketCatcher<T extends Packet<?>> implements PacketCatcher {
    public Class<T> packetClass;

    public AbstractTypedPacketCatcher(Class<T> packetClass) {
        this.packetClass = packetClass;
    }

    @Override
    @SuppressWarnings("unchecked") // packetClass.isInstance validates the context before dispatch.
    public boolean catchEvent(Event<?> packet) {
        if (packetClass.isInstance(packet.context())) {
            return onEvent((Event<T>) packet);
        }
        return false;
    }

    public abstract boolean onEvent(Event<T> packet);
}
