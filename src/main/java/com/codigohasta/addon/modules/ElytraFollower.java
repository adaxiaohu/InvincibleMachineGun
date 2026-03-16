package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.meteor.MouseButtonEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.lwjgl.glfw.GLFW;

public class ElytraFollower extends Module {
    // 直接使用中文作为枚举名，这是 Java 允许的，且在 Meteor GUI 中会直接显示中文
    public enum Mode {
        最近, 准星, 锁定, 无
    }

    public enum ToggleBehavior {
        回到准星, 停止追踪
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup(); // 常规设置
    private final SettingGroup sgFlight = settings.createGroup("起飞与视角");
    private final SettingGroup sgFilters = settings.createGroup("目标过滤");

    // --- 常规设置 ---
    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("追踪模式")
        .description("选择寻找目标的方式。设置为“无”时模块停止追踪。")
        .defaultValue(Mode.准星)
        .build()
    );

    private final Setting<ToggleBehavior> toggleBehavior = sgGeneral.add(new EnumSetting.Builder<ToggleBehavior>()
        .name("中键动作")
        .description("当已经锁定目标时，再次按中键的行为。")
        .defaultValue(ToggleBehavior.停止追踪)
        .build()
    );

    private final Setting<Boolean> lockAnytime = sgGeneral.add(new BoolSetting.Builder()
        .name("随时锁定")
        .description("开启后，即使没穿鞘翅也能通过中键锁定目标。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> middleClickLock = sgGeneral.add(new BoolSetting.Builder()
        .name("中键锁定")
        .description("允许使用鼠标中键锁定准星指向的玩家。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("追踪范围")
        .description("搜索玩家的最大距离。")
        .defaultValue(150.0)
        .min(0)
        .sliderMax(500)
        .build()
    );

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("飞行速度")
        .description("追踪时的推进速度。")
        .defaultValue(1.8)
        .min(0)
        .sliderMax(5)
        .build()
    );

    private final Setting<Double> stopDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("停止距离")
        .description("距离目标多近时停止推进，防止穿模。")
        .defaultValue(2.5)
        .min(0)
        .sliderMax(10)
        .build()
    );

    // --- 起飞与视角 ---
    private final Setting<Boolean> lookAt = sgFlight.add(new BoolSetting.Builder()
        .name("自动转向")
        .description("飞行时自动看向目标。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoTakeoff = sgFlight.add(new BoolSetting.Builder()
        .name("自动起飞")
        .description("落地时自动跳跃并开启鞘翅。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> takeoffDelay = sgFlight.add(new IntSetting.Builder()
        .name("起飞延迟")
        .description("自动起飞动作之间的检测延迟。")
        .defaultValue(5)
        .min(0)
        .build()
    );

    // --- 目标过滤 ---
    private final Setting<Boolean> followSurvival = sgFilters.add(new BoolSetting.Builder()
        .name("生存模式")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> followAdventure = sgFilters.add(new BoolSetting.Builder()
        .name("冒险模式")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> followCreative = sgFilters.add(new BoolSetting.Builder()
        .name("创造模式")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> followSpectator = sgFilters.add(new BoolSetting.Builder()
        .name("旁观模式")
        .defaultValue(false)
        .build()
    );

    private PlayerEntity target;
    private int takeoffTimer = 0;

    public ElytraFollower() {
        // 模块 ID 建议用英文 (elytra-follower)，显示名称和描述用中文
        super(AddonTemplate.CATEGORY, "鞘翅追人", "鞘翅追人：自动起飞并像导弹一样追踪玩家");
    }

    @Override
    public void onActivate() {
        target = null;
        takeoffTimer = 0;
    }

    private boolean isTargetValid(PlayerEntity player) {
        if (player == null || player == mc.player) return false;
        if (!player.isAlive()) return false;
        if (mc.player.distanceTo(player) > range.get() + 30) return false;
        if (!Friends.get().shouldAttack(player)) return false;

        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
        if (entry == null) return false;

        GameMode gm = entry.getGameMode();
        if (gm == GameMode.SURVIVAL) return followSurvival.get();
        if (gm == GameMode.ADVENTURE) return followAdventure.get();
        if (gm == GameMode.CREATIVE) return followCreative.get();
        if (gm == GameMode.SPECTATOR) return followSpectator.get();

        return false;
    }

    private PlayerEntity getTargetByCrosshair() {
        PlayerEntity best = null;
        double bestDiff = Double.MAX_VALUE;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (!isTargetValid(player)) continue;

            double diffX = player.getX() - mc.player.getX();
            double diffZ = player.getZ() - mc.player.getZ();
            float targetYaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90;
            float diff = Math.abs(MathHelper.wrapDegrees(mc.player.getYaw() - targetYaw));

            if (diff < bestDiff) {
                bestDiff = diff;
                best = player;
            }
        }
        return best;
    }

    private PlayerEntity findBestTarget() {
        if (mode.get() == Mode.无) return null;
        
        if (mode.get() == Mode.最近) {
            PlayerEntity best = null;
            double bestDist = Double.MAX_VALUE;
            for (PlayerEntity player : mc.world.getPlayers()) {
                if (!isTargetValid(player)) continue;
                double dist = mc.player.distanceTo(player);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = player;
                }
            }
            return best;
        } 
        
        if (mode.get() == Mode.准星) {
            return getTargetByCrosshair();
        }
        
        return null;
    }

    @EventHandler
    private void onMouseButton(MouseButtonEvent event) {
        if (middleClickLock.get() && event.button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && event.action == KeyAction.Press && mc.currentScreen == null) {
            
            if (!lockAnytime.get()) {
                ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
                if (!chest.isOf(Items.ELYTRA)) return;
            }

            if (mode.get() == Mode.锁定) {
                target = null;
                if (toggleBehavior.get() == ToggleBehavior.停止追踪) {
                    mode.set(Mode.无);
                    info("已停止追踪 (模式：无)");
                } else {
                    mode.set(Mode.准星);
                    info("已切回准星模式");
                }
                return;
            }

            PlayerEntity lookedAt = getTargetByCrosshair();
            if (lookedAt != null) {
                target = lookedAt;
                mode.set(Mode.锁定);
                info("已锁定目标: " + lookedAt.getName().getString());
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (mode.get() == Mode.无) {
            target = null;
            return;
        }

        if (mode.get() == Mode.锁定) {
            if (target != null && !isTargetValid(target)) {
                target = null;
                if (toggleBehavior.get() == ToggleBehavior.停止追踪) mode.set(Mode.无);
                else mode.set(Mode.准星);
                info("锁定目标失效，已重置");
            }
        } else {
            KillAura aura = Modules.get().get(KillAura.class);
            if (aura != null && aura.isActive() && aura.getTarget() instanceof PlayerEntity auraPlayer && isTargetValid(auraPlayer)) {
                target = auraPlayer;
            } else {
                target = findBestTarget();
            }
        }

        ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (!chest.isOf(Items.ELYTRA)) return;

        if (autoTakeoff.get() && mc.player.getPose() != EntityPose.GLIDING) {
            if (takeoffTimer > 0) takeoffTimer--;
            else {
                if (mc.player.isOnGround()) {
                    mc.player.jump();
                    takeoffTimer = takeoffDelay.get();
                } else if (!mc.player.isSubmergedInWater()) {
                    mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                    takeoffTimer = takeoffDelay.get();
                }
            }
            return;
        }

        if (mc.player.getPose() == EntityPose.GLIDING && target != null) {
            Vec3d targetPos = target.getBoundingBox().getCenter();
            Vec3d playerPos = mc.player.getEyePos();
            double distance = playerPos.distanceTo(targetPos);

            if (distance > stopDistance.get()) {
                Vec3d dir = targetPos.subtract(playerPos).normalize();
                mc.player.setVelocity(dir.x * speed.get(), dir.y * speed.get(), dir.z * speed.get());

                if (lookAt.get()) {
                    double diffX = targetPos.x - playerPos.x;
                    double diffY = targetPos.y - playerPos.y;
                    double diffZ = targetPos.z - playerPos.z;
                    double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
                    mc.player.setYaw((float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90F);
                    mc.player.setPitch((float) -Math.toDegrees(Math.atan2(diffY, diffXZ)));
                }
            } else {
                mc.player.setVelocity(mc.player.getVelocity().multiply(0.5));
            }
        }
    }

    @Override
    public String getInfoString() {
        if (target != null) return target.getName().getString();
        return mode.get().toString();
    }
}