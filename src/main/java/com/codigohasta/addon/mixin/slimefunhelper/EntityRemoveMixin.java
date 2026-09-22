package com.codigohasta.addon.mixin.slimefunhelper;

import com.codigohasta.addon.utils.slimefunhelper.SlimefunRuntime;
import com.codigohasta.addon.utils.slimefunhelper.events.Listener;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityRemoveMixin {
    @Inject(method="setRemoved",at=@At("RETURN"))
    private void sf$remove(Entity.RemovalReason reason,CallbackInfo ci) {
        Entity entity=(Entity)(Object)this;
        if(SlimefunRuntime.ready()&&entity.getEntityWorld().isClient())
            Listener.getEntityRemoveListener().handleValue(new com.codigohasta.addon.utils.slimefunhelper.events.Event<>(entity,false,false,reason));
    }
}
