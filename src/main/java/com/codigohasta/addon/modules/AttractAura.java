package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.meteor.MouseClickEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.lwjgl.glfw.GLFW;

public class AttractAura extends Module {
    public enum Mode {
        最近, 准星, 锁定, 无
    }

    public enum ToggleBehavior {
        回到准星, 停止追踪
    }

    public enum AntiKickMode {
        无, 向下抖动, 地板伪造
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBypass = settings.createGroup("绕过设置 (防踢)");
    private final SettingGroup sgFilters = settings.createGroup("目标过滤");

    // --- 常规设置 ---
    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>().name("追踪模式").defaultValue(Mode.准星).build());
    private final Setting<ToggleBehavior> toggleBehavior = sgGeneral.add(new EnumSetting.Builder<ToggleBehavior>().name("中键动作").defaultValue(ToggleBehavior.停止追踪).build());
    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder().name("吸引范围").defaultValue(30.0).min(0).sliderMax(100).build());
    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder().name("吸引速度").defaultValue(1.0).min(0).sliderMax(5).build());
    private final Setting<Double> stopDistance = sgGeneral.add(new DoubleSetting.Builder().name("停止距离").defaultValue(1.5).min(0).sliderMax(10).build());
    private final Setting<Boolean> lookAt = sgGeneral.add(new BoolSetting.Builder().name("自动转向").defaultValue(true).build());
    private final Setting<Boolean> middleClickLock = sgGeneral.add(new BoolSetting.Builder().name("中键锁定").defaultValue(true).build());

    // --- 绕过设置 ---
    private final Setting<AntiKickMode> antiKick = sgBypass.add(new EnumSetting.Builder<AntiKickMode>()
        .name("反踢模式")
        .description("向下抖动：每隔一段时间强制向下沉一下；地板伪造：告诉服务器你在地上。")
        .defaultValue(AntiKickMode.向下抖动)
        .build()
    );

    private final Setting<Integer> kickDelay = sgBypass.add(new IntSetting.Builder()
        .name("检测频率")
        .description("每隔多少个Tick执行一次反踢动作。")
        .defaultValue(25)
        .min(5)
        .visible(() -> antiKick.get() != AntiKickMode.无)
        .build()
    );

    // --- 目标过滤 ---
    private final Setting<Boolean> ignoreFriends = sgFilters.add(new BoolSetting.Builder().name("忽略好友").defaultValue(true).build());
    private final Setting<Boolean> followSurvival = sgFilters.add(new BoolSetting.Builder().name("生存模式").defaultValue(true).build());
    private final Setting<Boolean> followAdventure = sgFilters.add(new BoolSetting.Builder().name("冒险模式").defaultValue(true).build());
    private final Setting<Boolean> followCreative = sgFilters.add(new BoolSetting.Builder().name("创造模式").defaultValue(false).build());

    private PlayerEntity target;
    private int antiKickTimer = 0;

    public AttractAura() {
        super(AddonTemplate.CATEGORY, "attract-aura", "吸引光环：像磁铁一样把你拉向玩家 有点好玩");
    }

    @Override
    public void onActivate() {
        target = null;
        antiKickTimer = 0;
    }

    // --- 目标合法性判定逻辑 ---
    private boolean isTargetValid(PlayerEntity player) {
        if (player == null || player == mc.player || !player.isAlive()) return false;
        if (mode.get() != Mode.锁定 && mc.player.distanceTo(player) > range.get() + 30) return false;
        if (ignoreFriends.get() && !Friends.get().shouldAttack(player)) return false;
        
        if (mc.getNetworkHandler() == null) return false;
        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
        if (entry == null) return false;
        
        GameMode gm = entry.getGameMode();
        if (gm == GameMode.SURVIVAL) return followSurvival.get();
        if (gm == GameMode.ADVENTURE) return followAdventure.get();
        if (gm == GameMode.CREATIVE) return followCreative.get();
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

    // --- 核心修复：1.21.11 鼠标点击监听 ---
    @EventHandler
    private void onMouseClick(MouseClickEvent event) {
        // 根据你提供的源码：button() 是方法，action 是 public 字段
        if (middleClickLock.get() && event.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && event.action == KeyAction.Press && mc.currentScreen == null) {
            if (mode.get() == Mode.锁定) {
                target = null;
                if (toggleBehavior.get() == ToggleBehavior.停止追踪) {
                    mode.set(Mode.无);
                    info("磁力已解除 (模式：无)");
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
                // 1.21.11 修正：Entity 依然使用 getName().getString()
                info("磁力锁定目标: " + lookedAt.getName().getString());
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

        // 目标更新逻辑
        if (mode.get() == Mode.锁定) {
            if (target != null && !isTargetValid(target)) {
                target = null;
                if (toggleBehavior.get() == ToggleBehavior.停止追踪) mode.set(Mode.无);
                else mode.set(Mode.准星);
                info("锁定目标消失，重置状态");
            }
        } else {
            KillAura aura = Modules.get().get(KillAura.class);
            if (aura != null && aura.isActive() && aura.getTarget() instanceof PlayerEntity auraPlayer && isTargetValid(auraPlayer)) {
                target = auraPlayer;
            } else {
                if (mode.get() == Mode.最近) {
                    target = null;
                    double bestDist = Double.MAX_VALUE;
                    for (PlayerEntity p : mc.world.getPlayers()) {
                        if (isTargetValid(p) && mc.player.distanceTo(p) < bestDist) {
                            bestDist = mc.player.distanceTo(p);
                            target = p;
                        }
                    }
                } else if (mode.get() == Mode.准星) {
                    target = getTargetByCrosshair();
                }
            }
        }

        if (target == null) return;

        // --- 物理吸引计算 ---
        Vec3d targetPos = target.getBoundingBox().getCenter();
        Vec3d playerPos = mc.player.getEyePos();
        double distance = playerPos.distanceTo(targetPos);

        if (distance > stopDistance.get()) {
            Vec3d dir = targetPos.subtract(playerPos).normalize();
            
            double velX = dir.x * speed.get();
            double velY = dir.y * speed.get();
            double velZ = dir.z * speed.get();

            // 反踢计时器逻辑
            antiKickTimer++;
            if (antiKickTimer >= kickDelay.get()) {
                if (antiKick.get() == AntiKickMode.向下抖动) {
                    velY = -0.05;
                } else if (antiKick.get() == AntiKickMode.地板伪造 && mc.getNetworkHandler() != null) {
                    mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, mc.player.horizontalCollision));
                }
                antiKickTimer = 0;
            }

            mc.player.setVelocity(velX, velY, velZ);

            if (lookAt.get()) {
                double diffX = targetPos.x - playerPos.x;
                double diffY = targetPos.y - playerPos.y;
                double diffZ = targetPos.z - playerPos.z;
                double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
                mc.player.setYaw((float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90F);
                mc.player.setPitch((float) -Math.toDegrees(Math.atan2(diffY, diffXZ)));
            }
        } else {
            mc.player.setVelocity(0, 0, 0);
        }
    }

    @Override
    public String getInfoString() {
        if (target != null) return target.getName().getString();
        return mode.get().toString();
    }
}