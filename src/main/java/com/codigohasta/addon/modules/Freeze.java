package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.mixin.InventoryAccessor;
import com.codigohasta.addon.utils.openmyau.ChatUtil;
import meteordevelopment.meteorclient.events.entity.EntityRemovedEvent;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.consume.UseAction;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Freeze — 融合实现：
 * - BMWClient-nextgen (LiquidBounce) ModuleFreeze 的 Queue / Cancel / Stationary 三模式
 * - MeteorPlus Freeze 的 Grim 兼容设计（不 cancel S08、PlayerMoveEvent 拦截移动）
 * - 重锤伤害累计：Stationary 模式下缓慢下沉积累服务端 fallDistance
 *
 * 核心策略：
 * - Queue / Cancel 保留原 BMW 逻辑（用于非 Grim 服务器）
 * - Stationary 不取消移动包，而是替换为冻结坐标包，保持包序避免 PacketOrderO
 * - Mace 模式下冷冻 Y 缓慢下沉，让服务端累计坠落高度，每次攻击均有暴击伤害
 */
public class Freeze extends Module {

    public enum FreezeMode {
        Queue,
        Cancel,
        Stationary
    }

    // ===== 设置组 =====
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgStationary = settings.createGroup("Stationary");
    private final SettingGroup sgLook = settings.createGroup("Look");
    private final SettingGroup sgMace = settings.createGroup("Mace");

    // --- 通用 ---
    private final Setting<FreezeMode> mode = sgGeneral.add(new EnumSetting.Builder<FreezeMode>()
        .name("mode")
        .description("冻结模式")
        .defaultValue(FreezeMode.Stationary)
        .build()
    );

    private final Setting<Boolean> disableOnFlag = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-on-flag")
        .description("收到服务端位置校正包时自动禁用模块")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> notification = sgGeneral.add(new BoolSetting.Builder()
        .name("notification")
        .description("被 flag 时发送聊天提示")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> balance = sgGeneral.add(new BoolSetting.Builder()
        .name("balance")
        .description("禁用时回放丢失的刻（平衡玩家 tick）")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> bypassNegativeTimer = sgGeneral.add(new BoolSetting.Builder()
        .name("bypass-negative-timer")
        .description("禁用时执行一次物品交互以绕过负计时检测")
        .defaultValue(true)
        .build()
    );

    // --- Stationary 专用 ---
    private final Setting<Boolean> cancelC0B = sgStationary.add(new BoolSetting.Builder()
        .name("cancel-c0b")
        .description("取消 CommonPongC2SPacket（绕过 Grim BadPacketsR）")
        .defaultValue(false)
        .visible(() -> mode.get() == FreezeMode.Stationary)
        .build()
    );

    // --- 视角冻结 ---
    private final Setting<Boolean> freezeLook = sgLook.add(new BoolSetting.Builder()
        .name("freeze-look")
        .description("冻结视角（偏转角）")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> freezeLookSilent = sgLook.add(new BoolSetting.Builder()
        .name("freeze-look-silent")
        .description("静默冻结视角（客户端可转，服务端冻结）")
        .defaultValue(true)
        .visible(freezeLook::get)
        .build()
    );

    // --- 重锤伤害累计 ---
    private final Setting<Boolean> maceMode = sgMace.add(new BoolSetting.Builder()
        .name("mace-mode")
        .description("Stationary 模式下缓慢下沉累计服务端 fallDistance，每次攻击均有重锤暴击伤害")
        .defaultValue(true)
        .visible(() -> mode.get() == FreezeMode.Stationary)
        .build()
    );

    private final Setting<Double> driftSpeed = sgMace.add(new DoubleSetting.Builder()
        .name("drift-speed")
        .description("每 tick 下沉格数（0.05 = 1 格/秒，累计 fallDistance）")
        .defaultValue(0.05)
        .range(0.01, 0.5)
        .sliderRange(0.01, 0.2)
        .visible(() -> mode.get() == FreezeMode.Stationary && maceMode.get())
        .build()
    );

    private final Setting<Double> maxDrift = sgMace.add(new DoubleSetting.Builder()
        .name("max-drift")
        .description("最大总下沉距离（到达后不再下沉，服务端 S08 校正后会重置）")
        .defaultValue(5.0)
        .range(1.0, 20.0)
        .sliderRange(1.0, 10.0)
        .visible(() -> mode.get() == FreezeMode.Stationary && maceMode.get())
        .build()
    );

    // ===== 运行时状态 =====
    private int missedOutTick = 0;
    private boolean warpInProgress = false;
    private boolean isFlushing = false;
    private final List<Packet<?>> queuedPackets = new ArrayList<>();

    // 冻结坐标 & 视角
    private double frozenX, frozenY, frozenZ;
    private float frozenYaw, frozenPitch;

    // 重锤下沉累计
    private double accumulatedDrift = 0;

    // 旋转偏移生成器（预留）
    private float prevYawOffset = 0f;
    private float prevPitchOffset = 0f;
    private final Random random = new Random();

    public Freeze() {
        super(AddonTemplate.CATEGORY, "冻结",
            "冻结自身位置。Stationary 模式兼容 Grim（不取消 S08，替换移动包保持包序）。\n" +
            "Mace 模式下缓慢下沉累计服务端 fallDistance，重锤攻击持续有暴击伤害。\n" +
            "三种模式：Queue（缓存包）/ Cancel（丢弃包）/ Stationary（替换移动包，交互正常）没啥用");
    }

    // ===== 生命周期 =====

    @Override
    public void onActivate() {
        if (mc.player == null) {
            toggle();
            return;
        }

        missedOutTick = 0;
        queuedPackets.clear();
        warpInProgress = false;
        accumulatedDrift = 0;

        frozenX = mc.player.getX();
        frozenY = mc.player.getY();
        frozenZ = mc.player.getZ();
        frozenYaw = mc.player.getYaw();
        frozenPitch = mc.player.getPitch();
    }

    @Override
    public void onDeactivate() {
        if (balance.get() && mc.player != null) {
            warpInProgress = true;
            while (missedOutTick > 0) {
                mc.player.tick();
                missedOutTick--;
            }
            warpInProgress = false;
        }

        if (mode.get() == FreezeMode.Queue && !queuedPackets.isEmpty() && mc.getNetworkHandler() != null) {
            isFlushing = true;
            for (Packet<?> packet : queuedPackets) {
                mc.getNetworkHandler().sendPacket(packet);
            }
            queuedPackets.clear();
            isFlushing = false;
        }

        missedOutTick = 0;
        if (bypassNegativeTimer.get()) interact();
    }

    // ===== 事件：Ticks & 移动 =====

    /** 每 tick 强制锁定位置 + 重锤下沉累计 */
    @EventHandler
    public void onTickPre(TickEvent.Pre event) {
        if (warpInProgress) return;

        missedOutTick++;

        if (mc.player == null) return;

        mc.player.setVelocity(Vec3d.ZERO);

        // Mace 模式：Stationary 下缓慢下沉，累计服务端 fallDistance
        if (mode.get() == FreezeMode.Stationary && maceMode.get() && accumulatedDrift < maxDrift.get()) {
            double nextY = frozenY - driftSpeed.get();
            // 检测下一 tick 的下沉位置是否会撞到方块（防止钻地+穿墙 flag）
            if (mc.world != null && mc.player != null) {
                Box nextBox = mc.player.getBoundingBox().offset(0, nextY - frozenY, 0);
                if (mc.world.isSpaceEmpty(mc.player, nextBox)) {
                    frozenY = nextY;
                    accumulatedDrift += driftSpeed.get();
                } else {
                    accumulatedDrift = maxDrift.get(); // 触地/撞墙，停止下沉
                }
            } else {
                frozenY -= driftSpeed.get();
                accumulatedDrift += driftSpeed.get();
            }
        }

        mc.player.setPosition(frozenX, frozenY, frozenZ);
    }

    /** 移动发生前置零，让客户端位置不变化 */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        event.movement = Vec3d.ZERO;
    }

    // ===== 事件：数据包 =====

    /**
     * S08 处理：
     * - 不 cancel S08！让服务端正常校正，避免飞行踢出和 Grim Timer 检测。
     * - 接受服务端校正的位置/视角，更新冻结状态。
     * - 根据 disableOnFlag 决定是否自动禁用。
     */
    @EventHandler
    public void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerPositionLookS2CPacket) {
            missedOutTick = 0;

            if (mc.player != null) {
                frozenYaw = mc.player.getYaw();
                frozenPitch = mc.player.getPitch();
                frozenX = mc.player.getX();
                frozenY = mc.player.getY();
                frozenZ = mc.player.getZ();
            }

            accumulatedDrift = 0;

            if (disableOnFlag.get()) {
                if (notification.get()) {
                    ChatUtil.sendFormatted("&c[Freeze] &7Flagged - disabled");
                }
                toggle();
            }
        }
    }

    /** 根据模式拦截/替换出站包 */
    @SuppressWarnings("rawtypes")
    @EventHandler
    public void onPacketSend(PacketEvent.Send event) {
        if (warpInProgress || isFlushing) return;

        Packet<?> packet = event.packet;

        switch (mode.get()) {
            case Queue -> handleQueue(event, packet);
            case Cancel -> handleCancel(event, packet);
            case Stationary -> handleStationary(event, packet);
        }
    }

    // ===== Queue 模式 =====

    @SuppressWarnings("rawtypes")
    private void handleQueue(PacketEvent.Send event, Packet<?> packet) {
        queuedPackets.add(packet);
        event.cancel();
    }

    // ===== Cancel 模式 =====

    @SuppressWarnings("rawtypes")
    private void handleCancel(PacketEvent.Send event, Packet<?> packet) {
        if (packet instanceof PlayerMoveC2SPacket) {
            event.cancel();
        }
    }

    // ===== Stationary 模式（核心） =====

    /**
     * Stationary 策略：
     * - PlayerMoveC2SPacket → 替换为冻结坐标包（不取消！保持包序）
     * - CommonPongC2SPacket → 可选取消（绕过 BadPacketsR）
     * - 其余所有包（交互、动画、物品使用）正常放行
     *
     * 由于移动包正常流动，Grim 不会触发 PacketOrderO。
     * 替换包使用 isFlushing 守卫防止递归。
     */
    @SuppressWarnings("rawtypes")
    private void handleStationary(PacketEvent.Send event, Packet<?> packet) {
        if (packet instanceof PlayerMoveC2SPacket movePacket) {
            PlayerMoveC2SPacket replacement = replaceMovePacket(movePacket);
            if (replacement != movePacket) {
                event.cancel();
                isFlushing = true;
                sendPacketRaw(replacement);
                isFlushing = false;
            }
            // replacement == movePacket 时不 cancel，原包正常通过
            return;
        }

        if (packet instanceof CommonPongC2SPacket) {
            if (cancelC0B.get()) event.cancel();
            return;
        }
    }

    /**
     * 替换移动包内容：
     * - Full（位置+视角）→ PositionAndOnGround（仅锁定位置，不带视角，避免 AimDuplicateLook）
     * - PositionAndOnGround → 位置锁定为 frozen
     * - LookAndOnGround → 视角锁定为 frozen（仅当 freezeLook 开启）
     * - OnGroundOnly / LookAndOnGround (无 freezeLook) → 原包通过
     */
    private PlayerMoveC2SPacket replaceMovePacket(PlayerMoveC2SPacket original) {
        boolean onGround = mc.player != null && mc.player.isOnGround();
        boolean horizCollision = mc.player != null && mc.player.horizontalCollision;

        if (original.changesPosition() && original.changesLook()) {
            // Full → PositionAndOnGround: 仅替换位置，丢弃视角数据
            // 不发送 Full 包（不携带 rotation），Server 不会触发 RotationUpdate，
            // 避免连续两个 rotation 包 yaw/pitch 完全相同时 AimDuplicateLook 误判
            return new PlayerMoveC2SPacket.PositionAndOnGround(frozenX, frozenY, frozenZ, onGround, horizCollision);
        }

        if (original.changesPosition()) {
            // PositionAndOnGround: 替换位置
            return new PlayerMoveC2SPacket.PositionAndOnGround(frozenX, frozenY, frozenZ, onGround, horizCollision);
        }

        if (original.changesLook() && freezeLook.get()) {
            // LookAndOnGround + freezeLook: 替换视角
            return new PlayerMoveC2SPacket.LookAndOnGround(frozenYaw, frozenPitch, onGround, horizCollision);
        }

        // LookAndOnGround (无 freezeLook) 或 OnGroundOnly: 无需替换
        return original;
    }

    // ===== 工具 =====

    private void sendPacketRaw(Packet<?> packet) {
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(packet);
        }
    }

    // ===== 安全事件 =====

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (isActive()) toggle();
    }

    @EventHandler
    private void onEntityRemoved(EntityRemovedEvent event) {
        if (event.entity == mc.player && isActive()) {
            toggle();
        }
    }

    // ===== 旋转偏移生成器（预留） =====

    @SuppressWarnings("unused")
    private float generateYawOffset() {
        float offset;
        do {
            offset = (float) (0.002 + random.nextDouble() * 0.008);
        } while (Math.abs(offset - prevYawOffset) < 1.0E-6F);
        prevYawOffset = offset;
        return offset;
    }

    @SuppressWarnings("unused")
    private float generatePitchOffset() {
        float offset;
        do {
            offset = (float) (0.002 + random.nextDouble() * 0.008);
        } while (Math.abs(offset - prevPitchOffset) < 1.0E-6F);
        prevPitchOffset = offset;
        return offset;
    }

    // ===== bypassNegativeTimer 物品交互 =====

    private boolean isInteractable(ItemStack stack) {
        if (stack.getItem() == Items.ENDER_PEARL
            || stack.getItem() == Items.TNT
            || stack.getItem() == Items.FIRE_CHARGE
            || stack.getItem() == Items.WIND_CHARGE) {
            return false;
        }
        UseAction action = stack.getUseAction();
        return action != UseAction.EAT && action != UseAction.DRINK
            && action != UseAction.BOW && action != UseAction.CROSSBOW;
    }

    private void interact() {
        if (mc.player == null || mc.interactionManager == null || mc.world == null) return;

        InventoryAccessor inv = (InventoryAccessor) mc.player.getInventory();

        Hand hand = Hand.OFF_HAND;
        int prevSlot = -1;

        if (!isInteractable(mc.player.getStackInHand(Hand.OFF_HAND))) {
            for (int i = 0; i <= 8; i++) {
                if (isInteractable(mc.player.getInventory().getStack(i))) {
                    hand = Hand.MAIN_HAND;
                    if (i != inv.getSelectedSlot()) {
                        prevSlot = inv.getSelectedSlot();
                        inv.setSelectedSlot(i);
                    }
                    break;
                }
            }
        }

        mc.interactionManager.interactItem(mc.player, hand);

        if (prevSlot != -1) {
            inv.setSelectedSlot(prevSlot);
        }
    }
}
