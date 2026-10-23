package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.utils.alien.AlienRotationUtil;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;

public class RoundRotation extends Module {
    public static RoundRotation INSTANCE;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Boolean> showPitch = sgGeneral.add(new BoolSetting.Builder()
        .name("show-pitch")
        .description("同时显示服务端的俯仰角。")
        .defaultValue(true)
        .build());
    private final Setting<Boolean> sync = sgGeneral.add(new BoolSetting.Builder()
        .name("sync")
        .description("每次发包都刷新渲染朝向")
        .defaultValue(false)
        .build());

    public RoundRotation() {
        super(AddonTemplate.CATEGORY, "显示服务端转头",
            "在第三人称显示服务端看到的你的朝向");
        INSTANCE = this;
    }

    public boolean shouldShow() {
        return isActive();
    }

    public boolean showPitchEnabled() {
        return showPitch.get();
    }

    public boolean syncEnabled() {
        return sync.get();
    }

    
    public float renderYaw() {
        if (AlienRotationUtil.shouldRotate) return AlienRotationUtil.fixYaw;
        return AlienRotationUtil.rotationYaw;
    }

    public float renderPitch() {
        if (!showPitch.get()) return 0.0F;
        if (AlienRotationUtil.shouldRotate) return AlienRotationUtil.fixPitch;
        return AlienRotationUtil.rotationPitch;
    }
}
