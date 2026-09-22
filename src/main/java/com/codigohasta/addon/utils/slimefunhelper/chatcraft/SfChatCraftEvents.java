package com.codigohasta.addon.utils.slimefunhelper.chatcraft;
import com.codigohasta.addon.utils.slimefunhelper.events.Channel;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ChatScreen;
/** Dedicated source event channels, so migration does not change shared inventory hooks. */
public final class SfChatCraftEvents {
    public static final Channel<String> CHAT_SEND = new Channel<>();
    public static final Channel<Screen> SCREEN_INIT = new Channel<>();
    public static final Channel<ChatScreen> CHAT_CLOSE = new Channel<>();
}
