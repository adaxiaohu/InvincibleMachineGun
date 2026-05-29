package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.utils.bmw.*;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * BMWClient-nextgen (LiquidBounce) 的 ModuleSprint 完整移植。
 *
 * 结构完全对应 LB 源码：
 * - 使用 BMWDirectionalInput 封装输入
 * - 使用 BMWPlayerUtil.getMovementDirectionOfInput() 计算方向 yaw
 * - 使用 BMWRotation + BMWRotationManager 管理旋转
 *
 * 三种模式：
 *   LEGIT           → 仅按 W 时疾跑
 *   OMNIDIRECTIONAL → 任意方向疾跑 + 跳跃 yaw 修正（由 MixinBMWLivingEntity 处理）
 *   OMNIROTATIONAL  → 任意方向疾跑 + sendMovementPackets 时旋转 yaw（地面 SILENT）
 *                     或直接修改 player.yaw（鞘翅 CHANGE_LOOK，让烟花沿 WASD 加速）
 */
public class BMWSprint extends Module {

    public static BMWSprint INSTANCE;
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    // 设置
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Mode> sprintMode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("疾跑模式")
        .defaultValue(Mode.OMNIROTATIONAL)
        .build()
    );

    // Ignore 设置
    private final Setting<Boolean> ignoreBlindness = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-blindness")
        .description("在失明状态下仍然保持疾跑")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> ignoreHunger = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-hunger")
        .description("在饥饿状态下仍然保持疾跑")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> ignoreCollision = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-collision")
        .description("撞墙时仍然保持疾跑")
        .defaultValue(false)
        .build()
    );

    // StopOn 设置（仅 Legit 模式）
    private final Setting<Boolean> stopOnGround = sgGeneral.add(new BoolSetting.Builder()
        .name("stop-on-ground")
        .description("在地面时若没有正向移动则停止疾跑（仅 Legit 模式）")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> stopOnAir = sgGeneral.add(new BoolSetting.Builder()
        .name("stop-on-air")
        .description("在空中时若没有正向移动则停止疾跑（仅 Legit 模式）")
        .defaultValue(false)
        .build()
    );

    // Elytra 设置
    private final Setting<Boolean> elytraRotation = sgGeneral.add(new BoolSetting.Builder()
        .name("elytra-rotation")
        .description("鞘翅飞行时也启用 Omnirotational 旋转（烟花沿 WASD 方向加速）")
        .defaultValue(true)
        .build()
    );

    // 状态变量
    private float preElytraYaw;
    private float preElytraPitch;
    private boolean elytraChangedLookThisTick;

    public BMWSprint() {
        super(AddonTemplate.CATEGORY, "强制疾跑2",
            "BMWClient-nextgen 抄的 Sprint。§lOmnirotational§r：任意方向疾跑+自动旋转；"
            + "鞘翅飞行时烟花沿 WASD 方向加速无法grim");
        INSTANCE = this;
    }

    @Override
    public void onActivate() {
        BMWRotationUtil.shouldRotate = false;
        BMWRotationManager.clearTarget();
        elytraChangedLookThisTick = false;
    }

    @Override
    public void onDeactivate() {
        BMWRotationUtil.shouldRotate = false;
        BMWRotationManager.clearTarget();
        if (mc.player != null) {
            mc.player.bodyYaw = mc.player.getYaw();
            mc.player.headYaw = mc.player.getYaw();
        }
        elytraChangedLookThisTick = false;
    }

    @Override
    public String getInfoString() {
        return sprintMode.get().name();
    }

    // ========= 对应 LB: GameTickEvent handler (FIRST_PRIORITY) =========

    @EventHandler(priority = 150)
    private void onTickPre(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // 每次 tick 先清除上一帧的旋转状态
        BMWRotationUtil.shouldRotate = false;
        BMWRotationManager.clearTarget();
        elytraChangedLookThisTick = false;

        boolean isFlying = mc.player.getPose().name().equals("GLIDING");
        if (isFlying && sprintMode.get() != Mode.OMNIROTATIONAL) return;

        switch (sprintMode.get()) {
            case LEGIT -> handleLegit();
            case OMNIDIRECTIONAL -> handleOmnidirectional();
            case OMNIROTATIONAL -> {
                if (isFlying) {
                    handleOmnirotationalElytra();
                } else {
                    handleOmnirotational();
                }
            }
        }

        // 调用 RotationManager.update() —— 在 CHANGE_LOOK 模式下直接设 player.yaw
        BMWRotationManager.update();
    }

    // ========= 对应 LB: TickEvent.Post（恢复 elytra 的 yaw）=========

    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        if (elytraChangedLookThisTick && mc.player != null) {
            mc.player.setYaw(preElytraYaw);
            mc.player.setPitch(preElytraPitch);
            elytraChangedLookThisTick = false;
        }
    }

    // ========= 移动方向修正：使 velocity 方向匹配 packet yaw =========

    /**
     * 当 Omnirotational 激活且 SILENT 旋转生效时，修正 PlayerMoveEvent 中的水平移动方向，
     * 使其与发送给服务器的 sprintYaw 一致。避免 GrimAC Simulation flag。
     *
     * 仅在有 WASD 输入时触发（不干扰击退等外部 force）。
     */
    @EventHandler
    private void onPlayerMove(PlayerMoveEvent event) {
        if (sprintMode.get() != Mode.OMNIROTATIONAL) return;
        if (!BMWRotationUtil.shouldRotate) return;
        if (!BMWPlayerUtil.isMoving()) return;

        double horizSpeed = Math.sqrt(
            event.movement.x * event.movement.x + event.movement.z * event.movement.z);
        if (horizSpeed < 1.0e-7) return;

        float yawRad = BMWRotationUtil.sprintYaw * MathHelper.RADIANS_PER_DEGREE;
        double newX = -MathHelper.sin(yawRad) * horizSpeed;
        double newZ = MathHelper.cos(yawRad) * horizSpeed;

        event.movement = new Vec3d(newX, event.movement.y, newZ);
    }

    // ========= 三种模式实现 =========

    /**
     * 对应 LB Legit: 按 W 时 setSprinting(true)
     */
    private void handleLegit() {
        if (!canSprint()) return;

        if (mc.options.forwardKey.isPressed()) {
            mc.player.setSprinting(true);
        } else {
            if (!mc.player.isSprinting()) return;
            if (stopOnGround.get() && mc.player.isOnGround()) {
                mc.player.setSprinting(false);
            } else if (stopOnAir.get() && !mc.player.isOnGround()) {
                mc.player.setSprinting(false);
            }
        }
    }

    /**
     * 对应 LB Omnidirectional: 任意方向疾跑，跳跃 yaw 由 mixin 修正
     */
    private void handleOmnidirectional() {
        if (!canSprint()) return;
        mc.player.setSprinting(true);
    }

    /**
     * 对应 LB Omnirotational (SILENT 模式):
     * 设置旋转目标，由 MixinBMWClientPlayerEntity 在 sendMovementPackets 时应用。
     * 不修改 player.yaw。
     */
    private void handleOmnirotational() {
        if (!canSprint()) return;
        mc.player.setSprinting(true);

        float moveYaw = BMWPlayerUtil.getMovementDirectionOfInput(
            mc.player.getYaw(), BMWDirectionalInput.fromPlayer());

        // 微小随机偏移避免 GrimAC AimModulo360 检测（精确角度倍数会被 flag）
        moveYaw += (float) ((Math.random() - 0.5) * 0.001);

        // SILENT: 设目标但不改 player.yaw
        BMWRotationManager.setRotationTarget(new BMWRotation(moveYaw, mc.player.getPitch()), false);

        // 通知 mixin 在 sendMovementPackets 时旋转 yaw
        // onPlayerMove(PlayerMoveEvent) 同时修正水平移动方向使 velocity 匹配 packet yaw
        BMWRotationUtil.sprintYaw = moveYaw;
        BMWRotationUtil.shouldRotate = true;
    }

    /**
     * 鞘翅 Omnirotational (CHANGE_LOOK 模式):
     * 直接修改 player.yaw，使烟花包的 yaw 指向移动方向。
     * TickEvent.Post 恢复。
     */
    private void handleOmnirotationalElytra() {
        if (!elytraRotation.get()) return;
        if (!BMWPlayerUtil.isMoving()) return;

        float moveYaw = BMWPlayerUtil.getMovementDirectionOfInput(
            mc.player.getYaw(), BMWDirectionalInput.fromPlayer());

        // 保存原 yaw 供恢复
        preElytraYaw = mc.player.getYaw();
        preElytraPitch = mc.player.getPitch();

        // CHANGE_LOOK: RotationManager.update() 会调用 setPlayerRotation
        BMWRotationManager.setRotationTarget(new BMWRotation(moveYaw, mc.player.getPitch()), true);

        elytraChangedLookThisTick = true;
    }

    // ========= 通用条件检查 =========

    private boolean canSprint() {
        if (mc.player == null) return false;

        // 饥饿
        boolean isHungry = mc.player.getHungerManager().getFoodLevel() <= 6 && !mc.player.isCreative();
        if (isHungry && !ignoreHunger.get()) return false;

        // 失明
        if (mc.player.hasStatusEffect(StatusEffects.BLINDNESS) && !ignoreBlindness.get()) {
            return false;
        }

        // 撞墙
        if (mc.player.horizontalCollision && !ignoreCollision.get()) return false;

        // 必须按了移动键
        if (!BMWPlayerUtil.isMoving()) return false;

        if (mc.player.isSneaking()) return false;
        if (mc.player.isRiding()) return false;
        if (mc.player.isInFluid()) return false;

        return true;
    }

    // ========= 内部枚举 =========

    public enum Mode {
        LEGIT("Legit"),
        OMNIDIRECTIONAL("Omnidirectional"),
        OMNIROTATIONAL("Omnirotational");

        private final String title;
        Mode(String title) { this.title = title; }
        @Override public String toString() { return title; }
    }
}
