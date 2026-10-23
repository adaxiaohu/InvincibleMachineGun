package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.utils.gcore.GcoreBlockUtil;
import com.codigohasta.addon.utils.gcore.GcoreMSTimer;
import com.codigohasta.addon.utils.gcore.GcoreTpOption;
import com.codigohasta.addon.utils.gcore.GcoreTpUtil;
import com.codigohasta.addon.utils.gcore.GcoreServerEquivMove;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.atomic.AtomicBoolean;
public class GAntiLag extends Module {

    public enum VersionType {
        MC1_16("1.16"),
        MC1_9("1.9");

        private final String choiceName;

        VersionType(String choiceName) {
            this.choiceName = choiceName;
        }

        public String getChoiceName() {
            return this.choiceName;
        }

        @Override
        public String toString() {
            return this.choiceName;
        }
    }

    private final SettingGroup sgGeneral = this.settings.createGroup("通用");

    private final Setting<VersionType> version;
    private final Setting<Double> range;

    private final Setting<Integer> limitPerSecond;

    // 2026-10-15同刻去重
    private final Setting<Boolean> dedupSameTick;
    private final Setting<Integer> dedupWindowMs;
    private final Setting<Integer> dedupMaxPerWindow;
    private volatile long lastHandleNanos = 0L;
    private final java.util.concurrent.atomic.AtomicInteger handledInWindow =
        new java.util.concurrent.atomic.AtomicInteger(0);
    private final GcoreTpOption tpOption;
    private final GcoreMSTimer timer = new GcoreMSTimer();
    private int lagCounter;
    private final Setting<Boolean> asyncProcess;
    private final AtomicBoolean busy = new AtomicBoolean(false);

    public GAntiLag() {
        super(AddonTemplate.CATEGORY, "防回拉",
            "让你不回弹，使用tp类模块应一并开启这个。原版。");

        this.version = this.sgGeneral.add(new EnumSetting.Builder<VersionType>()
            .name("版本模式")
            .description("服务端版本模式。1.16 走 TpUtil 穿墙路径，1.9 走简单直线。")
            .defaultValue(VersionType.MC1_16)
            .build());

        this.range = this.sgGeneral.add(new DoubleSetting.Builder()
            .name("触发距离")
            .description("触发拦截的最大距离（格）。超出此距离的回拉不管。")
            .defaultValue(100.0)
            .range(0.1, 200.0)
            .sliderRange(0.1, 200.0)
            .build());

        this.limitPerSecond = this.sgGeneral.add(new IntSetting.Builder()
            .name("每秒拦截上限")
            .description("每秒最多拦截几次（防止反拉本身发太多包）。")
            .defaultValue(12)
            .range(1, 20)
            .sliderRange(1, 20)
            .build());

        this.dedupSameTick = this.sgGeneral.add(new BoolSetting.Builder()
            .name("同刻只响应一次")
            .description("同一个tick内只对第一个回拉包做反拉")
            .defaultValue(true)
            .build());

        this.dedupWindowMs = this.sgGeneral.add(new IntSetting.Builder()
            .name("去重窗口(ms)")
            .description("多长时间内算同一刻")
            .defaultValue(50)
            .min(0)
            .sliderRange(10, 200)
            .max(1000)
            .visible(this.dedupSameTick::get)
            .build());

        this.dedupMaxPerWindow = this.sgGeneral.add(new IntSetting.Builder()
            .name("窗口内最大响应次数")
            .description("一个窗口内最多反拉几次。")
            .defaultValue(1)
            .min(1)
            .sliderRange(1, 4)
            .max(20)
            .visible(this.dedupSameTick::get)
            .build());

        this.asyncProcess = this.sgGeneral.add(new BoolSetting.Builder()
            .name("异步处理")
            .description("把重扫描丢到后台线程，避免阻塞网络线程")
            .defaultValue(false)
            .build());

        this.tpOption = new GcoreTpOption(this.sgGeneral, false);
    }

    @Override
    public void onActivate() {
        this.lagCounter = 0;
        this.timer.reset();
        this.busy.set(false);
    }

    @Override
    public void onDeactivate() {
        this.lagCounter = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (this.timer.hasPassTime(1000)) {
            this.lagCounter = 0;
            this.timer.reset();
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (this.mc.player == null || this.mc.world == null) {
            return;
        }

        if (this.lagCounter > this.limitPerSecond.get()) {
            return;
        }

        if (!(event.packet instanceof PlayerPositionLookS2CPacket packet)) {
            return;
        }

        //  版本适配（1.20.4 → 1.21.11）：
        //   `PlayerPositionLookS2CPacket` 在 1.21.11 变成了 record，
        //   新路径：packet.change() → EntityPosition → position() → Vec3d
        Vec3d serverPos = packet.change().position();
        Vec3d playerPos = this.mc.player.getEntityPos();

        if (serverPos.distanceTo(playerPos) > this.range.get()) {
            return;
        }
        boolean serverPosFree = GcoreBlockUtil.checkNoPosCollie(serverPos);
        boolean playerPosFree = GcoreBlockUtil.checkNoPosCollie(playerPos);
        boolean willHandle;
        if (serverPosFree) {
            willHandle = playerPosFree;                 // 路径 A 处理；路径 B 放弃
        } else {
            willHandle = this.version.get() != VersionType.MC1_16 || playerPosFree;  // 路径 C
        }

        if (!willHandle) {
            return;                                      // 放弃：包正常通过，不 cancel
        }

        if (this.dedupSameTick.get()) {
            long now = System.nanoTime();
            long windowNs = Math.max(1L, this.dedupWindowMs.get()) * 1_000_000L;
            if (this.lastHandleNanos != 0L && (now - this.lastHandleNanos) < windowNs) {
                if (this.handledInWindow.get() >= this.dedupMaxPerWindow.get()) {
                    this.sendTeleportConfirm(packet.teleportId());
                    event.cancel();
                    return;
                }
            } else {
                this.handledInWindow.set(0);
            }
            this.lastHandleNanos = now;
            this.handledInWindow.incrementAndGet();
        }

        this.lagCounter = this.lagCounter + 1;

        event.cancel();

        if (!this.asyncProcess.get()) {
            this.runCore(serverPosFree, playerPosFree, serverPos, playerPos, packet.teleportId());
            return;
        }
        if (!this.busy.compareAndSet(false, true)) {
            return;
        }

        int teleportId = packet.teleportId();
        boolean sFree = serverPosFree;
        boolean pFree = playerPosFree;
        Thread.ofVirtual().name("gcore-antilag").start(() -> {
            try {
                this.runCore(sFree, pFree, serverPos, playerPos, teleportId);
            } catch (Throwable ignored) {
            } finally {
                this.busy.set(false);
            }
        });
    }

    private void runCore(boolean serverPosFree, boolean playerPosFree,
                         Vec3d serverPos, Vec3d playerPos, int teleportId) {
        try (var ignored = GcoreServerEquivMove.enter("GAntiLag")) {
            if (serverPosFree) {
                this.sendTeleportConfirm(teleportId);
                GcoreTpUtil.doTpMove(this.tpOption, serverPos, playerPos,
                    GcoreBlockUtil.getBufferMap(), (counter, curVec) -> {
                    });
            } else {
                this.sendTeleportConfirm(teleportId);
                GcoreTpUtil.doTp(serverPos, playerPos, this.tpOption.getMoveDistance(), true);
            }
        }
    }

    private void sendTeleportConfirm(int teleportId) {
        if (this.mc.getNetworkHandler() == null) {
            return;
        }
        this.mc.getNetworkHandler().sendPacket(new TeleportConfirmC2SPacket(teleportId));
    }
}
