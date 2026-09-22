package com.codigohasta.addon.mixin.slimefunhelper;
import com.codigohasta.addon.utils.slimefunhelper.chatcraft.SfChatCraftEvents;
import com.codigohasta.addon.utils.slimefunhelper.events.Event;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import java.util.Objects;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ChatCraftNetworkMixin {
    @Shadow public abstract void sendChatMessage(String message);
    @Shadow public abstract void sendChatCommand(String message);
    @Unique
    boolean escapeSendEvent = false;

    @Inject(method = "sendChatCommand", at = @At("HEAD"), cancellable = true)
    private void onChat0(String command, CallbackInfo ci, @Local(argsOnly = true) LocalRef<String> commandRef) {
        if (escapeSendEvent) {
            escapeSendEvent = false;
            return;
        }
        String chatContent = "/" + command;
        Event<String> value = new Event<>(chatContent, true, true);
        SfChatCraftEvents.CHAT_SEND.dispatch(value);
        if (value.isCancelled()) {
            ci.cancel();
            return;
        }
        String valueChange = value.context();
        if (valueChange == null || valueChange.isEmpty()) {
            ci.cancel();
            return;
        }
        if (!Objects.equals(chatContent, valueChange)) {
            if (valueChange.startsWith("/")) {
                commandRef.set(valueChange.substring(1));
            } else {
                // change a command to a chat message
                ci.cancel();
                escapeSendEvent = true;
                sendChatMessage(valueChange);
            }
        }
    }

    @Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true)
    private void onChat2(String content, CallbackInfo ci, @Local(argsOnly = true) LocalRef<String> contentRef) {
        if (escapeSendEvent) {
            escapeSendEvent = false;
            return;
        }
        Event<String> value = new Event<>(content, true, true);
        SfChatCraftEvents.CHAT_SEND.dispatch(value);
        if (value.isCancelled()) {
            // set null string to trigger ret
            ci.cancel();
            return;
        }
        if (value.context() == null) {
            ci.cancel();
            return;
        }
        String valueChange = value.context();
        if (!Objects.equals(content, valueChange)) {
            if (!valueChange.startsWith("/")) {
                contentRef.set(valueChange);
            } else {
                ci.cancel();
                escapeSendEvent = true;
                sendChatCommand(valueChange.substring(1));
            }
        }
    }

}
