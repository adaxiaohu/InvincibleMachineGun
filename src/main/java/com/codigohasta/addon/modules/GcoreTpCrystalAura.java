package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.utils.gcore.GcoreBlockUtil;
import com.codigohasta.addon.utils.gcore.GcoreCrystalAndObsidianPlaceData;
import com.codigohasta.addon.utils.gcore.GcoreCrystalUtil;
import com.codigohasta.addon.utils.gcore.GcoreEnumType;
import com.codigohasta.addon.utils.gcore.GcoreEntityUtilKt;
import com.codigohasta.addon.utils.gcore.GcoreMainThread;
import com.codigohasta.addon.utils.gcore.GcoreMSTimer;
import com.codigohasta.addon.utils.gcore.GcoreNewItemUtil;
import com.codigohasta.addon.utils.gcore.GcorePlayerActionUtil;
import com.codigohasta.addon.utils.gcore.GcoreServerEquivMove;
import com.codigohasta.addon.utils.gcore.GcoreSwapItemOption;
import com.codigohasta.addon.utils.gcore.GcoreTargetUtil;
import com.codigohasta.addon.utils.gcore.GcoreTickUpdate;
import com.codigohasta.addon.utils.gcore.GcoreTpCrystalAndObsidianPlaceData;
import com.codigohasta.addon.utils.gcore.GcoreTpCrystalUtil;
import com.codigohasta.addon.utils.gcore.GcoreTpOption;
import com.codigohasta.addon.utils.gcore.GcoreTpSelectTargetsConfig;
import com.codigohasta.addon.utils.gcore.GcoreTpUtil;
import com.codigohasta.addon.utils.gcore.GcoreWebTrapDiag;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.LinkedHashMap;
import java.util.Map;

public class GcoreTpCrystalAura extends Module {

    private final SettingGroup sgTarget = this.settings.createGroup("目标");
    private final SettingGroup sgGeneral = this.settings.createGroup("通用");
    private final SettingGroup sgCrystal = this.settings.createGroup("打水晶");
    private final SettingGroup sgPlace = this.settings.createGroup("放置");
    private final SettingGroup sgDamage = this.settings.createGroup("伤害");
    private final SettingGroup sgOther = this.settings.createGroup("其它");

    private final GcoreTpSelectTargetsConfig targetSelectOption;
    private final GcoreSwapItemOption swapItemOption;
    private final GcoreTpOption tpOption;

    private final Setting<GcoreEnumType.CrystalPlaceVersion> version;

    private final Setting<Integer> attackDelay;
    private final Setting<Double> hitRange;
    private final Setting<Double> throughWallRange;

    private final Setting<Integer> placeDelay;
    private final Setting<Integer> scanBorder;
    private final Setting<Double> placeRange;
    private final Setting<Integer> placeAmount;
    private final Setting<Boolean> placeObsidian;
    private final Setting<Boolean> placeCrystalSame;
    private final Setting<Boolean> fixMissingPlaceData;
    private final Setting<Boolean> skipCrystalWithoutPlaceData;

    private final Setting<Double> minDamageToTarget;
    private final Setting<Double> maxDamageToSelf;

    private final Setting<Double> prev;
    private final Setting<Boolean> swingHand;
    private final Setting<Boolean> render;
    private final Setting<Boolean> logStages;

    private final GcoreMSTimer attackTimer = new GcoreMSTimer();
    private final GcoreMSTimer placeTimer = new GcoreMSTimer();

    private Entity target;

    private BlockPos lastCrystalPos;
    private BlockPos lastObsidianPos;
    private Vec3d lastVec;

    public GcoreTpCrystalAura() {
        super(AddonTemplate.CATEGORY, "如来真掌·魔术水晶", """
            魔术一样的水晶，凭空出现在目标脚下。
            瞬移到水晶旁引爆，自己一点不吃炸。
            开启TP服务器等价模块从而发挥真威力。""");

        this.targetSelectOption = new GcoreTpSelectTargetsConfig(this.sgTarget);
        this.swapItemOption = new GcoreSwapItemOption(this.sgGeneral);
        this.tpOption = new GcoreTpOption(this.sgGeneral);

        this.version = this.sgPlace.add(new EnumSetting.Builder<GcoreEnumType.CrystalPlaceVersion>()
            .name("放置版本")
            .description("水晶放置的版本行为。MC1_14 可直接朝下放；MC1_12 要求上方 2 格都是空气。")
            .defaultValue(GcoreEnumType.CrystalPlaceVersion.MC1_14)
            .build());

        this.attackDelay = this.sgCrystal.add(new IntSetting.Builder()
            .name("攻击间隔")
            .description("两次打水晶之间的间隔，单位毫秒。")
            .defaultValue(100)
            .range(1, 2000)
            .sliderRange(1, 2000)
            .build());

        this.hitRange = this.sgCrystal.add(new DoubleSetting.Builder()
            .name("命中距离")
            .description("能直接看到水晶时的最大攻击距离。")
            .defaultValue(5.0)
            .range(0.1, 6.0)
            .sliderRange(0.1, 6.0)
            .build());

        this.throughWallRange = this.sgCrystal.add(new DoubleSetting.Builder()
            .name("穿墙距离")
            .description("被方块挡住时的最大攻击距离（通常要站得更近）。")
            .defaultValue(5.0)
            .range(0.1, 6.0)
            .sliderRange(0.1, 6.0)
            .build());

        this.placeDelay = this.sgPlace.add(new IntSetting.Builder()
            .name("放置间隔")
            .description("两次放置之间的间隔，单位毫秒。")
            .defaultValue(180)
            .range(1, 2000)
            .sliderRange(1, 2000)
            .build());

        this.scanBorder = this.sgPlace.add(new IntSetting.Builder()
            .name("搜索半径")
            .description("在目标周围搜索可放置位置的范围。5 表示 11×11×11 的网格。")
            .defaultValue(5)
            .range(1, 8)
            .sliderRange(1, 8)
            .build());

        this.placeRange = this.sgPlace.add(new DoubleSetting.Builder()
            .name("放置距离")
            .description("放置距离。上限受服务端交互距离（6 格）限制。")
            .defaultValue(4.5)
            .range(0.1, 6.0)
            .sliderRange(0.1, 6.0)
            .build());

        this.placeAmount = this.sgPlace.add(new IntSetting.Builder()
            .name("放置数量")
            .description("一次 TP 最多放几套（黑曜石+水晶）。")
            .defaultValue(1)
            .range(1, 5)
            .sliderRange(1, 5)
            .build());

        this.placeObsidian = this.sgPlace.add(new BoolSetting.Builder()
            .name("放黑曜石")
            .description("允许自己放黑曜石当底座。关掉则只利用已有的黑曜石/基岩。")
            .defaultValue(true)
            .build());

        this.placeCrystalSame = this.sgPlace.add(new BoolSetting.Builder()
            .name("同格放水晶")
            .description("放完黑曜石后，在同一个站位立刻放水晶（对手来不及反应）。")
            .defaultValue(true)
            .build());

        this.fixMissingPlaceData = this.sgPlace.add(new BoolSetting.Builder()
            .name("修复漏给放置面")
            .description("修复 A：源在选中方块后才发现「刚入列的方块没被写上放置面」，"
                + "导致那块黑曜石根本不放、却照样发水晶包。开启后每个方块都会拿到放置面。")
            .defaultValue(true)
            .build());

        this.skipCrystalWithoutPlaceData = this.sgPlace.add(new BoolSetting.Builder()
            .name("无面不发水晶")
            .description("修复 B：某方块没有放置面（黑曜石放不下）时，跳过它的水晶包，"
                + "不再发必然被服务端拒绝的包。")
            .defaultValue(true)
            .build());

        this.minDamageToTarget = this.sgDamage.add(new DoubleSetting.Builder()
            .name("对目标最小伤害")
            .description("对目标伤害低于此值的方案会被丢弃。")
            .defaultValue(0.3)
            .range(0.01, 1.0)
            .sliderRange(0.01, 1.0)
            .build());

        this.maxDamageToSelf = this.sgDamage.add(new DoubleSetting.Builder()
            .name("对自己最大伤害")
            .description("自己会吃的伤害上限。1.0 表示不限制（只要换血划算就打）。")
            .defaultValue(1.0)
            .range(0.01, 1.0)
            .sliderRange(0.01, 1.0)
            .build());

        this.prev = this.sgOther.add(new DoubleSetting.Builder()
            .name("位置回溯")
            .description("目标位置回溯系数（抵消网络延迟）。0 为当前位置。")
            .defaultValue(0.0)
            .range(0.1, 5.0)
            .sliderRange(0.1, 5.0)
            .build());

        this.swingHand = this.sgOther.add(new BoolSetting.Builder()
            .name("挥手")
            .description("放置 / 攻击时挥手。")
            .defaultValue(true)
            .build());

        this.render = this.sgOther.add(new BoolSetting.Builder()
            .name("渲染")
            .description("渲染水晶位 / 黑曜石位 / TP 落点的盒子。")
            .defaultValue(true)
            .build());

        this.logStages = this.sgOther.add(new BoolSetting.Builder()
            .name("阶段诊断日志")
            .description("【诊断】把每个阶段的结果写进 gcore-webtrap.log，用于定位「水晶放不出」。 开发用的")
            .defaultValue(false)
            .onChanged(v -> GcoreWebTrapDiag.setModuleDiag(MODULE_NAME, v))
            .build());
    }

    @Override
    public void onActivate() {
        GcoreTickUpdate.INSTANCE.register();
        this.target = null;
        GcoreWebTrapDiag.setModuleDiag(MODULE_NAME, this.logStages.get());
    }

    @Override
    public void onDeactivate() {
        GcoreTickUpdate.INSTANCE.unregister();
        this.target = null;
        this.lastCrystalPos = null;
        this.lastObsidianPos = null;
        this.lastVec = null;
        GcoreWebTrapDiag.setModuleDiag(MODULE_NAME, false);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        this.target = GcoreTargetUtil.getTarget(this.targetSelectOption);
        if (this.target != null) {
            doAura(this.target);
        }
    }

    private static final String MODULE_NAME = "GcoreTpCrystalAura";

    private void doAura(Entity t) {
        Thread.ofVirtual().name("gcore-doAura").start(() -> {
            try (var ignored = GcoreServerEquivMove.enter(MODULE_NAME)) {
                doAttackCrystal();
                doPlace(t);
            } catch (Throwable ignored) {
            }
        });
    }

    private static void executeOnMainThread(Runnable body) {
        GcoreMainThread.runAndWait(() -> {
            try (var ignored = GcoreServerEquivMove.enter(MODULE_NAME)) {
                body.run();
            }
        });
    }

    private static Vec3d prevPos(Entity entity) {
        return new Vec3d(entity.lastX, entity.lastY, entity.lastZ);
    }

    private void doAttackCrystal() {
        if (!this.attackTimer.hasPassTime(this.attackDelay.get())) {
            return;
        }
        this.attackTimer.reset();

        var player = this.mc.player;
        if (player == null || this.mc.world == null || this.target == null) {
            return;
        }

        Entity crystal = GcoreCrystalUtil.getAttackCrystalFromTarget(
            this.target,
            GcoreEntityUtilKt.getPrevPos(this.target, this.prev.get().floatValue()),
            player,
            player.getEntityPos(),
            this.minDamageToTarget.get().floatValue(),
            999.0F,
            12.0F,
            crystalEntity -> true);

        if (crystal == null) {
            GcoreWebTrapDiag.logfFor(MODULE_NAME, "打水晶", "★没找到可打的水晶 → 跳过");
            return;
        }

        GcoreWebTrapDiag.logfFor(MODULE_NAME, "打水晶", "找到水晶 pos=[%.2f,%.2f,%.2f]",
            crystal.getEntityPos().x, crystal.getEntityPos().y, crystal.getEntityPos().z);

        Vec3d safePos = GcoreTpCrystalUtil.getSafePosToAttackCrystal(
            crystal.getEntityPos(),
            6,
            this.hitRange.get(),
            this.throughWallRange.get(),
            this.maxDamageToSelf.get().floatValue());

        if (safePos == null) {
            return;
        }

        Vec3d playerPos = player.getEntityPos();
        Map<BlockPos, BlockState> extraPosMap = GcoreBlockUtil.getBufferMap();

        executeOnMainThread(() ->
            GcoreTpUtil.doTpMove(this.tpOption, playerPos, safePos, extraPosMap,
                (counter, curVec) -> {
                    GcorePlayerActionUtil.doAttackEntity(crystal, false);
                    if (this.swingHand.get()) {
                        player.swingHand(Hand.MAIN_HAND);
                    }
                }));
    }

    private void doPlace(Entity t) {
        if (!this.placeTimer.hasPassTime(this.placeDelay.get())) {
            return;
        }
        this.placeTimer.reset();

        var player = this.mc.player;
        if (player == null || this.mc.world == null) {
            return;
        }

        if (GcoreNewItemUtil.findItem(this.swapItemOption, Items.END_CRYSTAL) == -1) {
            GcoreWebTrapDiag.logfFor(MODULE_NAME, "物品", "★末影水晶缺失 → 放弃");
            return;
        }

        int obsidianSlot = GcoreNewItemUtil.findItem(this.swapItemOption, Items.OBSIDIAN);
        if (obsidianSlot == -1 && this.placeObsidian.get()) {
            GcoreWebTrapDiag.logfFor(MODULE_NAME, "物品", "★黑曜石缺失且「放黑曜石」开 → 放弃");
            return;
        }

        boolean placeObs = obsidianSlot != -1 && this.placeObsidian.get();
        boolean placeCSame = this.placeCrystalSame.get();

        GcoreTpCrystalAndObsidianPlaceData result = GcoreTpCrystalUtil.getTpPlaceCrystalData(
            this.version.get(),
            t,
            GcoreEntityUtilKt.getPrevPos(t, this.prev.get().floatValue()),
            GcoreTpCrystalUtil.DEFAULT_PLACER_EYE_Y,
            this.placeRange.get(),
            this.placeAmount.get(),
            t.getEntityPos(),
            this.scanBorder.get(),
            this.minDamageToTarget.get().floatValue(),
            placeObs,
            placeCSame,
            this.fixMissingPlaceData.get());

        if (result == null) {
            return;
        }

        Map<BlockPos, BlockState> extraPosMap = new LinkedHashMap<>();
        for (GcoreCrystalAndObsidianPlaceData data : result.getPlaceDataList()) {
            if (data.getType() == GcoreCrystalAndObsidianPlaceData.Type.PlaceObsidian) {
                extraPosMap.put(data.getObsidianPos(), Blocks.OBSIDIAN.getDefaultState());
            }
        }

        Vec3d tpVec = result.getTpVec();
        Vec3d playerPos = prevPos(player);

        executeOnMainThread(() -> {
            this.lastVec = tpVec;
            GcoreTpUtil.doTpMove(this.tpOption, playerPos, tpVec, extraPosMap,
                (counter, curVec) -> doPlaceFromData(result.getPlaceDataList(), placeCSame));
        });
    }

    private void doPlaceFromData(java.util.List<GcoreCrystalAndObsidianPlaceData> placeDataList,
                                 boolean placeCrystalSame) {
        var player = this.mc.player;
        if (player == null) {
            return;
        }

        for (GcoreCrystalAndObsidianPlaceData data : placeDataList) {
            this.lastCrystalPos = data.getObsidianPos();
            this.lastObsidianPos = (data.getPlaceData() != null)
                ? data.getPlaceData().getPlacePos() : null;

            switch (data.getType()) {
                case PlaceCrystal -> GcoreNewItemUtil.swapUse(this.swapItemOption, Items.END_CRYSTAL, () ->
                    GcorePlayerActionUtil.doPlaceAt(Hand.MAIN_HAND, data.getObsidianPos(), Direction.DOWN));

                case PlaceObsidian -> {
                    boolean placedObsidian = false;
                    if (data.getPlaceData() != null) {
                        GcoreNewItemUtil.swapUse(this.swapItemOption, Items.OBSIDIAN, () ->
                            GcorePlayerActionUtil.doPlaceAt(Hand.MAIN_HAND,
                                data.getPlaceData().getPlacePos(),
                                data.getPlaceData().getPlaceFacing()));
                        placedObsidian = true;
                    }
                    boolean canPlaceCrystal = !this.skipCrystalWithoutPlaceData.get() || placedObsidian;
                    if (placeCrystalSame && canPlaceCrystal) {
                        GcoreNewItemUtil.swapUse(this.swapItemOption, Items.END_CRYSTAL, () ->
                            GcorePlayerActionUtil.doPlaceAt(Hand.MAIN_HAND,
                                data.getObsidianPos(), Direction.DOWN));
                    }
                }
            }

            if (data.getType() == GcoreCrystalAndObsidianPlaceData.Type.PlaceObsidian) {
                GcoreWebTrapDiag.logf("执行",
                    "黑曜石 obs=%s placePos=%s facing=%s 底座方块=%s 上方方块=%s"
                        + " | 水晶位=%s 空气=%b 1x2x1有实体=%b | 目标=%s 实体表=%d",
                    data.getObsidianPos(),
                    (data.getPlaceData() != null) ? data.getPlaceData().getPlacePos() : null,
                    (data.getPlaceData() != null) ? data.getPlaceData().getPlaceFacing() : null,
                    this.blockNameAt(data.getObsidianPos()),
                    this.blockNameAt(data.getObsidianPos().up()),
                    data.getObsidianPos().up(),
                    this.obstructionAirCheck(data.getObsidianPos()),
                    this.obstructionEntityCheck(data.getObsidianPos()),
                    this.targetPosStr(),
                    GcoreTickUpdate.getWorldEntitiesList().size());
            } else {
                GcoreWebTrapDiag.logf("执行",
                    "水晶(已有底座) obs=%s 底座方块=%s 上方方块=%s"
                        + " | 水晶位=%s 空气=%b 1x2x1有实体=%b | 目标=%s 实体表=%d",
                    data.getObsidianPos(),
                    this.blockNameAt(data.getObsidianPos()),
                    this.blockNameAt(data.getObsidianPos().up()),
                    data.getObsidianPos().up(),
                    this.obstructionAirCheck(data.getObsidianPos()),
                    this.obstructionEntityCheck(data.getObsidianPos()),
                    this.targetPosStr(),
                    GcoreTickUpdate.getWorldEntitiesList().size());
            }

            if (this.swingHand.get()) {
                player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private boolean obstructionAirCheck(BlockPos obsPos) {
        return this.mc.world != null && this.mc.world.isAir(obsPos.up());
    }

    private String blockNameAt(BlockPos pos) {
        if (this.mc.world == null) {
            return "?";
        }
        return net.minecraft.registry.Registries.BLOCK
            .getId(this.mc.world.getBlockState(pos).getBlock()).toString();
    }

    private String targetPosStr() {
        Entity t = this.target;
        if (t == null) {
            return "none";
        }
        var p = t.getEntityPos();
        return String.format(java.util.Locale.ROOT, "[%.2f,%.2f,%.2f]", p.x, p.y, p.z);
    }

    private boolean obstructionEntityCheck(BlockPos obsPos) {
        if (this.mc.world == null) {
            return false;
        }
        BlockPos up = obsPos.up();
        return !this.mc.world.getOtherEntities(null,
            new net.minecraft.util.math.Box(
                up.getX(), up.getY(), up.getZ(),
                up.getX() + 1.0, up.getY() + 2.0, up.getZ() + 1.0)).isEmpty();
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!this.render.get()) {
            return;
        }

        if (this.lastCrystalPos != null) {
            Color c = new Color(255, 100, 100, 200);
            event.renderer.box(
                this.lastCrystalPos.getX(), this.lastCrystalPos.getY(), this.lastCrystalPos.getZ(),
                this.lastCrystalPos.getX() + 1, this.lastCrystalPos.getY() + 1, this.lastCrystalPos.getZ() + 1,
                c, c, ShapeMode.Both, 0);
        }

        if (this.lastObsidianPos != null) {
            Color c = new Color(100, 255, 100, 200);
            event.renderer.box(
                this.lastObsidianPos.getX(), this.lastObsidianPos.getY(), this.lastObsidianPos.getZ(),
                this.lastObsidianPos.getX() + 1, this.lastObsidianPos.getY() + 1, this.lastObsidianPos.getZ() + 1,
                c, c, ShapeMode.Both, 0);
        }

        if (this.lastVec != null) {
            Color c = new Color(100, 100, 255, 200);
            event.renderer.box(
                this.lastVec.x - 0.3, this.lastVec.y, this.lastVec.z - 0.3,
                this.lastVec.x + 0.3, this.lastVec.y + 1.8, this.lastVec.z + 0.3,
                c, c, ShapeMode.Both, 0);
        }
    }
}
