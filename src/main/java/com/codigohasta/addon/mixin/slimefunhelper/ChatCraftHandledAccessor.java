package com.codigohasta.addon.mixin.slimefunhelper;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(HandledScreen.class)
public interface ChatCraftHandledAccessor {
    @Accessor("x") int sfimg$x();
    @Accessor("y") int sfimg$y();
    @Accessor("backgroundWidth") int sfimg$width();
    @Accessor("backgroundHeight") int sfimg$height();
}
