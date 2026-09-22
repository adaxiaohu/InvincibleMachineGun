package com.codigohasta.addon.mixin.slimefunhelper;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

import net.minecraft.entity.Entity;
import net.minecraft.entity.data.*;
import java.util.*;
import com.codigohasta.addon.utils.slimefunhelper.events.Listener;
import com.codigohasta.addon.utils.slimefunhelper.SlimefunRuntime;
@Mixin(DataTracker.class)
public abstract class DataTrackerMixin {
    @Shadow @Final private DataTracked trackedEntity;
    @ModifyVariable(method="writeUpdatedEntries",at=@At("HEAD"),argsOnly=true)
    private List<DataTracker.SerializedEntry<?>> sf$data(List<DataTracker.SerializedEntry<?>> entries){
        if(!SlimefunRuntime.ready()||!(trackedEntity instanceof Entity entity))return entries;
        List<DataTracker.SerializedEntry<?>> result=new ArrayList<>();
        for(var entry:entries){var event=Listener.getEntityTrackDataUpdate().getChannel(entity.getType()).fire(entry,entity);if(!event.isCancelled()&&event.context()!=null)result.add(event.context());}return result;
    }
}
