package com.codigohasta.addon.mixin;

import com.codigohasta.addon.utils.translation.JsonEnglishTranslationManager;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.input.WMeteorDropdown;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Translates the currently selected enum value in Meteor dropdowns. */
@Mixin(value = WMeteorDropdown.class, remap = false)
public abstract class WMeteorDropdownEnglishTranslationMixin {
    @Redirect(
        method = "onRender",
        at = @At(value = "INVOKE", target = "Ljava/lang/Object;toString()Ljava/lang/String;")
    )
    private String img$translateSelectedValue(Object value) {
        return JsonEnglishTranslationManager.displayValue(value);
    }
}
