package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.utils.alien.AlienAnimateUtil;
import com.codigohasta.addon.utils.alien.AlienMathUtil;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class MotionCamera extends Module {
    public static MotionCamera INSTANCE;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Boolean> noFirstPerson = sgGeneral.add(new BoolSetting.Builder()
        .name("no-first-person")
        .description("在第一人称视角下禁用 MotionCamera。")
        .defaultValue(true)
        .build()
    );

    public final Setting<Double> firstPersonSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("first-person-speed")
        .description("第一人称视角下的插值速度。")
        .defaultValue(0.6)
        .min(0.0)
        .max(1.0)
        .sliderMax(1.0)
        .build()
    );

    public final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("第三人称视角下的插值速度。")
        .defaultValue(0.3)
        .min(0.0)
        .max(1.0)
        .sliderMax(1.0)
        .build()
    );

    private double fakeX;
    private double fakeY;
    private double fakeZ;
    private double prevFakeX;
    private double prevFakeY;
    private double prevFakeZ;

    public MotionCamera() {
        super(AddonTemplate.CATEGORY, "R丝滑相机", "平滑插值相机位置，营造电影般的镜头运动效果，来自AlienV4的MotionCamera模块。");
        INSTANCE = this;
    }

    public boolean on() {
        return isActive() && (!noFirstPerson.get() || !mc.options.getPerspective().isFirstPerson());
    }

    @Override
    public void onActivate() {
        if (mc.player != null) {
            fakeX = mc.player.getX();
            fakeY = mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose());
            fakeZ = mc.player.getZ();
            prevFakeX = fakeX;
            prevFakeY = fakeY;
            prevFakeZ = fakeZ;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        prevFakeX = fakeX;
        prevFakeY = fakeY;
        prevFakeZ = fakeZ;

        double spd = mc.options.getPerspective().isFirstPerson() ? firstPersonSpeed.get() : speed.get();

        fakeX = AlienAnimateUtil.animate(fakeX, mc.player.getX(), spd);
        fakeY = AlienAnimateUtil.animate(fakeY, mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()), spd);
        fakeZ = AlienAnimateUtil.animate(fakeZ, mc.player.getZ(), spd);
    }

    public double getFakeX(float tickDelta) {
        return AlienMathUtil.interpolate(prevFakeX, fakeX, tickDelta);
    }

    public double getFakeY(float tickDelta) {
        return AlienMathUtil.interpolate(prevFakeY, fakeY, tickDelta);
    }

    public double getFakeZ(float tickDelta) {
        return AlienMathUtil.interpolate(prevFakeZ, fakeZ, tickDelta);
    }
}
