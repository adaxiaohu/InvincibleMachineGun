package com.codigohasta.addon.utils.translation;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.mixin.ModuleTranslationAccessor;
import com.codigohasta.addon.mixin.SettingGroupTranslationAccessor;
import com.codigohasta.addon.mixin.SettingTranslationAccessor;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * JSON-backed display translation for IMG modules.
 *
 * <p>Only display fields (title / description / setting group name) are changed.
 * Module IDs, setting IDs, enum constants, commands and saved profile keys are untouched.</p>
 */
public final class JsonEnglishTranslationManager {
    private static final String RESOURCE = "/assets/invincible_machine_gun/lang/img_en_us.json";
    private static final String ADDON_PACKAGE = "com.codigohasta.addon.";
    private static final Gson GSON = new Gson();

    private static final Map<Module, ModuleText> MODULE_TEXT = new IdentityHashMap<>();
    private static final Map<Setting<?>, SettingText> SETTING_TEXT = new IdentityHashMap<>();
    private static final Map<SettingGroup, String> GROUP_TEXT = new IdentityHashMap<>();

    private static TranslationFile translations;
    private static boolean applied;

    private JsonEnglishTranslationManager() {}

    public static synchronized void apply() {
        if (applied) return;

        TranslationFile file = load();
        if (file == null) {
            AddonTemplate.LOG.error("IMG English translation resource could not be loaded: {}", RESOURCE);
            return;
        }

        for (Module module : Modules.get().getAll()) {
            if (!module.getClass().getName().startsWith(ADDON_PACKAGE)) continue;
            translateModule(module, file);
        }

        Modules.get().sortModules();
        applied = true;
        AddonTemplate.LOG.info("IMG English translation applied ({} module definitions).", safeModules(file).size());
    }

    /** Reapplies translations after Meteor has restored module profiles and GUI state. */
    public static synchronized void refresh() {
        if (applied) restore();
        apply();
    }

    public static synchronized void restore() {
        if (!applied) return;

        for (Map.Entry<Module, ModuleText> entry : MODULE_TEXT.entrySet()) {
            ModuleTranslationAccessor accessor = (ModuleTranslationAccessor) entry.getKey();
            accessor.img$setTitle(entry.getValue().title());
            accessor.img$setDescription(entry.getValue().description());
        }

        for (Map.Entry<Setting<?>, SettingText> entry : SETTING_TEXT.entrySet()) {
            SettingTranslationAccessor accessor = (SettingTranslationAccessor) entry.getKey();
            accessor.img$setTitle(entry.getValue().title());
            accessor.img$setDescription(entry.getValue().description());
        }

        for (Map.Entry<SettingGroup, String> entry : GROUP_TEXT.entrySet()) {
            ((SettingGroupTranslationAccessor) entry.getKey()).img$setName(entry.getValue());
        }

        Modules.get().sortModules();
        for (Module module : MODULE_TEXT.keySet()) module.settings.invalidate();
        applied = false;
        AddonTemplate.LOG.info("IMG English translation restored to source UI text.");
    }

    public static synchronized boolean isApplied() {
        return applied;
    }

    /**
     * Returns the English display text for addon enum values used by Meteor dropdowns.
     * The enum object itself is never changed, so serialization still uses the original constant.
     */
    public static synchronized String displayValue(Object value) {
        if (value == null) return "";

        String original = value.toString();
        if (!applied || !(value instanceof Enum<?> enumValue)) return original;

        Class<?> enumClass = enumValue.getDeclaringClass();
        if (!enumClass.getName().startsWith(ADDON_PACKAGE)) return original;

        TranslationFile file = translations != null ? translations : load();
        if (file != null && file.enumValues != null) {
            Map<String, String> values = file.enumValues.get(enumClass.getName());
            if (values != null) {
                String translated = values.get(enumValue.name());
                if (notBlank(translated)) return translated;
            }
        }

        // Most custom enums keep an English constant name but override toString() with Chinese.
        // Humanizing the constant gives a stable English UI label without touching persistence.
        if (containsHan(original) && !containsHan(enumValue.name())) return humanize(enumValue.name());

        return original;
    }

    private static void translateModule(Module module, TranslationFile file) {
        String className = module.getClass().getSimpleName();
        ModuleEntry entry = safeModules(file).get(className);

        MODULE_TEXT.putIfAbsent(module, new ModuleText(module.title, module.description));
        ModuleTranslationAccessor moduleAccessor = (ModuleTranslationAccessor) module;

        String translatedModuleTitle = entry != null && notBlank(entry.title)
            ? entry.title
            : humanize(className);
        String translatedModuleDescription = entry != null && notBlank(entry.description)
            ? entry.description
            : "Provides the " + translatedModuleTitle + " functionality and its related controls.";

        moduleAccessor.img$setTitle(translatedModuleTitle);
        moduleAccessor.img$setDescription(translatedModuleDescription);

        Map<String, String> groups = entry != null && entry.groups != null
            ? entry.groups
            : Collections.emptyMap();
        Map<String, SettingEntry> settings = entry != null && entry.settings != null
            ? entry.settings
            : Collections.emptyMap();

        for (SettingGroup group : module.settings) {
            GROUP_TEXT.putIfAbsent(group, group.name);

            String groupField = findFieldName(module, group);
            String translatedGroup = groupField == null ? null : groups.get(groupField);
            if (!notBlank(translatedGroup)) translatedGroup = applyPatterns(group.name, safeGroupPatterns(file));
            if (!notBlank(translatedGroup) && containsHan(group.name)) translatedGroup = "Settings";
            if (notBlank(translatedGroup)) {
                ((SettingGroupTranslationAccessor) group).img$setName(translatedGroup);
            }

            for (Setting<?> setting : group) {
                SETTING_TEXT.putIfAbsent(setting, new SettingText(setting.title, setting.description));

                String settingField = findFieldName(module, setting);
                SettingEntry settingEntry = settingField == null ? null : settings.get(settingField);

                String translatedTitle = settingEntry != null && notBlank(settingEntry.title)
                    ? settingEntry.title
                    : applyPatterns(setting.title, safeSettingPatterns(file));

                String dynamicDescription = settingEntry == null
                    ? applyPatternDescriptions(setting.title, safeSettingPatterns(file))
                    : null;

                if (!notBlank(translatedTitle)) {
                    if (settingField != null) translatedTitle = humanize(settingField);
                    else if (containsHan(setting.title)) translatedTitle = "Module Setting";
                }

                String translatedDescription = settingEntry != null && notBlank(settingEntry.description)
                    ? settingEntry.description
                    : (notBlank(dynamicDescription)
                        ? dynamicDescription
                        : applyPatterns(setting.description, safeDescriptionPatterns(file)));

                if (!notBlank(translatedDescription) && containsHan(setting.description)) {
                    String effectiveTitle = notBlank(translatedTitle) ? translatedTitle : "this setting";
                    translatedDescription = "Configures " + effectiveTitle + " for " + translatedModuleTitle + ".";
                }

                SettingTranslationAccessor settingAccessor = (SettingTranslationAccessor) setting;
                if (notBlank(translatedTitle)) settingAccessor.img$setTitle(translatedTitle);
                if (notBlank(translatedDescription)) settingAccessor.img$setDescription(translatedDescription);
            }
        }

        // Rebuild an already open Meteor settings panel with its new display text.
        module.settings.invalidate();
    }

    private static TranslationFile load() {
        if (translations != null) return translations;

        try (InputStream stream = JsonEnglishTranslationManager.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) return null;
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                TranslationFile file = GSON.fromJson(reader, TranslationFile.class);
                if (file == null || file.modules == null || file.modules.isEmpty()) return null;
                translations = file;
                return translations;
            }
        } catch (IOException | JsonParseException exception) {
            AddonTemplate.LOG.error("Failed to read IMG English translation JSON.", exception);
            return null;
        }
    }

    private static String findFieldName(Module module, Object value) {
        for (Class<?> type = module.getClass(); type != null && type != Module.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    if (field.get(module) == value) return field.getName();
                } catch (IllegalAccessException | RuntimeException ignored) {
                    // Dynamic/list-backed settings are handled by the JSON regex fallback.
                }
            }
        }
        return null;
    }

    private static String applyPatterns(String input, List<PatternEntry> entries) {
        if (!notBlank(input) || entries == null || entries.isEmpty()) return null;

        for (PatternEntry entry : entries) {
            if (entry == null || !notBlank(entry.pattern) || !notBlank(entry.replacement)) continue;
            try {
                Matcher matcher = Pattern.compile(entry.pattern).matcher(input);
                if (matcher.matches()) return matcher.replaceAll(entry.replacement);
            } catch (PatternSyntaxException ignored) {
                AddonTemplate.LOG.warn("Invalid IMG translation regex: {}", entry.pattern);
            }
        }
        return null;
    }

    private static String applyPatternDescriptions(String input, List<PatternEntry> entries) {
        if (!notBlank(input) || entries == null || entries.isEmpty()) return null;

        for (PatternEntry entry : entries) {
            if (entry == null || !notBlank(entry.pattern) || !notBlank(entry.description)) continue;
            try {
                Matcher matcher = Pattern.compile(entry.pattern).matcher(input);
                if (matcher.matches()) return matcher.replaceAll(entry.description);
            } catch (PatternSyntaxException ignored) {
                AddonTemplate.LOG.warn("Invalid IMG translation regex: {}", entry.pattern);
            }
        }
        return null;
    }

    private static boolean containsHan(String value) {
        if (value == null || value.isEmpty()) return false;
        return value.codePoints().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String humanize(String identifier) {
        if (!notBlank(identifier)) return "Setting";

        String text = identifier
            .replaceFirst("^sg(?=[A-Z])", "")
            .replaceAll("([a-z0-9])([A-Z])", "$1 $2")
            .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2")
            .replace('_', ' ')
            .replace('-', ' ')
            .trim();

        if (text.isEmpty()) return "Setting";

        StringBuilder result = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (!result.isEmpty()) result.append(' ');
            result.append(switch (word.toLowerCase(Locale.ROOT)) {
                case "img", "esp", "hud", "tpa", "tp", "fov", "odm", "bmw", "tnt", "ip", "id", "ai", "crt", "rgb", "bvr" -> word.toUpperCase(Locale.ROOT);
                case "ms" -> "ms";
                case "x", "y", "z" -> word.toUpperCase(Locale.ROOT);
                default -> Character.toUpperCase(word.charAt(0)) + word.substring(1);
            });
        }
        return result.toString();
    }

    private static Map<String, ModuleEntry> safeModules(TranslationFile file) {
        return file.modules == null ? Collections.emptyMap() : file.modules;
    }

    private static List<PatternEntry> safeGroupPatterns(TranslationFile file) {
        return file.dynamic == null || file.dynamic.groups == null ? Collections.emptyList() : file.dynamic.groups;
    }

    private static List<PatternEntry> safeSettingPatterns(TranslationFile file) {
        return file.dynamic == null || file.dynamic.settings == null ? Collections.emptyList() : file.dynamic.settings;
    }

    private static List<PatternEntry> safeDescriptionPatterns(TranslationFile file) {
        return file.dynamic == null || file.dynamic.descriptions == null ? Collections.emptyList() : file.dynamic.descriptions;
    }

    private static final class TranslationFile {
        int version;
        String language;
        Map<String, ModuleEntry> modules;
        Map<String, Map<String, String>> enumValues;
        DynamicEntry dynamic;
    }

    private static final class ModuleEntry {
        String title;
        String description;
        Map<String, String> groups;
        Map<String, SettingEntry> settings;
    }

    private static final class SettingEntry {
        String title;
        String description;
    }

    private static final class DynamicEntry {
        List<PatternEntry> groups;
        List<PatternEntry> settings;
        List<PatternEntry> descriptions;
    }

    private static final class PatternEntry {
        String pattern;
        String replacement;
        String description;
    }

    private record ModuleText(String title, String description) {}
    private record SettingText(String title, String description) {}
}
