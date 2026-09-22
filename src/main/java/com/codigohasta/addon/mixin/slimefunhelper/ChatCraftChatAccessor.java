package com.codigohasta.addon.mixin.slimefunhelper;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(ChatScreen.class)
public interface ChatCraftChatAccessor {
    @Accessor("chatField") TextFieldWidget sfimg$getChatField();
}
