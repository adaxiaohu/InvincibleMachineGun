package com.codigohasta.addon.mixin;

import meteordevelopment.meteorclient.settings.Setting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Keeps serialized setting names untouched while translating only their visible text. */
@Mixin(value = Setting.class, remap = false)
public interface SettingTranslationAccessor {
    @Mutable
    @Accessor("title")
    void img$setTitle(String title);

    @Mutable
    @Accessor("description")
    void img$setDescription(String description);
}
