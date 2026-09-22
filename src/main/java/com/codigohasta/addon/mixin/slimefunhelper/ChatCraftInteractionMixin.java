package com.codigohasta.addon.mixin.slimefunhelper;
import com.codigohasta.addon.modules.slimefunhelper.FastCraft;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.recipe.NetworkRecipeId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientPlayerInteractionManager.class)
public abstract class ChatCraftInteractionMixin {
    @Inject(method="clickRecipe",at=@At("HEAD"))
    private void sfimg$recipe(int syncId,NetworkRecipeId recipe,boolean craftAll,CallbackInfo ci){
        if(FastCraft.INSTANCE!=null)FastCraft.INSTANCE.onRecipeClicked(recipe);
    }
}
