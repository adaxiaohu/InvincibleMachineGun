package com.codigohasta.addon.mixin.slimefunhelper;
import com.codigohasta.addon.utils.slimefunhelper.chatcraft.SfChatCraftEvents;
import com.codigohasta.addon.modules.slimefunhelper.FastCraft;
import com.codigohasta.addon.modules.slimefunhelper.InGuiChatBox;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Screen.class)
public abstract class ChatCraftScreenMixin {
    @Inject(method={"init(II)V", "clearAndInit"},at=@At("RETURN"))
    private void sfimg$initialized(CallbackInfo ci) { SfChatCraftEvents.SCREEN_INIT.broadcast((Screen)(Object)this); }
    @Inject(method="renderWithTooltip",at=@At("HEAD"))
    private void sfimg$position(DrawContext context,int mouseX,int mouseY,float delta,CallbackInfo ci) {
        if(FastCraft.INSTANCE!=null) FastCraft.INSTANCE.positionWidgets((Screen)(Object)this);
        if(InGuiChatBox.INSTANCE!=null) InGuiChatBox.INSTANCE.positionWidget((Screen)(Object)this);
    }
}
