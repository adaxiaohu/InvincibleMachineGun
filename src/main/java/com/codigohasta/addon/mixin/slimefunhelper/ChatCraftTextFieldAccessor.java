package com.codigohasta.addon.mixin.slimefunhelper;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(TextFieldWidget.class)
public interface ChatCraftTextFieldAccessor {
    @Accessor("maxLength") int sfimg$maxLength();
}
