package com.codigohasta.addon.utils.slimefunhelper;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import net.minecraft.text.Text;

/** Source labels and help text, without replacing another mod's language keys. */
public final class SourceText {
    private static final Map<String, String> TEXT = load();

    private static Map<String, String> load() {
        try (var stream = SourceText.class.getResourceAsStream(
                "/assets/invincible_machine_gun/slimefunhelper/messages.json")) {
            if (stream == null) return Map.of();
            return new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8),
                    new TypeToken<Map<String, String>>() {}.getType());
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot load SlimefunHelper descriptions", exception);
        }
    }

    public static String description(String path) {
        String label = SfTranslations.text(path, TEXT.getOrDefault(path, path));
        String help = TEXT.get(path + ".tooltips");
        return help == null ? label : label + "\n" + help;
    }

    public static Text message(String key, Object... arguments) {
        return Text.translatableWithFallback(key, SfTranslations.text(key, TEXT.getOrDefault(key, key)), arguments);
    }
}
