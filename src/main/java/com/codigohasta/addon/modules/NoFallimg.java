package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.utils.epsilon.EpsilonMovementUtil;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Vec3d;

public class NoFallimg extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public enum Mode {
        GroundSpoof,
        Packet,
        GrimMotion,
        LazyGrimPlus2
    }

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("模式")
        .defaultValue(Mode.GroundSpoof)
        .build());

    private final Setting<Double> fallDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("摔落距离触发")
        .defaultValue(3.0)
        .min(3.0)
        .max(16.0)
        .sliderMin(3.0)
        .sliderMax(16.0)
        .build());

    // === 全局状态 ===
    private boolean flag;
    private boolean jumpScheduled;
    private boolean jumpInjected;
    private boolean bypass;
    private double lastOnGroundHeight;
    private int tickCounter;

    // === LazyGrimPlus2 状态机 ===
    // step: 0=COMMON, 1=WAIT_RESYNC, 2=APPLY_JUMP
    private int step;
    private Vec3d lastStartWaitPos;        // 发送等待回弹时的位置
    private int lastStartWaitTick;          // 发送等待回弹时的 tick
    private static final int LATENCY = 5;   // 等待回弹的最大 tick 数
    private PlayerMoveC2SPacket storedPacket; // 延迟到 Post 发送的包
    private boolean applyJumpThisTick;

    public NoFallimg() {
        super(AddonTemplate.CATEGORY, "没摔伤", "防止摔落伤害");
    }

    @Override
    public void onActivate() {
        flag = false;
        jumpScheduled = false;
        jumpInjected = false;
        step = 0;
        lastStartWaitPos = null;
        lastStartWaitTick = -1;
        lastOnGroundHeight = Double.MIN_VALUE;
        tickCounter = 0;
    }

    @Override
    public void onDeactivate() {
        if (jumpInjected) {
            mc.options.jumpKey.setPressed(false);
            jumpInjected = false;
        }
    }

    // ========== 刻前处理 ==========

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        tickCounter++;

        // 更新落地高度
        if (mc.player.isOnGround() || mc.player.isTouchingWater()) {
            lastOnGroundHeight = mc.player.getY();
        } else if (mc.player.getY() > lastOnGroundHeight) {
            lastOnGroundHeight = mc.player.getY();
        }

        // 超出安全距离标记
        if (mc.player.fallDistance > fallDistance.get()
                || mc.player.getY() <= lastOnGroundHeight - mc.player.getSafeFallDistance() - 3) {
            flag = true;
        }

        // 重置标志（如果回到地面了）
        if (mc.player.isOnGround() && mc.player.fallDistance < 0.5) {
            if (step == 0) flag = false;
        }

        // === LazyGrimPlus2: APPLY_JUMP 步 ===
        if (step == 2) {
            // 回弹已匹配，执行跳跃
            mc.player.setOnGround(true);
            mc.options.jumpKey.setPressed(true);
            jumpInjected = true;
            step = 0; // 回到 COMMON
        }

        // 跳跃注入（GrimMotion 共用）
        if (jumpScheduled) {
            mc.options.jumpKey.setPressed(true);
            jumpInjected = true;
            jumpScheduled = false;
        }

        // WAIT_RESYNC 超时
        if (step == 1 && tickCounter > lastStartWaitTick + LATENCY * 2) {
            step = 0;
            lastStartWaitPos = null;
        }
    }

    // ========== 刻后处理 ==========

    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        // 释放跳跃键
        if (jumpInjected) {
            mc.options.jumpKey.setPressed(false);
            jumpInjected = false;
        }
    }

    // ========== 入站包：回弹检测 ==========

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.player == null) return;
        if (mode.get() != Mode.LazyGrimPlus2) return;
        if (step != 1) return; // 只有 WAIT_RESYNC 状态才处理

        if (event.packet instanceof PlayerPositionLookS2CPacket lookPacket) {
            Vec3d setbackPos = lookPacket.change().position();
            double y = setbackPos.y;

            // 服务端拉回：Y 明显低于当前高度
            if (mc.player.getY() - y > 0.5) {
                // 检查回弹位置是否匹配我们记录的位置（距离 < 1）
                if (lastStartWaitPos != null
                        && lastStartWaitPos.squaredDistanceTo(setbackPos) < 1
                        && tickCounter <= lastStartWaitTick + LATENCY) {
                    // 匹配成功！回弹位置 = 服务端确认的位置
                    // 执行 APPLY_JUMP：瞬移 + 跳跃
                    lastStartWaitPos = null;
                    mc.player.setPosition(setbackPos.x, y, setbackPos.z);
                    mc.player.setOnGround(true);
                    step = 2; // 下个 tick 执行跳跃
                }
            }
        }
    }

    // ========== 出站包：拦截 + 发送伪造包 ==========

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (mc.player == null) return;
        if (bypass) return;
        if (!flag) return;
        if (!(event.packet instanceof PlayerMoveC2SPacket)) return;
        if (!mc.player.isOnGround()) return;

        PlayerMoveC2SPacket pkt = (PlayerMoveC2SPacket) event.packet;

        switch (mode.get()) {

            case GroundSpoof -> {
                EpsilonMovementUtil.setOnGround(pkt, false);
                flag = false;
            }

            case Packet -> {
                event.cancel();
                bypass = true;
                mc.getNetworkHandler().sendPacket(
                    new PlayerMoveC2SPacket.OnGroundOnly(false, mc.player.horizontalCollision));
                bypass = false;
                flag = false;
            }

            case GrimMotion -> {
                event.cancel();
                bypass = true;
                mc.getNetworkHandler().sendPacket(EpsilonMovementUtil.createGrimPositionPacket(0.1));
                bypass = false;
                jumpScheduled = true;
                flag = false;
            }

            // ===== LazyGrimPlus2: 精细移植自 SlimefunHelper =====

            case LazyGrimPlus2 -> {
                // 状态机忙或冷却中
                if (step != 0) return;
                if (tickCounter <= lastStartWaitTick + LATENCY) return;

                // Phase 1: COMMON → WAIT_RESYNC
                // 落地瞬间：取消原始包，立即发 OnGroundOnly(true) 诱使服务端回弹
                // （不延迟到 Post，避免包序混乱导致 Grim PacketOrder/TickTimer 标记）
                event.cancel();

                lastStartWaitPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
                lastStartWaitTick = tickCounter;

                mc.player.setOnGround(true);
                bypass = true;
                mc.getNetworkHandler().sendPacket(
                    new PlayerMoveC2SPacket.OnGroundOnly(true, mc.player.horizontalCollision));
                bypass = false;

                step = 1;
                lastOnGroundHeight = mc.player.getY();
                flag = false;
            }
        }
    }
}
