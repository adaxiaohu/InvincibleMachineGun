package com.codigohasta.addon.mixin.slimefunhelper;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import com.codigohasta.addon.utils.slimefunhelper.SlimefunRuntime;
import com.codigohasta.addon.utils.slimefunhelper.events.Event;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
@Mixin(PlayerEntity.class)
public abstract class PlayerTravelMixin {
    @Inject(method="travel",at=@At("HEAD"),cancellable=true)
    private void sf$preTravel(Vec3d input,CallbackInfo ci,@Local(argsOnly=true)LocalRef<Vec3d> inputRef){
        if((Object)this instanceof ClientPlayerEntity player){
            var event=new Event<>(input,true,true,player);
            if(!SlimefunRuntime.beforeTravel(player,event))ci.cancel();
            else if(event.context()!=input)inputRef.set(event.context());
        }
    }
    @Inject(method="travel",at=@At("RETURN"))
    private void sf$postTravel(Vec3d input,CallbackInfo ci){if((Object)this instanceof ClientPlayerEntity player)SlimefunRuntime.afterTravel(player,input);}
}
