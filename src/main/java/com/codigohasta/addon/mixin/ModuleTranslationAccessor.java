package com.codigohasta.addon.mixin;

import meteordevelopment.meteorclient.systems.modules.Module;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Allows the translation module to change UI-only module text without changing module ids. */
@Mixin(value = Module.class, remap = false)
public interface ModuleTranslationAccessor {
    @Mutable
    @Accessor("title")
    void img$setTitle(String title);

    @Mutable
    @Accessor("description")
    void img$setDescription(String description);
}
