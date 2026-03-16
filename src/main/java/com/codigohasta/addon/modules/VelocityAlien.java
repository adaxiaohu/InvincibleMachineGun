package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class VelocityAlien extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public enum Mode {
        Custom,
        Grim,
        Wall
    }

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("The mode to use for velocity.")
        .defaultValue(Mode.Custom)
        .build()
    );

    private final Setting<Double> horizontal = sgGeneral.add(new DoubleSetting.Builder()
        .name("horizontal")
        .description("Horizontal velocity factor.")
        .defaultValue(0)
        .min(0).max(100)
        .sliderMax(100)
        .visible(() -> mode.get() == Mode.Custom)
        .build()
    );

    private final Setting<Double> vertical = sgGeneral.add(new DoubleSetting.Builder()
        .name("vertical")
        .description("Vertical velocity factor.")
        .defaultValue(0)
        .min(0).max(100)
        .sliderMax(100)
        .visible(() -> mode.get() == Mode.Custom)
        .build()
    );

    private final Setting<Boolean> flagInWall = sgGeneral.add(new BoolSetting.Builder()
        .name("flag-in-wall")
        .description("Whether to flag when inside a wall (Grim/Wall mode).")
        .defaultValue(false)
        .visible(() -> mode.get() == Mode.Grim || mode.get() == Mode.Wall)
        .build()
    );

    private final Setting<Boolean> noExplosions = sgGeneral.add(new BoolSetting.Builder()
        .name("no-explosions")
        .description("Prevents knockback from explosions.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> pauseInLiquid = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-in-liquid")
        .description("Pauses the module when in liquid.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> fishBob = sgGeneral.add(new BoolSetting.Builder()
        .name("no-fish-bob")
        .description("Prevents being pulled by fishing rods.")
        .defaultValue(true)
        .build()
    );

    private long lastTeleportTime = 0;
    private boolean flag;

    // --- 反射字段缓存 ---
    private Field explosionVecField; // 1.21.4 新版爆炸字段 (Vec3d)
    private Field velXField, velYField, velZField; // 实体击退字段 (int)
    private Field entityIdField; // 实体ID字段 (int)
    private boolean reflectionInitialized = false;

    public VelocityAlien() {
        super(AddonTemplate.CATEGORY, "velocity-alien", "Anti-Knockback module port from Luminous.");
        // 在构造时不做反射，延迟到启用或第一次调用时，防止初始化崩溃
    }

    private void initReflection() {
        if (reflectionInitialized) return;
        
        try {
            // 1. 查找 ExplosionS2CPacket 的字段
            // 1.21.4 应该有一个 Vec3d 类型的字段用来存玩家击退
            for (Field f : ExplosionS2CPacket.class.getDeclaredFields()) {
                f.setAccessible(true);
                // 查找类型为 Vec3d 的字段 (通常只有一个，就是 knockback)
                if (f.getType() == Vec3d.class) {
                    explosionVecField = f;
                    break;
                }
            }

            // 2. 查找 EntityVelocityUpdateS2CPacket 的字段
            // 这个包通常有4个int：id, x, y, z。顺序通常是 id 在前。
            List<Field> intFields = new ArrayList<>();
            for (Field f : EntityVelocityUpdateS2CPacket.class.getDeclaredFields()) {
                f.setAccessible(true);
                if (f.getType() == int.class || f.getType() == Integer.TYPE) {
                    intFields.add(f);
                }
            }

            if (intFields.size() >= 4) {
                // 假设第一个是ID，后面三个是XYZ
                entityIdField = intFields.get(0);
                velXField = intFields.get(1);
                velYField = intFields.get(2);
                velZField = intFields.get(3);
            }

            reflectionInitialized = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getInfoString() {
        return mode.get() == Mode.Custom ? String.format("%.0f%% %.0f%%", horizontal.get(), vertical.get()) : mode.get().name();
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive event) {
        if (mc.player == null || mc.world == null) return;
        if (!reflectionInitialized) initReflection();

        if (event.packet instanceof PlayerPositionLookS2CPacket) {
            lastTeleportTime = System.currentTimeMillis();
        }

        if (pauseInLiquid.get() && (mc.player.isTouchingWater() || mc.player.isInLava())) {
            return;
        }

        if (fishBob.get() && event.packet instanceof EntityStatusS2CPacket packet) {
            if (packet.getStatus() == 31) {
                if (packet.getEntity(mc.world) instanceof FishingBobberEntity fishHook) {
                    if (fishHook.getHookedEntity() == mc.player) {
                        event.cancel();
                    }
                }
            }
        }

        // Custom Mode
        if (mode.get() == Mode.Custom) {
            float h = horizontal.get().floatValue() / 100.0F;
            float v = vertical.get().floatValue() / 100.0F;

            // --- 爆炸击退 (Vec3d) ---
            if (event.packet instanceof ExplosionS2CPacket packet) {
                if (noExplosions.get()) {
                    event.cancel();
                    return;
                }
                
                if (explosionVecField != null) {
                    try {
                        Vec3d original = (Vec3d) explosionVecField.get(packet);
                        if (original != null) {
                            Vec3d modified = new Vec3d(original.x * h, original.y * v, original.z * h);
                            explosionVecField.set(packet, modified);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                return;
            }

            // --- 实体击退 (Ints) ---
            if (event.packet instanceof EntityVelocityUpdateS2CPacket packet) {
                try {
                    if (entityIdField != null && entityIdField.getInt(packet) == mc.player.getId()) {
                        if (horizontal.get() == 0 && vertical.get() == 0) {
                            event.cancel();
                        } else {
                            if (velXField != null) {
                                int x = velXField.getInt(packet);
                                int y = velYField.getInt(packet);
                                int z = velZField.getInt(packet);

                                velXField.setInt(packet, (int) (x * h));
                                velYField.setInt(packet, (int) (y * v));
                                velZField.setInt(packet, (int) (z * h));
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } 
        // Grim / Wall Mode
        else {
            if (System.currentTimeMillis() - lastTeleportTime < 100L) {
                return;
            }

            boolean insideBlock = isInsideBlock();
            if (mode.get() == Mode.Wall && !insideBlock) {
                return;
            }

            if (event.packet instanceof ExplosionS2CPacket) {
                // Grim 模式直接设为 Zero
                if (explosionVecField != null) {
                    try {
                        explosionVecField.set(event.packet, Vec3d.ZERO);
                    } catch (Exception ignored) {}
                }
                this.flag = true;
                return;
            }

            if (event.packet instanceof EntityVelocityUpdateS2CPacket packet) {
                try {
                    if (entityIdField != null && entityIdField.getInt(packet) == mc.player.getId()) {
                        event.cancel();
                        this.flag = true;
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    @EventHandler
    public void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        if (pauseInLiquid.get() && (mc.player.isTouchingWater() || mc.player.isInLava())) {
            return;
        }

        if (this.flag) {
            if (System.currentTimeMillis() - lastTeleportTime >= 100L) {
                boolean insideBlock = isInsideBlock();
                if (flagInWall.get() || !insideBlock) {
                    BlockPos pos = mc.player.getBlockPos();
                    mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
                        pos,
                        Direction.DOWN
                    ));
                }
            }
            this.flag = false;
        }
    }

    @SuppressWarnings("deprecation")
    private boolean isInsideBlock() {
        return mc.world.getBlockState(mc.player.getBlockPos()).isSolid() || 
               mc.world.getBlockState(mc.player.getBlockPos().up()).isSolid();
    }
}