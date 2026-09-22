package com.codigohasta.addon.utils.slimefunhelper;

import com.codigohasta.addon.mixin.ModuleTranslationAccessor;
import com.codigohasta.addon.mixin.SettingTranslationAccessor;
import com.codigohasta.addon.utils.slimefunhelper.hacks.api.BaseModule;
import com.codigohasta.addon.utils.slimefunhelper.managers.config.ConfigEnum;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

/** Display-only translations keyed by complete source paths; IDs remain unchanged. */
public final class SfTranslations {
    private static final Map<String, String> CHINESE = load("zh_cn");
    private static final Map<String, String> ENGLISH = load("en_us");
    private static final java.util.List<ChatRule> CHAT_RULES = CHINESE.keySet().stream()
        .filter(key -> key.startsWith("chat.literal.")).map(ChatRule::new).toList();
    private static final Map<BaseModule, String> MODULES = new IdentityHashMap<>();
    private static final Map<Setting<?>, String> SETTINGS = new IdentityHashMap<>();
    private static final Map<SettingGroup, String> GROUPS = new IdentityHashMap<>();
    private static boolean english;

    private SfTranslations() {}

    private static Map<String, String> load(String language) {
        String resource = "/assets/invincible_machine_gun/lang/sfimg_" + language + ".json";
        try (var stream = SfTranslations.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IllegalStateException("Missing " + resource);
            return new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8),
                new TypeToken<Map<String, String>>() {}.getType());
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot load sfIMG translations", exception);
        }
    }

    public static String text(String key, String fallback) {
        return (english ? ENGLISH : CHINESE).getOrDefault(key, fallback);
    }

    /** Translate source feedback without changing styles or entity-name siblings. */
    public static net.minecraft.text.Text chatText(net.minecraft.text.Text original) {
        var result = net.minecraft.text.MutableText.of(original.getContent()).setStyle(original.getStyle());
        if (original.getContent() instanceof net.minecraft.text.PlainTextContent.Literal literal) {
            for (ChatRule rule : CHAT_RULES) {
                var match = rule.pattern.matcher(literal.string());
                if (!match.matches()) continue;
                Object[] arguments = new Object[match.groupCount()];
                for (int i = 0; i < arguments.length; i++) arguments[i] = match.group(i + 1);
                result = net.minecraft.text.Text.literal(text(rule.key, literal.string()).formatted(arguments))
                    .setStyle(original.getStyle());
                break;
            }
        }
        for (var sibling : original.getSiblings()) result.append(chatText(sibling));
        return result;
    }

    private static final class ChatRule {
        final String key;
        final java.util.regex.Pattern pattern;
        ChatRule(String key) {
            this.key = key;
            pattern = java.util.regex.Pattern.compile(java.util.Arrays.stream(key.substring("chat.literal.".length()).split("%s", -1))
                .map(java.util.regex.Pattern::quote).collect(java.util.stream.Collectors.joining("(.*?)")));
        }
    }

    public static void bindModule(BaseModule module, String sourceName) {
        MODULES.put(module, sourceName);
        translateModule(module, sourceName);
    }

    public static void bindSetting(Setting<?> setting, String fullPath) {
        SETTINGS.put(setting, fullPath);
        translateSetting(setting, fullPath);
    }

    public static boolean isSourceSetting(Setting<?> setting) { return SETTINGS.containsKey(setting); }

    public static void bindGroup(SettingGroup group, String fullPath) {
        GROUPS.put(group, fullPath);
    }

    public static String groupTitle(SettingGroup group) {
        String path = GROUPS.get(group);
        return path == null ? group.name : text("group." + path, group.name);
    }

    public static void setEnglish(boolean enabled) {
        english = enabled;
        MODULES.forEach(SfTranslations::translateModule);
        SETTINGS.forEach(SfTranslations::translateSetting);
        MODULES.keySet().forEach(module -> module.settings.invalidate());
        refreshScreens();
    }

    public static void refreshFromModule() {
        var modules = meteordevelopment.meteorclient.systems.modules.Modules.get();
        var toggle = modules == null ? null : modules.get(com.codigohasta.addon.modules.EnglishUITranslation.class);
        setEnglish(toggle != null && toggle.isActive());
        if (modules != null) modules.sortModules();
    }

    private static void refreshScreens() {
        var client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || !(client.currentScreen instanceof meteordevelopment.meteorclient.gui.WidgetScreen screen)) return;
        // The category list caches strings in its widgets. Rebuild after the current input/render pass.
        Runnable previous = screen.taskAfterRender;
        screen.taskAfterRender = () -> {
            if (previous != null) previous.run();
            for (var current = screen; current != null;
                 current = current.parent instanceof meteordevelopment.meteorclient.gui.WidgetScreen parent ? parent : null) {
                if (current instanceof meteordevelopment.meteorclient.gui.screens.ModulesScreen) {
                    current.clear();
                    current.initWidgets();
                } else if (current instanceof meteordevelopment.meteorclient.gui.screens.ModuleScreen) {
                    current.reload();
                }
            }
        };
    }

    private static void translateModule(BaseModule module, String name) {
        if ((Object) module instanceof ModuleTranslationAccessor accessor) {
            accessor.img$setTitle(text("module." + name, name));
            accessor.img$setDescription(text("module." + name + ".description", module.description));
        }
    }

    private static void translateSetting(Setting<?> setting, String path) {
        if ((Object) setting instanceof SettingTranslationAccessor accessor) {
            String base = path;
            String suffix = "";
            if (path.endsWith("-enabled")) {
                base = path.substring(0, path.length() - 8);
                suffix = text("ui.optional-enabled", " (Enabled)");
            } else if (path.endsWith("-value")) {
                base = path.substring(0, path.length() - 6);
                suffix = text("ui.optional-value", " (Value)");
            }
            accessor.img$setTitle(text(base, setting.title) + suffix);
            accessor.img$setDescription(text(base + ".description", text(base, setting.description)));
        }
    }

    /** Called before the host manager's English-only enum handling. */
    public static String enumTitle(Enum<?> value) {
        if (!value.getDeclaringClass().getName().contains(".slimefunhelper.")) return null;
        String key = "enum." + value.getDeclaringClass().getName() + "." + value.name();
        if (value instanceof ConfigEnum config) {
            String sourceKey = "configenum." + config.getConfigEnumType() + "." + value.name().toLowerCase(Locale.ROOT);
            return text(key, text(sourceKey, value.toString()));
        }
        return text(key, value.toString());
    }
}
