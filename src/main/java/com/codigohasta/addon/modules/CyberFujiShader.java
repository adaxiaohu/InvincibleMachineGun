package com.codigohasta.addon.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;

public final class CyberFujiShader extends FullscreenShaderModule {
    private final SettingGroup general = settings.getDefaultGroup();
    private final Setting<Double> gridSpeed = general.add(new DoubleSetting.Builder()
        .name("网格速度").description("赛博网格流动速度。")
        .defaultValue(0.3).min(0.0).sliderMax(10.0).build());
    private final Setting<Double> sunSpeed = general.add(new DoubleSetting.Builder()
        .name("太阳速度").description("太阳扫描条纹的移动速度。")
        .defaultValue(0.1).min(0.0).sliderMax(2.0).build());
    private final Setting<Double> sunAzimuth = general.add(new DoubleSetting.Builder()
        .name("太阳方位").description("太阳在世界水平方向上的角度。")
        .defaultValue(180.0).min(0.0).sliderMax(360.0).build());
    private final Setting<Double> sunElevation = general.add(new DoubleSetting.Builder()
        .name("太阳高度").description("太阳高于地平线的角度。")
        .defaultValue(5.0).min(5.0).sliderMax(80.0).build());
    private final Setting<Double> sunSize = general.add(new DoubleSetting.Builder()
        .name("太阳大小").description("合成波太阳圆盘的大小。")
        .defaultValue(0.33).min(0.1).sliderMax(0.6).build());
    private final Setting<Double> sunBrightness = general.add(new DoubleSetting.Builder()
        .name("太阳亮度").description("太阳圆盘和光晕的亮度。")
        .defaultValue(1.0).min(0.0).sliderMax(3.0).build());
    private final Setting<Double> pulseSpeed = general.add(new DoubleSetting.Builder()
        .name("呼吸速度").description("网格呼吸灯速度。")
        .defaultValue(2.0).min(0.0).sliderMax(10.0).build());
    private final Setting<Double> scanSpeed = general.add(new DoubleSetting.Builder()
        .name("展开速度").description("着色器从玩家附近向外展开的速度。")
        .defaultValue(1.0).min(0.1).sliderMax(5.0).build());
    private final Setting<Boolean> loopScan = general.add(new BoolSetting.Builder()
        .name("循环展开").description("周期性重新播放展开动画。")
        .defaultValue(false).build());
    private final Setting<Double> scanDuration = general.add(new DoubleSetting.Builder()
        .name("循环周期").description("两次展开动画之间的秒数。")
        .defaultValue(3.0).min(1.0).sliderMax(10.0).visible(loopScan::get).build());

    public CyberFujiShader() {
        super("合成器浪潮着色器", "开关赛博蒸汽波风格的全屏世界着色器。", "synthwave");
    }

    @Override
    protected void configureUniforms(Render3DEvent event, float elapsedSeconds) {
        float azimuth = (float) Math.toRadians(sunAzimuth.get());
        float elevation = (float) Math.toRadians(sunElevation.get());
        float horizontal = (float) Math.cos(elevation);

        uniform1f("U_GridSpeed", gridSpeed.get().floatValue());
        uniform1f("U_SunSpeed", sunSpeed.get().floatValue());
        uniform3f("U_SunDirection",
            (float) Math.cos(azimuth) * horizontal,
            (float) Math.sin(elevation),
            (float) Math.sin(azimuth) * horizontal);
        uniform1f("U_SunSize", sunSize.get().floatValue());
        uniform1f("U_SunBrightness", sunBrightness.get().floatValue());
        uniform1f("U_PulseSpeed", pulseSpeed.get().floatValue());
        uniform1f("U_ScanSpeed", scanSpeed.get().floatValue());
        uniform1f("U_LoopEnabled", loopScan.get() ? 1.0f : 0.0f);
        uniform1f("U_ScanDuration", scanDuration.get().floatValue());
    }
}
