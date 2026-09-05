package com.codigohasta.addon.mixin;

import meteordevelopment.meteorclient.settings.SettingGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = SettingGroup.class, remap = false)
public interface SettingGroupTranslationAccessor {
    @Mutable
    @Accessor("name")
    void img$setName(String name);
}
