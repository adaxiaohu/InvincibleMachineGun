package com.codigohasta.addon.modules;

import com.codigohasta.addon.utils.sakura.render.AbstractRowHud;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;

import java.util.Locale;

public class SpeedHud extends AbstractRowHud {

    private final SettingGroup sgSpeed = settings.createGroup("速度");

    private final Setting<Boolean> bps = sgSpeed.add(new BoolSetting.Builder()
        .name("用 b/s")
        .description("开 = 方块/秒（b/s）；关 = 千米/小时（km/h）。")
        .defaultValue(false).build());

    private final Setting<Boolean> horizontalOnly = sgSpeed.add(new BoolSetting.Builder()
        .name("只算水平速度")
        .description("取水平速度（不含下落）")
        .defaultValue(true).build());

    public SpeedHud() {
        super("speed-hud", "在你屏幕上画个面板告诉你，你有多快", 815, 512, 0.9);
    }

    @Override
    protected String iconName() {
        return "speed-widget";
    }

    @Override
    protected String displayText() {
        var player = mc.player;
        if (player == null) return "--";

        double vx = player.getVelocity().x;
        double vz = player.getVelocity().z;
        double vy = player.getVelocity().y;

        double speed;
        if (horizontalOnly.get()) {
            speed = Math.sqrt(vx * vx + vz * vz);
        } else {
            speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
        }

        float factor = bps.get() ? 200f : 720f;
        float value = (float) Math.rint(speed * factor) * 0.1f;

        return String.format(Locale.ROOT, "%.1f %s", value, bps.get() ? "b/s" : "km/h");
    }
}
