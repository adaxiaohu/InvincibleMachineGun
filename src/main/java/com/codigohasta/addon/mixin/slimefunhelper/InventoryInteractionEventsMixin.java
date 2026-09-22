package com.codigohasta.addon.mixin.slimefunhelper;

import com.codigohasta.addon.utils.slimefunhelper.SlimefunRuntime;
import com.codigohasta.addon.utils.slimefunhelper.events.Event;
import com.codigohasta.addon.utils.slimefunhelper.events.Listener;
import com.codigohasta.addon.utils.slimefunhelper.events.impl.SlotClickAction;
import com.codigohasta.addon.utils.slimefunhelper.events.impl.UseItem;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class InventoryInteractionEventsMixin {
    @Inject(method="clickSlot",at=@At("HEAD"),cancellable=true)
    private void sf$preClick(int syncId,int slotId,int button,SlotActionType type,PlayerEntity player,CallbackInfo ci){
        if(!SlimefunRuntime.modifying())return;
        var event=new Event<>(new SlotClickAction(type,syncId,slotId,button),true,false);
        Listener.getPreClickSlot().dispatch(event);
        if(event.isCancelled())ci.cancel();
    }
    @Inject(method="clickSlot",at=@At("RETURN"))
    private void sf$postClick(int syncId,int slotId,int button,SlotActionType type,PlayerEntity player,CallbackInfo ci){
        if(SlimefunRuntime.modifying())Listener.getPostClickSlot().broadcast(new SlotClickAction(type,syncId,slotId,button));
    }
    @Inject(method="method_41929",at=@At("RETURN"))
    private void sf$postUse(Hand hand,PlayerEntity player,MutableObject<ActionResult> result,int sequence,CallbackInfoReturnable<Packet<?>> cir){
        if(!SlimefunRuntime.modifying())return;
        var event=new Event<>(new UseItem(result.get(),hand),false,true);
        Listener.getPostPlayerUseItem().dispatch(event);
        result.setValue(event.context.actionResult());
    }
}
