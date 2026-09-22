package com.codigohasta.addon.utils.slimefunhelper.events;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;

/** Source entity channels select by entity type while preserving each listener's payload type. */
public final class EntityChannels {
    private final Map<Object,Channel<?>> channels=new ConcurrentHashMap<>();
    @SuppressWarnings("unchecked")
    public <T extends Entity> Channel<T> getChannel(EntityType<? extends T> type){return (Channel<T>)channels.computeIfAbsent(type,k->new Channel<>());}
    @SuppressWarnings({"unchecked","rawtypes"})
    public void handleValue(Event<? extends Entity> event){((Channel)getChannel(event.context().getType())).dispatch(event);}
}
