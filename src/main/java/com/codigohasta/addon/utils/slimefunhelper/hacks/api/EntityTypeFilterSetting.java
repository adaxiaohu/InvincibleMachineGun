package com.codigohasta.addon.utils.slimefunhelper.hacks.api;

import com.codigohasta.addon.utils.slimefunhelper.SfTranslations;
import com.codigohasta.addon.utils.slimefunhelper.hacks.utils.config.EntityTypeRegex;
import com.codigohasta.addon.utils.slimefunhelper.hacks.utils.config.Regex;
import com.google.gson.Gson;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.screens.settings.EntityTypeListSettingScreen;
import meteordevelopment.meteorclient.gui.utils.SettingsWidgetFactory;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.IVisible;
import meteordevelopment.meteorclient.settings.StringSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;

/** Native Meteor picker over the original JSON value; no second persisted whitelist. */
public final class EntityTypeFilterSetting extends StringSetting {
    private static final Gson GSON = new Gson();
    private final JsonValueCache<EntityTypeRegex> cache;

    static {
        SettingsWidgetFactory.registerCustomFactory(EntityTypeFilterSetting.class, theme -> (table, raw) -> {
            var setting = (EntityTypeFilterSetting) raw;
            var column = table.add(theme.verticalList()).expandX().widget();
            var row = column.add(theme.horizontalList()).expandX().widget();
            var select = row.add(theme.button(SfTranslations.text("ui.target-select", "Select entities"))).expandX().widget();
            var reset = row.add(theme.button(GuiRenderer.RESET)).widget();
            reset.tooltip = SfTranslations.text("ui.target-reset", "Reset target types");
            var advanced = column.add(theme.section(SfTranslations.text("ui.target-advanced", "Advanced regex (JSON)"), false)).expandX().widget();
            var editor = advanced.add(theme.textBox(setting.get())).minWidth(250).expandX().widget();
            editor.action = () -> setting.set(editor.get());
            select.action = () -> {
                var picker = setting.createPicker();
                MinecraftClient.getInstance().setScreen(new EntityTypeListSettingScreen(theme, picker) {
                    @Override public void close() { editor.set(setting.get()); super.close(); }
                });
            };
            reset.action = () -> { setting.reset(); editor.set(setting.get()); };
        });
    }

    public EntityTypeFilterSetting(String name, String description, EntityTypeRegex defaults,
                                   IVisible visible, Predicate<EntityTypeRegex> validator) {
        super(name, description, GSON.toJson(defaults), null, null, visible, "", null, null, false);
        cache = new JsonValueCache<>(GSON, defaults, validator);
    }

    public EntityTypeRegex filterValue() { return cache.get(get()); }

    public boolean setFilter(EntityTypeRegex filter) { return set(GSON.toJson(filter)); }

    public EntityTypeListSetting createPicker() {
        Set<EntityType<?>> selected = new HashSet<>();
        EntityTypeRegex filter = filterValue();
        for (EntityType<?> type : Registries.ENTITY_TYPE) if (filter.test(type)) selected.add(type);
        return new EntityTypeListSetting.Builder().name(name).description(description)
            .defaultValue(selected).onChanged(types -> {
                // Preserve group/exclusion expressions when opening/closing without edits.
                Set<EntityType<?>> current = new HashSet<>();
                EntityTypeRegex existing = filterValue();
                for (EntityType<?> type : Registries.ENTITY_TYPE) if (existing.test(type)) current.add(type);
                if (current.equals(types)) return;
                String expression = types.isEmpty() ? "(?!)" : types.stream()
                    .map(type -> Pattern.quote(Registries.ENTITY_TYPE.getId(type).getPath()))
                    .sorted().collect(Collectors.joining("|", "^(?:", ")$"));
                setFilter(new EntityTypeRegex(new Regex(expression)));
            }).build();
    }
}
