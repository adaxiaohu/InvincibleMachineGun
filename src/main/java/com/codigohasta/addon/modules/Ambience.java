package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;

public class Ambience extends Module {
    public static Ambience INSTANCE;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // Filter
    public final Setting<Boolean> filterEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("filter")
        .description("在屏幕上叠加滤镜颜色")
        .defaultValue(false)
        .build()
    );
    public final Setting<SettingColor> filterColor = sgGeneral.add(new ColorSetting.Builder()
        .name("filter-color")
        .description("滤镜颜色")
        .defaultValue(new SettingColor(255, 255, 255, 20))
        .visible(filterEnabled::get)
        .build()
    );

    // World Color
    public final Setting<Boolean> worldColorEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("world-color")
        .description("覆盖世界光照贴图颜色")
        .defaultValue(true)
        .build()
    );
    public final Setting<SettingColor> worldColor = sgGeneral.add(new ColorSetting.Builder()
        .name("world-color-value")
        .description("世界光照颜色")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .visible(worldColorEnabled::get)
        .build()
    );

    // Custom Time
    public final Setting<Boolean> customTime = sgGeneral.add(new BoolSetting.Builder()
        .name("custom-time")
        .description("启用自定义世界时间")
        .defaultValue(false)
        .build()
    );
    public final Setting<Integer> time = sgGeneral.add(new IntSetting.Builder()
        .name("time")
        .description("自定义时间值 (0-24000)")
        .defaultValue(0)
        .min(0)
        .max(24000)
        .sliderMax(24000)
        .visible(customTime::get)
        .build()
    );

    // Fog Color
    public final Setting<Boolean> fogEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("fog")
        .description("覆盖雾颜色")
        .defaultValue(false)
        .build()
    );
    public final Setting<SettingColor> fogColor = sgGeneral.add(new ColorSetting.Builder()
        .name("fog-color")
        .description("雾颜色")
        .defaultValue(new SettingColor(204, 136, 85))
        .visible(fogEnabled::get)
        .build()
    );

    // Sky Color
    public final Setting<Boolean> skyEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("sky")
        .description("覆盖天空颜色")
        .defaultValue(false)
        .build()
    );
    public final Setting<SettingColor> skyColor = sgGeneral.add(new ColorSetting.Builder()
        .name("sky-color")
        .description("天空颜色")
        .defaultValue(new SettingColor(0, 0, 0))
        .visible(skyEnabled::get)
        .build()
    );

    // Cloud Color
    public final Setting<Boolean> cloudEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("cloud")
        .description("覆盖云颜色")
        .defaultValue(false)
        .build()
    );
    public final Setting<SettingColor> cloudColor = sgGeneral.add(new ColorSetting.Builder()
        .name("cloud-color")
        .description("云颜色")
        .defaultValue(new SettingColor(0, 0, 0))
        .visible(cloudEnabled::get)
        .build()
    );

    // Dimension Color
    public final Setting<Boolean> dimensionColorEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("dimension-color")
        .description("覆盖维度背景颜色")
        .defaultValue(false)
        .build()
    );
    public final Setting<SettingColor> dimensionColor = sgGeneral.add(new ColorSetting.Builder()
        .name("dimension-color-value")
        .description("维度颜色")
        .defaultValue(new SettingColor(0, 0, 0))
        .visible(dimensionColorEnabled::get)
        .build()
    );

    // Fog Distance
    public final Setting<Boolean> fogDistance = sgGeneral.add(new BoolSetting.Builder()
        .name("fog-distance")
        .description("启用自定义雾距离")
        .defaultValue(false)
        .build()
    );
    public final Setting<Double> fogStart = sgGeneral.add(new DoubleSetting.Builder()
        .name("fog-start")
        .description("雾起始距离")
        .defaultValue(50)
        .min(0)
        .max(1000)
        .sliderMax(1000)
        .visible(fogDistance::get)
        .build()
    );
    public final Setting<Double> fogEnd = sgGeneral.add(new DoubleSetting.Builder()
        .name("fog-end")
        .description("雾结束距离")
        .defaultValue(100)
        .min(0)
        .max(1000)
        .sliderMax(1000)
        .visible(fogDistance::get)
        .build()
    );

    // Full Bright
    public final Setting<Boolean> fullBright = sgGeneral.add(new BoolSetting.Builder()
        .name("full-bright")
        .description("启用夜视效果")
        .defaultValue(false)
        .build()
    );

    // Force Overworld
    public final Setting<Boolean> forceOverworld = sgGeneral.add(new BoolSetting.Builder()
        .name("force-overworld")
        .description("强制使用主世界天空效果")
        .defaultValue(false)
        .build()
    );

    // Custom Luminance
    public final Setting<Boolean> customLuminance = sgGeneral.add(new BoolSetting.Builder()
        .name("custom-luminance")
        .description("启用自定义方块亮度")
        .defaultValue(false)
        .build()
    );
    public final Setting<Integer> luminance = sgGeneral.add(new IntSetting.Builder()
        .name("luminance")
        .description("自定义亮度值 (0-15)")
        .defaultValue(15)
        .min(0)
        .max(15)
        .sliderMax(15)
        .visible(customLuminance::get)
        .build()
    );

    public Ambience() {
        super(AddonTemplate.CATEGORY, "IMG环境效果", "来自alien。自定义环境效果，包括颜色、时间、雾气等");
        INSTANCE = this;
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (filterEnabled.get()) {
            event.drawContext.fill(0, 0, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight(), filterColor.get().getPacked());
        }
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (event.packet instanceof WorldTimeUpdateS2CPacket && customTime.get()) {
            event.cancel();
        }
    }
}
