package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Vec3d;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

public class GcoreTpDiagnostic extends Module {

    private final SettingGroup sgGeneral = settings.createGroup("通用");

    private final Setting<Boolean> logMovePackets = sgGeneral.add(new BoolSetting.Builder()
        .name("记录移动包")
        .description("记录每个发出的移动包。")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> logS08 = sgGeneral.add(new BoolSetting.Builder()
        .name("记录回拉包")
        .description("记录收到的 s08 回拉包。")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> logTickState = sgGeneral.add(new BoolSetting.Builder()
        .name("记录每刻位置")
        .description("每 tick 记一行客户端真实位置。用于判断 s08 到底有没有被应用。")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> logActionPackets = sgGeneral.add(new BoolSetting.Builder()
        .name("记录动作包")
        .description("记录攻击 / 挖掘 / 换物 / 放置等动作包，以及它们的发出时刻相对移动包的位置。")
        .defaultValue(true)
        .build());

    private int tickCounter = 0;
    private int movePacketsThisTick = 0;

    private int totalMovePackets = 0;
    private int totalS08 = 0;
    private BufferedWriter writer;

    public GcoreTpDiagnostic() {
        super(AddonTemplate.CATEGORY, "TP 诊断",
            "日志 TP 移动包与 s08 回拉，输出 tick/线程/坐标。开发用的，没事别开。");
    }

    // ─────────────────────── 生命周期 ───────────────────────

    @Override
    public void onActivate() {
        this.tickCounter = 0;
        this.movePacketsThisTick = 0;
        this.totalMovePackets = 0;
        this.totalS08 = 0;
        this.openLog();
        this.line("=== GcoreTpDiagnostic start ===");
        this.line("世界时间(ms)=" + System.currentTimeMillis());
        this.line("说明: 同一 tick 的移动包数 = 服务端看到的 i; i>5 进入惩罚区");
        this.info("诊断已启动，日志写入 .minecraft/gcore-tp-diag.log");
    }

    @Override
    public void onDeactivate() {
        this.line("=== stop: movePackets=" + this.totalMovePackets
            + " s08=" + this.totalS08 + " ===");
        this.closeLog();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        this.tickCounter++;
        if (this.movePacketsThisTick > 5) {
            this.line("!! TICK " + this.tickCounter + " 发出 " + this.movePacketsThisTick
                + " 个移动包 (>5，服务端会进入惩罚区)");
        }
        this.movePacketsThisTick = 0;

        if (this.logTickState.get() && this.mc.player != null) {
            Vec3d p = this.mc.player.getEntityPos();
            this.line(String.format(Locale.ROOT,
                "TICKSTATE tick=%d pos=[%.4f,%.4f,%.4f] onGround=%b",
                this.tickCounter, p.x, p.y, p.z, this.mc.player.isOnGround()));
        }
    }
    @EventHandler
    private void onActionPacketSend(PacketEvent.Send event) {
        if (!this.logActionPackets.get()) return;
        if (!(event.packet instanceof PlayerInteractEntityC2SPacket)
            && !(event.packet instanceof PlayerActionC2SPacket)
            && !(event.packet instanceof UpdateSelectedSlotC2SPacket)
            && !(event.packet instanceof HandSwingC2SPacket)
            && !(event.packet instanceof PlayerInteractBlockC2SPacket)
            && !(event.packet instanceof PlayerInteractItemC2SPacket)) {
            return;
        }
        this.line(String.format(Locale.ROOT,
            "ACTION tick=%d t=%.3f thread=%s cls=%s (此前本tick已发移动包=%d)",
            this.tickCounter, System.nanoTime() / 1_000_000.0,
            Thread.currentThread().getName(),
            event.packet.getClass().getSimpleName(),
            this.movePacketsThisTick));
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!this.logMovePackets.get()) return;
        if (!(event.packet instanceof PlayerMoveC2SPacket p)) return;

        double x = p.getX(0.0);
        double y = p.getY(0.0);
        double z = p.getZ(0.0);
        boolean onGround = p.isOnGround();
        boolean hColl = p.horizontalCollision();
        boolean hasPos = p.changesPosition();
        boolean hasLook = p.changesLook();

        this.movePacketsThisTick++;
        this.totalMovePackets++;

        this.line(String.format(Locale.ROOT,
            "SEND tick=%d inTick=%d t=%.3f thread=%s cls=%s pos=%s xyz=[%.4f,%.4f,%.4f] onGround=%b hColl=%b hasPos=%b hasLook=%b",
            this.tickCounter, this.movePacketsThisTick,
            System.nanoTime() / 1_000_000.0,
            Thread.currentThread().getName(),
            p.getClass().getSimpleName(),
            hasPos ? "yes" : "no",
            x, y, z, onGround, hColl, hasPos, hasLook));
    }


    @EventHandler(priority = Integer.MAX_VALUE)
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!this.logS08.get()) return;
        if (!(event.packet instanceof PlayerPositionLookS2CPacket p)) return;

        Vec3d serverPos = p.change().position();
        this.totalS08++;
        this.line(String.format(Locale.ROOT,
            "RECV-S08 tick=%d t=%.3f thread=%s id=%d serverPos=[%.4f,%.4f,%.4f] clientPos=%s",
            this.tickCounter,
            System.nanoTime() / 1_000_000.0,
            Thread.currentThread().getName(),
            p.teleportId(),
            serverPos.x, serverPos.y, serverPos.z,
            this.mc.player == null ? "null" : this.mc.player.getEntityPos().toString()));
    }

    // ─────────────────────── 日志 ───────────────────────

    private Path logPath;

    private void openLog() {
        try {
            // 与 .minecraft 同级：FabricLoader 的运行目录
            Path dir = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir();
            this.logPath = dir.resolve("gcore-tp-diag.log");
            this.writer = Files.newBufferedWriter(this.logPath, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException e) {
            this.writer = null;
        }
    }

    private void line(String s) {
        if (this.writer == null) return;
        try {
            this.writer.write(s);
            this.writer.newLine();
            this.writer.flush();
        } catch (IOException ignored) {
        }
    }

    private void closeLog() {
        if (this.writer != null) {
            try {
                this.writer.flush();
                this.writer.close();
            } catch (IOException ignored) {
            }
            this.writer = null;
        }
    }
}
