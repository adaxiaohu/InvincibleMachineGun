package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class ElytraFlyPlus extends Module {
    public ElytraFlyPlus() {
        
        super(AddonTemplate.CATEGORY, "elytra-fly-plus", "抄袭的blackout的平飞");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSpeed = settings.createGroup("速度设置");

    //--------------------通用设置--------------------//
    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("模式")
        .description("飞行的控制模式。")
        .defaultValue(Mode.Wasp)
        .build()
    );

    private final Setting<Boolean> stopWater = sgGeneral.add(new BoolSetting.Builder()
        .name("水中停止")
        .description("在水中时不修改移动。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> stopLava = sgGeneral.add(new BoolSetting.Builder()
        .name("岩浆中停止")
        .description("在岩浆中时不修改移动。")
        .defaultValue(true)
        .build()
    );

    //--------------------速度设置--------------------//
    private final Setting<Double> horizontal = sgSpeed.add(new DoubleSetting.Builder()
        .name("水平速度")
        .description("每刻水平移动的方块数。")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 5)
        .visible(() -> mode.get() == Mode.Wasp)
        .build()
    );

    private final Setting<Double> up = sgSpeed.add(new DoubleSetting.Builder()
        .name("上升速度")
        .description("每刻上升的方块数。")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 5)
        .visible(() -> mode.get() == Mode.Wasp)
        .build()
    );

    private final Setting<Double> speed = sgSpeed.add(new DoubleSetting.Builder()
        .name("基础速度")
        .description("每刻移动的基础速度。")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 5)
        .visible(() -> mode.get() == Mode.Control)
        .build()
    );

    private final Setting<Double> upMultiplier = sgSpeed.add(new DoubleSetting.Builder()
        .name("上升倍率")
        .description("向上飞行时的速度倍率。")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 5)
        .visible(() -> mode.get() == Mode.Control)
        .build()
    );

    private final Setting<Double> down = sgSpeed.add(new DoubleSetting.Builder()
        .name("下降速度")
        .description("每刻下降的方块数。")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 5)
        .visible(() -> mode.get() == Mode.Control || mode.get() == Mode.Wasp)
        .build()
    );

    private final Setting<Boolean> smartFall = sgSpeed.add(new BoolSetting.Builder()
        .name("智能下降")
        .description("仅在看下方时快速下降。")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.Wasp)
        .build()
    );

    private final Setting<Double> fallSpeed = sgSpeed.add(new DoubleSetting.Builder()
        .name("滑翔速度")
        .description("每刻自然下落的速度。")
        .defaultValue(0.01)
        .min(0)
        .sliderRange(0, 1)
        .visible(() -> mode.get() == Mode.Control || mode.get() == Mode.Wasp)
        .build()
    );

    private final Setting<Double> constSpeed = sgSpeed.add(new DoubleSetting.Builder()
        .name("恒定速度")
        .description("Constantiam 模式的最大速度。")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 5)
        .visible(() -> mode.get() == Mode.Constantiam)
        .build()
    );

    private final Setting<Double> constAcceleration = sgSpeed.add(new DoubleSetting.Builder()
        .name("恒定加速度")
        .description("Constantiam 模式的加速度。")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 5)
        .visible(() -> mode.get() == Mode.Constantiam)
        .build()
    );

    private final Setting<Boolean> constStop = sgSpeed.add(new BoolSetting.Builder()
        .name("无输入停止")
        .description("没有按键输入时停止移动。")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.Constantiam)
        .build()
    );

    private boolean moving;
    private float yaw;
    private float pitch;
    private float p;
    private double velocity;
    private int activeFor;

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onMove(PlayerMoveEvent event) {
        if (!shouldRun()) return;

        activeFor++;
        if (activeFor < 5) return;

        switch (mode.get()) {
            case Wasp -> waspTick(event);
            case Control -> controlTick(event);
            case Constantiam -> constantiamTick(event);
        }
    }

    private void constantiamTick(PlayerMoveEvent event) {
        Vec3d motion = getMotion(mc.player.getVelocity());
        if (motion != null) {
            ((IVec3d) event.movement).meteor$set(motion.getX(), motion.getY(), motion.getZ());
            event.movement = motion;
        }
    }

    private Vec3d getMotion(Vec3d velocity) {
        // 适配 1.21.11 输入
        float forwardInput = (mc.player.input.playerInput.forward() ? 1.0f : 0.0f) - (mc.player.input.playerInput.backward() ? 1.0f : 0.0f);
        
        if (forwardInput == 0) {
            if (constStop.get()) return new Vec3d(0, 0, 0);
            return null;
        }

        boolean forward = forwardInput > 0;

        double yaw = Math.toRadians(mc.player.getYaw() + (forward ? 90 : -90));

        double x = Math.cos(yaw);
        double z = Math.sin(yaw);
        double maxAcc = calcAcceleration(velocity.x, velocity.z, x, z);
        
        // 简单模拟 getLerpProgress (value - min) / (max - min)
        double progress = (velocity.horizontalLength() - 0) / (0.5 - 0);
        double delta = MathHelper.clamp(progress, 0, 1);

        double acc = Math.min(maxAcc, constAcceleration.get() / 20 * (0.1 + delta * 0.9));
        return new Vec3d(velocity.getX() + x * acc, velocity.getY(), velocity.getZ() + z * acc);
    }

    private double calcAcceleration(double vx, double vz, double x, double z) {
        double xz = x * x + z * z;
        return (Math.sqrt(xz * constSpeed.get() * constSpeed.get() - x * x * vz * vz - z * z * vx * vx + 2 * x * z * vx * vz) - x * vx - z * vz) / xz;
    }

    // Wasp 模式
    private void waspTick(PlayerMoveEvent event) {
        if (!mc.player.isGliding()) return;

        updateWaspMovement();
        pitch = mc.player.getPitch();

        double cos = Math.cos(Math.toRadians(yaw + 90));
        double sin = Math.sin(Math.toRadians(yaw + 90));

        double x = moving ? cos * horizontal.get() : 0;
        double y = -fallSpeed.get();
        double z = moving ? sin * horizontal.get() : 0;

        if (smartFall.get()) {
            y *= Math.abs(Math.sin(Math.toRadians(pitch)));
        }

        if (mc.options.sneakKey.isPressed() && !mc.options.jumpKey.isPressed()) {
            y = -down.get();
        }
        if (!mc.options.sneakKey.isPressed() && mc.options.jumpKey.isPressed()) {
            y = up.get();
        }

        ((IVec3d) event.movement).meteor$set(x, y, z);
        mc.player.setVelocity(0, 0, 0);
    }

    private void updateWaspMovement() {
        float yaw = mc.player.getYaw();

        // 适配 1.21.11 输入逻辑
        float f = (mc.player.input.playerInput.forward() ? 1.0f : 0.0f) - (mc.player.input.playerInput.backward() ? 1.0f : 0.0f);
        float s = (mc.player.input.playerInput.left() ? 1.0f : 0.0f) - (mc.player.input.playerInput.right() ? 1.0f : 0.0f);

        if (f > 0) {
            moving = true;
            yaw += s > 0 ? -45 : s < 0 ? 45 : 0;
        } else if (f < 0) {
            moving = true;
            yaw += s > 0 ? -135 : s < 0 ? 135 : 180;
        } else {
            moving = s != 0;
            yaw += s > 0 ? -90 : s < 0 ? 90 : 0;
        }
        this.yaw = yaw;
    }

    // Control 模式
    private void controlTick(PlayerMoveEvent event) {
        if (!mc.player.isGliding()) {return;}

        updateControlMovement();
        pitch = 0;

        boolean movingUp = false;

        if (!mc.options.sneakKey.isPressed() && mc.options.jumpKey.isPressed() && velocity > speed.get() * 0.4) {
            p = (float) Math.min(p + 0.1 * (1 - p) * (1 - p) * (1 - p), 1f);

            pitch = Math.max(Math.max(p, 0) * -90, -90);

            movingUp = true;
            moving = false;
        } else {
            velocity = speed.get();
            p = -0.2f;
        }

        velocity = moving ? speed.get() : Math.min(velocity + Math.sin(Math.toRadians(pitch)) * 0.08, speed.get());

        double cos = Math.cos(Math.toRadians(yaw + 90));
        double sin = Math.sin(Math.toRadians(yaw + 90));

        double x = moving && !movingUp ? cos * speed.get() : movingUp ? velocity * Math.cos(Math.toRadians(pitch)) * cos : 0;
        double y = pitch < 0 ? velocity * upMultiplier.get() * -Math.sin(Math.toRadians(pitch)) * velocity : -fallSpeed.get();
        double z = moving && !movingUp ? sin * speed.get() : movingUp ? velocity * Math.cos(Math.toRadians(pitch)) * sin : 0;

        y *= Math.abs(Math.sin(Math.toRadians(movingUp ? pitch : mc.player.getPitch())));

        if (mc.options.sneakKey.isPressed() && !mc.options.jumpKey.isPressed()) {
            y = -down.get();
        }

        ((IVec3d) event.movement).meteor$set(x, y, z);
        mc.player.setVelocity(0, 0, 0);
    }

    private void updateControlMovement() {
        float yaw = mc.player.getYaw();

        // 适配 1.21.11 输入逻辑
        float f = (mc.player.input.playerInput.forward() ? 1.0f : 0.0f) - (mc.player.input.playerInput.backward() ? 1.0f : 0.0f);
        float s = (mc.player.input.playerInput.left() ? 1.0f : 0.0f) - (mc.player.input.playerInput.right() ? 1.0f : 0.0f);

        if (f > 0) {
            moving = true;
            yaw += s > 0 ? -45 : s < 0 ? 45 : 0;
        } else if (f < 0) {
            moving = true;
            yaw += s > 0 ? -135 : s < 0 ? 135 : 180;
        } else {
            moving = s != 0;
            yaw += s > 0 ? -90 : s < 0 ? 90 : 0;
        }
        this.yaw = yaw;
    }

    public boolean shouldRun() {
        if (stopWater.get() && mc.player.isTouchingWater()) {
            activeFor = 0;
            return false;
        }
        if (stopLava.get() && mc.player.isInLava()) {
            activeFor = 0;
            return false;
        }
        return mc.player.isGliding();
    }

    public enum Mode {
        Wasp,
        Control,
        Constantiam
    }
}