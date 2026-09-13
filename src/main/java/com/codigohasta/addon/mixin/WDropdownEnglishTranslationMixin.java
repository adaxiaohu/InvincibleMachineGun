package com.codigohasta.addon.mixin;

import com.codigohasta.addon.utils.translation.JsonEnglishTranslationManager;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keeps dropdown width calculation in sync with translated enum labels. */
@Mixin(value = WDropdown.class, remap = false)
public abstract class WDropdownEnglishTranslationMixin {
    @Redirect(
        method = "onCalculateSize",
        at = @At(value = "INVOKE", target = "Ljava/lang/Object;toString()Ljava/lang/String;")
    )
    private String img$translateDropdownWidth(Object value) {
        return JsonEnglishTranslationManager.displayValue(value);
    }
}
