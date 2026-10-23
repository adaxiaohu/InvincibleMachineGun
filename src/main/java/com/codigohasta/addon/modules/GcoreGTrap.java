package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.utils.gcore.GcoreEntityUtilKt;
import com.codigohasta.addon.utils.gcore.GcoreEnumType;
import com.codigohasta.addon.utils.gcore.GcoreMainThread;
import com.codigohasta.addon.utils.gcore.GcoreMSTimer;
import com.codigohasta.addon.utils.gcore.GcoreNewItemUtil;
import com.codigohasta.addon.utils.gcore.GcorePlaceData;
import com.codigohasta.addon.utils.gcore.GcorePlayerActionUtil;
import com.codigohasta.addon.utils.gcore.GcoreServerEquivMove;
import com.codigohasta.addon.utils.gcore.GcoreSwapItemOption;
import com.codigohasta.addon.utils.gcore.GcoreTargetUtil;
import com.codigohasta.addon.utils.gcore.GcoreTickUpdate;
import com.codigohasta.addon.utils.gcore.GcoreTpOption;
import com.codigohasta.addon.utils.gcore.GcoreTpSelectTargetsConfig;
import com.codigohasta.addon.utils.gcore.GcoreTpUtil;
import com.codigohasta.addon.utils.gcore.GcoreTrapUtil;
import com.codigohasta.addon.utils.gcore.GcoreWebTrapDiag;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GcoreGTrap extends Module {

    static final String MODULE_NAME = "GcoreGTrap";

    private final SettingGroup sgTarget = this.settings.createGroup("目标");
    private final SettingGroup sgTargetTp = this.settings.createGroup("传送目标");
    private final SettingGroup sgGeneral = this.settings.createGroup("通用");
    private final SettingGroup sgPlace = this.settings.createGroup("放置");

    private final GcoreTpSelectTargetsConfig targetOption;
    private final GcoreTpSelectTargetsConfig targetTpOption;
    private final GcoreSwapItemOption swapItemOption;
    private final GcoreTpOption tpOption;

    private final Setting<GcoreEnumType.BlockPlaceVersion> version;
    private final Setting<Boolean> useAnchorPlace;
    private final Setting<Integer> scanBorder;
    private final Setting<Integer> placeDelay;
    private final Setting<Double> placeRange;
    private final Setting<Integer> placeAmount;
    private final Setting<Boolean> swingHand;
    private final Setting<Boolean> useTp;

    private final Setting<Boolean> logStages;

    private final GcoreMSTimer placeTimer = new GcoreMSTimer();

    public GcoreGTrap() {
        super(AddonTemplate.CATEGORY, "困阵",
            "把目标的四周用黑曜石或重生锚围死。使用TP 服务端等价。");

        this.targetOption = new GcoreTpSelectTargetsConfig(this.sgTarget, 8.0, 500.0, true, true);
        this.targetTpOption = new GcoreTpSelectTargetsConfig(this.sgTargetTp, 500.0, 500.0, false, false);
        this.swapItemOption = new GcoreSwapItemOption(this.sgGeneral);
        this.tpOption = new GcoreTpOption(this.sgGeneral);

        this.version = this.sgPlace.add(new EnumSetting.Builder<GcoreEnumType.BlockPlaceVersion>()
            .name("放置版本")
            .description("方块放置的版本行为。MC1_14 可直接朝下放，MC1_12 需要相邻不透明方块。")
            .defaultValue(GcoreEnumType.BlockPlaceVersion.MC1_14)
            .build());

        this.useAnchorPlace = this.sgPlace.add(new BoolSetting.Builder()
            .name("用重生锚")
            .description("用重生锚代替黑曜石。")
            .defaultValue(true)
            .build());

        this.scanBorder = this.sgPlace.add(new IntSetting.Builder()
            .name("搜索半径")
            .description("搜索半径（格）。")
            .defaultValue(6)
            .range(1, 6)
            .sliderRange(1, 6)
            .build());

        this.placeDelay = this.sgPlace.add(new IntSetting.Builder()
            .name("放置间隔")
            .description("两次放置之间的间隔（毫秒）。")
            .defaultValue(1)
            .range(1, 1000)
            .sliderRange(1, 1000)
            .build());

        this.placeRange = this.sgPlace.add(new DoubleSetting.Builder()
            .name("放置距离")
            .description("放置距离。上限受服务端交互距离限制（6 格）。")
            .defaultValue(5.5)
            .range(0.1, 6.0)
            .sliderRange(0.1, 6.0)
            .build());

        this.placeAmount = this.sgPlace.add(new IntSetting.Builder()
            .name("放置数量")
            .description("一次最多放几个方块。")
            .defaultValue(5)
            .range(1, 5)
            .sliderRange(1, 5)
            .build());

        this.swingHand = this.sgGeneral.add(new BoolSetting.Builder()
            .name("挥手")
            .description("放置时挥手。")
            .defaultValue(true)
            .build());

        this.useTp = this.sgGeneral.add(new BoolSetting.Builder()
            .name("传送版")
            .description("先 VClip 传到目标身边再放，放完传回来。关闭则就地放。")
            .defaultValue(true)
            .build());

        this.logStages = this.sgGeneral.add(new BoolSetting.Builder()
            .name("阶段诊断日志")
            .description("【诊断】把每个阶段的结果写进 gcore-webtrap.log，用于定位「什么都不做」。开发用的")
            .defaultValue(false)
            .onChanged(v -> GcoreWebTrapDiag.setModuleDiag(MODULE_NAME, v))
            .build());
    }

    @Override
    public void onActivate() {
        GcoreTickUpdate.INSTANCE.register();
        GcoreWebTrapDiag.setModuleDiag(MODULE_NAME, this.logStages.get());
    }

    @Override
    public void onDeactivate() {
        GcoreTickUpdate.INSTANCE.unregister();
        GcoreWebTrapDiag.setModuleDiag(MODULE_NAME, false);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        Entity target = this.useTp.get()
            ? GcoreTargetUtil.getTarget(this.targetTpOption)
            : GcoreTargetUtil.getTarget(this.targetOption);
        if (target == null) {
            GcoreWebTrapDiag.logfFor(MODULE_NAME, "目标", "未找到目标（%s，搜索半径=%.0f）",
                this.useTp.get() ? "传送目标组" : "目标组",
                this.useTp.get() ? this.targetTpOption.getRange() : this.targetOption.getRange());
            return;
        }

        Entity t = target;
        Thread.ofVirtual().name("gcore-gtrap").start(() -> {
            try (var ignored = GcoreServerEquivMove.enter(MODULE_NAME)) {
                doTrap(t);
            } catch (Throwable ignored) {
            }
        });
    }

    public void doTrapFromOther(Entity target) {
        if (target == null) {
            return;
        }
        try (var ignored = GcoreServerEquivMove.enter(MODULE_NAME)) {
            doTrap(target);
        } catch (Throwable ignored) {
        }
    }

    private void doTrap(Entity target) {
        if (!this.placeTimer.hasPassTime(this.placeDelay.get())) {
            return;
        }
        this.placeTimer.reset();

        Item placeItem = this.useAnchorPlace.get() ? Items.RESPAWN_ANCHOR : Items.OBSIDIAN;
        if (GcoreNewItemUtil.findItem(this.swapItemOption, placeItem) == -1) {
            GcoreWebTrapDiag.logf("物品", "★%s缺失 → 放弃", placeItem.getName().getString());
            return;
        }
        GcoreWebTrapDiag.logf("物品", "OK（%s）", placeItem.getName().getString());

        if (this.useTp.get()) {
            doTpTrap(target);
        } else {
            doNormalTrap(target);
        }
    }

    private void doTpTrap(Entity target) {
        var player = this.mc.player;
        if (player == null || this.mc.world == null) {
            return;
        }

        List<BlockPos> trapPosList = GcoreTrapUtil.getGTrapPosFromList(
            GcoreTrapUtil.getEntitySurroundPos(target, true, true),
            (pos, dir) -> true);
        if (trapPosList.isEmpty()) {
            GcoreWebTrapDiag.log("包围格", "★没找到任何可围的格子 → 放弃");
            return;
        }

        List<GcorePlaceData> placeDataList = GcoreTrapUtil.getPlaceDataFromPosList(
            this.version.get(),
            trapPosList,
            new ArrayList<>(),
            target.getEntityPos(),
            player.getEyeHeight(player.getPose()),
            this.scanBorder.get(),
            999.0);
        GcoreWebTrapDiag.logf("放置点", "包围格=%d → 可放=%d", trapPosList.size(), placeDataList.size());
        if (placeDataList.isEmpty()) {
            GcoreWebTrapDiag.log("放置点", "★全部不可放 → 放弃");
            return;
        }

        Vec3d targetPrevPos = GcoreEntityUtilKt.getPrevPos(target, 1.0f);
        placeDataList.sort((a, b) -> Double.compare(
            a.getTargetPos().toCenterPos().distanceTo(targetPrevPos),
            b.getTargetPos().toCenterPos().distanceTo(targetPrevPos)));

        Vec3d tpVec = GcoreTrapUtil.getTpVecFromPlaceDataList(
            placeDataList, this.scanBorder.get(), this.placeRange.get());
        if (tpVec == null) {
            GcoreWebTrapDiag.logf("站位", "★找不到落脚点(搜索半径=%d, 放置距离=%.1f) → 放弃",
                this.scanBorder.get(), this.placeRange.get());
            return;
        }
        GcoreWebTrapDiag.logf("站位", "OK tpVec=[%.2f,%.2f,%.2f]", tpVec.x, tpVec.y, tpVec.z);

        Map<BlockPos, BlockState> extraPosMap = new LinkedHashMap<>();
        for (GcorePlaceData data : placeDataList) {
            extraPosMap.put(data.getTargetPos(), Blocks.OBSIDIAN.getDefaultState());
        }

        Vec3d playerPos = GcoreEntityUtilKt.getPrevPos(player, 1.0f);
        Item placeItem = this.useAnchorPlace.get() ? Items.RESPAWN_ANCHOR : Items.OBSIDIAN;
        int amount = this.placeAmount.get();

        GcoreWebTrapDiag.logf("传送", "开始 doTpMove 起点=[%.1f,%.1f,%.1f] → 落点=[%.1f,%.1f,%.1f] 距离=%.1f",
            playerPos.x, playerPos.y, playerPos.z, tpVec.x, tpVec.y, tpVec.z,
            playerPos.distanceTo(tpVec));

        executeOnMainThread(() -> GcoreTpUtil.doTpMove(this.tpOption, playerPos, tpVec, extraPosMap,
            (counter, curVec) -> GcoreNewItemUtil.swapUse(this.swapItemOption, placeItem, () -> {
                GcoreWebTrapDiag.log("执行", "到位，开始放方块");
                int n = Math.min(amount, placeDataList.size());
                for (int i = 0; i < n; i++) {
                    GcorePlaceData data = placeDataList.get(i);
                    GcorePlayerActionUtil.doPlaceAt(Hand.MAIN_HAND,
                        data.getPlacePos(), data.getPlaceFacing());
                }
                if (this.swingHand.get() && this.mc.player != null) {
                    this.mc.player.swingHand(Hand.MAIN_HAND);
                }
            })));
    }

    private void doNormalTrap(Entity target) {
        var player = this.mc.player;
        if (player == null || this.mc.world == null) {
            return;
        }

        List<BlockPos> trapPosList = GcoreTrapUtil.getGTrapPosFromList(
            GcoreTrapUtil.getEntitySurroundPos(target, true, true),
            (pos, dir) -> true);
        if (trapPosList.isEmpty()) {
            return;
        }

        List<GcorePlaceData> placeDataList = GcoreTrapUtil.getPlaceDataFromPosList(
            this.version.get(),
            trapPosList,
            new ArrayList<>(),
            player.getEntityPos(),
            player.getEyeHeight(player.getPose()),
            this.scanBorder.get(),
            this.placeRange.get());
        if (placeDataList.isEmpty()) {
            return;
        }

        Vec3d targetPrevPos = GcoreEntityUtilKt.getPrevPos(target, 1.0f);
        placeDataList.sort((a, b) -> Double.compare(
            a.getTargetPos().toCenterPos().distanceTo(targetPrevPos),
            b.getTargetPos().toCenterPos().distanceTo(targetPrevPos)));

        Item placeItem = this.useAnchorPlace.get() ? Items.RESPAWN_ANCHOR : Items.OBSIDIAN;
        int amount = this.placeAmount.get();

        executeOnMainThread(() -> GcoreNewItemUtil.swapUse(this.swapItemOption, placeItem, () -> {
            int n = Math.min(amount, placeDataList.size());
            for (int i = 0; i < n; i++) {
                GcorePlaceData data = placeDataList.get(i);
                GcorePlayerActionUtil.doPlaceAt(Hand.MAIN_HAND,
                    data.getPlacePos(), data.getPlaceFacing());
            }
            if (this.swingHand.get() && this.mc.player != null) {
                this.mc.player.swingHand(Hand.MAIN_HAND);
            }
        }));
    }

    private void executeOnMainThread(Runnable body) {
        GcoreMainThread.runAndWait(() -> {
            try (var ignored = GcoreServerEquivMove.enter(MODULE_NAME)) {
                body.run();
            }
        });
    }
}
