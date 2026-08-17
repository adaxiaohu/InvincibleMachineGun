package com.codigohasta.addon.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;

public final class SilentHillShader extends FullscreenShaderModule {
    private final SettingGroup general = settings.getDefaultGroup();
    private final Setting<Boolean> otherworld = general.add(new BoolSetting.Builder()
        .name("里世界").description("使用昏暗铁锈红色的里世界风格。")
        .defaultValue(false).build());
    private final Setting<Double> fogDensity = general.add(new DoubleSetting.Builder()
        .name("雾浓度").description("距离雾遮蔽场景的速度。")
        .defaultValue(0.025).min(0.0).sliderMax(0.1).build());
    private final Setting<Double> grainIntensity = general.add(new DoubleSetting.Builder()
        .name("颗粒强度").description("画面噪点颗粒的强度。")
        .defaultValue(0.08).min(0.0).sliderMax(0.5).build());
    private final Setting<Double> scanSpeed = general.add(new DoubleSetting.Builder()
        .name("侵蚀速度").description("寂静岭效果侵蚀场景的速度。")
        .defaultValue(1.0).min(0.1).sliderMax(5.0).build());
    private final Setting<Boolean> loopScan = general.add(new BoolSetting.Builder()
        .name("循环侵蚀").description("周期性重新播放侵蚀动画。")
        .defaultValue(false).build());
    private final Setting<Double> scanDuration = general.add(new DoubleSetting.Builder()
        .name("循环周期").description("两次侵蚀动画之间的秒数。")
        .defaultValue(3.0).min(1.0).sliderMax(10.0).visible(loopScan::get).build());

    public SilentHillShader() {
        super("寂静岭着色器", "开关灰雾表世界或铁锈红里世界效果。", "silenthill");
    }

    @Override
    protected void configureUniforms(Render3DEvent event, float elapsedSeconds) {
        uniform1f("U_StyleMode", otherworld.get() ? 1.0f : 0.0f);
        uniform1f("U_FogDensity", fogDensity.get().floatValue());
        uniform1f("U_GrainIntensity", grainIntensity.get().floatValue());
        uniform1f("U_ScanSpeed", scanSpeed.get().floatValue());
        uniform1f("U_LoopEnabled", loopScan.get() ? 1.0f : 0.0f);
        uniform1f("U_ScanDuration", scanDuration.get().floatValue());
    }
}
