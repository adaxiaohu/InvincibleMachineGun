package com.codigohasta.addon.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class BlueHourShader extends FullscreenShaderModule {

    private final SettingGroup general = settings.getDefaultGroup();
    private final SettingGroup clouds = settings.createGroup("云层");
    private final SettingGroup rain = settings.createGroup("雨景");
    private final SettingGroup dispersion = settings.createGroup("色散");
    private final SettingGroup lighting = settings.createGroup("光照");
    private final SettingGroup shadows = settings.createGroup("生物阴影");

    private final Setting<Double> moodIntensity = general.add(new DoubleSetting.Builder()
        .name("蓝调强度").description("雨后蓝调时刻调色覆盖原画面的强度。")
        .defaultValue(0.82).min(0.0).sliderMax(1.0).build());
    private final Setting<Boolean> customBlueTone = general.add(new BoolSetting.Builder()
        .name("自定义蓝调色调").description("单独调整蓝调时刻的环境色相；局部人工光只有在被识别为真实局部照明贡献时才从蓝调中分离。")
        .defaultValue(false).build());
    private final Setting<Double> blueTone = general.add(new DoubleSetting.Builder()
        .name("蓝调色调").description("0 为偏青绿的蓝调，0.5 保持当前默认蓝调，1 为更纯、更深的蓝色蓝调。")
        .defaultValue(0.5).min(0.0).sliderMax(1.0).visible(customBlueTone::get).build());
    private final Setting<Double> contrast = general.add(new DoubleSetting.Builder()
        .name("对比度").description("冷蓝画面的明暗对比强度。")
        .defaultValue(1.12).min(0.6).sliderMax(1.8).build());
    private final Setting<Double> wetness = general.add(new DoubleSetting.Builder()
        .name("地面湿润度").description("增强地面已有亮部的冷色湿润反光，不生成方向性阴影。")
        .defaultValue(0.58).min(0.0).sliderMax(1.0).build());
    private final Setting<Double> introSpeed = general.add(new DoubleSetting.Builder()
        .name("展开速度").description("蓝调效果从玩家附近向外展开的速度。")
        .defaultValue(1.0).min(0.2).sliderMax(3.0).build());
    private final Setting<Boolean> loopIntro = general.add(new BoolSetting.Builder()
        .name("循环展开").description("周期性重新播放开场展开动画。")
        .defaultValue(false).build());
    private final Setting<Double> introDuration = general.add(new DoubleSetting.Builder()
        .name("循环周期").description("两次展开动画之间的秒数。")
        .defaultValue(6.0).min(4.0).sliderMax(15.0).visible(loopIntro::get).build());

    private final Setting<CloudStyle> cloudStyle = clouds.add(new EnumSetting.Builder<CloudStyle>()
        .name("云层风格").description("选择柔和、低多边形或混合体积云。")
        .defaultValue(CloudStyle.Hybrid).build());
    private final Setting<CloudQuality> cloudQuality = clouds.add(new EnumSetting.Builder<CloudQuality>()
        .name("云层质量").description("控制体积云采样数；更高质量需要更多性能。")
        .defaultValue(CloudQuality.Medium).build());
    private final Setting<Double> cloudCoverage = clouds.add(new DoubleSetting.Builder()
        .name("云量").description("控制天空中厚云的覆盖范围。")
        .defaultValue(0.72).min(0.0).sliderMax(1.0).build());
    private final Setting<Double> cloudThickness = clouds.add(new DoubleSetting.Builder()
        .name("云层厚度").description("控制云层的垂直厚度和体积感。")
        .defaultValue(0.72).min(0.1).sliderMax(1.0).build());
    private final Setting<Double> cloudSpeed = clouds.add(new DoubleSetting.Builder()
        .name("云层速度").description("控制云层随风水平移动的速度。")
        .defaultValue(0.08).min(0.0).sliderMax(0.5).build());
    private final Setting<Double> polygonStrength = clouds.add(new DoubleSetting.Builder()
        .name("棱面强度").description("控制低多边形云的明暗分块程度。")
        .defaultValue(0.55).min(0.0).sliderMax(1.0)
        .visible(() -> cloudStyle.get() != CloudStyle.Soft).build());

    private final Setting<RainMode> rainMode = rain.add(new EnumSetting.Builder<RainMode>()
        .name("雨景模式").description("选择世界空间雨丝、玻璃雨流或同时启用。")
        .defaultValue(RainMode.Screen).build());
    private final Setting<Double> rainDensity = rain.add(new DoubleSetting.Builder()
        .name("雨量").description("控制世界雨丝密度、镜头持续落滴频率与积水量。")
        .defaultValue(0.60).min(0.0).sliderMax(1.0)
        .visible(() -> rainMode.get() != RainMode.Off).build());
    private final Setting<Double> rainSpeed = rain.add(new DoubleSetting.Builder()
        .name("雨速").description("控制雨丝落下与镜头水滴流动的速度。")
        .defaultValue(1.0).min(0.1).sliderMax(3.0)
        .visible(() -> rainMode.get() != RainMode.Off).build());
    private final Setting<Double> rainOpacity = rain.add(new DoubleSetting.Builder()
        .name("雨景透明度").description("控制雨丝和镜头水滴的总体可见程度。")
        .defaultValue(0.66).min(0.0).sliderMax(1.0)
        .visible(() -> rainMode.get() != RainMode.Off).build());
    private final Setting<Double> dropletRefraction = rain.add(new DoubleSetting.Builder()
        .name("水滴折射").description("控制镜头水滴对画面的折射强度。")
        .defaultValue(0.55).min(0.0).sliderMax(1.0)
        .visible(() -> rainMode.get().hasScreen()).build());
    private final Setting<Boolean> glassFog = rain.add(new BoolSetting.Builder()
        .name("玻璃雾气").description("在镜头玻璃上生成不均匀的冷凝雾和失焦模糊。")
        .defaultValue(true).visible(() -> rainMode.get().hasScreen()).build());
    private final Setting<Double> glassFogStrength = rain.add(new DoubleSetting.Builder()
        .name("雾气浓度").description("控制玻璃冷凝雾覆盖画面的强度。")
        .defaultValue(0.46).min(0.0).sliderMax(1.0)
        .visible(() -> rainMode.get().hasScreen() && glassFog.get()).build());
    private final Setting<Double> glassFogBlur = rain.add(new DoubleSetting.Builder()
        .name("雾气模糊").description("只控制玻璃雾气的背景失焦半径，不再直接模糊雨滴边缘。")
        .defaultValue(4.5).min(1.0).sliderMax(14.0)
        .visible(() -> rainMode.get().hasScreen() && glassFog.get()).build());
    private final Setting<Boolean> wiper = rain.add(new BoolSetting.Builder()
        .name("雨刮器").description("让雨刮器周期性扫过镜头并暂时清除水滴和雾气。")
        .defaultValue(false).visible(() -> rainMode.get().hasScreen()).build());
    private final Setting<Double> wiperInterval = rain.add(new DoubleSetting.Builder()
        .name("刮动间隔").description("控制两次雨刮扫动之间的秒数。")
        .defaultValue(7.0).min(2.0).sliderMax(20.0)
        .visible(() -> rainMode.get().hasScreen() && wiper.get()).build());
    private final Setting<Double> wiperDuration = rain.add(new DoubleSetting.Builder()
        .name("刮动时间").description("控制雨刮器完成一次扫动所需的秒数。")
        .defaultValue(1.15).min(0.35).sliderMax(3.0)
        .visible(() -> rainMode.get().hasScreen() && wiper.get()).build());
    private final Setting<Double> wiperWidth = rain.add(new DoubleSetting.Builder()
        .name("雨刮宽度").description("控制雨刮片及其清洁轨迹的宽度。")
        .defaultValue(0.055).min(0.015).sliderMax(0.14)
        .visible(() -> rainMode.get().hasScreen() && wiper.get()).build());
    private final Setting<Double> wiperRecovery = rain.add(new DoubleSetting.Builder()
        .name("雾气恢复时间").description("控制雨刮扫过后水滴和雾气重新覆盖玻璃的秒数。")
        .defaultValue(3.2).min(0.5).sliderMax(12.0)
        .visible(() -> rainMode.get().hasScreen() && wiper.get()).build());

    private final Setting<Boolean> chromaticDispersion = dispersion.add(new BoolSetting.Builder()
        .name("RGB 色散").description("在方块、实体和其它几何/颜色边缘产生 RGB 通道分离，不再要求目标是光源或高亮区域。")
        .defaultValue(true).build());
    private final Setting<Double> dispersionStrength = dispersion.add(new DoubleSetting.Builder()
        .name("色散强度").description("控制所有可见边缘的 RGB 通道分离强度。")
        .defaultValue(0.32).min(0.0).sliderMax(1.0).visible(chromaticDispersion::get).build());
    private final Setting<Double> dispersionRadius = dispersion.add(new DoubleSetting.Builder()
        .name("色散半径").description("控制边缘红蓝通道分离的像素距离。")
        .defaultValue(2.4).min(0.5).sliderMax(8.0).visible(chromaticDispersion::get).build());
    private final Setting<Double> dispersionEdgeThreshold = dispersion.add(new DoubleSetting.Builder()
        .name("边缘灵敏度").description("控制多细微的方块/实体边缘会触发色散。数值越低越敏感；与亮度无关。")
        .defaultValue(0.075).min(0.015).sliderMax(0.30).visible(chromaticDispersion::get).build());
    private final Setting<Double> edgeBias = dispersion.add(new DoubleSetting.Builder()
        .name("镜头边缘增强").description("额外增强靠近屏幕边缘的色差，但画面中央的方块和实体边缘同样会产生色散。")
        .defaultValue(0.2).min(0.0).sliderMax(1.0).visible(chromaticDispersion::get).build());

    private final Setting<Boolean> lightBloom = lighting.add(new BoolSetting.Builder()
        .name("真实光晕").description("让场景中的高亮光源产生柔和扩散光晕。")
        .defaultValue(true).build());
    private final Setting<Double> bloomStrength = lighting.add(new DoubleSetting.Builder()
        .name("光晕强度").description("控制光源周围扩散光的亮度。")
        .defaultValue(0.52).min(0.0).sliderMax(1.5).visible(lightBloom::get).build());
    private final Setting<Double> bloomRadius = lighting.add(new DoubleSetting.Builder()
        .name("光晕半径").description("控制光源扩散光在屏幕上的范围。")
        .defaultValue(5.0).min(1.0).sliderMax(14.0).visible(lightBloom::get).build());
    private final Setting<Double> bloomThreshold = lighting.add(new DoubleSetting.Builder()
        .name("光晕阈值").description("只控制 Bloom 需要多亮才开始扩散，与 RGB 色散无关。")
        .defaultValue(0.68).min(0.1).sliderMax(1.0).visible(lightBloom::get).build());
    private final Setting<Boolean> independentLightColor = lighting.add(new BoolSetting.Builder()
        .name("光源颜色独立").description("只分离相对周围表面真正抬升的局部照明颜色，不再按绝对暖色/亮度判断整个世界；不扫描发光方块，也不创建光照体积。")
        .defaultValue(true).build());
    private final Setting<Double> lightColorIsolationStrength = lighting.add(new DoubleSetting.Builder()
        .name("光源颜色分离强度").description("控制局部人工照明从蓝调环境中恢复的程度。只恢复相对邻域的光照残差，避免草地、天空和普通亮面被全局带色。")
        .defaultValue(1.0).min(0.0).sliderMax(1.0).visible(independentLightColor::get).build());
    private final Setting<Boolean> customLightTemperature = lighting.add(new BoolSetting.Builder()
        .name("自定义光源色温").description("只对白平衡重着色已经存在的局部光照，不增加亮度。关闭时保留 Minecraft 原画面的光源颜色；蓝调滤镜仍只负责环境。")
        .defaultValue(false).visible(independentLightColor::get).build());
    private final Setting<Integer> lightTemperature = lighting.add(new IntSetting.Builder()
        .name("光源色温").description("只改变被识别出的原版光照颜色，并保持其原有亮度。低色温偏暖，高色温偏冷白，单位为 K。")
        .defaultValue(3600).min(1800).sliderMax(10000)
        .visible(() -> independentLightColor.get() && customLightTemperature.get()).build());
    private final Setting<Boolean> tyndallEffect = lighting.add(new BoolSetting.Builder()
        .name("丁达尔光束").description("启用轻量级场景感知丁达尔/耶稣光效果；通过深度缓冲估算遮挡，避免完整体积光追的高开销。")
        .defaultValue(true).build());
    private final Setting<Double> tyndallStrength = lighting.add(new DoubleSetting.Builder()
        .name("丁达尔强度").description("控制空气中可见光束的强度。")
        .defaultValue(0.34).min(0.0).sliderMax(1.0).visible(tyndallEffect::get).build());
    private final Setting<Boolean> ambientOcclusion = lighting.add(new BoolSetting.Builder()
        .name("环境光遮蔽").description("使用深度缓冲增强物体接触处和凹角的环境阴影。")
        .defaultValue(true).build());
    private final Setting<Double> aoStrength = lighting.add(new DoubleSetting.Builder()
        .name("遮蔽强度").description("控制屏幕空间环境光遮蔽的强度。")
        .defaultValue(0.28).min(0.0).sliderMax(0.8).visible(ambientOcclusion::get).build());
    private final Setting<Boolean> lowPolyLighting = lighting.add(new BoolSetting.Builder()
        .name("低多边形光照").description("将光晕和环境遮蔽量化为克制的多边形明暗层次。")
        .defaultValue(false).build());

    private final Setting<Boolean> entityShadows = shadows.add(new BoolSetting.Builder()
        .name("生物多边形阴影").description("为可见生物绘制随时间和环境亮度变化的低多边形投影阴影。")
        .defaultValue(true).build());
    private final Setting<Double> entityShadowOpacity = shadows.add(new DoubleSetting.Builder()
        .name("阴影强度").description("控制生物多边形阴影的不透明度。")
        .defaultValue(0.46).min(0.0).sliderMax(0.85).visible(entityShadows::get).build());
    private final Setting<Double> entityShadowRange = shadows.add(new DoubleSetting.Builder()
        .name("阴影距离").description("只为此距离以内的可见生物计算阴影。")
        .defaultValue(40.0).min(8.0).sliderMax(96.0).visible(entityShadows::get).build());
    private final Setting<Integer> maxShadowEntities = shadows.add(new IntSetting.Builder()
        .name("最大阴影数量").description("限制每帧渲染的生物阴影数量以节省性能。")
        .defaultValue(24).min(1).sliderMax(64).visible(entityShadows::get).build());

    public BlueHourShader() {
        super("蓝调时刻着色器", "雨后傍晚的冷蓝世界，带体积云、真实雨景、局部光色分离与全边缘 RGB 色散。", "blue_hour");
    }

    @Override
    protected void configureUniforms(Render3DEvent event, float elapsedSeconds) {
        uniform1f("U_MoodIntensity", moodIntensity.get().floatValue());
        uniform1f("U_CustomBlueTone", customBlueTone.get() ? 1.0f : 0.0f);
        uniform1f("U_BlueTone", blueTone.get().floatValue());
        uniform1f("U_Contrast", contrast.get().floatValue());
        uniform1f("U_Wetness", wetness.get().floatValue());
        uniform1f("U_IntroSpeed", introSpeed.get().floatValue());
        uniform1f("U_LoopIntro", loopIntro.get() ? 1.0f : 0.0f);
        uniform1f("U_IntroDuration", introDuration.get().floatValue());

        uniform1f("U_CloudStyle", cloudStyle.get().shaderValue);
        uniform1f("U_CloudSteps", cloudQuality.get().steps);
        uniform1f("U_CloudCoverage", cloudCoverage.get().floatValue());
        uniform1f("U_CloudThickness", cloudThickness.get().floatValue());
        uniform1f("U_CloudSpeed", cloudSpeed.get().floatValue());
        uniform1f("U_PolygonStrength", polygonStrength.get().floatValue());

        uniform1f("U_WorldRain", rainMode.get().hasWorld() ? 1.0f : 0.0f);
        uniform1f("U_ScreenRain", rainMode.get().hasScreen() ? 1.0f : 0.0f);
        uniform1f("U_RainDensity", rainDensity.get().floatValue());
        uniform1f("U_RainSpeed", rainSpeed.get().floatValue());
        uniform1f("U_RainOpacity", rainOpacity.get().floatValue());
        uniform1f("U_DropletRefraction", dropletRefraction.get().floatValue());
        uniform1f("U_GlassFogEnabled", glassFog.get() ? 1.0f : 0.0f);
        uniform1f("U_GlassFogStrength", glassFogStrength.get().floatValue());
        uniform1f("U_GlassFogBlur", glassFogBlur.get().floatValue());
        uniform1f("U_WiperEnabled", wiper.get() ? 1.0f : 0.0f);
        uniform1f("U_WiperInterval", wiperInterval.get().floatValue());
        uniform1f("U_WiperDuration", wiperDuration.get().floatValue());
        uniform1f("U_WiperWidth", wiperWidth.get().floatValue());
        uniform1f("U_WiperRecovery", wiperRecovery.get().floatValue());

        uniform1f("U_DispersionEnabled", chromaticDispersion.get() ? 1.0f : 0.0f);
        uniform1f("U_DispersionStrength", dispersionStrength.get().floatValue());
        uniform1f("U_DispersionRadius", dispersionRadius.get().floatValue());
        uniform1f("U_DispersionEdgeThreshold", dispersionEdgeThreshold.get().floatValue());
        uniform1f("U_HighlightThreshold", bloomThreshold.get().floatValue());
        uniform1f("U_EdgeBias", edgeBias.get().floatValue());
        uniform1f("U_BloomEnabled", lightBloom.get() ? 1.0f : 0.0f);
        uniform1f("U_BloomStrength", bloomStrength.get().floatValue());
        uniform1f("U_BloomRadius", bloomRadius.get().floatValue());
        uniform1f("U_IndependentLightColorEnabled", independentLightColor.get() ? 1.0f : 0.0f);
        uniform1f("U_LightColorIsolationStrength", lightColorIsolationStrength.get().floatValue());
        uniform1f("U_CustomLightTemperature", customLightTemperature.get() ? 1.0f : 0.0f);
        uniform1f("U_LightTemperature", lightTemperature.get().floatValue());
        uniform1f("U_TyndallEnabled", tyndallEffect.get() ? 1.0f : 0.0f);
        uniform1f("U_TyndallStrength", tyndallStrength.get().floatValue());
        float dayTime = mc.world == null ? 0.25f : (mc.world.getTimeOfDay() % 24000L) / 24000.0f;
        uniform1f("U_WorldDayTime", dayTime);
        uniform1f("U_AOEnabled", ambientOcclusion.get() ? 1.0f : 0.0f);
        uniform1f("U_AOStrength", aoStrength.get().floatValue());
        uniform1f("U_LowPolyLighting", lowPolyLighting.get() ? 1.0f : 0.0f);
    }

    @EventHandler(priority = 0)
    private void onRenderEntityShadows(Render3DEvent event) {
        if (!entityShadows.get() || mc.world == null || mc.player == null) return;

        var camera = mc.gameRenderer.getCamera();
        Vec3d cameraPos = camera.getCameraPos();
        Vec3d cameraForward = Vec3d.fromPolar(camera.getPitch(), camera.getYaw()).normalize();
        double range = entityShadowRange.get();
        double rangeSquared = range * range;
        List<LivingEntity> visible = new ArrayList<>();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;
            if (living == camera.getFocusedEntity() && mc.options.getPerspective().isFirstPerson()) continue;

            Vec3d position = living.getLerpedPos(event.tickDelta);
            Vec3d center = position.add(0.0, living.getBoundingBox().getLengthY() * 0.5, 0.0);
            Vec3d toEntity = center.subtract(cameraPos);
            double distanceSquared = toEntity.lengthSquared();
            if (distanceSquared > rangeSquared || distanceSquared < 0.0001) continue;

            double angularRadius = Math.max(living.getBoundingBox().getLengthX(), living.getBoundingBox().getLengthZ())
                / Math.sqrt(distanceSquared);
            if (cameraForward.dotProduct(toEntity.normalize()) + angularRadius < 0.42) continue;
            visible.add(living);
        }

        visible.sort(Comparator.comparingDouble(entity -> cameraPos.squaredDistanceTo(entity.getLerpedPos(event.tickDelta))));
        int rendered = 0;
        for (LivingEntity living : visible) {
            if (rendered >= maxShadowEntities.get()) break;
            if (living != mc.player && !mc.player.canSee(living)) continue;
            if (drawEntityShadow(event, living, cameraPos, range)) rendered++;
        }
    }

    private boolean drawEntityShadow(Render3DEvent event, LivingEntity entity, Vec3d cameraPos, double range) {
        Vec3d position = entity.getLerpedPos(event.tickDelta);
        Vec3d rayStart = position.add(0.0, 0.15, 0.0);
        Vec3d rayEnd = position.add(0.0, -14.0, 0.0);
        BlockHitResult hit = mc.world.raycast(new RaycastContext(
            rayStart, rayEnd, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, entity));
        if (hit.getType() != HitResult.Type.BLOCK || hit.getSide() != Direction.UP) return false;

        Vec3d ground = hit.getPos();
        double height = Math.max(0.0, position.y - ground.y);
        double width = Math.max(entity.getBoundingBox().getLengthX(), entity.getBoundingBox().getLengthZ());
        double baseRadius = Math.clamp(width * 0.58, 0.25, 1.75);

        double dayAngle = (mc.world.getTimeOfDay() % 24000L) / 24000.0 * Math.PI * 2.0;
        double sunHeight = Math.sin(dayAngle);
        double daylight = Math.clamp((sunHeight + 0.08) / 0.55, 0.0, 1.0);
        double azimuth = dayAngle + Math.PI * 0.18;
        double lightX = Math.cos(azimuth);
        double lightZ = Math.sin(azimuth);
        double elevation = 0.16 + Math.max(sunHeight, 0.0) * 0.84;

        double distanceFade = 1.0 - Math.clamp(cameraPos.distanceTo(ground) / range, 0.0, 1.0);
        double lightLevel = mc.world.getLightLevel(entity.getBlockPos()) / 15.0;
        double visibility = 0.42 + 0.58 * Math.pow(Math.clamp(lightLevel, 0.0, 1.0), 0.70);
        int baseAlpha = (int) Math.clamp(entityShadowOpacity.get() * distanceFade * visibility * 255.0, 0.0, 215.0);
        if (baseAlpha <= 2) return false;

        double y = ground.y + 0.012;

        // Stable contact shadow: always present, prevents the entity from appearing detached from the ground.
        drawShadowFan(event, ground.x, y, ground.z, lightX, lightZ,
            baseRadius * 0.96, baseRadius * 0.72,
            new Color(4, 9, 18, (int) (baseAlpha * 0.72)),
            new Color(10, 21, 38, (int) (baseAlpha * 0.08)),
            10);

        // Directional cast shadow: longer near sunrise/sunset, but fades at night.
        if (daylight > 0.02) {
            double majorRadius = baseRadius * (1.30 + (1.0 - elevation) * 2.15) + Math.min(height * 0.15, 1.5);
            double minorRadius = baseRadius * (0.78 + elevation * 0.12);
            double centerShift = Math.min(height * 0.12 + majorRadius * 0.26, 2.4);
            double centerX = ground.x - lightX * centerShift;
            double centerZ = ground.z - lightZ * centerShift;
            int castAlpha = (int) (baseAlpha * (0.28 + daylight * 0.44));

            drawShadowFan(event, centerX, y + 0.001, centerZ, lightX, lightZ,
                majorRadius, minorRadius,
                new Color(6, 14, 27, castAlpha),
                new Color(18, 35, 57, (int) (castAlpha * 0.055)),
                12);
        }
        return true;
    }

    private void drawShadowFan(Render3DEvent event, double centerX, double y, double centerZ,
                               double lightX, double lightZ, double majorRadius, double minorRadius,
                               Color centerColor, Color edgeColor, int sides) {
        for (int side = 0; side < sides; side++) {
            double angleA = Math.PI * 2.0 * side / sides;
            double angleB = Math.PI * 2.0 * (side + 1) / sides;
            Vec3d a = shadowPoint(centerX, y, centerZ, lightX, lightZ, majorRadius, minorRadius, angleA);
            Vec3d b = shadowPoint(centerX, y, centerZ, lightX, lightZ, majorRadius, minorRadius, angleB);
            event.depthRenderer.quad(
                centerX, y, centerZ,
                a.x, a.y, a.z,
                b.x, b.y, b.z,
                b.x, b.y, b.z,
                centerColor, edgeColor, edgeColor, edgeColor);
        }
    }

    private Vec3d shadowPoint(double centerX, double y, double centerZ, double lightX, double lightZ,
                              double majorRadius, double minorRadius, double angle) {
        double along = Math.cos(angle) * majorRadius;
        double across = Math.sin(angle) * minorRadius;
        double perpendicularX = -lightZ;
        double perpendicularZ = lightX;
        return new Vec3d(
            centerX - lightX * along + perpendicularX * across,
            y,
            centerZ - lightZ * along + perpendicularZ * across);
    }

    public enum CloudStyle {
        Soft("柔和", 0.0f),
        LowPoly("低多边形", 1.0f),
        Hybrid("混合", 2.0f);

        private final String title;
        private final float shaderValue;

        CloudStyle(String title, float shaderValue) {
            this.title = title;
            this.shaderValue = shaderValue;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    public enum CloudQuality {
        Low("低", 5.0f),
        Medium("中", 8.0f),
        High("高", 12.0f);

        private final String title;
        private final float steps;

        CloudQuality(String title, float steps) {
            this.title = title;
            this.steps = steps;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    public enum RainMode {
        Off("关闭", false, false),
        World("世界雨", true, false),
        Screen("玻璃雨流", false, true),
        Both("两者", true, true);

        private final String title;
        private final boolean world;
        private final boolean screen;

        RainMode(String title, boolean world, boolean screen) {
            this.title = title;
            this.world = world;
            this.screen = screen;
        }

        public boolean hasWorld() {
            return world;
        }

        public boolean hasScreen() {
            return screen;
        }

        @Override
        public String toString() {
            return title;
        }
    }
}
