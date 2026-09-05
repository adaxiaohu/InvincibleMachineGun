package com.codigohasta.addon.modules;

import com.codigohasta.addon.utils.TerminatorModelScan;
import com.mojang.blaze3d.systems.RenderSystem;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.WireframeEntityRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import org.joml.Vector3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class TerminatorHudShader extends FullscreenShaderModule {
    private static final String SOUND_NAMESPACE = "invincible_machine_gun";
    private static final Identifier[] ENTITY_CONFIRM_SOUND_IDS = {
        Identifier.of(SOUND_NAMESPACE, "scan_and_confirm_1"),
        Identifier.of(SOUND_NAMESPACE, "scan_and_confirm_2"),
        Identifier.of(SOUND_NAMESPACE, "scan_and_confirm_3")
    };
    private static final Identifier BLOCK_CONFIRM_SOUND_ID = Identifier.of(SOUND_NAMESPACE, "block_scanning_confirmation");
    private static final Identifier FOUND_SOUND_ID = Identifier.of(SOUND_NAMESPACE, "found_it");
    private static final Identifier HURT_SOUND_ID = Identifier.of(SOUND_NAMESPACE, "hurt");
    private static final Identifier DYING_SOUND_ID = Identifier.of(SOUND_NAMESPACE, "dying");
    private static final Identifier TERMINATOR_SOUND_ID = Identifier.of(SOUND_NAMESPACE, "terminator");
    private final SettingGroup general = settings.getDefaultGroup();
    private final SettingGroup scanner = settings.createGroup("目标扫描");
    private final SettingGroup blockScanner = settings.createGroup("方块扫描");
    private final SettingGroup missions = settings.createGroup("扫描任务");
    private final SettingGroup audio = settings.createGroup("音效与生命反馈");
    private final SettingGroup interfaceGroup = settings.createGroup("电影 HUD");

    private final Setting<SettingColor> filterColor = general.add(new ColorSetting.Builder()
        .name("视觉滤镜颜色").description("机器视觉世界滤镜的主色。")
        .defaultValue(new SettingColor(255, 28, 18)).build());
    private final Setting<SettingColor> interfaceColor = general.add(new ColorSetting.Builder()
        .name("界面颜色").description("数据表、准心、扫描框和网格的颜色。")
        .defaultValue(new SettingColor(255, 255, 255)).build());
    private final Setting<Double> filterStrength = general.add(new DoubleSetting.Builder()
        .name("滤镜强度").description("红色机器视觉滤镜覆盖原画面的强度。")
        .defaultValue(0.82).min(0.0).sliderMax(1.0).build());
    private final Setting<Double> contrast = general.add(new DoubleSetting.Builder()
        .name("对比度").description("增强机器视觉画面的明暗对比。")
        .defaultValue(1.25).min(0.5).sliderMax(2.5).build());
    private final Setting<Double> scanlineStrength = general.add(new DoubleSetting.Builder()
        .name("扫描线强度").description("水平 CRT 扫描线的可见程度。")
        .defaultValue(0.18).min(0.0).sliderMax(0.8).build());
    private final Setting<Double> scanSpeed = general.add(new DoubleSetting.Builder()
        .name("扫描速度").description("扫描线与扫描光束的移动速度。")
        .defaultValue(1.0).min(0.0).sliderMax(5.0).build());
    private final Setting<Double> noiseStrength = general.add(new DoubleSetting.Builder()
        .name("噪点强度").description("机器视觉画面的动态噪点强度。")
        .defaultValue(0.035).min(0.0).sliderMax(0.25).build());
    private final Setting<Double> vignetteStrength = general.add(new DoubleSetting.Builder()
        .name("暗角强度").description("压暗屏幕边缘的程度。")
        .defaultValue(0.55).min(0.0).sliderMax(1.0).build());
    private final Setting<Double> hudOpacity = general.add(new DoubleSetting.Builder()
        .name("HUD 亮度").description("瞄准框、刻度和数据条的亮度。")
        .defaultValue(0.85).min(0.0).sliderMax(1.5).build());
    private final Setting<Boolean> reticle = general.add(new BoolSetting.Builder()
        .name("瞄准界面").description("显示终结者风格的中心断环准心。")
        .defaultValue(true).build());
    private final Setting<Double> reticleSize = general.add(new DoubleSetting.Builder()
        .name("瞄准框大小").description("调整中心瞄准框的尺寸。")
        .defaultValue(0.22).min(0.1).sliderMax(0.45).visible(reticle::get).build());
    private final Setting<Boolean> reticleMotion = general.add(new BoolSetting.Builder()
        .name("准心视线惯性").description("转动视角时让准心产生 T-800 镜头式的移动惯性。")
        .defaultValue(true).visible(reticle::get).build());
    private final Setting<Boolean> reticleTargetTracking = general.add(new BoolSetting.Builder()
        .name("准心跟随扫描").description("扫描目标时让准心平滑移动并锁定到目标。")
        .defaultValue(true).visible(reticle::get).build());

    private final Setting<Boolean> scanPlayers = scanner.add(new BoolSetting.Builder()
        .name("扫描玩家").description("为屏幕中出现的其他玩家绘制分析和锁定框。")
        .defaultValue(true).build());
    private final Setting<Boolean> scanLiving = scanner.add(new BoolSetting.Builder()
        .name("扫描生物").description("扫描怪物、动物及其他生物。")
        .defaultValue(true).build());
    private final Setting<Boolean> scanEntities = scanner.add(new BoolSetting.Builder()
        .name("扫描实体").description("扫描载具、投射物等非生物实体。")
        .defaultValue(false).build());
    private final Setting<Boolean> scanItems = scanner.add(new BoolSetting.Builder()
        .name("扫描掉落物").description("扫描掉落物并显示物品名称和数量。")
        .defaultValue(true).build());
    private final Setting<Double> scanRange = scanner.add(new DoubleSetting.Builder()
        .name("扫描范围").description("可以被 HUD 扫描到的最大目标距离。")
        .defaultValue(96.0).min(8.0).sliderMax(256.0).visible(this::hasEnabledScanner).build());
    private final Setting<Double> acquireDuration = scanner.add(new DoubleSetting.Builder()
        .name("确认动画时长").description("从发现目标到显示 TARGET ACQUIRED 的秒数。")
        .defaultValue(0.9).min(0.15).sliderMax(3.0).visible(this::hasEnabledScanner).build());
    private final Setting<Boolean> throughWalls = scanner.add(new BoolSetting.Builder()
        .name("隔墙扫描").description("允许锁定被方块遮挡的目标。")
        .defaultValue(false).visible(this::hasEnabledScanner).build());
    private final Setting<Boolean> targetDetails = scanner.add(new BoolSetting.Builder()
        .name("目标数据").description("在锁定框旁显示名称、距离、生命值或物品数量。")
        .defaultValue(true).visible(this::hasDisplayableTargets).build());
    private final Setting<Boolean> confirmationFlash = scanner.add(new BoolSetting.Builder()
        .name("确认闪白").description("确认目标时用白色高亮闪烁一次。")
        .defaultValue(true).build());
    private final Setting<Boolean> mergeLivingTargets = scanner.add(new BoolSetting.Builder()
        .name("合并同类生物").description("同类生物靠近时合并为一条 HUD 信息和一个总扫描框。")
        .defaultValue(true).visible(scanLiving::get).build());
    private final Setting<Double> mergeLivingDistance = scanner.add(new DoubleSetting.Builder()
        .name("生物合并距离").description("同类生物之间触发合并显示的最大距离。")
        .defaultValue(5.0).min(1.0).sliderMax(20.0)
        .visible(() -> scanLiving.get() && mergeLivingTargets.get()).build());
    private final Setting<Boolean> structuralScan = scanner.add(new BoolSetting.Builder()
        .name("生物结构扫描").description("依次扫描生物的各个身体结构，每个阶段闪白一次。")
        .defaultValue(true).visible(scanLiving::get).build());
    private final Setting<Double> structuralScanChance = scanner.add(new DoubleSetting.Builder()
        .name("多目标全面扫描概率").description("屏幕内有多个生物时，每个生物触发全面结构扫描的概率。只有一个生物时始终触发。")
        .defaultValue(5.0).min(0.0).sliderMax(100.0)
        .visible(() -> scanLiving.get() && structuralScan.get()).build());
    private final Setting<Double> structuralScanDuration = scanner.add(new DoubleSetting.Builder()
        .name("结构扫描时长").description("完成全部身体结构扫描所需的秒数。")
        .defaultValue(2.2).min(0.8).sliderMax(6.0)
        .visible(() -> scanLiving.get() && structuralScan.get()).build());

    private final Setting<Boolean> blockScanEnabled = blockScanner.add(new BoolSetting.Builder()
        .name("启用方块扫描").description("扫描并合并连续的指定方块。")
        .defaultValue(false).build());
    private final Setting<List<Block>> scannedBlocks = blockScanner.add(new BlockListSetting.Builder()
        .name("方块类型").description("需要扫描和显示的方块类型。")
        .defaultValue(Blocks.DIAMOND_ORE).visible(blockScanEnabled::get).build());
    private final Setting<Integer> blockScanRadius = blockScanner.add(new IntSetting.Builder()
        .name("方块水平范围").description("玩家周围扫描方块的水平半径。")
        .defaultValue(56).min(4).sliderMax(100).visible(blockScanEnabled::get).build());
    private final Setting<Integer> blockVerticalRange = blockScanner.add(new IntSetting.Builder()
        .name("方块垂直范围").description("玩家上下扫描方块的高度。")
        .defaultValue(52).min(2).sliderMax(100).visible(blockScanEnabled::get).build());
    private final Setting<Integer> minimumConnectedBlocks = blockScanner.add(new IntSetting.Builder()
        .name("最小连续方块数").description("连通方块数达到该数量后才合并显示。")
        .defaultValue(1).min(1).sliderMax(128).visible(blockScanEnabled::get).build());
    private final Setting<Integer> blockScanInterval = blockScanner.add(new IntSetting.Builder()
        .name("方块重扫间隔").description("更新周围方块结果的 tick 间隔。")
        .defaultValue(10).min(1).sliderMax(100).visible(blockScanEnabled::get).build());
    private final Setting<Boolean> signTextScan = blockScanner.add(new BoolSetting.Builder()
        .name("读取告示牌文字").description("扫描告示牌、墙上告示牌和悬挂告示牌时显示正反两面的非空文字。")
        .defaultValue(true).build());

    private final Setting<Boolean> missionScan = missions.add(new BoolSetting.Builder()
        .name("启用扫描任务").description("对指定方块、掉落物和实体进行优先任务扫描。")
        .defaultValue(false).build());
    private final Setting<List<Block>> missionBlocks = missions.add(new BlockListSetting.Builder()
        .name("任务方块").description("扫描到后持续闪白的方块目标。")
        .visible(missionScan::get).build());
    private final Setting<List<Item>> missionItems = missions.add(new ItemListSetting.Builder()
        .name("任务物品").description("扫描到后持续闪白的掉落物目标。")
        .visible(missionScan::get).build());
    private final Setting<Set<EntityType<?>>> missionEntities = missions.add(new EntityTypeListSetting.Builder()
        .name("任务实体").description("扫描到后持续闪白的实体类型。")
        .visible(missionScan::get).build());

    private final Setting<Boolean> soundsEnabled = audio.add(new BoolSetting.Builder()
        .name("启用音效").description("终结者 HUD 全部音效的总开关。")
        .defaultValue(true).build());
    private final Setting<Boolean> entityConfirmSounds = audio.add(new BoolSetting.Builder()
        .name("生物实体确认音效").description("扫描确认时随机播放三个确认音效之一。")
        .defaultValue(true).visible(soundsEnabled::get).build());
    private final Setting<Boolean> blockConfirmSounds = audio.add(new BoolSetting.Builder()
        .name("方块确认音效").description("方块组扫描确认时播放音效。")
        .defaultValue(true).visible(soundsEnabled::get).build());
    private final Setting<Boolean> missionSounds = audio.add(new BoolSetting.Builder()
        .name("任务发现音效").description("发现扫描任务目标时播放 found_it。")
        .defaultValue(true).visible(soundsEnabled::get).build());
    private final Setting<Boolean> healthSounds = audio.add(new BoolSetting.Builder()
        .name("受伤与濒死音效").description("玩家受伤或进入低生命状态时播放对应音效。")
        .defaultValue(true).visible(soundsEnabled::get).build());
    private final Setting<Boolean> deathSound = audio.add(new BoolSetting.Builder()
        .name("死亡音效").description("死亡时播放 terminator 音效。")
        .defaultValue(true).visible(soundsEnabled::get).build());
    private final Setting<Double> lowHealthThreshold = audio.add(new DoubleSetting.Builder()
        .name("低生命阈值").description("生命值首次降到该数值或以下时播放 dying。")
        .defaultValue(6.0).min(1.0).sliderMax(20.0).visible(healthSounds::get).build());
    private final Setting<Double> soundVolume = audio.add(new DoubleSetting.Builder()
        .name("音效音量").description("终结者 HUD 自定义音效音量。")
        .defaultValue(1.0).min(0.0).sliderMax(2.0).visible(soundsEnabled::get).build());
    private final Setting<Boolean> deathAnimation = audio.add(new BoolSetting.Builder()
        .name("死亡关机动画").description("死亡时将画面收束成红线，再缩成白点消失。")
        .defaultValue(true).build());
    private final Setting<Double> deathAnimationDuration = audio.add(new DoubleSetting.Builder()
        .name("死亡动画时长").description("老电视关机动画的总时长。")
        .defaultValue(1.8).min(0.8).sliderMax(4.0).visible(deathAnimation::get).build());

    private final Setting<Boolean> dataList = interfaceGroup.add(new BoolSetting.Builder()
        .name("左侧数据列表").description("显示位置、姿态、生命、环境和网络等实时信息。")
        .defaultValue(true).build());
    private final Setting<Boolean> extendedDataList = interfaceGroup.add(new BoolSetting.Builder()
        .name("左侧扩展信息").description("在左侧列表增加吸收生命、饥饿、经验、手持物、状态效果和区块信息。")
        .defaultValue(true).visible(dataList::get).build());
    private final Setting<Boolean> environmentGrid = interfaceGroup.add(new BoolSetting.Builder()
        .name("右侧环境网格").description("显示 T-800 风格的透视环境扫描网格。")
        .defaultValue(true).build());
    private final Setting<Boolean> compass = interfaceGroup.add(new BoolSetting.Builder()
        .name("右上角罗盘").description("显示东南西北方向、航向角和移动刻度。")
        .defaultValue(true).build());
    private final Setting<Boolean> contextData = interfaceGroup.add(new BoolSetting.Builder()
        .name("情景数据列表").description("在屏幕最右侧根据目标、生命和环境状态切换数据。")
        .defaultValue(true).build());
    private final Setting<Boolean> extendedContextData = interfaceGroup.add(new BoolSetting.Builder()
        .name("右侧扩展信息").description("在情景列表增加目标标识、坐标、姿态、体积、物品、生物及方块组详细数据。")
        .defaultValue(true).visible(contextData::get).build());

    private final Map<UUID, ScanState> scanStates = new HashMap<>();
    private final Map<BlockClusterKey, ScanState> blockScanStates = new HashMap<>();
    private List<BlockCluster> cachedBlockClusters = List.of();
    private int blockScanTicks;
    private long lastHudFrameNanos;
    private long lastReticleFrameNanos;
    private float reticleX = 0.5f;
    private float reticleY = 0.5f;
    private float reticleDriftX;
    private float reticleDriftY;
    private float lastYaw;
    private float lastPitch;
    private boolean cameraSampled;
    private boolean hasTrackingTarget;
    private float trackingTargetX = 0.5f;
    private float trackingTargetY = 0.5f;
    private UUID trackedPlayerId;
    private float previousHealth = Float.NaN;
    private boolean lowHealthSoundPlayed;
    private boolean wasDead;
    private long lastHurtSoundNanos;
    private long lastTaskSoundNanos;
    private long deathStartedNanos;

    public TerminatorHudShader() {
        super("终结者 HUD 着色器", "开启红色机器视觉、玩家扫描锁定和 T-800 风格战术 HUD。变成没敌终结者，无尽的追杀敌人。。。", "terminator");
    }

    @Override
    protected void configureUniforms(Render3DEvent event, float elapsedSeconds) {
        updateReticlePosition();
        uniformColor("U_FilterColor", filterColor.get());
        uniformColor("U_HudColor", interfaceColor.get());
        uniform1f("U_FilterStrength", filterStrength.get().floatValue());
        uniform1f("U_Contrast", contrast.get().floatValue());
        uniform1f("U_ScanlineStrength", scanlineStrength.get().floatValue());
        uniform1f("U_ScanSpeed", scanSpeed.get().floatValue());
        uniform1f("U_NoiseStrength", noiseStrength.get().floatValue());
        uniform1f("U_VignetteStrength", vignetteStrength.get().floatValue());
        uniform1f("U_HudOpacity", hudOpacity.get().floatValue());
        uniform1f("U_ReticleEnabled", reticle.get() ? 1.0f : 0.0f);
        uniform1f("U_ReticleSize", reticleSize.get().floatValue());
        uniform2f("U_ReticleCenter", reticleX, reticleY);
        uniform1f("U_DeathProgress", deathProgress());
    }

    private void updateReticlePosition() {
        if (mc.player == null) return;

        long now = System.nanoTime();
        double dt = lastReticleFrameNanos == 0L ? 0.016
            : clamp((now - lastReticleFrameNanos) / 1_000_000_000.0, 0.001, 0.1);
        lastReticleFrameNanos = now;

        float yaw = mc.gameRenderer.getCamera().getYaw();
        float pitch = mc.gameRenderer.getCamera().getPitch();
        if (!cameraSampled) {
            lastYaw = yaw;
            lastPitch = pitch;
            cameraSampled = true;
        }

        float yawDelta = wrapDegrees(yaw - lastYaw);
        float pitchDelta = pitch - lastPitch;
        lastYaw = yaw;
        lastPitch = pitch;

        double decay = Math.exp(-dt * 5.5);
        reticleDriftX = (float) (reticleDriftX * decay);
        reticleDriftY = (float) (reticleDriftY * decay);
        if (reticleMotion.get()) {
            reticleDriftX = (float) clamp(reticleDriftX - yawDelta * 0.0045, -0.13, 0.13);
            reticleDriftY = (float) clamp(reticleDriftY + pitchDelta * 0.0045, -0.10, 0.10);
        }

        float desiredX = 0.5f + (reticleMotion.get() ? reticleDriftX : 0.0f);
        float desiredY = 0.5f + (reticleMotion.get() ? reticleDriftY : 0.0f);
        boolean tracking = reticleTargetTracking.get() && hasTrackingTarget;
        if (tracking) {
            desiredX = trackingTargetX;
            desiredY = trackingTargetY;
        }

        double response = tracking ? 9.0 : 5.0;
        float blend = (float) (1.0 - Math.exp(-dt * response));
        reticleX += (desiredX - reticleX) * blend;
        reticleY += (desiredY - reticleY) * blend;
        reticleX = (float) clamp(reticleX, 0.08, 0.92);
        reticleY = (float) clamp(reticleY, 0.08, 0.92);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) {
            trackedPlayerId = null;
            previousHealth = Float.NaN;
            cachedBlockClusters = List.of();
            blockScanStates.clear();
            return;
        }

        UUID playerId = mc.player.getUuid();
        if (!playerId.equals(trackedPlayerId)) {
            trackedPlayerId = playerId;
            previousHealth = mc.player.getHealth();
            lowHealthSoundPlayed = previousHealth <= lowHealthThreshold.get();
            wasDead = mc.player.isDead() || previousHealth <= 0.0f;
            deathStartedNanos = wasDead ? System.nanoTime() : 0L;
        }

        long now = System.nanoTime();
        float health = mc.player.getHealth();
        boolean dead = mc.player.isDead() || health <= 0.0f;
        if (!dead) {
            if (!Float.isNaN(previousHealth) && health < previousHealth
                && now - lastHurtSoundNanos > 450_000_000L) {
                if (soundsEnabled.get() && healthSounds.get()) playSound(HURT_SOUND_ID);
                lastHurtSoundNanos = now;
            }
            if (health <= lowHealthThreshold.get()) {
                if (!lowHealthSoundPlayed && soundsEnabled.get() && healthSounds.get()) playSound(DYING_SOUND_ID);
                lowHealthSoundPlayed = true;
            } else {
                lowHealthSoundPlayed = false;
            }
        }

        if (dead && !wasDead) {
            deathStartedNanos = now;
            if (soundsEnabled.get() && deathSound.get()) playSound(TERMINATOR_SOUND_ID);
        } else if (!dead && wasDead) {
            deathStartedNanos = 0L;
            lowHealthSoundPlayed = health <= lowHealthThreshold.get();
        }
        wasDead = dead;
        previousHealth = health;

        if (shouldScanBlocks()) {
            if (++blockScanTicks >= blockScanInterval.get()) {
                blockScanTicks = 0;
                scanSurroundingBlocks();
            }
        } else {
            blockScanTicks = 0;
            cachedBlockClusters = List.of();
            blockScanStates.clear();
        }
    }

    private boolean shouldScanBlocks() {
        return blockScanEnabled.get() || (missionScan.get() && !missionBlocks.get().isEmpty());
    }

    private void scanSurroundingBlocks() {
        Set<Block> requested = new HashSet<>();
        if (blockScanEnabled.get()) requested.addAll(scannedBlocks.get());
        if (missionScan.get()) requested.addAll(missionBlocks.get());
        if (requested.isEmpty()) {
            cachedBlockClusters = List.of();
            return;
        }

        int horizontal = blockScanRadius.get();
        int vertical = blockVerticalRange.get();
        Map<BlockPos, Block> matches = new HashMap<>();
        for (BlockPos cursor : BlockPos.iterateOutwards(mc.player.getBlockPos(), horizontal, vertical, horizontal)) {
            BlockPos pos = cursor.toImmutable();
            Block block = mc.world.getBlockState(pos).getBlock();
            if (requested.contains(block)) matches.put(pos, block);
        }

        List<BlockCluster> clusters = new ArrayList<>();
        while (!matches.isEmpty()) {
            Map.Entry<BlockPos, Block> seed = matches.entrySet().iterator().next();
            Block type = seed.getValue();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            queue.add(seed.getKey());
            matches.remove(seed.getKey());
            int memberCount = 0;
            List<SignSnapshot> signs = new ArrayList<>();

            int minX = seed.getKey().getX();
            int minY = seed.getKey().getY();
            int minZ = seed.getKey().getZ();
            int maxX = minX;
            int maxY = minY;
            int maxZ = minZ;

            while (!queue.isEmpty()) {
                BlockPos pos = queue.removeFirst();
                memberCount++;
                minX = Math.min(minX, pos.getX());
                minY = Math.min(minY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxX = Math.max(maxX, pos.getX());
                maxY = Math.max(maxY, pos.getY());
                maxZ = Math.max(maxZ, pos.getZ());

                if (signTextScan.get() && mc.world.getBlockEntity(pos) instanceof SignBlockEntity sign) {
                    SignSnapshot snapshot = snapshotSign(pos, sign);
                    if (snapshot.hasText()) signs.add(snapshot);
                }

                for (Direction direction : Direction.values()) {
                    BlockPos neighbor = pos.offset(direction);
                    if (matches.get(neighbor) == type) {
                        matches.remove(neighbor);
                        queue.addLast(neighbor);
                    }
                }
            }

            boolean mission = missionScan.get() && missionBlocks.get().contains(type);
            if (memberCount < (mission ? 1 : minimumConnectedBlocks.get())) continue;
            BlockPos min = new BlockPos(minX, minY, minZ);
            BlockPos max = new BlockPos(maxX, maxY, maxZ);
            clusters.add(new BlockCluster(type, memberCount, min, max, mission, List.copyOf(signs)));
        }
        cachedBlockClusters = List.copyOf(clusters);
    }

    private SignSnapshot snapshotSign(BlockPos pos, SignBlockEntity sign) {
        return new SignSnapshot(pos.toImmutable(), signLines(sign.getFrontText()), signLines(sign.getBackText()));
    }

    private List<String> signLines(SignText text) {
        List<String> lines = new ArrayList<>(4);
        for (var message : text.getMessages(mc.shouldFilterText())) {
            String line = compactHudText(message.getString(), 42);
            if (!line.isEmpty()) lines.add(line);
        }
        return List.copyOf(lines);
    }

    private static String compactHudText(String text, int maxLength) {
        String compact = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (compact.length() <= maxLength) return compact;
        return compact.substring(0, Math.max(1, maxLength - 1)) + "…";
    }

    private float deathProgress() {
        if (!deathAnimation.get() || deathStartedNanos == 0L) return 0.0f;
        double elapsed = (System.nanoTime() - deathStartedNanos) / 1_000_000_000.0;
        return (float) clamp(elapsed / deathAnimationDuration.get(), 0.0, 1.0);
    }

    private void playSound(Identifier id) {
        SoundEvent sound = SoundEvent.of(id);
        mc.getSoundManager().play(PositionedSoundInstance.ui(sound, 1.0f, soundVolume.get().floatValue()));
    }

    private void playRandomEntityConfirmSound() {
        if (!soundsEnabled.get() || !entityConfirmSounds.get()) return;
        Identifier sound = ENTITY_CONFIRM_SOUND_IDS[ThreadLocalRandom.current().nextInt(ENTITY_CONFIRM_SOUND_IDS.length)];
        playSound(sound);
    }

    @EventHandler(priority = 100)
    private void onRender3DFlash(Render3DEvent event) {
        if (mc.world == null) return;
        long now = System.nanoTime();
        for (Entity entity : mc.world.getEntities()) {
            ScanState state = scanStates.get(entity.getUuid());
            if (state == null) continue;

            if (isMissionTarget(entity)) {
                int alpha = 35 + (int) (65.0 * (0.5 + 0.5 * Math.sin(now / 85_000_000.0)));
                event.renderer.box(entity.getBoundingBox(), new Color(255, 255, 255, alpha),
                    new Color(255, 255, 255, Math.min(255, alpha + 145)), ShapeMode.Both, 0);
            }

            if (state.structuralScan && state.confirmedAtNanos == 0L) {
                double age = (now - state.firstSeenNanos) / 1_000_000_000.0;
                double progress = clamp(age / structuralScanDuration.get(), 0.0, 0.999999);
                int knownParts = Math.max(1, state.structuralPartCount);
                double local = progress * knownParts - Math.floor(progress * knownParts);
                int sideAlpha = local < 0.32
                    ? (int) (145.0 * (1.0 - local / 0.32)) + 45
                    : 28;
                int lineAlpha = Math.min(255, sideAlpha + 65);

                TerminatorModelScan.begin(progress);
                TerminatorModelScan.Result result;
                try {
                    WireframeEntityRenderer.render(event, entity, 1.0,
                        new SettingColor(255, 255, 255, sideAlpha),
                        new SettingColor(255, 255, 255, lineAlpha),
                        ShapeMode.Both);
                } finally {
                    result = TerminatorModelScan.end();
                }

                if (result != null) {
                    state.structuralPartIndex = result.partIndex();
                    state.structuralPartCount = result.partCount();
                    state.structuralPartName = result.partName();
                    if (state.lastStructuralSoundPart != result.partIndex()) {
                        state.lastStructuralSoundPart = result.partIndex();
                        playRandomEntityConfirmSound();
                    }
                }
            }

            if (confirmationFlash.get() && state.confirmedAtNanos != 0L) {
                long elapsed = now - state.confirmedAtNanos;
                if (elapsed >= 0L && elapsed <= 240_000_000L) {
                    int alpha = (int) (125.0 * (1.0 - elapsed / 240_000_000.0));
                    event.renderer.box(entity.getBoundingBox(), new Color(255, 255, 255, alpha),
                        new Color(255, 255, 255, Math.min(255, alpha + 100)), ShapeMode.Both, 0);
                }
            }
        }

        for (BlockCluster cluster : cachedBlockClusters) {
            ScanState state = blockScanStates.get(cluster.key());
            if (state == null) continue;
            if (cluster.mission) {
                int alpha = 30 + (int) (75.0 * (0.5 + 0.5 * Math.sin(now / 85_000_000.0)));
                event.renderer.box(cluster.worldBox(), new Color(255, 255, 255, alpha),
                    new Color(255, 255, 255, Math.min(255, alpha + 140)), ShapeMode.Both, 0);
            }
            if (confirmationFlash.get() && state.confirmedAtNanos != 0L) {
                long elapsed = now - state.confirmedAtNanos;
                if (elapsed >= 0L && elapsed <= 280_000_000L) {
                    int alpha = (int) (135.0 * (1.0 - elapsed / 280_000_000.0));
                    event.renderer.box(cluster.worldBox(), new Color(255, 255, 255, alpha),
                        new Color(255, 255, 255, Math.min(255, alpha + 110)), ShapeMode.Both, 0);
                }
            }
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.world == null || mc.player == null || mc.currentScreen != null) return;

        long now = System.nanoTime();
        if (lastHudFrameNanos == 0L || now - lastHudFrameNanos > 500_000_000L) scanStates.clear();
        lastHudFrameNanos = now;

        List<TargetRender> targets = collectTargets(event, now);
        List<BlockTargetRender> blockTargets = collectBlockTargets(now);
        List<TargetGroup> groups = buildTargetGroups(targets);
        Set<UUID> groupedTargets = groupedTargetIds(groups);
        List<String> systemData = dataList.get() ? buildSystemData(targets.size() + blockTargets.size()) : List.of();
        List<String> contextual = contextData.get()
            ? fitContextData(buildContextData(targets, blockTargets, now))
            : List.of();

        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try {
            // Meteor fires Render2DEvent with an unscaled framebuffer projection. All values in this
            // class are GUI pixels (the same coordinate space as DrawContext), so restore GUI scale
            // for Renderer2D. This keeps geometry and labels locked to the exact same projection.
            float scale = mc.getWindow().getScaleFactor();
            modelView.scale(scale, scale, 1.0f);
            Renderer2D.COLOR.begin();
            if (dataList.get()) drawDataPanelGeometry(systemData);
            if (environmentGrid.get()) drawEnvironmentGridGeometry(event, targets, now);
            if (compass.get()) drawCompassGeometry(event);
            if (contextData.get()) drawContextPanelGeometry(event, contextual);
            for (BlockTargetRender blockTarget : blockTargets) drawBlockTargetGeometry(blockTarget, now);
            for (TargetGroup group : groups) drawTargetGroupGeometry(group, now);
            for (TargetRender target : targets) drawTargetGeometry(event, target, now);
            Renderer2D.COLOR.render();
        } finally {
            modelView.popMatrix();
        }

        if (dataList.get()) drawDataPanelText(event, systemData);
        if (environmentGrid.get()) drawEnvironmentGridText(event);
        if (compass.get()) drawCompassText(event);
        if (contextData.get()) drawContextPanelText(event, contextual);
        if (targetDetails.get()) {
            for (BlockTargetRender blockTarget : blockTargets) drawBlockTargetText(event, blockTarget);
            for (TargetGroup group : groups) drawTargetGroupText(event, group);
            for (TargetRender target : targets) {
                if (!groupedTargets.contains(target.entity.getUuid())) drawTargetText(event, target);
            }
        }

        updateTrackingTarget(event, targets);
    }

    private List<TargetRender> collectTargets(Render2DEvent event, long now) {
        if (!scanPlayers.get() && !scanLiving.get() && !scanEntities.get() && !scanItems.get() && !missionScan.get()) {
            scanStates.clear();
            hasTrackingTarget = false;
            return List.of();
        }

        List<TargetRender> result = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (Entity entity : mc.world.getEntities()) {
            if (!shouldScan(entity)) continue;
            if (mc.player.distanceTo(entity) > scanRange.get()) continue;
            if (!throughWalls.get() && !mc.player.canSee(entity)) continue;

            ScreenBox box = projectEntity(entity, event);
            if (box == null) continue;

            UUID id = entity.getUuid();
            ScanState state = scanStates.computeIfAbsent(id, ignored -> new ScanState(now));
            state.lastSeenNanos = now;
            seen.add(id);

            double age = (now - state.firstSeenNanos) / 1_000_000_000.0;
            result.add(new TargetRender(entity, box, age, 0.0));
        }

        scanStates.entrySet().removeIf(entry -> !seen.contains(entry.getKey())
            && now - entry.getValue().lastSeenNanos > 250_000_000L);
        return finalizeTargetScans(result, now);
    }

    private List<TargetRender> finalizeTargetScans(List<TargetRender> targets, long now) {
        int livingCount = 0;
        for (TargetRender target : targets) {
            if (isMergeableLiving(target.entity)) livingCount++;
        }

        List<TargetRender> finalized = new ArrayList<>(targets.size());
        for (TargetRender target : targets) {
            ScanState state = scanStates.get(target.entity.getUuid());
            boolean eligibleForStructure = structuralScan.get() && isMergeableLiving(target.entity);
            if (!eligibleForStructure) {
                state.structuralScan = false;
                state.structuralScanDecided = false;
            } else if (livingCount == 1) {
                state.structuralScan = true;
                state.structuralScanDecided = true;
            } else if (!state.structuralScanDecided) {
                state.structuralScan = ThreadLocalRandom.current().nextDouble(100.0) < structuralScanChance.get();
                state.structuralScanDecided = true;
            }

            double duration = state.structuralScan ? structuralScanDuration.get() : acquireDuration.get();
            double progress = clamp(target.age / duration, 0.0, 1.0);
            if (progress >= 1.0 && state.confirmedAtNanos == 0L) {
                state.confirmedAtNanos = now;
                playRandomEntityConfirmSound();
            }

            if (isMissionTarget(target.entity) && !state.missionAnnounced) {
                state.missionAnnounced = true;
                playMissionFoundSound(now);
            }
            finalized.add(new TargetRender(target.entity, target.box, target.age, progress));
        }
        return finalized;
    }

    private List<TargetGroup> buildTargetGroups(List<TargetRender> targets) {
        if (!mergeLivingTargets.get()) return List.of();

        List<TargetGroup> groups = new ArrayList<>();
        Set<UUID> assigned = new HashSet<>();
        double maxDistance = mergeLivingDistance.get();

        for (TargetRender seed : targets) {
            if (!isMergeableLiving(seed.entity) || assigned.contains(seed.entity.getUuid())) continue;

            List<TargetRender> members = new ArrayList<>();
            members.add(seed);
            assigned.add(seed.entity.getUuid());

            // Connected same-type targets form one group. Iterating newly-added members also
            // handles schools/herds whose edge members are close even if the first and last are not.
            for (int i = 0; i < members.size(); i++) {
                TargetRender anchor = members.get(i);
                for (TargetRender candidate : targets) {
                    if (!isMergeableLiving(candidate.entity)
                        || assigned.contains(candidate.entity.getUuid())
                        || candidate.entity.getType() != seed.entity.getType()) continue;
                    if (anchor.entity.distanceTo(candidate.entity) <= maxDistance) {
                        members.add(candidate);
                        assigned.add(candidate.entity.getUuid());
                    }
                }
            }

            if (members.size() < 2) {
                assigned.remove(seed.entity.getUuid());
                continue;
            }

            ScreenBox merged = members.get(0).box;
            double progress = members.get(0).progress;
            double age = members.get(0).age;
            for (int i = 1; i < members.size(); i++) {
                TargetRender member = members.get(i);
                merged = merged.union(member.box);
                progress = Math.min(progress, member.progress);
                age = Math.min(age, member.age);
            }
            groups.add(new TargetGroup(List.copyOf(members), merged, age, progress));
        }
        return groups;
    }

    private boolean isMergeableLiving(Entity entity) {
        return entity instanceof LivingEntity && !(entity instanceof PlayerEntity);
    }

    private Set<UUID> groupedTargetIds(List<TargetGroup> groups) {
        Set<UUID> ids = new HashSet<>();
        for (TargetGroup group : groups) {
            for (TargetRender member : group.members) ids.add(member.entity.getUuid());
        }
        return ids;
    }

    private boolean shouldScan(Entity entity) {
        if (entity == mc.player || !entity.isAlive()) return false;
        boolean mission = isMissionTarget(entity);
        if (entity instanceof PlayerEntity player) return (scanPlayers.get() || mission) && !player.isSpectator();
        if (entity instanceof ItemEntity) return scanItems.get() || mission;
        if (entity instanceof LivingEntity) return scanLiving.get() || mission;
        return scanEntities.get() || mission;
    }

    private boolean isMissionTarget(Entity entity) {
        if (!missionScan.get()) return false;
        if (missionEntities.get().contains(entity.getType())) return true;
        return entity instanceof ItemEntity item && missionItems.get().contains(item.getStack().getItem());
    }

    private void playMissionFoundSound(long now) {
        if (!soundsEnabled.get() || !missionSounds.get() || now - lastTaskSoundNanos < 250_000_000L) return;
        lastTaskSoundNanos = now;
        playSound(FOUND_SOUND_ID);
    }

    private boolean hasEnabledScanner() {
        return scanPlayers.get() || scanLiving.get() || scanEntities.get() || scanItems.get();
    }

    private boolean hasDisplayableTargets() {
        return hasEnabledScanner() || blockScanEnabled.get() || missionScan.get();
    }

    private ScreenBox projectEntity(Entity entity, Render2DEvent event) {
        Vector3d interpolated = new Vector3d();
        Utils.set(interpolated, entity, event.tickDelta);
        Box box = entity.getBoundingBox().offset(
            interpolated.x - entity.getX(),
            interpolated.y - entity.getY(),
            interpolated.z - entity.getZ()
        ).expand(0.08);

        return projectWorldBox(box);
    }

    private List<BlockTargetRender> collectBlockTargets(long now) {
        if (!shouldScanBlocks() || cachedBlockClusters.isEmpty()) {
            blockScanStates.clear();
            return List.of();
        }

        List<BlockTargetRender> result = new ArrayList<>();
        Set<BlockClusterKey> seen = new HashSet<>();
        for (BlockCluster cluster : cachedBlockClusters) {
            ScreenBox box = projectWorldBox(cluster.worldBox().expand(0.03));
            if (box == null) continue;

            BlockClusterKey key = cluster.key();
            ScanState state = blockScanStates.computeIfAbsent(key, ignored -> new ScanState(now));
            state.lastSeenNanos = now;
            seen.add(key);
            double age = (now - state.firstSeenNanos) / 1_000_000_000.0;
            double progress = clamp(age / acquireDuration.get(), 0.0, 1.0);
            if (progress >= 1.0 && state.confirmedAtNanos == 0L) {
                state.confirmedAtNanos = now;
                if (soundsEnabled.get() && blockConfirmSounds.get()) playSound(BLOCK_CONFIRM_SOUND_ID);
            }
            if (cluster.mission && !state.missionAnnounced) {
                state.missionAnnounced = true;
                playMissionFoundSound(now);
            }
            result.add(new BlockTargetRender(cluster, box, age, progress));
        }
        blockScanStates.entrySet().removeIf(entry -> !seen.contains(entry.getKey())
            && now - entry.getValue().lastSeenNanos > 500_000_000L);
        return result;
    }

    private ScreenBox projectWorldBox(Box box) {

        double[] xs = {box.minX, box.maxX};
        double[] ys = {box.minY, box.maxY};
        double[] zs = {box.minZ, box.maxZ};
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        int projected = 0;
        double guiScale = mc.getWindow().getScaleFactor();

        for (double x : xs) {
            for (double y : ys) {
                for (double z : zs) {
                    Vector3d point = new Vector3d(x, y, z);
                    if (!NametagUtils.to2D(point, 1.0, false)) continue;
                    point.x /= guiScale;
                    point.y /= guiScale;
                    minX = Math.min(minX, point.x);
                    minY = Math.min(minY, point.y);
                    maxX = Math.max(maxX, point.x);
                    maxY = Math.max(maxY, point.y);
                    projected++;
                }
            }
        }

        int screenWidth = guiWidth();
        int screenHeight = guiHeight();
        if (projected == 0 || maxX < 0 || maxY < 0 || minX > screenWidth || minY > screenHeight) return null;
        minX = clamp(minX, 1.0, screenWidth - 1.0);
        minY = clamp(minY, 1.0, screenHeight - 1.0);
        maxX = clamp(maxX, 1.0, screenWidth - 1.0);
        maxY = clamp(maxY, 1.0, screenHeight - 1.0);
        if (maxX - minX < 2.0 || maxY - minY < 3.0) return null;
        return new ScreenBox(minX, minY, maxX, maxY);
    }

    private void drawTargetGeometry(Render2DEvent event, TargetRender target, long now) {
        ScreenBox box = target.box;
        double convergence = clamp(target.progress / 0.72, 0.0, 1.0);
        double eased = 1.0 - Math.pow(1.0 - convergence, 3.0);
        double expansion = (1.0 - eased) * Math.min(42.0, Math.max(box.width(), box.height()) * 0.55);
        double x1 = box.minX - expansion;
        double y1 = box.minY - expansion;
        double x2 = box.maxX + expansion;
        double y2 = box.maxY + expansion;
        double width = x2 - x1;
        double height = y2 - y1;
        double corner = Math.min(width, height) * 0.26;

        int pulse = target.progress >= 1.0
            ? 180 + (int) (55.0 * (0.5 + 0.5 * Math.sin(now / 90_000_000.0)))
            : 220;
        Color bright = color(pulse);
        Color faint = color(Math.max(45, pulse / 3));

        Renderer2D.COLOR.boxLines(x1, y1, width, height, faint);
        corner(x1, y1, x2, y2, corner, bright);

        double scanPhase = clamp((target.progress - 0.18) / 0.74, 0.0, 1.0);
        double sweep = target.progress < 1.0
            ? y1 + height * scanPhase
            : y1 + height * ((target.age * 0.45) % 1.0);
        Renderer2D.COLOR.line(x1, sweep, x2, sweep, color(target.progress < 1.0 ? 210 : 75));
        if (target.progress < 1.0 && scanPhase > 0.0) {
            Renderer2D.COLOR.line(x1 + width * 0.12, sweep - 2.0,
                x2 - width * 0.12, sweep - 2.0, color(70));
        }

        if (target.progress < 1.0) {
            Renderer2D.COLOR.line(guiWidth() * 0.5, guiHeight() * 0.5,
                box.centerX(), box.centerY(), color(85));
        } else {
            double tick = Math.min(7.0, Math.max(3.0, width * 0.08));
            Renderer2D.COLOR.line(box.centerX() - tick, box.centerY(), box.centerX() + tick, box.centerY(), faint);
            Renderer2D.COLOR.line(box.centerX(), box.centerY() - tick, box.centerX(), box.centerY() + tick, faint);
        }

        if (isMissionTarget(target.entity)) {
            drawMissionTargetPulse(box, now);
        }

        double flashAge = target.age - acquireDuration.get();
        if (flashAge >= 0.0 && flashAge < 0.24) {
            double flashProgress = flashAge / 0.24;
            double flashExpand = 2.0 + flashProgress * 12.0;
            int flashAlpha = (int) (255.0 * (1.0 - flashProgress));
            Renderer2D.COLOR.boxLines(box.minX - flashExpand, box.minY - flashExpand,
                box.width() + flashExpand * 2.0, box.height() + flashExpand * 2.0,
                new Color(255, 255, 255, flashAlpha));
        }
    }

    private void drawMissionTargetPulse(ScreenBox box, long now) {
        double wave = 0.5 + 0.5 * Math.sin(now / 85_000_000.0);
        double expand = 2.0 + wave * 5.0;
        int alpha = 105 + (int) (145.0 * wave);
        Renderer2D.COLOR.quad(box.minX, box.minY, box.width(), box.height(),
            new Color(255, 255, 255, 10 + (int) (22.0 * wave)));
        Renderer2D.COLOR.boxLines(box.minX - expand, box.minY - expand,
            box.width() + expand * 2.0, box.height() + expand * 2.0,
            new Color(255, 255, 255, alpha));
    }

    private void drawTargetGroupGeometry(TargetGroup group, long now) {
        ScreenBox box = group.box.expand(5.0);
        double convergence = clamp(group.progress / 0.72, 0.0, 1.0);
        double eased = 1.0 - Math.pow(1.0 - convergence, 3.0);
        double expansion = (1.0 - eased) * Math.min(56.0, Math.max(box.width(), box.height()) * 0.30);
        double x1 = box.minX - expansion;
        double y1 = box.minY - expansion;
        double x2 = box.maxX + expansion;
        double y2 = box.maxY + expansion;
        double width = x2 - x1;
        double height = y2 - y1;
        double cornerLength = Math.min(18.0, Math.max(7.0, Math.min(width, height) * 0.18));
        int pulse = 175 + (int) (55.0 * (0.5 + 0.5 * Math.sin(now / 120_000_000.0)));

        Renderer2D.COLOR.boxLines(x1, y1, width, height, color(55));
        corner(x1, y1, x2, y2, cornerLength, color(pulse));

        double sweep = group.progress < 1.0
            ? y1 + height * clamp((group.progress - 0.12) / 0.88, 0.0, 1.0)
            : y1 + height * ((group.age * 0.28) % 1.0);
        Renderer2D.COLOR.line(x1, sweep, x2, sweep, color(group.progress < 1.0 ? 175 : 60));

        double marker = 5.0;
        Renderer2D.COLOR.line(box.centerX() - marker, y1 - 3.0, box.centerX() + marker, y1 - 3.0, color(210));
        Renderer2D.COLOR.line(box.centerX(), y1 - 7.0, box.centerX(), y1 + 1.0, color(210));
    }

    private void drawBlockTargetGeometry(BlockTargetRender target, long now) {
        ScreenBox box = target.box;
        double convergence = clamp(target.progress / 0.78, 0.0, 1.0);
        double expansion = (1.0 - (1.0 - Math.pow(1.0 - convergence, 3.0)))
            * Math.min(54.0, Math.max(box.width(), box.height()) * 0.35);
        double x1 = box.minX - expansion;
        double y1 = box.minY - expansion;
        double x2 = box.maxX + expansion;
        double y2 = box.maxY + expansion;
        double width = x2 - x1;
        double height = y2 - y1;
        double cornerLength = Math.min(20.0, Math.max(6.0, Math.min(width, height) * 0.22));

        Renderer2D.COLOR.boxLines(x1, y1, width, height, color(65));
        corner(x1, y1, x2, y2, cornerLength, color(target.progress >= 1.0 ? 215 : 245));
        double sweep = target.progress < 1.0
            ? y1 + height * clamp((target.progress - 0.12) / 0.88, 0.0, 1.0)
            : y1 + height * ((target.age * 0.32) % 1.0);
        Renderer2D.COLOR.line(x1, sweep, x2, sweep, color(target.progress < 1.0 ? 205 : 70));

        double flashAge = target.age - acquireDuration.get();
        if (flashAge >= 0.0 && flashAge < 0.28) {
            double fade = 1.0 - flashAge / 0.28;
            Renderer2D.COLOR.quad(box.minX, box.minY, box.width(), box.height(),
                new Color(255, 255, 255, (int) (65.0 * fade)));
            Renderer2D.COLOR.boxLines(box.minX - 5.0 * (1.0 - fade), box.minY - 5.0 * (1.0 - fade),
                box.width() + 10.0 * (1.0 - fade), box.height() + 10.0 * (1.0 - fade),
                new Color(255, 255, 255, (int) (255.0 * fade)));
        }
        if (target.cluster.mission) drawMissionTargetPulse(box, now);
    }

    private void corner(double x1, double y1, double x2, double y2, double length, Color color) {
        Renderer2D.COLOR.line(x1, y1, x1 + length, y1, color);
        Renderer2D.COLOR.line(x1, y1, x1, y1 + length, color);
        Renderer2D.COLOR.line(x2, y1, x2 - length, y1, color);
        Renderer2D.COLOR.line(x2, y1, x2, y1 + length, color);
        Renderer2D.COLOR.line(x1, y2, x1 + length, y2, color);
        Renderer2D.COLOR.line(x1, y2, x1, y2 - length, color);
        Renderer2D.COLOR.line(x2, y2, x2 - length, y2, color);
        Renderer2D.COLOR.line(x2, y2, x2, y2 - length, color);
    }

    private void drawTargetText(Render2DEvent event, TargetRender target) {
        ScanState state = scanStates.get(target.entity.getUuid());
        String status;
        if (state != null && state.structuralScan && target.progress < 1.0) {
            int partCount = Math.max(1, state.structuralPartCount);
            int part = clamp(state.structuralPartIndex, 0, partCount - 1);
            status = String.format(Locale.ROOT, "STRUCTURAL %02d/%02d // %s",
                part + 1, partCount, state.structuralPartName);
        } else {
            status = target.progress < 0.3 ? "SEARCHING PATTERN"
                : target.progress < 0.75 ? "ANALYZING TARGET"
                : target.progress < 1.0 ? "VERIFYING SIGNATURE"
                : isMissionTarget(target.entity) ? "PRIORITY TARGET ACQUIRED" : "TARGET ACQUIRED";
        }
        String identity = targetName(target.entity);
        String metrics = targetMetrics(target.entity);
        int labelWidth = Math.max(mc.textRenderer.getWidth(status),
            Math.max(mc.textRenderer.getWidth(identity), mc.textRenderer.getWidth(metrics)));
        int x = (int) clamp(target.box.centerX() - labelWidth * 0.5, 3.0, guiWidth() - labelWidth - 3.0);
        int statusY = (int) Math.max(3.0, target.box.minY - 12.0);
        event.drawContext.drawText(mc.textRenderer, status, x, statusY, argb(240), true);
        if (target.progress >= 0.35) {
            int detailY = (int) Math.min(guiHeight() - 23.0, target.box.maxY + 4.0);
            event.drawContext.drawText(mc.textRenderer, identity, x, detailY, argb(215), true);
            event.drawContext.drawText(mc.textRenderer, metrics, x, detailY + 11, argb(190), true);
        }
    }

    private void drawTargetGroupText(Render2DEvent event, TargetGroup group) {
        Entity representative = group.members.get(0).entity;
        String status = group.progress < 0.3 ? "SEARCHING GROUP"
            : group.progress < 0.75 ? "ANALYZING GROUP"
            : group.progress < 1.0 ? "VERIFYING GROUP"
            : "TARGET GROUP ACQUIRED";
        String identity = ("GROUP // " + representative.getType().getName().getString()
            + " X" + group.members.size()).toUpperCase(Locale.ROOT);
        double averageRange = 0.0;
        double totalHealth = 0.0;
        for (TargetRender member : group.members) {
            averageRange += mc.player.distanceTo(member.entity);
            totalHealth += ((LivingEntity) member.entity).getHealth();
        }
        averageRange /= group.members.size();
        String metrics = String.format(Locale.ROOT, "COUNT %02d  AVG RNG %.1fM  VIT %.0f",
            group.members.size(), averageRange, totalHealth);
        int labelWidth = Math.max(mc.textRenderer.getWidth(status),
            Math.max(mc.textRenderer.getWidth(identity), mc.textRenderer.getWidth(metrics)));
        int x = (int) clamp(group.box.centerX() - labelWidth * 0.5, 3.0, guiWidth() - labelWidth - 3.0);
        int statusY = (int) Math.max(3.0, group.box.minY - 18.0);
        event.drawContext.drawText(mc.textRenderer, status, x, statusY, argb(245), true);
        if (group.progress >= 0.35) {
            int detailY = (int) Math.min(guiHeight() - 23.0, group.box.maxY + 8.0);
            event.drawContext.drawText(mc.textRenderer, identity, x, detailY, argb(220), true);
            event.drawContext.drawText(mc.textRenderer, metrics, x, detailY + 11, argb(195), true);
        }
    }

    private void drawBlockTargetText(Render2DEvent event, BlockTargetRender target) {
        String status = target.progress < 0.35 ? "MATERIAL SEARCH"
            : target.progress < 0.78 ? "STRUCTURE MAPPING"
            : target.progress < 1.0 ? "MATERIAL VERIFY"
            : target.cluster.mission ? "PRIORITY MATERIAL FOUND" : "BLOCK GROUP ACQUIRED";
        String identity = ("MATERIAL // " + target.cluster.block.getName().getString()).toUpperCase(Locale.ROOT);
        int dx = target.cluster.max.getX() - target.cluster.min.getX() + 1;
        int dy = target.cluster.max.getY() - target.cluster.min.getY() + 1;
        int dz = target.cluster.max.getZ() - target.cluster.min.getZ() + 1;
        var center = target.cluster.worldBox().getCenter();
        double range = Math.sqrt(mc.player.squaredDistanceTo(center.x, center.y, center.z));
        String metrics = String.format(Locale.ROOT, "COUNT %d  SIZE %dX%dX%d  RNG %.1fM",
            target.cluster.count, dx, dy, dz, range);
        List<String> details = new ArrayList<>();
        details.add(identity);
        details.add(metrics);
        if (target.progress >= 0.30) details.addAll(signHudLines(target.cluster, 6, 36));

        int labelWidth = mc.textRenderer.getWidth(status);
        for (String line : details) labelWidth = Math.max(labelWidth, mc.textRenderer.getWidth(line));
        int x = (int) clamp(target.box.centerX() - labelWidth * 0.5, 3.0, guiWidth() - labelWidth - 3.0);
        int statusY = (int) Math.max(3.0, target.box.minY - 13.0);
        event.drawContext.drawText(mc.textRenderer, status, x, statusY, argb(245), true);
        if (target.progress >= 0.30) {
            int detailHeight = details.size() * 11;
            int detailY = (int) clamp(target.box.maxY + 5.0, 3.0, guiHeight() - detailHeight - 2.0);
            for (int i = 0; i < details.size(); i++) {
                event.drawContext.drawText(mc.textRenderer, details.get(i), x, detailY + i * 11,
                    argb(i == 0 ? 220 : 195), true);
            }
        }
    }

    private List<String> signHudLines(BlockCluster cluster, int maxLines, int maxTextLength) {
        if (!signTextScan.get() || cluster.signs.isEmpty() || maxLines <= 0) return List.of();

        List<String> result = new ArrayList<>();
        outer:
        for (int signIndex = 0; signIndex < cluster.signs.size(); signIndex++) {
            SignSnapshot sign = cluster.signs.get(signIndex);
            for (String line : sign.front) {
                result.add(signLineLabel(cluster.signs.size(), signIndex, "F", line, maxTextLength));
                if (result.size() >= maxLines) break outer;
            }
            for (String line : sign.back) {
                result.add(signLineLabel(cluster.signs.size(), signIndex, "B", line, maxTextLength));
                if (result.size() >= maxLines) break outer;
            }
        }
        return result;
    }

    private String signLineLabel(int signCount, int signIndex, String face, String text, int maxTextLength) {
        String source = compactHudText(text, maxTextLength);
        return signCount == 1
            ? "SIGN " + face + " // " + source
            : String.format(Locale.ROOT, "SIGN %02d%s // %s", signIndex + 1, face, source);
    }

    private String targetName(Entity entity) {
        if (entity instanceof ItemEntity item) {
            return ("ITEM // " + item.getStack().getName().getString()).toUpperCase(Locale.ROOT);
        }
        return (entity instanceof PlayerEntity ? "HUMAN // " : "ENTITY // ")
            + entity.getName().getString().toUpperCase(Locale.ROOT);
    }

    private String targetMetrics(Entity entity) {
        double range = mc.player.distanceTo(entity);
        if (entity instanceof ItemEntity item) {
            return String.format(Locale.ROOT, "RNG %.1fM  QTY %d", range, item.getStack().getCount());
        }
        if (entity instanceof LivingEntity living) {
            return String.format(Locale.ROOT, "RNG %.1fM  VIT %.0f/%.0f", range, living.getHealth(), living.getMaxHealth());
        }
        return String.format(Locale.ROOT, "RNG %.1fM  ID %d", range, entity.getId());
    }

    private void updateTrackingTarget(Render2DEvent event, List<TargetRender> targets) {
        if (targets.isEmpty()) {
            hasTrackingTarget = false;
            return;
        }

        TargetRender focus = null;
        double bestScore = Double.POSITIVE_INFINITY;
        double screenWidth = guiWidth();
        double screenHeight = guiHeight();
        double centerX = screenWidth * 0.5;
        double centerY = screenHeight * 0.5;
        for (TargetRender target : targets) {
            double dx = (target.box.centerX() - centerX) / screenWidth;
            double dy = (target.box.centerY() - centerY) / screenHeight;
            double score = dx * dx + dy * dy + (target.progress >= 1.0 ? 0.08 : 0.0);
            if (score < bestScore) {
                bestScore = score;
                focus = target;
            }
        }

        hasTrackingTarget = focus != null;
        if (focus != null) {
            trackingTargetX = (float) clamp(focus.box.centerX() / screenWidth, 0.08, 0.92);
            trackingTargetY = (float) clamp(1.0 - focus.box.centerY() / screenHeight, 0.08, 0.92);
        }
    }

    private List<String> buildSystemData(int targetCount) {
        PlayerEntity player = mc.player;
        List<String> lines = new ArrayList<>();
        double speed = player.getVelocity().horizontalLength() * 20.0;
        String biome = mc.world.getBiome(player.getBlockPos()).getKey()
            .map(key -> key.getValue().getPath()).orElse("unknown").toUpperCase(Locale.ROOT);
        String dimension = mc.world.getRegistryKey().getValue().getPath().toUpperCase(Locale.ROOT);
        long dayTime = mc.world.getTimeOfDay();
        int ping = 0;
        if (mc.getNetworkHandler() != null) {
            PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
            if (entry != null) ping = entry.getLatency();
        }

        lines.add("T-800 // CSM-101");
        lines.add("STATUS  SYSTEM ONLINE");
        lines.add(String.format(Locale.ROOT, "POS     %+07.1f %+06.1f %+07.1f", player.getX(), player.getY(), player.getZ()));
        lines.add(String.format(Locale.ROOT, "VECTOR  YAW %06.1f  PITCH %+05.1f", normalizeYaw(player.getYaw()), player.getPitch()));
        lines.add(String.format(Locale.ROOT, "MOTION  %05.2f M/S", speed));
        lines.add(String.format(Locale.ROOT, "VITALS  %05.1f / %05.1f", player.getHealth(), player.getMaxHealth()));
        lines.add(String.format(Locale.ROOT, "ARMOR   %02d     AIR %03d", player.getArmor(), player.getAir()));
        lines.add("ZONE    " + dimension);
        lines.add("TERRAIN " + biome);
        lines.add(String.format(Locale.ROOT, "CYCLE   DAY %05d  T%05d", dayTime / 24000L, dayTime % 24000L));
        lines.add(String.format(Locale.ROOT, "LINK    %03dMS   FPS %03d", ping, mc.getCurrentFps()));
        lines.add(String.format(Locale.ROOT, "CONTACT %02d  THREAT SCAN ACTIVE", targetCount));
        if (extendedDataList.get()) {
            lines.add(String.format(Locale.ROOT, "RESERVE ABS %04.1f  FOOD %02d  SAT %04.1f",
                player.getAbsorptionAmount(), player.getHungerManager().getFoodLevel(),
                player.getHungerManager().getSaturationLevel()));
            lines.add(String.format(Locale.ROOT, "EXPER   LV %03d  PROGRESS %03d PERCENT",
                player.experienceLevel, (int) (player.experienceProgress * 100.0f)));
            lines.add("PRIMARY " + stackLabel(player.getMainHandStack()));
            lines.add("AUX     " + stackLabel(player.getOffHandStack()));
            lines.add(String.format(Locale.ROOT, "STATE   %-10s EFFECTS %02d",
                player.getPose().asString().toUpperCase(Locale.ROOT), player.getStatusEffects().size()));
            lines.add(String.format(Locale.ROOT, "SECTOR  CHUNK %+05d %+05d  SLOT %02d",
                player.getBlockX() >> 4, player.getBlockZ() >> 4, player.getInventory().getSelectedSlot() + 1));
        }
        return lines;
    }

    private void drawDataPanelGeometry(List<String> lines) {
        int width = 0;
        for (String line : lines) width = Math.max(width, mc.textRenderer.getWidth(line));
        double x = 12.0;
        double y = 14.0;
        double w = width + 14.0;
        double h = lines.size() * 10.0 + 12.0;
        Renderer2D.COLOR.quad(x, y, w, h, color(32));
        Renderer2D.COLOR.boxLines(x, y, w, h, color(105));
        Renderer2D.COLOR.line(x + 4.0, y + 15.0, x + w - 4.0, y + 15.0, color(130));
        for (int i = 0; i < 5; i++) {
            double markerY = y + 23.0 + i * 20.0;
            Renderer2D.COLOR.line(x - 4.0, markerY, x, markerY, color(150));
        }
    }

    private void drawDataPanelText(Render2DEvent event, List<String> lines) {
        int y = 18;
        for (int i = 0; i < lines.size(); i++) {
            int alpha = i == 0 ? 255 : 205;
            event.drawContext.drawText(mc.textRenderer, lines.get(i), 18, y, argb(alpha), true);
            y += 10;
        }
    }

    private void drawEnvironmentGridGeometry(Render2DEvent event, List<TargetRender> targets, long now) {
        double screenWidth = guiWidth();
        double screenHeight = guiHeight();
        double width = Math.min(174.0, screenWidth * 0.26);
        double height = Math.min(98.0, screenHeight * 0.23);
        double x = screenWidth - width - 14.0;
        double y = screenHeight - height - 18.0;
        double horizonY = y + 18.0;
        double centerX = x + width * 0.5;

        Renderer2D.COLOR.quad(x, y, width, height, color(25));
        Renderer2D.COLOR.boxLines(x, y, width, height, color(115));
        Renderer2D.COLOR.line(x, horizonY, x + width, horizonY, color(135));

        for (int i = 0; i <= 8; i++) {
            double t = i / 8.0;
            double curve = t * t;
            double lineY = horizonY + curve * (height - 18.0);
            double half = 18.0 + t * (width * 0.5 - 3.0);
            Renderer2D.COLOR.line(centerX - half, lineY, centerX + half, lineY, color(75 + i * 5));
        }
        for (int i = 0; i <= 10; i++) {
            double t = i / 10.0;
            Renderer2D.COLOR.line(centerX + (t - 0.5) * 36.0, horizonY,
                x + 3.0 + t * (width - 6.0), y + height, color(80));
        }

        double sweep = ((now / 1_000_000_000.0) * scanSpeed.get() * 0.25) % 1.0;
        double sweepY = horizonY + sweep * sweep * (height - 18.0);
        Renderer2D.COLOR.line(x + 3.0, sweepY, x + width - 3.0, sweepY, color(175));

        for (TargetRender target : targets) {
            double nx = target.box.centerX() / screenWidth;
            double ny = target.box.centerY() / screenHeight;
            double px = x + 6.0 + nx * (width - 12.0);
            double py = horizonY + 3.0 + ny * (height - 23.0);
            Renderer2D.COLOR.line(px - 3.0, py, px + 3.0, py, color(220));
            Renderer2D.COLOR.line(px, py - 3.0, px, py + 3.0, color(220));
        }
    }

    private void drawEnvironmentGridText(Render2DEvent event) {
        double screenWidth = guiWidth();
        double screenHeight = guiHeight();
        double width = Math.min(174.0, screenWidth * 0.26);
        double height = Math.min(98.0, screenHeight * 0.23);
        int x = (int) (screenWidth - width - 10.0);
        int y = (int) (screenHeight - height - 15.0);
        event.drawContext.drawText(mc.textRenderer, "ENVIRONMENTAL MAPPING", x, y, argb(225), true);
        String bearing = String.format(Locale.ROOT, "AZ %06.1f  EL %+05.1f", normalizeYaw(mc.player.getYaw()), -mc.player.getPitch());
        event.drawContext.drawText(mc.textRenderer, bearing, x, (int) (screenHeight - 15.0), argb(190), true);
    }

    private void drawCompassGeometry(Render2DEvent event) {
        double screenWidth = guiWidth();
        double width = Math.min(190.0, screenWidth * 0.42);
        double x = screenWidth - width - 14.0;
        double y = 14.0;
        double center = x + width * 0.5;
        double heading = normalizeYaw(mc.player.getYaw());

        Renderer2D.COLOR.quad(x, y, width, 34.0, color(24));
        Renderer2D.COLOR.boxLines(x, y, width, 34.0, color(100));
        Renderer2D.COLOR.line(center, y + 19.0, center, y + 31.0, color(245));
        for (int angle = 0; angle < 360; angle += 15) {
            double diff = wrapDegrees((float) (angle - heading));
            if (Math.abs(diff) > 90.0) continue;
            double px = center + diff / 90.0 * (width * 0.47);
            boolean cardinal = angle % 90 == 0;
            Renderer2D.COLOR.line(px, y + (cardinal ? 22.0 : 26.0), px, y + 31.0,
                color(cardinal ? 210 : 105));
        }
    }

    private void drawCompassText(Render2DEvent event) {
        double screenWidth = guiWidth();
        double width = Math.min(190.0, screenWidth * 0.42);
        double x = screenWidth - width - 14.0;
        double y = 14.0;
        double center = x + width * 0.5;
        double heading = normalizeYaw(mc.player.getYaw());
        String headingText = String.format(Locale.ROOT, "HEADING %06.1f // %s", heading, cardinalName(heading));
        int headingX = (int) (center - mc.textRenderer.getWidth(headingText) * 0.5);
        event.drawContext.drawText(mc.textRenderer, headingText, headingX, (int) y + 4, argb(230), true);

        String[] labels = {"S", "W", "N", "E"};
        int[] angles = {0, 90, 180, 270};
        for (int i = 0; i < labels.length; i++) {
            double diff = wrapDegrees((float) (angles[i] - heading));
            if (Math.abs(diff) > 90.0) continue;
            int px = (int) (center + diff / 90.0 * (width * 0.47) - mc.textRenderer.getWidth(labels[i]) * 0.5);
            event.drawContext.drawText(mc.textRenderer, labels[i], px, (int) y + 13, argb(245), true);
        }
    }

    private List<String> buildContextData(List<TargetRender> targets, List<BlockTargetRender> blockTargets, long now) {
        List<String> lines = new ArrayList<>();
        if (!targets.isEmpty()) {
            TargetRender target = targets.get(0);
            for (TargetRender candidate : targets) {
                if (candidate.progress < target.progress) target = candidate;
            }
            Entity entity = target.entity;
            lines.add("CONTEXT // TARGET ANALYSIS");
            lines.add("CLASS   " + entity.getType().getName().getString().toUpperCase(Locale.ROOT));
            lines.add("IDENT   " + targetName(entity));
            lines.add(String.format(Locale.ROOT, "RANGE   %06.2f M", mc.player.distanceTo(entity)));
            lines.add(String.format(Locale.ROOT, "VECTOR  %+05.2f %+05.2f %+05.2f",
                entity.getVelocity().x, entity.getVelocity().y, entity.getVelocity().z));
            lines.add("SIGNAL  " + targetMetrics(entity));
            if (extendedContextData.get()) {
                lines.add(String.format(Locale.ROOT, "ENTITY  ID %05d  UUID %s",
                    entity.getId(), entity.getUuidAsString().substring(0, 8).toUpperCase(Locale.ROOT)));
                lines.add(String.format(Locale.ROOT, "POS     %+07.1f %+06.1f %+07.1f",
                    entity.getX(), entity.getY(), entity.getZ()));
                lines.add(String.format(Locale.ROOT, "BODY    %04.2f X %04.2f  POSE %s",
                    entity.getWidth(), entity.getHeight(), entity.getPose().asString().toUpperCase(Locale.ROOT)));
                lines.add("STATE   " + entityState(entity));
                if (entity instanceof LivingEntity living) {
                    lines.add(String.format(Locale.ROOT, "DEFENSE ARMOR %02d  ABS %04.1f  FX %02d",
                        living.getArmor(), living.getAbsorptionAmount(), living.getStatusEffects().size()));
                } else if (entity instanceof ItemEntity item) {
                    lines.add(String.format(Locale.ROOT, "ITEM    QTY %03d  AGE %05.1f S",
                        item.getStack().getCount(), item.getItemAge() / 20.0));
                }
            }
            lines.add(String.format(Locale.ROOT, "LOCK    %03d PERCENT", (int) (target.progress * 100.0)));
            lines.add(target.progress >= 1.0 ? "DECISION TARGET CONFIRMED" : "DECISION CALCULATING...");
            return lines;
        }

        if (!blockTargets.isEmpty()) {
            BlockTargetRender target = blockTargets.get(0);
            for (BlockTargetRender candidate : blockTargets) {
                if (candidate.progress < target.progress) target = candidate;
            }
            BlockCluster cluster = target.cluster;
            int dx = cluster.max.getX() - cluster.min.getX() + 1;
            int dy = cluster.max.getY() - cluster.min.getY() + 1;
            int dz = cluster.max.getZ() - cluster.min.getZ() + 1;
            var center = cluster.worldBox().getCenter();
            double range = Math.sqrt(mc.player.squaredDistanceTo(center.x, center.y, center.z));
            lines.add("CONTEXT // MATERIAL ANALYSIS");
            lines.add("CLASS   " + cluster.block.getName().getString().toUpperCase(Locale.ROOT));
            lines.add(String.format(Locale.ROOT, "COUNT   %05d BLOCKS", cluster.count));
            lines.add(String.format(Locale.ROOT, "BOUNDS  %03d X %03d X %03d", dx, dy, dz));
            lines.add(String.format(Locale.ROOT, "CENTER  %+06.1f %+06.1f %+06.1f", center.x, center.y, center.z));
            lines.add(String.format(Locale.ROOT, "RANGE   %06.2f M", range));
            lines.addAll(signHudLines(cluster, 6, 40));
            if (extendedContextData.get()) {
                lines.add("BLOCKID " + Registries.BLOCK.getId(cluster.block).toString().toUpperCase(Locale.ROOT));
                lines.add(String.format(Locale.ROOT, "MINIMUM %+05d %+05d %+05d",
                    cluster.min.getX(), cluster.min.getY(), cluster.min.getZ()));
                lines.add(String.format(Locale.ROOT, "MAXIMUM %+05d %+05d %+05d",
                    cluster.max.getX(), cluster.max.getY(), cluster.max.getZ()));
                double density = cluster.count * 100.0 / Math.max(1, dx * dy * dz);
                lines.add(String.format(Locale.ROOT, "DENSITY %05.1f PERCENT  SIGNS %02d",
                    density, cluster.signs.size()));
                for (int i = 0; i < Math.min(2, cluster.signs.size()); i++) {
                    BlockPos pos = cluster.signs.get(i).pos;
                    lines.add(String.format(Locale.ROOT, "SIGNPOS %02d %+05d %+05d %+05d",
                        i + 1, pos.getX(), pos.getY(), pos.getZ()));
                }
            }
            lines.add(String.format(Locale.ROOT, "LOCK    %03d PERCENT", (int) (target.progress * 100.0)));
            lines.add(target.progress >= 1.0 ? "DECISION MATERIAL CONFIRMED" : "DECISION MAPPING STRUCTURE...");
            return lines;
        }

        if (mc.player.getHealth() < mc.player.getMaxHealth() * 0.45f || mc.player.getAir() < 100) {
            lines.add("CONTEXT // DAMAGE CONTROL");
            lines.add(String.format(Locale.ROOT, "VITALS  %05.1f / %05.1f", mc.player.getHealth(), mc.player.getMaxHealth()));
            lines.add(String.format(Locale.ROOT, "ARMOR   %02d", mc.player.getArmor()));
            lines.add(String.format(Locale.ROOT, "OXYGEN  %03d / %03d", mc.player.getAir(), mc.player.getMaxAir()));
            if (extendedContextData.get()) {
                lines.add(String.format(Locale.ROOT, "ABSORB  %04.1f", mc.player.getAbsorptionAmount()));
                lines.add(String.format(Locale.ROOT, "ENERGY  %02d / 20  SAT %04.1f",
                    mc.player.getHungerManager().getFoodLevel(), mc.player.getHungerManager().getSaturationLevel()));
                lines.add(String.format(Locale.ROOT, "EFFECTS %02d ACTIVE", mc.player.getStatusEffects().size()));
            }
            lines.add("STATUS  " + (mc.player.getHealth() < 6.0f ? "CRITICAL" : "DAMAGED"));
            lines.add("ACTION  SEEK COVER");
            return lines;
        }

        int page = (int) ((now / 4_000_000_000L) % 3L);
        if (page == 0) {
            lines.add("CONTEXT // ENVIRONMENT");
            lines.add("WEATHER " + (mc.world.isThundering() ? "ELECTRICAL STORM" : mc.world.isRaining() ? "PRECIPITATION" : "CLEAR"));
            lines.add("ZONE    " + mc.world.getRegistryKey().getValue().getPath().toUpperCase(Locale.ROOT));
            String biome = mc.world.getBiome(mc.player.getBlockPos()).getKey()
                .map(key -> key.getValue().getPath()).orElse("unknown").toUpperCase(Locale.ROOT);
            lines.add("TERRAIN " + biome);
            lines.add(String.format(Locale.ROOT, "ALTITUDE %06.1f", mc.player.getY()));
            lines.add(String.format(Locale.ROOT, "CYCLE   %05d", mc.world.getTimeOfDay() % 24000L));
            if (extendedContextData.get()) {
                lines.add(String.format(Locale.ROOT, "SECTOR  CHUNK %+05d %+05d",
                    mc.player.getBlockX() >> 4, mc.player.getBlockZ() >> 4));
                lines.add(String.format(Locale.ROOT, "CONTACT %02d ENTITIES  %02d MATERIALS",
                    targets.size(), blockTargets.size()));
            }
        } else if (page == 1) {
            lines.add("CONTEXT // LOADOUT");
            lines.add("PRIMARY " + stackLabel(mc.player.getMainHandStack()));
            lines.add("AUX     " + stackLabel(mc.player.getOffHandStack()));
            lines.add(String.format(Locale.ROOT, "ARMOR   %02d", mc.player.getArmor()));
            lines.add(String.format(Locale.ROOT, "ENERGY  %02d / 20", mc.player.getHungerManager().getFoodLevel()));
            lines.add(String.format(Locale.ROOT, "EFFECTS %02d ACTIVE", mc.player.getStatusEffects().size()));
            if (extendedContextData.get()) {
                lines.add(String.format(Locale.ROOT, "EXPER   LEVEL %03d  %03d PERCENT",
                    mc.player.experienceLevel, (int) (mc.player.experienceProgress * 100.0f)));
                lines.add(String.format(Locale.ROOT, "HOTBAR  SLOT %02d", mc.player.getInventory().getSelectedSlot() + 1));
                lines.add(String.format(Locale.ROOT, "ABSORB  %04.1f  SATURATION %04.1f",
                    mc.player.getAbsorptionAmount(), mc.player.getHungerManager().getSaturationLevel()));
            }
        } else {
            lines.add("CONTEXT // MOTION CONTROL");
            lines.add(String.format(Locale.ROOT, "X       %+09.3f", mc.player.getX()));
            lines.add(String.format(Locale.ROOT, "Y       %+09.3f", mc.player.getY()));
            lines.add(String.format(Locale.ROOT, "Z       %+09.3f", mc.player.getZ()));
            lines.add(String.format(Locale.ROOT, "SPEED   %06.2f M/S", mc.player.getVelocity().length() * 20.0));
            lines.add(String.format(Locale.ROOT, "AZIMUTH %06.1f", normalizeYaw(mc.player.getYaw())));
            if (extendedContextData.get()) {
                lines.add(String.format(Locale.ROOT, "HORIZ   %06.2f M/S  VERT %+06.2f M/S",
                    mc.player.getVelocity().horizontalLength() * 20.0, mc.player.getVelocity().y * 20.0));
                lines.add("STATE   " + entityState(mc.player));
                lines.add(String.format(Locale.ROOT, "PITCH   %+06.1f  POSE %s",
                    mc.player.getPitch(), mc.player.getPose().asString().toUpperCase(Locale.ROOT)));
            }
            lines.add("CONTACT NO ACTIVE TARGET");
        }
        return lines;
    }

    private List<String> fitContextData(List<String> lines) {
        int maxLines = Math.max(4, (guiHeight() - 16) / 10);
        if (lines.size() <= maxLines) return lines;

        List<String> fitted = new ArrayList<>(lines.subList(0, maxLines - 1));
        String finalLine = lines.get(lines.size() - 1);
        fitted.add(finalLine.startsWith("DECISION") || finalLine.startsWith("ACTION") || finalLine.startsWith("CONTACT")
            ? finalLine
            : String.format(Locale.ROOT, "DATA    %02d ADDITIONAL RECORDS", lines.size() - maxLines + 1));
        return fitted;
    }

    private void drawContextPanelGeometry(Render2DEvent event, List<String> lines) {
        int textWidth = 142;
        for (String line : lines) textWidth = Math.max(textWidth, mc.textRenderer.getWidth(line));
        double width = textWidth + 12.0;
        double height = lines.size() * 10.0 + 10.0;
        double x = guiWidth() - width - 12.0;
        double y = contextPanelY(height);
        Renderer2D.COLOR.quad(x, y, width, height, color(27));
        Renderer2D.COLOR.boxLines(x, y, width, height, color(100));
        Renderer2D.COLOR.line(x + 4.0, y + 14.0, x + width - 4.0, y + 14.0, color(130));
        Renderer2D.COLOR.line(x - 5.0, y, x, y, color(185));
        Renderer2D.COLOR.line(x - 5.0, y + height, x, y + height, color(185));
    }

    private void drawContextPanelText(Render2DEvent event, List<String> lines) {
        int textWidth = 142;
        for (String line : lines) textWidth = Math.max(textWidth, mc.textRenderer.getWidth(line));
        double panelHeight = lines.size() * 10.0 + 10.0;
        int x = (int) (guiWidth() - textWidth - 18.0);
        int y = (int) (contextPanelY(panelHeight) + 4.0);
        for (int i = 0; i < lines.size(); i++) {
            event.drawContext.drawText(mc.textRenderer, lines.get(i), x, y + i * 10, argb(i == 0 ? 245 : 195), true);
        }
    }

    private String entityState(Entity entity) {
        List<String> states = new ArrayList<>(4);
        states.add(entity.isOnGround() ? "GROUND" : "AIRBORNE");
        if (entity.isSubmergedInWater()) states.add("SUBMERGED");
        if (entity.isOnFire()) states.add("BURNING");
        if (entity.isSneaking()) states.add("CROUCH");
        return String.join(" / ", states);
    }

    private String stackLabel(net.minecraft.item.ItemStack stack) {
        if (stack.isEmpty()) return "NONE";
        return compactHudText(stack.getName().getString() + " X" + stack.getCount(), 32).toUpperCase(Locale.ROOT);
    }

    private static String cardinalName(double heading) {
        String[] names = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        return names[(int) Math.floor((heading + 22.5) / 45.0) & 7];
    }

    private Color color(int alpha) {
        SettingColor color = interfaceColor.get();
        return new Color(color.r, color.g, color.b, clamp(alpha, 0, 255));
    }

    private int argb(int alpha) {
        SettingColor color = interfaceColor.get();
        return (clamp(alpha, 0, 255) << 24) | (color.r << 16) | (color.g << 8) | color.b;
    }

    private int guiWidth() {
        return mc.getWindow().getScaledWidth();
    }

    private int guiHeight() {
        return mc.getWindow().getScaledHeight();
    }

    private double contextPanelY(double panelHeight) {
        double maxY = Math.max(6.0, guiHeight() - panelHeight - 6.0);
        return clamp(guiHeight() * 0.5 - panelHeight * 0.5 - 100.0, 6.0, maxY);
    }

    private static double normalizeYaw(float yaw) {
        double normalized = yaw % 360.0;
        return normalized < 0.0 ? normalized + 360.0 : normalized;
    }

    private static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0f;
        if (wrapped >= 180.0f) wrapped -= 360.0f;
        if (wrapped < -180.0f) wrapped += 360.0f;
        return wrapped;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class ScanState {
        private final long firstSeenNanos;
        private long lastSeenNanos;
        private long confirmedAtNanos;
        private boolean structuralScanDecided;
        private boolean structuralScan;
        private boolean missionAnnounced;
        private int structuralPartIndex;
        private int structuralPartCount;
        private int lastStructuralSoundPart = -1;
        private String structuralPartName = "MODEL";

        private ScanState(long now) {
            firstSeenNanos = now;
            lastSeenNanos = now;
        }
    }

    private record ScreenBox(double minX, double minY, double maxX, double maxY) {
        private double width() { return maxX - minX; }
        private double height() { return maxY - minY; }
        private double centerX() { return (minX + maxX) * 0.5; }
        private double centerY() { return (minY + maxY) * 0.5; }
        private ScreenBox expand(double amount) {
            return new ScreenBox(minX - amount, minY - amount, maxX + amount, maxY + amount);
        }
        private ScreenBox union(ScreenBox other) {
            return new ScreenBox(
                Math.min(minX, other.minX), Math.min(minY, other.minY),
                Math.max(maxX, other.maxX), Math.max(maxY, other.maxY)
            );
        }
    }

    private record TargetRender(Entity entity, ScreenBox box, double age, double progress) {}

    private record TargetGroup(List<TargetRender> members, ScreenBox box, double age, double progress) {}

    private record BlockCluster(Block block, int count, BlockPos min, BlockPos max,
                                boolean mission, List<SignSnapshot> signs) {
        private BlockClusterKey key() {
            return new BlockClusterKey(block, min, max);
        }

        private Box worldBox() {
            return new Box(min.getX(), min.getY(), min.getZ(),
                max.getX() + 1.0, max.getY() + 1.0, max.getZ() + 1.0);
        }
    }

    private record SignSnapshot(BlockPos pos, List<String> front, List<String> back) {
        private boolean hasText() {
            return !front.isEmpty() || !back.isEmpty();
        }
    }

    private record BlockClusterKey(Block block, BlockPos min, BlockPos max) {}

    private record BlockTargetRender(BlockCluster cluster, ScreenBox box, double age, double progress) {}
}
