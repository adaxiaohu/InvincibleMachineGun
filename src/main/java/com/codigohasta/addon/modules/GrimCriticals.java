package com.codigohasta.addon.modules;

import net.minecraft.util.math.Vec3d;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.CrystalAura;
import meteordevelopment.meteorclient.systems.modules.combat.Surround;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.lang.reflect.Field; // 新增导入：用于反射
import java.util.Random;

public class GrimCriticals extends Module {
    
    public enum Mode {
        Packet,
        Bypass,
        Grim,
        Strict,
        GrimV3
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("The mode to use for criticals.")
        .defaultValue(Mode.GrimV3)
        .build()
    );

    private final Setting<Boolean> multitask = sgGeneral.add(new BoolSetting.Builder()
        .name("multitask")
        .description("Allows criticals to work while using other modules like Surround.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> phasedOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("phased-only")
        .description("Only performs criticals when phased (for Grim modes).")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.Grim || mode.get() == Mode.GrimV3)
        .build()
    );

    private final Setting<Boolean> wallsOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("walls-only")
        .description("Only checks for walls when Phased Only is enabled.")
        .defaultValue(true)
        .visible(() -> (mode.get() == Mode.Grim || mode.get() == Mode.GrimV3) && phasedOnly.get())
        .build()
    );

    private final Setting<Boolean> moveFix = sgGeneral.add(new BoolSetting.Builder()
        .name("move-fix")
        .description("Prevents criticals while moving to avoid flag.")
        .defaultValue(true)
        .visible(() -> mode.get() != Mode.Grim && mode.get() != Mode.GrimV3)
        .build()
    );

    private final Setting<Boolean> pauseOnCA = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-on-ca")
        .description("Pauses Criticals when Crystal Aura is active.")
        .defaultValue(true)
        .build()
    );

    private final Random random = new Random();
    private long lastAttackTime = 0L;
    private boolean postUpdateGround = false;
    private boolean postUpdateSprint = false;

    public GrimCriticals() {
        super(AddonTemplate.CATEGORY, "criticals", "没用的功能");
    }

    @Override
    public String getInfoString() {
        return mode.get().name();
    }

    @EventHandler
    private void onAttackEntity(AttackEntityEvent event) {
        if (event.entity == null) return;
        
        // 1. Check Pause on CA
        if (pauseOnCA.get()) {
            CrystalAura ca = Modules.get().get(CrystalAura.class);
            if (ca != null && ca.isActive()) return;
        }

        // 2. Check Multitask
        if (!multitask.get()) {
            if (Modules.get().isActive(Surround.class)) return;
        }

        Entity target = event.entity;

        // 3. 基础条件检查
        if (target instanceof LivingEntity || target instanceof EndCrystalEntity) {
            if (mc.player == null) return;

            // 检查玩家状态
            if (!mc.player.isClimbing() && !mc.player.isTouchingWater() && !mc.player.hasVehicle()) {
                if (!mc.player.isUsingItem()) {
                    
                    // 4. Sprint Fix (处理疾跑状态)
                    this.postUpdateSprint = mc.player.isSprinting();
                    if (this.postUpdateSprint) {
                        mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
                    }

                    // 5. 执行逻辑
                    doCritical(target);
                }
            }
        }
    }

    @EventHandler
    private void onPacketSent(PacketEvent.Sent event) {
        if (mc.player == null) return;

        if (event.packet instanceof PlayerMoveC2SPacket) {
            // Strict 模式下恢复地面状态
            if (this.postUpdateGround) {
                // 修复: 使用反射来设置 onGround，避免找不到符号错误
                setPacketOnGround((PlayerMoveC2SPacket) event.packet, true);
                this.postUpdateGround = false;
            }

            // 恢复疾跑状态
            if (this.postUpdateSprint) {
                mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));
                this.postUpdateSprint = false;
            }
        }
    }

    private void doCritical(Entity target) {
        if (mc.player == null) return;
        if (!mc.player.isOnGround()) return;
        if (mc.options.jumpKey.isPressed()) return;

        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();
        
        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();

        switch (mode.get()) {
            case Grim: {
                double offset1 = 0.1016 + (random.nextDouble() * 0.001);
                double offset2 = 0.0202 + (random.nextDouble() * 0.001);
                double offset3 = 3.239E-4 + (random.nextDouble() * 0.0001);

                sendPosition(x, y + offset1, z, false);
                sendPosition(x, y + offset2, z, false);
                sendPosition(x, y + offset3, z, false);
                
                mc.player.networkHandler.sendPacket(PlayerInteractEntityC2SPacket.attack(target, mc.player.isSneaking()));
                break;
            }

            case Packet: {
                sendPosition(x, y + 0.0625, z, true);
                sendPosition(x, y, z, false);
                mc.player.networkHandler.sendPacket(PlayerInteractEntityC2SPacket.attack(target, mc.player.isSneaking()));
                break;
            }

            case Strict: {
                if (System.currentTimeMillis() - lastAttackTime < 500) return;

                // 修复 1: 改用标准的 0.0625 (1/16方块)，这是触发 fallDistance 的最小有效高度
                // 1.1E-7 太小了，服务器会认为是 0
                sendPosition(x, y + 0.0625, z, false); // 假装跳起
                sendPosition(x, y, z, false);          // 假装落地（但 onGround 发 false 以触发暴击）
                
                // 修复 2: 手动发送攻击包，确保 [跳起 -> 落下 -> 攻击] 的顺序绝对正确
                mc.player.networkHandler.sendPacket(PlayerInteractEntityC2SPacket.attack(target, mc.player.isSneaking()));
                
                this.postUpdateGround = true; // 下一 tick 修正地面状态防止被反作弊拉回
                this.lastAttackTime = System.currentTimeMillis();
                break;
            }

            case GrimV3: {
                // 1. Phased Check
                if (phasedOnly.get()) {
                    if (wallsOnly.get()) {
                        if (!isDoublePhased()) break;
                    } else {
                        if (!isPhased()) break;
                    }
                }

                // 2. Move Check
                if (moveFix.get() && PlayerUtils.isMoving()) return;

                // 3. Time/Fall Check
                if (System.currentTimeMillis() - lastAttackTime >= 250 || mc.player.fallDistance > 0) {
                     sendPositionFull(x, y + 0.0625, z, yaw, pitch, false);
                     sendPositionFull(x, y + 0.0625013579, z, yaw, pitch, false);
                     sendPositionFull(x, y + 1.3579E-6, z, yaw, pitch, false);
                     
                     this.lastAttackTime = System.currentTimeMillis();
                }
                break;
            }
            
            case Bypass: {
                sendPosition(x, y + 0.11, z, false);
                sendPosition(x, y + 0.1100013579, z, false);
                sendPosition(x, y + 0.0000013579, z, false);
                mc.player.networkHandler.sendPacket(PlayerInteractEntityC2SPacket.attack(target, mc.player.isSneaking()));
                break;
            }
        }
        
        mc.player.swingHand(mc.player.getActiveHand());
    }

    // --- Helper Methods ---

    /**
     * 使用反射修改 PlayerMoveC2SPacket 的 onGround 字段
     * 解决 Meteor 接口 IPlayerMoveC2SPacket 中 setOnGround 缺失的问题
     */
    private void setPacketOnGround(PlayerMoveC2SPacket packet, boolean onGround) {
        try {
            Field field = PlayerMoveC2SPacket.class.getDeclaredField("onGround");
            field.setAccessible(true);
            field.setBoolean(packet, onGround);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendPosition(double x, double y, double z, boolean onGround) {
        // 修复: 1.21.2+ 构造函数需要额外的 boolean 参数 (horizontalCollision)，通常设为 false
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, onGround, false));
    }

    private void sendPositionFull(double x, double y, double z, float yaw, float pitch, boolean onGround) {
        // 修复: 1.21.2+ 构造函数需要额外的 boolean 参数
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.Full(x, y, z, yaw, pitch, onGround, false));
    }

    private boolean isPhased() {
        if (mc.world == null || mc.player == null) return false;
        Box box = mc.player.getBoundingBox();
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int x = (int) Math.floor(box.minX); x < Math.ceil(box.maxX); x++) {
            for (int y = (int) Math.floor(box.minY); y < Math.ceil(box.maxY); y++) {
                for (int z = (int) Math.floor(box.minZ); z < Math.ceil(box.maxZ); z++) {
                    mutable.set(x, y, z);
                    if (mc.world.getBlockState(mutable).isFullCube(mc.world, mutable)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isDoublePhased() {
        if (mc.world == null || mc.player == null) return false;
        Box box = mc.player.getBoundingBox();
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int x = (int) Math.floor(box.minX); x < Math.ceil(box.maxX); x++) {
            for (int y = (int) Math.floor(box.minY); y < Math.ceil(box.maxY); y++) {
                for (int z = (int) Math.floor(box.minZ); z < Math.ceil(box.maxZ); z++) {
                    mutable.set(x, y, z);
                    boolean current = mc.world.getBlockState(mutable).isFullCube(mc.world, mutable);
                    mutable.set(x, y + 1, z);
                    boolean up = mc.world.getBlockState(mutable).isFullCube(mc.world, mutable);
                    
                    if (current && up) return true;
                }
            }
        }
        return false;
    }
}