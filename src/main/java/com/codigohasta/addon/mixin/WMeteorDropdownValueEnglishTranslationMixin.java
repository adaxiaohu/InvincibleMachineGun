package com.codigohasta.addon.mixin;

import com.codigohasta.addon.utils.translation.JsonEnglishTranslationManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Translates enum labels in the expanded Meteor dropdown list. */
@Mixin(targets = "meteordevelopment.meteorclient.gui.themes.meteor.widgets.input.WMeteorDropdown$WValue", remap = false)
public abstract class WMeteorDropdownValueEnglishTranslationMixin {
    @Redirect(
        method = {"onCalculateSize", "onRender"},
        at = @At(value = "INVOKE", target = "Ljava/lang/Object;toString()Ljava/lang/String;")
    )
    private String img$translateDropdownValue(Object value) {
        return JsonEnglishTranslationManager.displayValue(value);
    }
}
