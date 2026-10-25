package com.codigohasta.addon.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;

public final class MatrixShader extends FullscreenShaderModule {
    private final SettingGroup general = settings.getDefaultGroup();
    private final Setting<Double> surfaceDensity = general.add(new DoubleSetting.Builder()
        .name("表面密度").description("方块和实体表面字符雨的密度（每米列数），越大字符越细。")
        .defaultValue(12.0).min(0.5).sliderMax(32.0).build());
    private final Setting<Double> surfaceSpeed = general.add(new DoubleSetting.Builder()
        .name("表面速度").description("方块和实体表面字符雨的流动速度。")
        .defaultValue(1.0).min(0.0).sliderMax(5.0).build());
    private final Setting<Double> rainSpeed = general.add(new DoubleSetting.Builder()
        .name("雨滴速度").description("天空下降字符的流动速度。")
        .defaultValue(1.0).min(0.0).sliderMax(5.0).build());
    private final Setting<Double> rainDensity = general.add(new DoubleSetting.Builder()
        .name("雨滴密度").description("环绕天空一整圈的字符列数；越大字符越小越密。")
        .defaultValue(900.0).min(180.0).sliderMax(2000.0).build());    private final Setting<Double> sunSpeed = general.add(new DoubleSetting.Builder()
        .name("核心速度").description("天空数据核心的动画速度。")
        .defaultValue(1.0).min(0.0).sliderMax(5.0).build());
    private final Setting<Double> scanSpeed = general.add(new DoubleSetting.Builder()
        .name("展开速度").description("矩阵效果向外展开的速度。")
        .defaultValue(1.0).min(0.1).sliderMax(5.0).build());
    private final Setting<Boolean> loopScan = general.add(new BoolSetting.Builder()
        .name("循环展开").description("周期性重新播放展开动画。")
        .defaultValue(false).build());
    private final Setting<Double> scanDuration = general.add(new DoubleSetting.Builder()
        .name("循环周期").description("两次展开动画之间的秒数。")
        .defaultValue(3.0).min(1.0).sliderMax(10.0).visible(loopScan::get).build());
    private final Setting<Double> colorGrade = general.add(new DoubleSetting.Builder()
        .name("绿调强度").description("整体画面向黑客帝国绿单色靠拢的程度，0 为不处理。")
        .defaultValue(0.55).min(0.0).sliderMax(1.0).build());
    private final Setting<Double> surfaceFade = general.add(new DoubleSetting.Builder()
        .name("距离淡出").description("表面字符随距离衰减的强度，0 为不衰减。")
        .defaultValue(0.018).min(0.0).sliderMax(0.08).build());
    private final Setting<Double> entityBoost = general.add(new DoubleSetting.Builder()
        .name("实体亮度").description("实体表面字符的额外亮度，1 为与方块相同。")
        .defaultValue(1.7).min(1.0).sliderMax(3.0).build());

    public MatrixShader() {
        super("矩阵着色器", "开关绿色矩阵字符雨的世界着色器 黑客帝国。", "matrix");
    }

    @Override
    protected void configureUniforms(Render3DEvent event, float elapsedSeconds) {
        uniform1f("U_SurfaceDensity", surfaceDensity.get().floatValue());
        uniform1f("U_SurfaceSpeed", surfaceSpeed.get().floatValue());
        uniform1f("U_RainSpeed", rainSpeed.get().floatValue());
        uniform1f("U_RainDensity", rainDensity.get().floatValue());
        uniform1f("U_SunSpeed", sunSpeed.get().floatValue());
        uniform1f("U_ScanSpeed", scanSpeed.get().floatValue());
        uniform1f("U_LoopEnabled", loopScan.get() ? 1.0f : 0.0f);
        uniform1f("U_ScanDuration", scanDuration.get().floatValue());
        uniform1f("U_ColorGrade", colorGrade.get().floatValue());
        uniform1f("U_SurfaceFade", surfaceFade.get().floatValue());
        uniform1f("U_EntityBoost", entityBoost.get().floatValue());
    }
}
