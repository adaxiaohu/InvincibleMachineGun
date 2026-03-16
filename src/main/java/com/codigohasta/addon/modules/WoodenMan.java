package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;

public class WoodenMan extends Module {
    // 定义模式枚举
    public enum Mode {
        行走,
        瞬移
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTeleport = settings.createGroup("瞬移设置");

    // --- 基础设置 ---
    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("模式")
        .description("当被发现时的行为模式。")
        .defaultValue(Mode.行走)
        .build());

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("检测范围")
        .description("检测玩家的最大半径。")
        .defaultValue(30)
        .min(1)
        .sliderMax(100)
        .build());

    private final Setting<Double> stopDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("停止距离")
        .description("靠近目标到此距离时停止走动。")
        .defaultValue(2.0)
        .min(0.5)
        .sliderMax(10)
        .build());

    private final Setting<Double> fovThreshold = sgGeneral.add(new DoubleSetting.Builder()
        .name("视场角阈值")
        .description("对方看你时判定的角度范围（建议75左右）。")
        .defaultValue(75.0)
        .min(10)
        .max(130)
        .build());

    private final Setting<Boolean> raytraceSetting = sgGeneral.add(new BoolSetting.Builder()
        .name("射线追踪")
        .description("开启后，中间隔着墙看你不会判定为被看见。")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> sneakOnDetect = sgGeneral.add(new BoolSetting.Builder()
        .name("被看时蹲下")
        .description("被发现时自动按下潜行键（真实按键）。")
        .defaultValue(true)
        .build());

    // --- 瞬移设置 ---
    private final Setting<Double> tpOffset = sgTeleport.add(new DoubleSetting.Builder()
        .name("瞬移偏移")
        .description("瞬移到目标身后多少格。")
        .defaultValue(1.5)
        .min(0.5)
        .max(4.0)
        .visible(() -> mode.get() == Mode.瞬移)
        .build());

    private final Setting<Double> stepSize = sgTeleport.add(new DoubleSetting.Builder()
        .name("分段步长")
        .description("分段传送时每步的最大距离（越小越能绕过反作弊，建议2-4）。")
        .defaultValue(3.0)
        .min(0.1)
        .max(10.0)
        .visible(() -> mode.get() == Mode.瞬移)
        .build());

    private final Setting<Integer> cooldown = sgTeleport.add(new IntSetting.Builder()
        .name("瞬移冷却")
        .description("两次瞬移之间的Tick间隔（20tick=1秒）。")
        .defaultValue(20)
        .min(0)
        .max(100)
        .visible(() -> mode.get() == Mode.瞬移)
        .build());

    public WoodenMan() {
        super(AddonTemplate.CATEGORY, "wooden-man", "123木头人：被盯住时背身或瞬移。");
    }

    private int tpTimer = 0;

    @Override
    public void onDeactivate() {
        // 关闭模块时重置所有按键状态
        mc.options.sneakKey.setPressed(false);
        mc.options.forwardKey.setPressed(false);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (tpTimer > 0) tpTimer--;

        // 寻找最近的目标
        PlayerEntity target = mc.world.getPlayers().stream()
            .filter(p -> p != mc.player && p.isAlive() && !p.isSpectator())
            .filter(p -> mc.player.distanceTo(p) <= range.get())
            .min(Comparator.comparingDouble(p -> mc.player.distanceTo(p)))
            .orElse(null);

        if (target == null) {
            stopAllMovement();
            return;
        }

        if (isBeingWatchedBy(target)) {
            // --- 状态：被发现了 ---
            mc.options.forwardKey.setPressed(false);
            if (sneakOnDetect.get()) mc.options.sneakKey.setPressed(true);

            if (mode.get() == Mode.瞬移) {
                if (tpTimer <= 0) {
                    doStepTeleport(target);
                    tpTimer = cooldown.get();
                }
            } else {
                // 行走模式：背对敌人
                Rotations.rotate((float) (Rotations.getYaw(target) + 180), 0);
            }
        } else {
            // --- 状态：未被发现（安全） ---
            if (sneakOnDetect.get()) mc.options.sneakKey.setPressed(false);
            
            double dist = mc.player.distanceTo(target);
            Rotations.rotate(Rotations.getYaw(target), Rotations.getPitch(target), () -> {
                if (dist > stopDistance.get()) {
                    mc.options.forwardKey.setPressed(true);
                    // 自动跳跃避障
                    if (mc.player.horizontalCollision && mc.player.isOnGround()) {
                        mc.player.jump();
                    }
                } else {
                    mc.options.forwardKey.setPressed(false);
                }
            });
        }
    }

    /**
     * 分段传送逻辑：通过线性插值发送多个移动包，防止距离过远被拦截
     */
    private void doStepTeleport(PlayerEntity target) {
        // 1. 计算最终目标点（目标背后）
        Vec3d lookVec = target.getRotationVec(1.0F);
        Vec3d behindVec = new Vec3d(lookVec.x, 0, lookVec.z).normalize().multiply(-tpOffset.get());
        Vec3d dest = new Vec3d(target.getX() + behindVec.x, target.getY(), target.getZ() + behindVec.z);

        Vec3d current = mc.player.getPos();
        double totalDist = current.distanceTo(dest);
        
        // 2. 根据步长计算分段数
        int steps = (int) Math.ceil(totalDist / stepSize.get());

        // 3. 执行分段发包
        if (mc.getNetworkHandler() != null) {
            for (int i = 1; i <= steps; i++) {
                double t = (double) i / steps;
                double pX = current.x + (dest.x - current.x) * t;
                double pY = current.y + (dest.y - current.y) * t;
                double pZ = current.z + (dest.z - current.z) * t;

                // 发送瞬时的位置同步包
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    pX, pY, pZ, mc.player.isOnGround(), false
                ));
            }
        }

        // 4. 更新本地坐标
        mc.player.setPosition(dest.x, dest.y, dest.z);
        
        // 5. 传完后看向对方
        Rotations.rotate(target.getYaw(), 0);
    }

    private void stopAllMovement() {
        mc.options.forwardKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
    }

    /**
     * 判定观察者是否在注视本地玩家
     */
    private boolean isBeingWatchedBy(PlayerEntity observer) {
        Vec3d lookVec = observer.getRotationVec(1.0F).normalize();
        Vec3d diffVec = new Vec3d(
            mc.player.getX() - observer.getX(),
            mc.player.getEyeY() - observer.getEyeY(),
            mc.player.getZ() - observer.getZ()
        ).normalize();

        double dot = lookVec.dotProduct(diffVec);
        double angle = Math.toDegrees(Math.acos(dot));

        // 如果在视野角度内
        if (angle < fovThreshold.get()) {
            // 修正：引用正确定义的 raytraceSetting
            if (raytraceSetting.get()) {
                return observer.canSee(mc.player);
            }
            return true;
        }
        return false;
    }
}