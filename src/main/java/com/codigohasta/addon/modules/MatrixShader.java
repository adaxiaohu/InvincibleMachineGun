package com.codigohasta.addon.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;

public final class MatrixShader extends FullscreenShaderModule {
    private final SettingGroup general = settings.getDefaultGroup();
    private final Setting<Double> gridSpeed = general.add(new DoubleSetting.Builder()
        .name("网格速度").description("绿色数字网格沿世界 Z 轴流动的速度。")
        .defaultValue(2.0).min(0.0).sliderMax(10.0).build());
    private final Setting<Double> sunSpeed = general.add(new DoubleSetting.Builder()
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

    public MatrixShader() {
        super("矩阵着色器", "开关绿色矩阵网格和数据核心风格的世界着色器 黑客帝国。", "matrix");
    }

    @Override
    protected void configureUniforms(Render3DEvent event, float elapsedSeconds) {
        uniform1f("U_GridSpeed", gridSpeed.get().floatValue());
        uniform1f("U_SunSpeed", sunSpeed.get().floatValue());
        uniform1f("U_ScanSpeed", scanSpeed.get().floatValue());
        uniform1f("U_LoopEnabled", loopScan.get() ? 1.0f : 0.0f);
        uniform1f("U_ScanDuration", scanDuration.get().floatValue());
    }
}
