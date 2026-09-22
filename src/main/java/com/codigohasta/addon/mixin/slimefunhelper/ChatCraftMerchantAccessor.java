package com.codigohasta.addon.mixin.slimefunhelper;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(MerchantScreen.class)
public interface ChatCraftMerchantAccessor {
    @Accessor("selectedIndex") int sfimg$selected();
    @Invoker("syncRecipeIndex") void sfimg$syncRecipe();
}
