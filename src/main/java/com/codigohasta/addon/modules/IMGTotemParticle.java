package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.events.TotemParticleEvent;
import com.codigohasta.addon.utils.alien.AlienColorUtil;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;

import java.awt.Color;
import java.util.Random;

public class IMGTotemParticle extends Module {
    public static IMGTotemParticle INSTANCE;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> velocityXZ = sgGeneral.add(new IntSetting.Builder()
        .name("velocity-xz")
        .description("水平速度乘数。")
        .defaultValue(100)
        .min(0)
        .max(500)
        .sliderMax(500)
        .build()
    );

    private final Setting<Integer> velocityY = sgGeneral.add(new IntSetting.Builder()
        .name("velocity-y")
        .description("垂直速度乘数。")
        .defaultValue(100)
        .min(0)
        .max(500)
        .sliderMax(500)
        .build()
    );

    private final Setting<SettingColor> color1 = sgGeneral.add(new ColorSetting.Builder()
        .name("color-1")
        .description("第一个粒子颜色，用于渐变效果。")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );

    private final Setting<SettingColor> color2 = sgGeneral.add(new ColorSetting.Builder()
        .name("color-2")
        .description("第二个粒子颜色，用于渐变效果。")
        .defaultValue(new SettingColor(0, 0, 0, 255))
        .build()
    );

    private final Random random = new Random();

    public IMGTotemParticle() {
        super(AddonTemplate.CATEGORY, "R图腾粒子自定义", "自定义图腾粒子的颜色和速度。来自AlienV4的TotemParticle模块。");
        INSTANCE = this;
    }

    @EventHandler
    private void onTotemParticle(TotemParticleEvent event) {
        event.cancel();
        event.velocityX = event.velocityX * (velocityXZ.get() / 100.0);
        event.velocityZ = event.velocityZ * (velocityXZ.get() / 100.0);
        event.velocityY = event.velocityY * (velocityY.get() / 100.0);
        Color c1 = new Color(color1.get().r, color1.get().g, color1.get().b, color1.get().a);
        Color c2 = new Color(color2.get().r, color2.get().g, color2.get().b, color2.get().a);
        event.color = AlienColorUtil.fadeColor(c1, c2, random.nextDouble());
    }
}
