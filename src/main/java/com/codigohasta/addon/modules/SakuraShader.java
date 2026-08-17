package com.codigohasta.addon.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;

public final class SakuraShader extends FullscreenShaderModule {
    private final SettingGroup general = settings.getDefaultGroup();
    private final Setting<Double> petalDensity = general.add(new DoubleSetting.Builder()
        .name("花瓣密度").description("屏幕前飘落花瓣的数量和亮度。")
        .defaultValue(1.0).min(0.0).sliderMax(2.0).build());
    private final Setting<Double> pinkIntensity = general.add(new DoubleSetting.Builder()
        .name("粉色强度").description("场景粉色滤镜的强度。")
        .defaultValue(0.45).min(0.0).sliderMax(1.0).build());
    private final Setting<Double> scanSpeed = general.add(new DoubleSetting.Builder()
        .name("绽放速度").description("樱花世界向外绽放的速度。")
        .defaultValue(1.0).min(0.1).sliderMax(5.0).build());
    private final Setting<Boolean> loopScan = general.add(new BoolSetting.Builder()
        .name("循环绽放").description("周期性重新播放绽放动画。")
        .defaultValue(false).build());
    private final Setting<Double> scanDuration = general.add(new DoubleSetting.Builder()
        .name("循环周期").description("两次绽放动画之间的秒数。")
        .defaultValue(3.0).min(1.0).sliderMax(10.0).visible(loopScan::get).build());

    public SakuraShader() {
        super("樱花着色器", "开关粉蓝天空、粉色滤镜和飘落花瓣效果。", "sakura");
    }

    @Override
    protected void configureUniforms(Render3DEvent event, float elapsedSeconds) {
        uniform1f("U_PetalDensity", petalDensity.get().floatValue());
        uniform1f("U_PinkIntensity", pinkIntensity.get().floatValue());
        uniform1f("U_ScanSpeed", scanSpeed.get().floatValue());
        uniform1f("U_LoopEnabled", loopScan.get() ? 1.0f : 0.0f);
        uniform1f("U_ScanDuration", scanDuration.get().floatValue());
    }
}
