package com.codigohasta.addon.mixin.slimefunhelper;

import com.codigohasta.addon.utils.slimefunhelper.SlimefunRuntime;
import com.codigohasta.addon.utils.slimefunhelper.events.Event;
import com.codigohasta.addon.utils.slimefunhelper.events.RenderListener;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemModelManager.class)
public abstract class SpearModelMixin {
    @ModifyExpressionValue(method="update",at=@At(value="INVOKE",target="Lnet/minecraft/item/ItemStack;get(Lnet/minecraft/component/ComponentType;)Ljava/lang/Object;"))
    private Object sf$model(Object original,@Local(argsOnly=true) ItemStack stack){
        if(!SlimefunRuntime.modifying())return original;
        var event=new Event<Identifier>(null,true,true,stack);
        RenderListener.getCustomModelOverride().dispatch(event);
        return !event.isCancelled()&&event.context()!=null ? event.context() : original;
    }
}
