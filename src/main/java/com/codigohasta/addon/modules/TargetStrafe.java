package com.codigohasta.addon.modules;

import net.minecraft.util.math.Vec3d;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

public class TargetStrafe extends Module {

    public enum MoveMode {
        基础,
        滚动
    }

    public enum ExecuteMode {
        游戏刻, // Tick
        移动时  // Move
    }

    public TargetStrafe() {
        super(AddonTemplate.CATEGORY, "目标绕圈", "自动围绕目标移动，进行风筝走位。抄袭的meteorv2，没用的功能");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<MoveMode> mode = sgGeneral.add(new EnumSetting.Builder<MoveMode>()
        .name("移动模式")
        .description("绕圈移动的算法模式。")
        .defaultValue(MoveMode.基础)
        .build());

    public final Setting<ExecuteMode> executeMode = sgGeneral.add(new EnumSetting.Builder<ExecuteMode>()
        .name("执行模式")
        .description("在何时执行移动逻辑。'移动时'模式通常更流畅。")
        .defaultValue(ExecuteMode.移动时)
        .build());

    public final Setting<Double> targetRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("目标范围")
        .description("寻找目标的最大范围。")
        .defaultValue(7.0)
        .sliderRange(1, 15)
        .build());

    // --- 新增的两个开关 ---
    public final Setting<Boolean> targetCreative = sgGeneral.add(new BoolSetting.Builder()
        .name("攻击创造")
        .description("是否将创造模式的玩家视为目标。")
        .defaultValue(false)
        .build());

    public final Setting<Boolean> targetAdventure = sgGeneral.add(new BoolSetting.Builder()
        .name("攻击冒险")
        .description("是否将冒险模式的玩家视为目标。")
        .defaultValue(false)
        .build());
    // ---------------------

    public final Setting<Double> radius = sgGeneral.add(new DoubleSetting.Builder()
        .name("绕圈半径")
        .description("与目标保持的距离。")
        .defaultValue(2.5)
        .min(0.1)
        .sliderRange(0.1, 10)
        .build());

    public final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("绕圈速度")
        .description("围绕目标移动的基础速度。")
        .defaultValue(0.24)
        .min(0)
        .sliderRange(0, 2)
        .build());

    public final Setting<Double> scrollSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("滚动速度")
        .description("在'滚动'模式下的额外速度因子。")
        .defaultValue(0.26)
        .min(0)
        .sliderRange(0, 2)
        .visible(() -> mode.get() == MoveMode.滚动)
        .build());

    public final Setting<Boolean> damageBoost = sgGeneral.add(new BoolSetting.Builder()
        .name("伤害加速")
        .description("当你受到伤害时暂时提高移动速度。")
        .defaultValue(true)
        .build());

    public final Setting<Double> boost = sgGeneral.add(new DoubleSetting.Builder()
        .name("加速值")
        .description("受到伤害时额外增加的速度值。")
        .defaultValue(0.1)
        .min(0)
        .sliderRange(0, 0.5)
        .visible(damageBoost::get)
        .build());

    private PlayerEntity target;
    private int direction = 1;

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        // 使用自定义逻辑查找目标
        target = (PlayerEntity) TargetUtils.get(this::isValidTarget, SortPriority.ClosestAngle);

        // 如果没有找到有效目标，直接返回
        if (target == null) return;

        if (mc.player.isOnGround()) mc.player.jump();

        if (mc.options.leftKey.isPressed()) direction = 1;
        else if (mc.options.rightKey.isPressed()) direction = -1;

        if (mc.player.horizontalCollision) direction *= -1;

        if (executeMode.get() == ExecuteMode.游戏刻) {
            double currentSpeed = speed.get() + (damageBoost.get() && mc.player.hurtTime > 0 ? boost.get() : 0);
            double forward = mc.player.distanceTo(target) > radius.get() ? 1 : 0;
            float yaw = (float) Rotations.getYaw(target);
            mc.player.bodyYaw = yaw;
            mc.player.headYaw = yaw;
            mc.player.setVelocity(applySpeed(yaw, currentSpeed, forward, direction));
        }
    }

    @EventHandler
    private void onMove(PlayerMoveEvent event) {
        // 这里的判定也需要检查 target 是否依然有效 (防止上一tick的目标这一tick变无效了)
        if (executeMode.get() != ExecuteMode.移动时 || target == null || !isValidTarget(target)) return;
        
        double currentSpeed = speed.get() + (damageBoost.get() && mc.player.hurtTime > 0 ? boost.get() : 0);
        double forward = mc.player.distanceTo(target) > radius.get() ? 1 : 0;
        float yaw = (float) Rotations.getYaw(target);
        Vec3d newVelocity = applySpeed(yaw, currentSpeed, forward, direction);
        event.movement = new Vec3d(newVelocity.x, event.movement.y, newVelocity.z);
    }

    // 判断实体是否为有效目标的逻辑
    private boolean isValidTarget(Entity entity) {
        if (!(entity instanceof PlayerEntity player)) return false;
        if (player == mc.player) return false;
        if (player.isDead() || !player.isAlive()) return false;
        if (player.distanceTo(mc.player) > targetRange.get()) return false;
        if (Friends.get().isFriend(player)) return false;

        GameMode gm = getGameMode(player);
        
        // 观察者模式永远不打
        if (gm == GameMode.SPECTATOR) return false;
        
        // 创造模式：根据开关决定
        if (gm == GameMode.CREATIVE && !targetCreative.get()) return false;
        
        // 冒险模式：根据开关决定
        if (gm == GameMode.ADVENTURE && !targetAdventure.get()) return false;

        // 生存模式默认返回 true
        return true;
    }

    // 获取玩家游戏模式的辅助方法
    private GameMode getGameMode(PlayerEntity player) {
        if (player == null) return null;
        if (mc.getNetworkHandler() == null) return null;
        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
        return entry == null ? null : entry.getGameMode();
    }

    private Vec3d applySpeed(float yaw, double speed, double forward, double direction) {
        if (forward != 0.0D) {
            if (direction > 0.0D) {
                yaw += (float) (forward > 0.0D ? -45 : 45);
            } else if (direction < 0.0D) {
                yaw += (float) (forward > 0.0D ? 45 : -45);
            }
            direction = 0.0D;
            if (forward > 0.0D) forward = 1.0D;
            else if (forward < 0.0D) forward = -1.0D;
        }

        double cos = Math.cos(Math.toRadians((yaw + 90.0F)));
        double sin = Math.sin(Math.toRadians((yaw + 90.0F)));

        return new Vec3d(forward * speed * cos + direction * speed * sin, mc.player.getVelocity().y, forward * speed * sin - direction * speed * cos);
    }
}