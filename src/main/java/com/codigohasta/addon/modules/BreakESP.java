package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.utils.alien.*;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;

import java.awt.Color;
import java.text.DecimalFormat;

public class BreakESP extends Module {
    public static BreakESP INSTANCE;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    // General settings
    private final Setting<Boolean> progress = sgGeneral.add(new BoolSetting.Builder()
        .name("Progress")
        .description("Show break progress percentage.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Double> damage = sgGeneral.add(new DoubleSetting.Builder()
        .name("Damage")
        .description("Damage multiplier for break time calculation.")
        .defaultValue(1.0)
        .range(0.0, 2.0)
        .sliderRange(0.0, 2.0)
        .build()
    );
    private final Setting<AlienEasing> ease = sgGeneral.add(new EnumSetting.Builder<AlienEasing>()
        .name("Ease")
        .description("Easing mode for the box animation.")
        .defaultValue(AlienEasing.CubicInOut)
        .build()
    );
    private final Setting<Boolean> second = sgGeneral.add(new BoolSetting.Builder()
        .name("Second")
        .description("Show second/double break indicators.")
        .defaultValue(true)
        .build()
    );

    // Render settings - Box
    private final Setting<Boolean> boxToggle = sgRender.add(new BoolSetting.Builder()
        .name("Box")
        .description("Show box outline.")
        .defaultValue(true)
        .build()
    );
    private final Setting<SettingColor> boxColor = sgRender.add(new ColorSetting.Builder()
        .name("Box Color")
        .description("Color of the box outline.")
        .defaultValue(new SettingColor(198, 176, 12, 255))
        .build()
    );

    // Fill
    private final Setting<Boolean> fillToggle = sgRender.add(new BoolSetting.Builder()
        .name("Fill")
        .description("Show filled box.")
        .defaultValue(true)
        .build()
    );
    private final Setting<SettingColor> fillColor = sgRender.add(new ColorSetting.Builder()
        .name("Fill Color")
        .description("Color of the fill.")
        .defaultValue(new SettingColor(198, 176, 12, 78))
        .build()
    );

    // Friend Box
    private final Setting<Boolean> friendBoxToggle = sgRender.add(new BoolSetting.Builder()
        .name("Friend Box")
        .description("Show box outline for friends.")
        .defaultValue(true)
        .build()
    );
    private final Setting<SettingColor> friendBoxColor = sgRender.add(new ColorSetting.Builder()
        .name("Friend Box Color")
        .description("Color of the box outline for friends.")
        .defaultValue(new SettingColor(30, 45, 169, 255))
        .build()
    );

    // Friend Fill
    private final Setting<Boolean> friendFillToggle = sgRender.add(new BoolSetting.Builder()
        .name("Friend Fill")
        .description("Show filled box for friends.")
        .defaultValue(true)
        .build()
    );
    private final Setting<SettingColor> friendFillColor = sgRender.add(new ColorSetting.Builder()
        .name("Friend Fill Color")
        .description("Color of the fill for friends.")
        .defaultValue(new SettingColor(30, 45, 169, 78))
        .build()
    );

    // Second Box
    private final Setting<Boolean> secondBoxToggle = sgRender.add(new BoolSetting.Builder()
        .name("Second Box")
        .description("Show box outline for second break.")
        .defaultValue(true)
        .build()
    );
    private final Setting<SettingColor> secondBoxColor = sgRender.add(new ColorSetting.Builder()
        .name("Second Box Color")
        .description("Color of the second box outline.")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );

    // Second Fill
    private final Setting<Boolean> secondFillToggle = sgRender.add(new BoolSetting.Builder()
        .name("Second Fill")
        .description("Show filled box for second break.")
        .defaultValue(true)
        .build()
    );
    private final Setting<SettingColor> secondFillColor = sgRender.add(new ColorSetting.Builder()
        .name("Second Fill Color")
        .description("Color of the second fill.")
        .defaultValue(new SettingColor(255, 255, 255, 100))
        .build()
    );
    private final Setting<Double> maxTextScale = sgRender.add(new DoubleSetting.Builder()
        .name("TextMaxScale")
        .description("最大文字缩放上限。")
        .defaultValue(15.0)
        .min(1.0)
        .max(100.0)
        .sliderRange(1, 50)
        .build()
    );
    private final Setting<Double> textScaleBase = sgRender.add(new DoubleSetting.Builder()
        .name("TextScaleBase")
        .description("文字基础缩放（距离为0时的大小）。默认5.0")
        .defaultValue(5.0)
        .min(0.5)
        .max(30.0)
        .sliderRange(0.5, 20)
        .build()
    );
    private final Setting<Double> textScaleFactor = sgRender.add(new DoubleSetting.Builder()
        .name("TextScaleFactor")
        .description("每格距离增加的缩放值。默认0.1")
        .defaultValue(0.1)
        .min(0.0)
        .max(2.0)
        .sliderRange(0, 1)
        .build()
    );

    final DecimalFormat df = new DecimalFormat("0.0");
    final Color startColor = new Color(255, 6, 6);
    final Color endColor = new Color(0, 255, 12);
    final Color doubleColor = new Color(255, 179, 96);

    public BreakESP() {
        super(AddonTemplate.CATEGORY, "R挖掘显示者", "挖掘显示. 来自AlienV4的BreakESP模块。");
        INSTANCE = this;
    }

    @Override
    public void onActivate() {
        if (AlienBreakManager.INSTANCE != null) {
            AlienBreakManager.INSTANCE.damageMultiplier = damage.get();
        }
    }

    private Color getFillColor(PlayerEntity player) {
        return Friends.get().isFriend(player) ? toAwt(friendFillColor.get()) : toAwt(fillColor.get());
    }

    private Color getBoxColor(PlayerEntity player) {
        return Friends.get().isFriend(player) ? toAwt(friendBoxColor.get()) : toAwt(boxColor.get());
    }

    private boolean isBoxVisible(PlayerEntity player) {
        return Friends.get().isFriend(player) ? friendBoxToggle.get() : boxToggle.get();
    }

    private boolean isFillVisible(PlayerEntity player) {
        return Friends.get().isFriend(player) ? friendFillToggle.get() : fillToggle.get();
    }

    private Color toAwt(SettingColor c) {
        return new Color(c.r, c.g, c.b, c.a);
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        AlienBreakManager manager = AlienBreakManager.INSTANCE;
        if (manager == null) return;

        // Sync damage multiplier
        manager.damageMultiplier = damage.get();

        // Render break map entries
        for (AlienBreakManager.BreakData breakData : manager.breakMap.values()) {
            if (breakData == null || breakData.getEntity() == null) continue;

            PlayerEntity player = (PlayerEntity) breakData.getEntity();
            double easeVal = breakData.fade.ease(ease.get());
            double size = 0.5 * (1.0 - easeVal);
            Box cbox = new Box(breakData.pos).shrink(size, size, size).shrink(-size, -size, -size);

            if (isFillVisible(player)) {
                AlienRender3DUtil.drawFill(event, cbox, getFillColor(player));
            }
            if (isBoxVisible(player)) {
                AlienRender3DUtil.drawBox(event, cbox, getBoxColor(player));
            }

            // Name text
            AlienRender3DUtil.drawText3D(
                player.getName().getString(),
                breakData.pos.toCenterPos().add(0.0, progress.get() ? 0.15 : 0.0, 0.0),
                textScaleBase.get(), textScaleFactor.get(), maxTextScale.get(),
                -1
            );

            // Progress text
            if (progress.get()) {
                String progressText = breakData.failed
                    ? "§4Failed"
                    : (breakData.complete ? "Broke"
                    : df.format(Math.min(1.0, breakData.timer.getMs() / breakData.breakTime) * 100.0));

                Color progressColor = breakData.complete
                    ? (mc.world.isAir(breakData.pos) ? endColor : startColor)
                    : AlienColorUtil.fadeColor(startColor, endColor, breakData.timer.getMs() / breakData.breakTime);

                AlienRender3DUtil.drawText3D(
                    Text.of(progressText),
                    breakData.pos.toCenterPos().add(0.0, -0.15, 0.0),
                    0.0, 0.0, 1.0,
                    progressColor.getRGB(),
                    textScaleBase.get(), textScaleFactor.get(), maxTextScale.get()
                );
            }
        }

        // Render double break map
        if (second.get()) {
            for (int i : manager.doubleMap.keySet()) {
                AlienBreakManager.BreakData breakDatax = manager.doubleMap.get(i);
                if (breakDatax == null || breakDatax.getEntity() == null || mc.world.isAir(breakDatax.pos)) {
                    continue;
                }

                AlienBreakManager.BreakData singleBreakData = manager.breakMap.get(i);
                if (singleBreakData == null || !singleBreakData.pos.equals(breakDatax.pos)) {
                    double easeValx = breakDatax.fade.ease(ease.get());
                    double sizex = 0.5 * (1.0 - easeValx);
                    Box cboxx = new Box(breakDatax.pos).shrink(sizex, sizex, sizex).shrink(-sizex, -sizex, -sizex);

                    if (secondFillToggle.get()) {
                        AlienRender3DUtil.drawFill(event, cboxx, toAwt(secondFillColor.get()));
                    }
                    if (secondBoxToggle.get()) {
                        AlienRender3DUtil.drawBox(event, cboxx, toAwt(secondBoxColor.get()));
                    }

                    AlienRender3DUtil.drawText3D(
                        breakDatax.getEntity().getName().getString(),
                        breakDatax.pos.toCenterPos().add(0.0, 0.15, 0.0),
                        textScaleBase.get(), textScaleFactor.get(), maxTextScale.get(),
                        -1
                    );
                    AlienRender3DUtil.drawText3D(
                        "Double",
                        breakDatax.pos.toCenterPos().add(0.0, -0.15, 0.0),
                        textScaleBase.get(), textScaleFactor.get(), maxTextScale.get(),
                        doubleColor.getRGB()
                    );
                }
            }
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        AlienRender3DUtil.renderDeferred();
    }
}
