package com.codigohasta.addon.modules;

import net.minecraft.util.math.Vec3d;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.meteor.MouseScrollEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

public class KnockbackDirection extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgControls = settings.createGroup("Controls");

    // --- 改为偏移量 (Offset) ---
    private final Setting<Double> yawOffset = sgGeneral.add(new DoubleSetting.Builder()
        .name("yaw-offset")
        .description("Rotation relative to your current view. 180 = Pull towards you, 90 = To the left.")
        .defaultValue(180.0) // 默认180度，即反向拉回
        .min(-180)
        .max(180)
        .sliderMin(-180)
        .sliderMax(180)
        .build()
    );

    private final Setting<Double> recoilPercent = sgGeneral.add(new DoubleSetting.Builder()
        .name("recoil-amount")
        .description("How much to twitch back towards original view (0-1).")
        .defaultValue(0.3)
        .min(0)
        .max(1)
        .build()
    );

    private final Setting<Keybind> modifierKey = sgControls.add(new KeybindSetting.Builder()
        .name("modifier-key")
        .defaultValue(Keybind.fromKey(GLFW.GLFW_KEY_LEFT_ALT))
        .build()
    );

    private final Setting<Double> scrollStep = sgControls.add(new DoubleSetting.Builder()
        .name("scroll-step")
        .defaultValue(15.0)
        .build()
    );

    private boolean isInternalAttack = false;

    public KnockbackDirection() {
        super(AddonTemplate.CATEGORY, "knockback-direction", "控制击退方向，反作弊服务器不会绕过。原理就是这样的，转头attack");
    }

    // --- 滚轮调整偏移量 ---
    @EventHandler
    private void onMouseScroll(MouseScrollEvent event) {
        if (mc.currentScreen != null) return;
        if (modifierKey.get().isPressed()) {
            double newOffset = MathHelper.wrapDegrees(yawOffset.get() + event.value * scrollStep.get());
            yawOffset.set(newOffset);
            // 提示现在的效果，比如 "Pulling (180.0)"
            info("Yaw Offset: " + String.format("%.1f", newOffset));
            event.cancel();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onAttack(AttackEntityEvent event) {
        if (isInternalAttack || mc.player == null) return;

        Entity target = event.entity;
        event.cancel(); // 拦截原始攻击

        isInternalAttack = true;

        // 1. 获取当前实时数据
        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();
        boolean onGround = mc.player.isOnGround();

        // 2. 计算目标绝对角度 (当前视角 + 偏移量)
        float absoluteTargetYaw = (float) MathHelper.wrapDegrees(currentYaw + yawOffset.get());

        // 3. 发送 [瞬间转身] 数据包
        // 这一步告诉服务器：我已经看转向目标方向了
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(absoluteTargetYaw, currentPitch, onGround, mc.player.horizontalCollision));

        // 4. 发送 [攻击] 数据包
        // 紧随其后，服务器会用 absoluteTargetYaw 来计算击退向量
        mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(target, mc.player.isSneaking()));
        mc.player.swingHand(Hand.MAIN_HAND);

        // 5. 发送 [回弹/抽动] 数据包
        // 计算一个中间角度，让动作看起来像高灵敏度甩动
        float recoilYaw = (float) interpolateAngle(currentYaw, absoluteTargetYaw, recoilPercent.get());
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(recoilYaw, currentPitch, onGround, mc.player.horizontalCollision));

        isInternalAttack = false;
    }

    private double interpolateAngle(double start, double end, double factor) {
        double difference = end - start;
        while (difference < -180) difference += 360;
        while (difference >= 180) difference -= 360;
        return start + (difference * factor);
    }
}