package com.codigohasta.addon.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

public final class DuskShader extends FullscreenShaderModule {
    private final SettingGroup general = settings.getDefaultGroup();
    private final Setting<Double> moodIntensity = general.add(new DoubleSetting.Builder()
        .name("氛围强度").description("黄昏调色覆盖原画面的强度。")
        .defaultValue(0.8).min(0.0).sliderMax(1.0).build());
    private final Setting<Double> wetness = general.add(new DoubleSetting.Builder()
        .name("地面湿润度").description("增强平坦表面的湿润反光。")
        .defaultValue(0.55).min(0.0).sliderMax(1.0).build());
    private final Setting<SettingColor> skyTop = general.add(new ColorSetting.Builder()
        .name("天空顶部").defaultValue(new SettingColor(35, 22, 82)).build());
    private final Setting<SettingColor> skyBottom = general.add(new ColorSetting.Builder()
        .name("天空底部").defaultValue(new SettingColor(245, 112, 82)).build());
    private final Setting<SettingColor> fogColor = general.add(new ColorSetting.Builder()
        .name("雾颜色").defaultValue(new SettingColor(82, 78, 112)).build());
    private final Setting<SettingColor> moodColor = general.add(new ColorSetting.Builder()
        .name("氛围颜色").defaultValue(new SettingColor(150, 168, 220)).build());

    public DuskShader() {
        super("黄昏着色器", "开关黄昏天空、冷色雾气和湿润地面效果。", "dusk");
    }

    @Override
    protected void configureUniforms(Render3DEvent event, float elapsedSeconds) {
        uniformColor("U_SkyTop", skyTop.get());
        uniformColor("U_SkyBottom", skyBottom.get());
        uniformColor("U_FogColor", fogColor.get());
        uniformColor("U_MoodColor", moodColor.get());
        uniform1f("U_MoodIntensity", moodIntensity.get().floatValue());
        uniform1f("U_Wetness", wetness.get().floatValue());
    }
}
