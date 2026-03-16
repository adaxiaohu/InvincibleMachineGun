package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

public class CustomFov extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // 对应 Alien 的 fov 
    public final Setting<Double> fov = sgGeneral.add(new DoubleSetting.Builder()
        .name("fov")
        .description("自定义游戏视角。")
        .defaultValue(110)
        .min(30)
        .max(170)
        .sliderMax(170)
        .build()
    );

    // 对应 Alien 的 itemFov
    public final Setting<Double> itemFov = sgGeneral.add(new DoubleSetting.Builder()
        .name("item-fov")
        .description("自定义手持物品视角。")
        .defaultValue(70)
        .min(30)
        .max(170)
        .sliderMax(170)
        .build()
    );

    public CustomFov() {
        // 使用你主类里定义的 CATEGORY (MacePVP)
        super(AddonTemplate.CATEGORY, "custom-fov-plus", "允许独立调整视角和手持物品视角。");
    }
}