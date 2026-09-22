package com.codigohasta.addon.mixin.slimefunhelper;

import com.codigohasta.addon.utils.slimefunhelper.SlimefunRuntime;
import com.codigohasta.addon.utils.slimefunhelper.events.Event;
import com.codigohasta.addon.utils.slimefunhelper.events.Listener;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientWorld.class)
public abstract class ClientWorldEventsMixin {
    @WrapOperation(method="tickEntity",at=@At(value="INVOKE",target="Lnet/minecraft/entity/Entity;tick()V"))
    private void sf$tickEntity(Entity entity,Operation<Void> original){
        if(!SlimefunRuntime.modifying()){original.call(entity);return;}
        var event=new Event<>(entity,true,false);
        Listener.getEntityPreTickListener().handleValue(event);
        if(event.isCancelled())return;
        try{original.call(entity);}finally{Listener.getEntityPostTickListener().handleValue(event);}
    }
}
