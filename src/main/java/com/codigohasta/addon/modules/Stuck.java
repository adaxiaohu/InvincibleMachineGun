package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Field;

/**
 * Epsilon Stuck 完整移植。
 *
 * NoPacket   → 取消所有移动包（GrimAC 无位置数据无法运行 Simulation）。
 *              零化输入防止模块关闭后客户端与服务端位置不同步。
 *              定期发送旋转包保持连接。收到服务端强制同步时自动关闭。
 * CancelMove → PlayerMoveEvent 冻结 + 循环解冻（19t 冻结 / 1t 解冻），
 *              包正常流出，适合地面或近地使用。
 */
public class Stuck extends Module {

    public static Stuck INSTANCE;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("模式")
        .description("NoPacket: 取消所有移动包(空中可用) | CancelMove: 循环解冻(地面稳定)")
        .defaultValue(Mode.NoPacket)
        .build());

    // CancelMove 循环
    private static final int CYCLE_LENGTH = 20;
    private static final int UNFREEZE_START = 19;
    private int cycleTick;

    // NoPacket 旋转追踪
    private float lastYaw;
    private float lastPitch;
    private boolean bypassPacket;

    // 反射
    private Field velocityEntityIdField;

    public enum Mode {
        NoPacket("NoPacket"),
        CancelMove("CancelMove");

        private final String title;
        Mode(String title) { this.title = title; }
        @Override public String toString() { return title; }
    }

    public Stuck() {
        super(AddonTemplate.CATEGORY, "定身术", "(来自 Epsilon) NoPacket取消所有包 | CancelMove循环解冻");
        INSTANCE = this;
    }

    @Override
    public void onActivate() {
        if (mc.player != null) {
            lastYaw = mc.player.getYaw();
            lastPitch = mc.player.getPitch();
        }
        bypassPacket = false;
        cycleTick = 0;
        initReflection();
    }

    @Override
    public void onDeactivate() {
        // Epsilon: 在空中关闭时发送 +1337 传送包，服务端接受大偏移并覆盖模拟
        if (mode.get() == Mode.NoPacket && mc.player != null && !mc.player.isOnGround()) {
            bypassPacket = true;
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.Full(
                mc.player.getX() + 1337, mc.player.getY(), mc.player.getZ() + 1337,
                mc.player.getYaw() + 0.01f, mc.player.getPitch(),
                mc.player.isOnGround(), mc.player.horizontalCollision
            ));
            bypassPacket = false;
        }
    }

    @Override
    public String getInfoString() {
        return mode.get().title;
    }

    private void initReflection() {
        try {
            for (Field f : EntityVelocityUpdateS2CPacket.class.getDeclaredFields()) {
                f.setAccessible(true);
                if (f.getType() == int.class || f.getType() == Integer.TYPE) {
                    velocityEntityIdField = f;
                    break;
                }
            }
        } catch (Exception ignored) {}
    }

    private boolean shouldFreeze() {
        return (cycleTick % CYCLE_LENGTH) < UNFREEZE_START;
    }

    // ========== 零化输入（等价 Epsilon MoveInputEvent） ==========

    private void zeroInput() {
        if (mc.player == null) return;
        // PlayerInput 是 record，替换整个对象归零所有输入
        mc.player.input.playerInput = new PlayerInput(false, false, false, false, false, false, false);
    }

    // ========== Tick ==========

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (mc.player == null) return;

        cycleTick++;
        zeroInput();

        // NoPacket: 发送旋转包（等价 Epsilon ClickEvent）
        // 通过 bypassPacket 绕过取消，保持连接存活
        if (mode.get() == Mode.NoPacket) {
            float yaw = mc.player.getYaw();
            float pitch = mc.player.getPitch();

            if (yaw != lastYaw || pitch != lastPitch) {
                bypassPacket = true;
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                    yaw, pitch, mc.player.isOnGround(), mc.player.horizontalCollision));
                bypassPacket = false;
            }

            lastYaw = yaw;
            lastPitch = pitch;
        }
    }

    // ========== PlayerMoveEvent (CancelMove: 循环冻结客户端移动) ==========

    @EventHandler
    private void onPlayerMove(PlayerMoveEvent event) {
        if (mode.get() != Mode.CancelMove) return;
        if (shouldFreeze()) {
            ((IVec3d) event.movement).meteor$set(0, 0, 0);
            mc.player.setVelocity(Vec3d.ZERO);
        }
    }

    // ========== 数据包拦截 ==========

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (bypassPacket) return;

        if (mode.get() == Mode.NoPacket) {
            // 取消所有移动包 — GrimAC 收不到位置数据就无法运行 Simulation 检查
            if (event.packet instanceof PlayerMoveC2SPacket) {
                event.cancel();
            }

            // 取消服务端速度同步包，防止被推走
            if (event.packet instanceof EntityVelocityUpdateS2CPacket packet) {
                try {
                    if (velocityEntityIdField != null && velocityEntityIdField.getInt(packet) == mc.player.getId()) {
                        event.cancel();
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    // ========== 服务端强制同步 → 自动关闭（等价 Epsilon） ==========

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerPositionLookS2CPacket) {
            toggle();
            sendToggledMsg();
        }
    }
}
