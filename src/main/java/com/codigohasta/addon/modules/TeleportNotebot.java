package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.smarttpaura.pathfinding.AStarPathFinder;
import com.codigohasta.addon.smarttpaura.rendering.PathRenderer;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.Notebot;
import meteordevelopment.meteorclient.utils.notebot.song.Note;
import meteordevelopment.meteorclient.utils.notebot.song.Song;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.NoteBlock;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Field;
import java.util.*;

public class TeleportNotebot extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTeleport = settings.createGroup("A* 寻路与瞬移");
    private final SettingGroup sgRender = settings.createGroup("路径渲染设置");

    // --- General ---
    public final Setting<Integer> scanRange = sgGeneral.add(new IntSetting.Builder().name("扫描范围").defaultValue(100).min(1).sliderRange(1, 128).build());
    public final Setting<Integer> actionSpeed = sgGeneral.add(new IntSetting.Builder().name("执行速度").description("每Tick处理多少个音符").defaultValue(2).min(1).sliderMax(10).build());
    public final Setting<Boolean> polyphonic = sgGeneral.add(new BoolSetting.Builder().name("和弦模式").description("是否允许同一Tick弹奏多个音符").defaultValue(true).build());

    // --- Teleport ---
    private final Setting<Double> maxStep = sgTeleport.add(new DoubleSetting.Builder().name("TP最大跨度").defaultValue(8.0).min(1.0).sliderMax(10.0).build());
    private final Setting<Boolean> airPath = sgTeleport.add(new BoolSetting.Builder().name("V-Clip (穿墙)").defaultValue(true).build());
    private final Setting<Boolean> returnPos = sgTeleport.add(new BoolSetting.Builder().name("执行后返回").defaultValue(true).build());

    // --- Render ---
    private final Setting<Boolean> renderRaw = sgRender.add(new BoolSetting.Builder().name("渲染原始路径").defaultValue(true).build());
    private final Setting<SettingColor> rawColor = sgRender.add(new ColorSetting.Builder().name("原始路径颜色").defaultValue(new SettingColor(150, 150, 150, 100)).build());
    private final Setting<Boolean> renderPackets = sgRender.add(new BoolSetting.Builder().name("渲染发包路径").defaultValue(true).build());
    private final Setting<SettingColor> packetColor = sgRender.add(new ColorSetting.Builder().name("发包路径颜色").defaultValue(new SettingColor(255, 0, 255, 255)).build());
    private final Setting<SettingColor> boxColor = sgRender.add(new ColorSetting.Builder().name("音符盒边框颜色").defaultValue(new SettingColor(0, 255, 0, 60)).build());

    private Song song;
    private final Map<Note, BlockPos> noteBlockPositions = new HashMap<>();
    private final List<BlockPos> scannedNoteblocks = new ArrayList<>();
    private final Map<BlockPos, Integer> tuneHits = new HashMap<>();
    private Stage stage = Stage.None;
    private boolean isPlaying = false;
    private int currentTick = 0;

    private AStarPathFinder pathFinder;
    private PathRenderer pathRenderer;
    
    private List<Vec3d> lastRawPath = new ArrayList<>();
    private List<Vec3d> lastPacketsPath = new ArrayList<>();

    public TeleportNotebot() {
        super(AddonTemplate.CATEGORY, "TeleportNotebot", "如来神掌：深度集成 A* 与人体盒渲染");
    }

    @Override
    public void onActivate() {
        resetVariables();
        pathFinder = new AStarPathFinder(mc.world);
        pathRenderer = new PathRenderer();
    }

    private void resetVariables() {
        stage = Stage.None;
        isPlaying = false;
        currentTick = 0;
        noteBlockPositions.clear();
        scannedNoteblocks.clear();
        tuneHits.clear();
        lastRawPath.clear();
        lastPacketsPath.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onTick(TickEvent.Pre event) {
        if (mc.world == null || mc.player == null) return;
        hijackFromOriginal();
        if (stage == Stage.None) return;

        switch (stage) {
            case SetUp -> {
                doBroadScan();
                if (scannedNoteblocks.isEmpty()) { error("没找到音符盒！"); stage = Stage.None; return; }
                setupNoteblocksMap();
                setupTuneHitsMap();
                info("准备执行...");
                stage = Stage.Tune;
            }
            case Tune -> {
                for (int i = 0; i < actionSpeed.get(); i++) {
                    if (tuneHits.isEmpty()) { info("调音完毕，演奏开始。"); stage = Stage.Playing; isPlaying = true; break; }
                    tuneOneStep();
                }
            }
            case Playing -> {
                if (!isPlaying || currentTick > song.getLastTick()) { onSongEnd(); return; }
                if (song.getNotesMap().containsKey(currentTick)) {
                    for (Note note : song.getNotesMap().get(currentTick)) {
                        BlockPos pos = noteBlockPositions.get(note);
                        if (pos != null) performAStarAction(pos, () -> {
                            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, Direction.DOWN));
                        });
                        if (!polyphonic.get()) break;
                    }
                }
                currentTick++;
            }
        }
    }

    private void performAStarAction(BlockPos targetPos, Runnable action) {
        Vec3d start = mc.player.getPos();
        Vec3d targetVec = Vec3d.ofCenter(targetPos);

        pathFinder.setAirPath(airPath.get());
        List<Vec3d> simplified = pathFinder.findPath(start, targetVec);
        if (simplified == null || simplified.isEmpty()) return;

        lastRawPath = new ArrayList<>(simplified);
        List<Vec3d> packets = interpolate(simplified, maxStep.get());
        lastPacketsPath = new ArrayList<>(packets);

        for (Vec3d p : packets) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(p.x, p.y, p.z, true, false));
        }

        action.run();
        mc.player.swingHand(Hand.MAIN_HAND);
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

        if (returnPos.get()) {
            Collections.reverse(packets);
            for (Vec3d p : packets) {
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(p.x, p.y, p.z, true, false));
            }
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(start.x, start.y, start.z, true, false));
        }
    }

    private List<Vec3d> interpolate(List<Vec3d> path, double step) {
        List<Vec3d> res = new ArrayList<>();
        if (path.size() < 2) return path;
        res.add(path.get(0));
        for (int i = 0; i < path.size() - 1; i++) {
            Vec3d s = path.get(i);
            Vec3d e = path.get(i + 1);
            double d = s.distanceTo(e);
            if (d > step) {
                int n = (int) Math.ceil(d / step);
                for (int j = 1; j < n; j++) res.add(s.lerp(e, (double) j / n));
            }
            res.add(e);
        }
        return res;
    }

    private void tuneOneStep() {
        var it = tuneHits.entrySet().iterator();
        if (it.hasNext()) {
            var entry = it.next();
            performAStarAction(entry.getKey(), () -> {
                BlockHitResult bhr = new BlockHitResult(Vec3d.ofCenter(entry.getKey()), Direction.DOWN, entry.getKey(), false);
                mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, bhr, 0));
            });
            int remains = entry.getValue() - 1;
            if (remains <= 0) it.remove(); else entry.setValue(remains);
        }
    }

    private void doBroadScan() {
        scannedNoteblocks.clear();
        int r = scanRange.get();
        BlockPos p = mc.player.getBlockPos();
        BlockPos.Mutable mPos = new BlockPos.Mutable();
        for (int x = p.getX() - r; x <= p.getX() + r; x++) {
            for (int z = p.getZ() - r; z <= p.getZ() + r; z++) {
                for (int y = p.getY() - 64; y <= p.getY() + 64; y++) {
                    mPos.set(x, y, z);
                    if (mc.world.getBlockState(mPos).isOf(Blocks.NOTE_BLOCK)) scannedNoteblocks.add(mPos.toImmutable());
                }
            }
        }
    }

    private void setupNoteblocksMap() {
        noteBlockPositions.clear();
        List<Note> reqs = new ArrayList<>(song.getRequirements());
        for (int i = 0; i < reqs.size() && i < scannedNoteblocks.size(); i++) {
            noteBlockPositions.put(reqs.get(i), scannedNoteblocks.get(i));
        }
    }

    private void setupTuneHitsMap() {
        tuneHits.clear();
        for (var entry : noteBlockPositions.entrySet()) {
            int target = entry.getKey().getNoteLevel();
            BlockState s = mc.world.getBlockState(entry.getValue());
            if (s.isOf(Blocks.NOTE_BLOCK) && s.get(NoteBlock.NOTE) != target) {
                int current = s.get(NoteBlock.NOTE);
                tuneHits.put(entry.getValue(), target > current ? target - current : (25 - current) + target);
            }
        }
    }

    private void hijackFromOriginal() {
        try {
            Notebot original = Modules.get().get(Notebot.class);
            Field songField = Notebot.class.getDeclaredField("song");
            songField.setAccessible(true);
            Song oSong = (Song) songField.get(original);
            if (oSong != null) {
                this.song = oSong;
                this.stage = Stage.SetUp;
                songField.set(original, null);
                Field oStageField = Notebot.class.getDeclaredField("stage");
                oStageField.setAccessible(true);
                for (Object c : oStageField.getType().getEnumConstants()) {
                    if (c.toString().equalsIgnoreCase("None")) { oStageField.set(original, c); break; }
                }
                if (original.isActive()) original.toggle();
            }
        } catch (Exception ignored) {}
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!noteBlockPositions.isEmpty()) {
            for (BlockPos pos : noteBlockPositions.values()) {
                event.renderer.box(pos, boxColor.get(), boxColor.get(), ShapeMode.Both, 0);
            }
        }
        if (pathRenderer != null) {
            if (renderRaw.get() && !lastRawPath.isEmpty()) {
                pathRenderer.renderPath(event.renderer, lastRawPath, rawColor.get(), true);
            }
            if (renderPackets.get() && !lastPacketsPath.isEmpty()) {
                pathRenderer.renderPath(event.renderer, lastPacketsPath, packetColor.get(), false);
            }
        }
    }

    private void onSongEnd() { isPlaying = false; stage = Stage.None; info("歌曲结束。"); }
    public void stop() { isPlaying = false; stage = Stage.None; }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WTable table = theme.table();
        WButton open = table.add(theme.button("Meteor 歌曲库")).expandX().widget();
        open.action = () -> { if (!this.isActive()) this.toggle(); mc.setScreen(theme.notebotSongs()); };
        return table;
    }

    public enum Stage { None, SetUp, Tune, Playing }
}