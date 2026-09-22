package com.codigohasta.addon.mixin.slimefunhelper;
import com.codigohasta.addon.modules.slimefunhelper.ChatExtra;
import com.codigohasta.addon.utils.slimefunhelper.chatcraft.SfChatCraftEvents;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import java.util.Objects;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ChatScreen.class)
public abstract class ChatCraftChatScreenMixin {
    @Shadow protected String originalChatText;
    @Inject(method="removed",at=@At("HEAD"))
    private void sfimg$closed(CallbackInfo ci){SfChatCraftEvents.CHAT_CLOSE.broadcast((ChatScreen)(Object)this);}
    @WrapOperation(
            method = "onChatFieldUpdate",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/gui/screen/ChatInputSuggestor;setWindowActive(Z)V"))
    private void fixChatInputSuggestor(
            ChatInputSuggestor instance,
            boolean windowActive,
            Operation<Void> original,
            @Local(argsOnly = true) String chatText) {
        if (ChatExtra.INSTANCE != null && ChatExtra.INSTANCE.isActive() && ChatExtra.INSTANCE.tabFix.get()) {
            original.call(instance, true);
        } else {
            original.call(instance, windowActive);
        }
    }

    // resize

    // warn: do not cancel normalize, conflict with other mods
    @WrapOperation(
            method = "normalize",
            at = @At(value = "INVOKE", target = "Ljava/lang/String;trim()Ljava/lang/String;"))
    private String cancelTrim(String instance, Operation<String> original) {
        if (ChatExtra.INSTANCE == null || !ChatExtra.INSTANCE.isActive() || !ChatExtra.INSTANCE.escapeChatTrim.get()) {
            return original.call(instance);
        }
        return instance;
    }

    @WrapOperation(
            method = "normalize",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lorg/apache/commons/lang3/StringUtils;normalizeSpace(Ljava/lang/String;)Ljava/lang/String;",
                            remap = false))
    private String cancelNormalize(String actualChar, Operation<String> original) {
        if (ChatExtra.INSTANCE == null || !ChatExtra.INSTANCE.isActive() || !ChatExtra.INSTANCE.escapeNormalize.get()) {
            return original.call(actualChar);
        }
        return actualChar;
    }

    @WrapOperation(
            method = "normalize",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/util/StringHelper;truncateChat(Ljava/lang/String;)Ljava/lang/String;"))
    private String cancelTruncate(String text, Operation<String> original) {
        if (ChatExtra.INSTANCE == null || !ChatExtra.INSTANCE.isActive() || !ChatExtra.INSTANCE.noChathudInputLimit.get()) {
            return original.call(text);
        }
        return text;
    }

}
