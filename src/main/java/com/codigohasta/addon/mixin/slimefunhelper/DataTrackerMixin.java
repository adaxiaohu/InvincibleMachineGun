package com.codigohasta.addon.mixin.slimefunhelper;

import java.util.ArrayList;
import java.util.List;
import com.codigohasta.addon.utils.slimefunhelper.events.Event;
import com.codigohasta.addon.utils.slimefunhelper.events.Listener;
import com.codigohasta.addon.utils.slimefunhelper.events.impl.MetadataUpdate;
import net.minecraft.entity.Entity;
import net.minecraft.entity.data.DataTracked;
import net.minecraft.entity.data.DataTracker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Ported from upstream 1.1.7.2 {@code me.matl114.mixins.events.DataTrackerEvents}.
 *
 * <p>The payload is a {@link MetadataUpdate} (entity + entry) rather than IMG's previous
 * bare {@code SerializedEntry} + {@code extraArgs(0)} convention.
 */
@Mixin(DataTracker.class)
public abstract class DataTrackerMixin {
    @Shadow
    @Final
    private DataTracked trackedEntity;

    @ModifyVariable(method = "writeUpdatedEntries", at = @At("HEAD"), argsOnly = true)
    private List<DataTracker.SerializedEntry<?>> sf$data(List<DataTracker.SerializedEntry<?>> entries) {
        if (Listener.getEntityTrackDataUpdate().isEmpty()) return entries;
        if (this.trackedEntity instanceof Entity entity) {
            // recreate List to avoid immutableList
            List<DataTracker.SerializedEntry<?>> entryList = new ArrayList<>();
            var iterator = entries.iterator();
            while (iterator.hasNext()) {
                DataTracker.SerializedEntry<?> serializedEntry = iterator.next();
                Event<MetadataUpdate> serializedEntryMutableObject =
                        new Event<>(new MetadataUpdate(entity, serializedEntry), true, false);
                Listener.getEntityTrackDataUpdate().handleValue(serializedEntryMutableObject);
                if (serializedEntryMutableObject.isCancelled()
                        || serializedEntryMutableObject.context().metadata() == null) {
                    // skip current serializedEntry
                    continue;
                } else {
                    entryList.add(serializedEntryMutableObject.context().metadata());
                }
            }
            return entryList;
        }
        return entries;
    }
}
