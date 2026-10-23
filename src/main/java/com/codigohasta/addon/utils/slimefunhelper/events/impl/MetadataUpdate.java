package com.codigohasta.addon.utils.slimefunhelper.events.impl;

import net.minecraft.entity.Entity;
import net.minecraft.entity.data.DataTracker;

/**
 * Ported from upstream 1.1.7.2 {@code me.matl114.events.impl.MetadataUpdate}
 * (Lombok {@code @Getter @Setter @Accessors(fluent=true, chain=true) @AllArgsConstructor}
 * written explicitly).
 */
public class MetadataUpdate {
    final Entity entity;
    DataTracker.SerializedEntry<?> metadata;

    public MetadataUpdate(Entity entity, DataTracker.SerializedEntry<?> metadata) {
        this.entity = entity;
        this.metadata = metadata;
    }

    public Entity entity() { return entity; }
    public DataTracker.SerializedEntry<?> metadata() { return metadata; }
    public MetadataUpdate metadata(DataTracker.SerializedEntry<?> value) { metadata = value; return this; }
}
