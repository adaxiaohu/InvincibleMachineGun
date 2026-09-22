package com.codigohasta.addon.mixin.slimefunhelper;

import com.codigohasta.addon.utils.slimefunhelper.SfTranslations;
import meteordevelopment.meteorclient.gui.DefaultSettingsWidgetFactory;
import meteordevelopment.meteorclient.settings.SettingGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Localizes section headers without changing serialized SettingGroup.name. */
@Mixin(value = DefaultSettingsWidgetFactory.class, remap = false)
public abstract class SfSettingGroupTranslationMixin {
    @org.spongepowered.asm.mixin.injection.Inject(method = "intW", at = @At("RETURN"))
    private void sfimg$commitIntegerEdits(
            meteordevelopment.meteorclient.gui.widgets.containers.WTable table,
            meteordevelopment.meteorclient.settings.IntSetting setting,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci,
            @com.llamalad7.mixinextras.sugar.Local meteordevelopment.meteorclient.gui.widgets.input.WIntEdit editor) {
        com.codigohasta.addon.utils.slimefunhelper.hacks.api.SfIntegerEditor.bind(setting, editor);
    }

    @Redirect(method = "group", at = @At(value = "FIELD",
        target = "Lmeteordevelopment/meteorclient/settings/SettingGroup;name:Ljava/lang/String;"))
    private String sfimg$groupTitle(SettingGroup group) {
        return SfTranslations.groupTitle(group);
    }
}
