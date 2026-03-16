package com.codigohasta.addon.modules;

// 1. 导入 AddonTemplate
import com.codigohasta.addon.AddonTemplate;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
// 注意：移除了 Categories
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.mixininterface.IPlayerMoveC2SPacket;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.MaceItem;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class TeleportCriticals extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTeleport = settings.createGroup("Teleport");
    private final SettingGroup sgTargeting = settings.createGroup("Targeting");

    // General
    private final Setting<Boolean> requireMace = sgGeneral.add(new BoolSetting.Builder()
        .name("require-mace")
        .description("只在手持Mace时启用")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> swingHand = sgGeneral.add(new BoolSetting.Builder()
        .name("swing-hand")
        .description("是否显示手臂摆动动画")
        .defaultValue(true)
        .build()
    );

    // Teleport
    private final Setting<Double> teleportHeight = sgTeleport.add(new DoubleSetting.Builder()
        .name("teleport-height")
        .description("传送高度")
        .defaultValue(50.0)
        .min(0)
        .sliderRange(0, 200)
        .build()
    );

    private final Setting<Double> attackDistance = sgTeleport.add(new DoubleSetting.Builder()
        .name("attack-distance")
        .description("攻击距离")
        .defaultValue(2.0)
        .min(1)
        .sliderRange(1, 6)
        .build()
    );

    // Targeting
    private final Setting<Double> range = sgTargeting.add(new DoubleSetting.Builder()
        .name("range")
        .description("目标选择范围")
        .defaultValue(10.0)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> players = sgTargeting.add(new BoolSetting.Builder()
        .name("players")
        .description("攻击玩家")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> mobs = sgTargeting.add(new BoolSetting.Builder()
        .name("mobs")
        .description("攻击敌对生物")
        .defaultValue(false)
        .build()
    );

    private boolean teleporting;
    private int teleportStage;
    private Entity currentTarget;
    private Vec3d targetPos;
    private int attackCooldown;

    public TeleportCriticals() {
        // 2. 修改这里：使用自定义分类
        super(AddonTemplate.CATEGORY, "teleport-criticals", "传送暴击 - 瞬间传送到高空然后到目标面前触发Mace粉碎攻击");
    }

    @Override
    public void onActivate() {
        teleporting = false;
        teleportStage = 0;
        currentTarget = null;
        targetPos = null;
        attackCooldown = 0;
    }

    @Override
    public void onDeactivate() {
        teleporting = false;
        teleportStage = 0;
        currentTarget = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || !mc.player.isAlive()) return;

        // 攻击冷却
        if (attackCooldown > 0) {
            attackCooldown--;
            return;
        }

        // 检查武器要求
        if (requireMace.get() && !(mc.player.getMainHandStack().getItem() instanceof MaceItem)) {
            if (teleporting) {
                teleporting = false;
                teleportStage = 0;
            }
            return;
        }

        if (teleporting) {
            handleTeleportSequence();
            return;
        }

        // 寻找目标
        List<Entity> targets = new ArrayList<>();
        TargetUtils.getList(targets, this::entityCheck, SortPriority.ClosestAngle, 1);

        if (targets.isEmpty()) return;

        currentTarget = targets.get(0);
        startTeleportSequence();
    }

    private void startTeleportSequence() {
        if (currentTarget == null) return;

        teleporting = true;
        teleportStage = 0;

        // 计算目标面前的位置
        Vec3d targetLookVec = currentTarget.getRotationVec(1.0F);
        targetPos = currentTarget.getPos().add(targetLookVec.multiply(attackDistance.get()));
        targetPos = new Vec3d(targetPos.x, currentTarget.getY(), targetPos.z);

        info("开始传送暴击序列: " + EntityUtils.getName(currentTarget));
    }

    private void handleTeleportSequence() {
        switch (teleportStage) {
            case 0: // 第一阶段：瞬间传送到高空
                Vec3d currentPos = mc.player.getPos();
                sendTeleportPacket(currentPos.x, currentPos.y + teleportHeight.get(), currentPos.z, false);
                teleportStage = 1;
                break;

            case 1: // 第二阶段：瞬间传送到目标面前
                sendTeleportPacket(targetPos.x, targetPos.y, targetPos.z, false);
                teleportStage = 2;
                break;

            case 2: // 第三阶段：发送攻击包
                sendAttackPacket();
                teleportStage = 3;
                break;

            case 3: // 第四阶段：发送落地包并重置
                sendTeleportPacket(targetPos.x, targetPos.y, targetPos.z, true);
                
                // 重置状态
                teleporting = false;
                teleportStage = 0;
                currentTarget = null;
                attackCooldown = 5; // 5 tick 冷却

                info("传送暴击完成");
                break;
        }
    }

    private void sendAttackPacket() {
        if (currentTarget == null) return;

        // 发送实体攻击包
        PlayerInteractEntityC2SPacket attackPacket = PlayerInteractEntityC2SPacket.attack(currentTarget, mc.player.isSneaking());
        mc.getNetworkHandler().sendPacket(attackPacket);

        // 发送手臂摆动包（可选）
        if (swingHand.get()) {
            HandSwingC2SPacket swingPacket = new HandSwingC2SPacket(Hand.MAIN_HAND);
            mc.getNetworkHandler().sendPacket(swingPacket);
        }
    }

    private void sendTeleportPacket(double x, double y, double z, boolean onGround) {
        PlayerMoveC2SPacket packet = new PlayerMoveC2SPacket.PositionAndOnGround(
            x, y, z, onGround, mc.player.horizontalCollision
        );
        ((IPlayerMoveC2SPacket) packet).meteor$setTag(1337);
        mc.player.networkHandler.sendPacket(packet);
    }

    private boolean entityCheck(Entity entity) {
        if (!(entity instanceof LivingEntity) || !entity.isAlive()) return false;
        if (entity.equals(mc.player)) return false;

        double distance = mc.player.distanceTo(entity);

        // 距离检查
        if (distance > range.get()) return false;

        // 类型检查
        if (entity instanceof PlayerEntity) {
            if (!players.get()) return false;
            PlayerEntity player = (PlayerEntity) entity;
            if (player.isCreative()) return false;
        }
        else if (isHostile(entity)) {
            if (!mobs.get()) return false;
        }
        else {
            return false;
        }

        return true;
    }

    private boolean isHostile(Entity entity) {
        String entityName = EntityUtils.getName(entity);
        if (entityName == null) return false;
        return entityName.contains("Zombie") || 
               entityName.contains("Skeleton") || 
               entityName.contains("Creeper") ||
               entityName.contains("Spider") ||
               entityName.contains("Enderman");
    }

    @Override
    public String getInfoString() {
        if (teleporting) {
            return "传送中 " + teleportStage + "/3";
        }
        return null;
    }
}