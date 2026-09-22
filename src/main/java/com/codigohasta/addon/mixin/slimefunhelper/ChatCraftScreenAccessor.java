package com.codigohasta.addon.mixin.slimefunhelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Selectable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(Screen.class)
public interface ChatCraftScreenAccessor {
    @Invoker("addDrawableChild") <T extends Element & Drawable & Selectable> T sfimg$add(T widget);
    @Invoker("remove") void sfimg$remove(Element widget);
}
