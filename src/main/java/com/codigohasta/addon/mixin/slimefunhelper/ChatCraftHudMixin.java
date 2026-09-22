package com.codigohasta.addon.mixin.slimefunhelper;
import com.codigohasta.addon.modules.slimefunhelper.ChatExtra;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import java.util.List;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ChatHud.class)
public abstract class ChatCraftHudMixin {
    @Shadow @Final private List<ChatHudLine.Visible> visibleMessages;
    @Inject(method="addVisibleMessage",at=@At(value="INVOKE",target="Ljava/util/List;removeLast()Ljava/lang/Object;"),cancellable=true)
    private void sfimg$history(ChatHudLine line,CallbackInfo ci){
        ChatExtra module=ChatExtra.INSTANCE;
        if(module!=null && module.isActive() && module.overrideChatHistoryLength.get()){
            int limit=module.chatHistoryLength.get();
            if(limit>0 && visibleMessages.size()<=limit)ci.cancel();
        }
    }
}
