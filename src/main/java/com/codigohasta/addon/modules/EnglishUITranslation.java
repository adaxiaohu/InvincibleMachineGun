package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.utils.translation.JsonEnglishTranslationManager;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

/** English display translation for all IMG addon modules. */
public class EnglishUITranslation extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Boolean> autoEnableForNonChinese = sgGeneral.add(new BoolSetting.Builder()
        .name("auto ON")
        .description("It opens automatically for non-Chinese speakers")
        .defaultValue(true)
        .build()
    );

    public EnglishUITranslation() {
        super(
            AddonTemplate.CATEGORY,
            "English Language",
            "Translates all names and descriptions of IMG addon modules to English, without changing the configuration IDs.关闭此模块就是中文版"
        );
        runInMainMenu = true;
    }

    @Override
    public void onActivate() {
        JsonEnglishTranslationManager.apply();
    }

    @Override
    public void onDeactivate() {
        JsonEnglishTranslationManager.restore();
    }

    public void refreshTranslation() {
        JsonEnglishTranslationManager.refresh();
    }
}
