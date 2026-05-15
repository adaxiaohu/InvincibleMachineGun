package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.utils.alien.AlienEasing;
import com.codigohasta.addon.utils.alien.AlienFadeUtils;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.option.Perspective;

public class CameraClip extends Module {
    public static CameraClip INSTANCE;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> distance = sgGeneral.add(new DoubleSetting.Builder()
        .name("distance")
        .description("Camera distance.")
        .defaultValue(4.0)
        .min(1.0)
        .max(20.0)
        .sliderRange(1.0, 20.0)
        .build()
    );

    private final Setting<Integer> animationTime = sgGeneral.add(new IntSetting.Builder()
        .name("animation-time")
        .description("Animation time in milliseconds.")
        .defaultValue(200)
        .min(0)
        .max(1000)
        .sliderMax(1000)
        .build()
    );

    private final Setting<AlienEasing> ease = sgGeneral.add(new EnumSetting.Builder<AlienEasing>()
        .name("ease")
        .description("Easing mode for camera distance animation.")
        .defaultValue(AlienEasing.CubicInOut)
        .build()
    );

    private final Setting<Boolean> noFront = sgGeneral.add(new BoolSetting.Builder()
        .name("no-front")
        .description("Disable front third-person perspective.")
        .defaultValue(false)
        .build()
    );

    private final AlienFadeUtils animation = new AlienFadeUtils(300L);
    private boolean wasFirstPerson;

    public CameraClip() {
        super(AddonTemplate.CATEGORY, "R视角切换动画", "摄像机穿墙 - 允许你的摄像机穿过墙看，主要是有一个平滑的动画，来自AlienV4的CameraClip模块。");
        INSTANCE = this;
    }

    @Override
    public void onActivate() {
        // Sync animation length BEFORE reset, so ease() uses the correct length immediately
        animation.setLength(animationTime.get());
        animation.reset();
        wasFirstPerson = mc.options.getPerspective() == Perspective.FIRST_PERSON;
    }

    @Override
    public void onDeactivate() {
        wasFirstPerson = false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.options.getPerspective() == Perspective.THIRD_PERSON_FRONT && noFront.get()) {
            mc.options.setPerspective(Perspective.FIRST_PERSON);
        }

        animation.setLength(animationTime.get());

        // Detect perspective transitions and reset animation
        if (mc.options.getPerspective() == Perspective.FIRST_PERSON) {
            if (!wasFirstPerson) {
                wasFirstPerson = true;
                animation.reset();
            }
        } else if (wasFirstPerson) {
            wasFirstPerson = false;
            animation.reset();
        }
    }

    public double getDistance() {
        double quad = animation.ease(ease.get());
        if (mc.options.getPerspective() == Perspective.FIRST_PERSON) {
            // Animate from max to 0 when switching to first person
            return distance.get() * (1.0 - quad);
        } else {
            // Minimum 0.5 to avoid camera starting inside the player's head model
            return 0.5 + (distance.get() - 0.5) * quad;
        }
    }
}
