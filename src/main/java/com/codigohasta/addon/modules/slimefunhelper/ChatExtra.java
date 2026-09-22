package com.codigohasta.addon.modules.slimefunhelper;

import com.google.common.hash.Hashing;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.codigohasta.addon.mixin.slimefunhelper.ChatCraftChatAccessor;
import com.codigohasta.addon.utils.slimefunhelper.chatcraft.SfChatCraftEvents;
import com.codigohasta.addon.utils.slimefunhelper.events.Event;
import com.codigohasta.addon.utils.slimefunhelper.hacks.api.BaseModule;
import com.codigohasta.addon.utils.slimefunhelper.hacks.api.ModulePath;
import com.codigohasta.addon.utils.slimefunhelper.hacks.utils.config.Regex;
import com.codigohasta.addon.utils.slimefunhelper.hacks.utils.config.StringFormat;
import com.codigohasta.addon.utils.slimefunhelper.hooks.BaritoneHooks;
import com.codigohasta.addon.utils.slimefunhelper.managers.Configs;
import com.codigohasta.addon.utils.slimefunhelper.managers.config.FlagRef;
import com.codigohasta.addon.utils.slimefunhelper.managers.config.IntRef;
import com.codigohasta.addon.utils.slimefunhelper.managers.config.NBTRef;

import com.codigohasta.addon.utils.slimefunhelper.utils.ChatUtils;
import com.codigohasta.addon.utils.slimefunhelper.utils.Debug;
import com.codigohasta.addon.utils.slimefunhelper.chatcraft.ChatCraftKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.StringHelper;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.ApiStatus;

public class ChatExtra extends BaseModule {
    public static ChatExtra INSTANCE;

    public ChatExtra() {
        super("ChatExtra");
        INSTANCE = this;
    }

    public final ModulePath chat = makePath("chat", "chat-helper");
    public final ModulePath chatTools = makePath("chat", "chat-screen-tools");

    public final FlagRef noChathudInputLimit =
            flagBuilder(chat.add("ignore-chat-len-limit")).updateListener(value -> refreshInputLimits()).build();

    public final FlagRef escapeChatTrim =
            flagBuilder(chat.add("escape-trim-chat")).build();

    public final FlagRef escapeNormalize =
            flagBuilder(chat.add("escape-normalize-space-chat")).build();

    public final FlagRef checkMessageLength =
            flagBuilder(chat.add("check-chat-len")).build();

    public final IntRef messageLengthLimit =
            intBuilder(chat.add("chat-len-limit")).defaultValue(256).build();

    public final FlagRef checkCommandLength =
            flagBuilder(chat.add("check-command-len")).build();

    public final IntRef commandLengthLimit =
            intBuilder(chat.add("command-len-limit")).defaultValue(32760).build();

    public final NBTRef<String> warnFormat = builder(chat.add("limit-warn-format"), String.class)
            .defaultValue("&c你的输入内容太长了! %d / %d")
            .build();

    public final FlagRef overrideChatHistoryLength =
            flagBuilder(chat.add("override-chat-history-len")).build();

    public final IntRef chatHistoryLength = intBuilder(chat.add("chat-history-len"))
            .defaultValue(100)
            .validator(Configs.INT_NONNEGATIVE)
            .build();

    @ApiStatus.Experimental
    public final FlagRef addToHistoryWhenClose =
            flagBuilder(chat.add("add-to-history-when-close")).build();

    public final FlagRef doNotSendEmptyMessage =
            flagBuilder(chat.add("dont-send-empty-message")).build();

    public final FlagRef tabFix = flagBuilder(chat.add("enable-tab-fix")).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(SfChatCraftEvents.CHAT_SEND, this::onChatScreenSendMessage, Integer.MAX_VALUE - 1);
        registerListener(
                SfChatCraftEvents.SCREEN_INIT, this::onChatScreenInitialized);
        registerListener(SfChatCraftEvents.CHAT_CLOSE, this::onChatScreenClose);
        registerListener(SfChatCraftEvents.CHAT_SEND, this::onChatPasswordEncrypt, -999);
        registerListener(SfChatCraftEvents.CHAT_SEND, this::onStringReplace, Integer.MAX_VALUE - 10);
    }

    private final java.util.Map<TextFieldWidget, Integer> originalInputLimits = new java.util.WeakHashMap<>();

    @Override public void onDisableModule() {
        sampleWidget = null;
        restoreInputLimits();
    }

    private void restoreInputLimits() {
        originalInputLimits.forEach(TextFieldWidget::setMaxLength);
        originalInputLimits.clear();
    }

    private void refreshInputLimits() {
        if (!isActive()) return;
        restoreInputLimits();
        if (mc.currentScreen != null) onChatScreenInitialized(new Event<>(mc.currentScreen, false, false));
    }

    @Override public void onEnableModule() { refreshInputLimits(); }

    private void extendInput(TextFieldWidget widget) {
        originalInputLimits.putIfAbsent(widget,
            ((com.codigohasta.addon.mixin.slimefunhelper.ChatCraftTextFieldAccessor) widget).sfimg$maxLength());
        widget.setMaxLength(32768);
    }

    public void onChatScreenSendMessage(Event<String> stringEvent) {
        // check command empty
        if (doNotSendEmptyMessage.get()) {
            String value = stringEvent.context();
            // ignore meaningless shit, do not addToMessageHistory
            if (value.isEmpty()
                    || Objects.equals(value, "/")
                    || Objects.equals(value, "!!")
                    || Objects.equals(value, "/" + "!!")) {
                stringEvent.cancel();
            }
        }
        // check command length
        String command = stringEvent.context();
        if (command.startsWith("/")) {
            if (checkCommandLength(command)) {
                stringEvent.cancel();
                return;
            }
        } else if (checkMessageLength(command)) {
            stringEvent.cancel();
            return;
        }
    }

    public String normalizeSendText(String sent) {
        if (!isActive()) return StringHelper.truncateChat(StringUtils.normalizeSpace(sent.trim()));
        if (!escapeChatTrim.get()) {
            sent = sent.trim();
        }
        if (!escapeNormalize.get()) {
            sent = StringUtils.normalizeSpace(sent);
        }
        if (!noChathudInputLimit.get()) {
            sent = StringHelper.truncateChat(sent);
        }
        return sent;
    }

    private final NBTRef<Regex> regexLogin = builder(chatTools.add("login-command-pattern"), Regex.class)
            .defaultValue(new Regex("^(/login|/l|/reg|/register|/changepass|/changepassword) (.+)$"))
            .build();

    public final FlagRef encryptPass =
            flagBuilder(chatTools.add("password-encrypt")).build();

    public final IntRef encryptLength = intBuilder(chatTools.add("password-encrypt-length"))
            .defaultValue(20)
            .build();

    public final NBTRef<String> encryptSalt = builder(chatTools.add("password-encrypt-salt"), String.class)
            .defaultValue("")
            .build();

    // add chat screen extra things
    public void onChatScreenInitialized(Event<Screen> screenEvent) {
        if (noChathudInputLimit.get()) {
            if (screenEvent.context() instanceof ChatScreen chat) {
                ChatCraftChatAccessor access = (ChatCraftChatAccessor) chat;

                var chatField = access.sfimg$getChatField();
                extendInput(chatField);

            } else if (screenEvent.context() instanceof AnvilScreen anvilScreen) {
                if (anvilScreen.getFocused() instanceof TextFieldWidget widget) {
                    extendInput(widget);
                }
            }
        }
    }

    public void onChatScreenClose(Event<ChatScreen> chatScreenSave) {
        var chat = chatScreenSave.context();
        if (addToHistoryWhenClose.get()) {
            String chatInput = ((ChatCraftChatAccessor) chat).sfimg$getChatField().getText();
            // ignore two default input
            if (!chatInput.isEmpty() && !Objects.equals("/", chatInput)) {
                if (mc.inGameHud != null) {
                    mc.inGameHud.getChatHud().addToMessageHistory(chatInput);
                }
            }
        }
    }

    private boolean checkCommandLength(String command) {
        if (this.checkCommandLength.get() && command.length() > commandLengthLimit.get()) {
            String val = warnFormat.get();
            if ("&c你的输入内容太长了! %d / %d".equals(val)) val = com.codigohasta.addon.utils.slimefunhelper.SfTranslations.text("ui.chat-extra.length-warning", val);
            if (val != null && !val.isEmpty()) {
                Debug.chat(ChatUtils.stringToText(val.formatted(command.length(), commandLengthLimit.get())));
            }

            return true;
        }
        return false;
    }

    private boolean checkMessageLength(String command) {
        if (this.checkMessageLength.get() && command.length() > messageLengthLimit.get()) {
            String val = warnFormat.get();
            if ("&c你的输入内容太长了! %d / %d".equals(val)) val = com.codigohasta.addon.utils.slimefunhelper.SfTranslations.text("ui.chat-extra.length-warning", val);
            if (val != null && !val.isEmpty()) {
                Debug.chat(ChatUtils.stringToText(val.formatted(command.length(), messageLengthLimit.get())));
            }
            return true;
        }
        return false;
    }

    private TextFieldWidget sampleWidget;

    public boolean onChatObfRender(TextFieldWidget widget, DrawContext context, int x, int y, float partialTicks) {
        String text = widget.getText();
        var matcher = Pattern.compile(regexLogin.get().expression()).matcher(text);
        if (matcher.find() && matcher.groupCount() > 0) {
            String result = matcher.group(1) + " " + com.codigohasta.addon.utils.slimefunhelper.SfTranslations.text("ui.chat-extra.password-hidden", "密码已隐藏");
            if (sampleWidget == null) {
                sampleWidget = new TextFieldWidget(mc.textRenderer, 0, 0, 0, 0, Text.empty());
                {
                    sampleWidget.setDrawsBackground(false);
                    sampleWidget.setFocusUnlocked(false);
                }
            }
            TextFieldWidget newWidget = sampleWidget;
            newWidget.setX(widget.getX());
            newWidget.setY(widget.getY());
            newWidget.setWidth(widget.getWidth());
            newWidget.setHeight(widget.getHeight());
            newWidget.setText(result);
            newWidget.setCursorToEnd(false);
            newWidget.render(context, x, y, partialTicks);
            return true;
        } else {
            return false;
        }
    }

    public void onChatPasswordEncrypt(Event<String> commandChat) {
        if (mc.player != null && encryptPass.get() && !ChatCraftKeys.ctrl()) {
            String command = commandChat.context();
            if (regexLogin.get().test(command)) {
                String playerName = mc.player.getNameForScoreboard();
                String[] splits = command.split(" ");
                for (var i = 1; i < splits.length; i++) {
                    if (!splits[i].startsWith("plain:")) {
                        splits[i] = encryptWithPlayerName(splits[i], playerName);
                    } else {
                        splits[i] = splits[i].substring("plain:".length());
                    }
                }
                command = String.join(" ", splits);
                commandChat.context(command);
            }
        }
    }

    private String encryptWithPlayerName(String str, String playerName) {
        String concatStr = playerName + ":" + encryptSalt.get() + str;
        byte[] bytes =
                Hashing.sha256().hashString(concatStr, StandardCharsets.UTF_8).asBytes();
        BigInteger integer = new BigInteger(1, bytes);
        String hashResult = integer.toString(36);
        StringBuilder builder = new StringBuilder();
        int length = encryptLength.get();
        if (hashResult.length() > length) {
            builder.append(hashResult, 0, length);
        } else {
            int size = hashResult.length();
            builder.append(hashResult);
            while (size < length / 2 - 2) {
                size = 2 * size + 1;
                builder.append("@").append(hashResult);
            }
        }
        return builder.toString();
    }

    public final FlagRef enableFormat =
            flagBuilder(chat.add("enable-chat-message-format")).build();

    public final NBTRef<StringFormat> formatStr = builder(chat.add("chat-message-format-str"), StringFormat.class)
            .defaultValue(new StringFormat(List.of("message", "random+数字"), "{message}喵 | WurstV91 client | {random6}"))
            .build();

    public final NBTRef<Regex> commandEscapeFormatPattern = builder(chat.add("chat-message-escape-format"), Regex.class)
            .defaultValue(new Regex("^()$"))
            .build();
    private final Pattern randomPattern = Pattern.compile("\\{random(\\d+)\\}");

    private final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private final Random rand = new Random();

    private String generateRandomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARS.charAt(rand.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    public String generateFormatString(String string) {
        StringFormat format = formatStr.get();
        String template = format.formatString();
        Matcher m = randomPattern.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            int length = Integer.parseInt(m.group(1));
            String randomStr = generateRandomString(length);
            m.appendReplacement(sb, Matcher.quoteReplacement(randomStr));
        }
        m.appendTail(sb);
        String processedTemplate = sb.toString();

        return format.withFormatString(processedTemplate).format(string);
    }

    public boolean shouldEscapeFormatting(String originString) {
        boolean shouldStop = false;
        shouldStop = shouldStop || commandEscapeFormatPattern.get().test(originString);
        shouldStop = shouldStop || originString.startsWith("/") || originString.startsWith(".");
        if (BaritoneHooks.getInstance().isBaritoneAPISupported()) {
            shouldStop = shouldStop
                    || originString.startsWith(baritone.api.BaritoneAPI.getSettings().prefix.value);
        } else {
            shouldStop = shouldStop || originString.startsWith("#");
        }
        return shouldStop;
    }

    public void onStringReplace(Event<String> chatEvent) {
        if (chatEvent.isCancelled()) return;
        if (enableFormat.get()) {
            String originString = chatEvent.context();
            // remove our commands

            if (!shouldEscapeFormatting(originString)) {
                chatEvent.context(generateFormatString(originString));
            }
        }
    }
}
