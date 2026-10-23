package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.utils.gcore.GcoreBlockUtil;
import com.codigohasta.addon.utils.gcore.GcoreEntityUtilKt;
import com.codigohasta.addon.utils.gcore.GcoreEnumType;
import com.codigohasta.addon.utils.gcore.GcoreMainThread;
import com.codigohasta.addon.utils.gcore.GcoreMathUtilsKt;
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
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GcoreWebAura extends Module {

    static final String MODULE_NAME = "GcoreWebAura";

    private final SettingGroup sgTarget = this.settings.createGroup("目标");
    private final SettingGroup sgTargetTp = this.settings.createGroup("传送目标");
    private final SettingGroup sgGeneral = this.settings.createGroup("通用");
    private final SettingGroup sgPlace = this.settings.createGroup("放置");

    private final GcoreTpSelectTargetsConfig targetOption;
    private final GcoreTpSelectTargetsConfig targetTpOption;
    private final GcoreSwapItemOption swapItemOption;
    private final GcoreTpOption tpOption;

    private final Setting<GcoreEnumType.BlockPlaceVersion> version;
    private final Setting<Integer> placeDelay;
    private final Setting<Integer> scanBorder;
    private final Setting<Double> placeRange;
    private final Setting<Integer> webAmount;
    private final Setting<Integer> obsidianAmount;
    private final Setting<Boolean> useAnchor;
    private final Setting<Boolean> useTp;
    private final Setting<Boolean> useGTrap;
    private final Setting<Boolean> swingHand;

    private final Setting<Double> prev;

    private final Setting<Boolean> logStages;

    private final GcoreMSTimer placeTimer = new GcoreMSTimer();

    public GcoreWebAura() {
        super(AddonTemplate.CATEGORY, "天罗地网",
            "瞬移到目标身边用蜘蛛网困住，并在上下铺黑曜石封顶封底。 TP 服务端等价。");

        this.targetOption = new GcoreTpSelectTargetsConfig(this.sgTarget, 8.0, 500.0, true, true);
        this.targetTpOption = new GcoreTpSelectTargetsConfig(this.sgTargetTp, 500.0, 500.0, false, false);
        this.swapItemOption = new GcoreSwapItemOption(this.sgGeneral);
        this.tpOption = new GcoreTpOption(this.sgGeneral);

        this.version = this.sgPlace.add(new EnumSetting.Builder<GcoreEnumType.BlockPlaceVersion>()
            .name("放置版本")
            .description("方块放置的版本行为。MC1_14 可直接朝下放，MC1_12 需要相邻不透明方块。")
            .defaultValue(GcoreEnumType.BlockPlaceVersion.MC1_14)
            .build());

        this.placeDelay = this.sgPlace.add(new IntSetting.Builder()
            .name("放置间隔")
            .description("两次放置之间的间隔（毫秒）。")
            .defaultValue(50)
            .range(1, 2000)
            .sliderRange(1, 2000)
            .build());

        this.scanBorder = this.sgPlace.add(new IntSetting.Builder()
            .name("搜索半径")
            .description("搜索半径（格）。6 即 13³ 网格。")
            .defaultValue(6)
            .range(1, 8)
            .sliderRange(1, 8)
            .build());

        this.placeRange = this.sgPlace.add(new DoubleSetting.Builder()
            .name("放置距离")
            .description("放置距离。上限受服务端交互距离限制（6 格）。")
            .defaultValue(5.5)
            .range(0.1, 6.0)
            .sliderRange(0.1, 6.0)
            .build());

        this.webAmount = this.sgPlace.add(new IntSetting.Builder()
            .name("蛛网数量")
            .description("一次放几个蜘蛛网。")
            .defaultValue(1)
            .range(1, 5)
            .sliderRange(1, 5)
            .build());

        this.obsidianAmount = this.sgPlace.add(new IntSetting.Builder()
            .name("黑曜石数量")
            .description("一次放几个封顶/封底方块。0 表示不放。")
            .defaultValue(1)
            .range(0, 5)
            .sliderRange(0, 5)
            .build());

        this.useAnchor = this.sgPlace.add(new BoolSetting.Builder()
            .name("用重生锚")
            .description("封顶封底用重生锚代替黑曜石。")
            .defaultValue(true)
            .build());

        this.useTp = this.sgGeneral.add(new BoolSetting.Builder()
            .name("传送版")
            .description("先 VClip 传到目标身边再放，放完传回来。关闭则就地放。")
            .defaultValue(true)
            .build());

        this.useGTrap = this.sgGeneral.add(new BoolSetting.Builder()
            .name("用困阵")
            .description("目标已经被方块卡住时，改叫「困阵」模块收尾。")
            .defaultValue(true)
            .build());

        this.swingHand = this.sgGeneral.add(new BoolSetting.Builder()
            .name("挥手")
            .description("放置时挥手。")
            .defaultValue(true)
            .build());

        this.prev = this.sgGeneral.add(new DoubleSetting.Builder()
            .name("位置回溯")
            .description("目标位置回溯系数（抵消网络延迟）。0 为当前位置。")
            .defaultValue(0.0)
            .range(0.1, 10.0)
            .sliderRange(0.1, 10.0)
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
            return;
        }

        Entity t = target;
        boolean useTrap = this.useGTrap.get() && checkIsColliedWeb(target.getBoundingBox());
        GcoreGTrap trap = useTrap ? getGTrapModule() : null;
        boolean delegateToTrap = trap != null && trap.isActive();

        Thread.ofVirtual().name("gcore-webaura").start(() -> {
            try (var ignored = GcoreServerEquivMove.enter(MODULE_NAME)) {
                if (delegateToTrap) {
                    trap.doTrapFromOther(t);
                    return;
                }
                if (this.useTp.get()) {
                    doTpWebAura(t);
                } else {
                    doWebAura(t);
                }
            } catch (Throwable ignored) {
            }
        });
    }

    private GcoreGTrap getGTrapModule() {
        return Modules.get().get(GcoreGTrap.class);
    }

    private boolean checkIsColliedWeb(Box box) {
        if (this.mc.world == null) {
            return false;
        }
        final boolean[] collied = {true};
        GcoreBlockUtil.forEachCollidesPos(box, pos -> {
            BlockState state = this.mc.world.getBlockState(pos);
            if (!state.isOpaque() && !state.isOf(Blocks.COBWEB)) {
                collied[0] = false;
            }
        });
        return collied[0];
    }

    private List<BlockPos> getAndSortWebBlock(Box entityBox) {
        List<BlockPos> colliedPos = GcoreTrapUtil.getBoxColliePosList(entityBox);
        if (this.mc.world == null) {
            return new ArrayList<>();
        }

        List<BlockPos> filtered = new ArrayList<>();
        for (BlockPos pos : colliedPos) {
            if (GcoreBlockUtil.canReplaceable(pos)) {
                filtered.add(pos);
            }
        }

        filtered.sort((a, b) -> Double.compare(
            -intersectVolume(a, entityBox),
            -intersectVolume(b, entityBox)));

        List<BlockPos> result = new ArrayList<>();
        int n = Math.min(this.webAmount.get(), filtered.size());
        for (int i = 0; i < n; i++) {
            result.add(filtered.get(i));
        }
        return result;
    }

    private static double intersectVolume(BlockPos pos, Box box) {
        Box inter = new Box(
            pos.getX(), pos.getY(), pos.getZ(),
            pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0).intersection(box);
        return (inter.maxX - inter.minX) * (inter.maxY - inter.minY) * (inter.maxZ - inter.minZ);
    }

    private List<BlockPos> getObsidianPos(Box entityBox, List<BlockPos> webPosList) {
        List<BlockPos> list = new ArrayList<>();
        if (this.mc.world == null) {
            return list;
        }

        int top = (int) Math.floor(entityBox.maxY) + 1;
        int bottom = (int) Math.floor(entityBox.minY) - 1;
        List<Entity> entities = GcoreTickUpdate.INSTANCE.getWorldEntitiesList();
        int amount = 0;

        for (BlockPos it : webPosList) {
            if (amount >= this.obsidianAmount.get()) {
                break;
            }
            BlockPos topPos = new BlockPos(it.getX(), top, it.getZ());
            if (GcoreBlockUtil.canPlaceAtPos(topPos, entities) && !list.contains(topPos)) {
                list.add(topPos);
                amount++;
            }
            if (amount >= this.obsidianAmount.get()) {
                break;
            }
            BlockPos bottomPos = new BlockPos(it.getX(), bottom, it.getZ());
            if (GcoreBlockUtil.canPlaceAtPos(bottomPos, entities) && !list.contains(bottomPos)) {
                list.add(bottomPos);
                amount++;
            }
        }
        return list;
    }

    private void doTpWebAura(Entity target) {
        if (!this.placeTimer.hasPassTime(this.placeDelay.get())) {
            return;
        }
        this.placeTimer.reset();

        var player = this.mc.player;
        if (player == null || this.mc.world == null) {
            return;
        }

        if (GcoreNewItemUtil.findItem(this.swapItemOption, Items.COBWEB) == -1) {
            GcoreWebTrapDiag.log("物品", "★蜘蛛网缺失 → 放弃");
            return;
        }
        Item obsItem = this.useAnchor.get() ? Items.RESPAWN_ANCHOR : Items.OBSIDIAN;
        if (this.obsidianAmount.get() > 0
            && GcoreNewItemUtil.findItem(this.swapItemOption, obsItem) == -1) {
            GcoreWebTrapDiag.logf("物品", "★封顶材料缺失(%s) → 放弃", obsItem.getName().getString());
            return;
        }
        GcoreWebTrapDiag.log("物品", "OK");

        Box targetBox = GcoreMathUtilsKt.position(
            target.getBoundingBox(),
            GcoreEntityUtilKt.getPrevPos(target, this.prev.get().floatValue()));

        List<BlockPos> webPosList = getAndSortWebBlock(targetBox);
        List<BlockPos> obsidianPosList = getObsidianPos(targetBox, webPosList);

        List<GcorePlaceData> placeWebList = GcoreTrapUtil.getPlaceDataFromPosList(
            this.version.get(), webPosList, new ArrayList<>(),
            player.getEntityPos(), player.getEyeHeight(player.getPose()),
            this.scanBorder.get(), 999.0);
        List<GcorePlaceData> placeObsList = GcoreTrapUtil.getPlaceDataFromPosList(
            this.version.get(), obsidianPosList, webPosList,
            player.getEntityPos(), player.getEyeHeight(player.getPose()),
            this.scanBorder.get(), 999.0);

        GcoreWebTrapDiag.logf("放置点",
            "蜘蛛网格=%d → 可放网=%d ; 封顶底格=%d → 可放=%d",
            webPosList.size(), placeWebList.size(), obsidianPosList.size(), placeObsList.size());

        if (placeWebList.isEmpty() && placeObsList.isEmpty()) {
            GcoreWebTrapDiag.log("放置点", "★全部不可放 → 放弃");
            return;
        }

        List<GcorePlaceData> all = new ArrayList<>(placeObsList);
        all.addAll(placeWebList);
        Vec3d tpVec = GcoreTrapUtil.getTpVecFromPlaceDataList(
            all, this.scanBorder.get(), this.placeRange.get());
        if (tpVec == null) {
            GcoreWebTrapDiag.logf("站位", "★找不到落脚点(搜索半径=%d, 放置距离=%.1f) → 放弃",
                this.scanBorder.get(), this.placeRange.get());
            return;
        }
        GcoreWebTrapDiag.logf("站位", "OK tpVec=[%.2f,%.2f,%.2f]", tpVec.x, tpVec.y, tpVec.z);

        Map<BlockPos, BlockState> extraPosMap = new LinkedHashMap<>();
        for (BlockPos pos : webPosList) {
            extraPosMap.put(pos, Blocks.OBSIDIAN.getDefaultState());
        }
        for (BlockPos pos : obsidianPosList) {
            extraPosMap.put(pos, Blocks.OBSIDIAN.getDefaultState());
        }

        Vec3d playerPos = new Vec3d(player.lastX, player.lastY, player.lastZ);
        boolean placeObs = this.obsidianAmount.get() > 0;
        Item finalObsItem = obsItem;

        GcoreWebTrapDiag.logf("传送", "开始 doTpMove 起点=[%.1f,%.1f,%.1f] → 落点=[%.1f,%.1f,%.1f] 距离=%.1f",
            playerPos.x, playerPos.y, playerPos.z, tpVec.x, tpVec.y, tpVec.z,
            playerPos.distanceTo(tpVec));

        executeOnMainThread(() -> GcoreTpUtil.doTpMove(this.tpOption, playerPos, tpVec, extraPosMap,
            (counter, curVec) -> {
                GcoreWebTrapDiag.log("执行", "到位，开始放方块");
                GcoreNewItemUtil.swapUse(this.swapItemOption, Items.COBWEB, () -> {
                    for (GcorePlaceData data : placeWebList) {
                        GcorePlayerActionUtil.doPlaceAt(Hand.MAIN_HAND,
                            data.getPlacePos(), data.getPlaceFacing());
                    }
                });
                if (placeObs) {
                    GcoreNewItemUtil.swapUse(this.swapItemOption, finalObsItem, () -> {
                        for (GcorePlaceData data : placeObsList) {
                            GcorePlayerActionUtil.doPlaceAt(Hand.MAIN_HAND,
                                data.getPlacePos(), data.getPlaceFacing());
                        }
                    });
                }
                if (this.swingHand.get() && this.mc.player != null) {
                    this.mc.player.swingHand(Hand.MAIN_HAND);
                }
            }));
    }

    private void doWebAura(Entity target) {
        if (!this.placeTimer.hasPassTime(this.placeDelay.get())) {
            return;
        }
        this.placeTimer.reset();

        var player = this.mc.player;
        if (player == null || this.mc.world == null) {
            return;
        }

        if (GcoreNewItemUtil.findItem(this.swapItemOption, Items.COBWEB) == -1) {
            return;
        }
        Item obsItem = this.useAnchor.get() ? Items.RESPAWN_ANCHOR : Items.OBSIDIAN;
        if (this.obsidianAmount.get() > 0
            && GcoreNewItemUtil.findItem(this.swapItemOption, obsItem) == -1) {
            return;
        }

        Box targetBox = GcoreMathUtilsKt.position(
            target.getBoundingBox(),
            GcoreEntityUtilKt.getPrevPos(target, this.prev.get().floatValue()));

        List<BlockPos> webPosList = getAndSortWebBlock(targetBox);
        List<BlockPos> obsidianPosList = getObsidianPos(targetBox, webPosList);

        List<GcorePlaceData> placeWebList = GcoreTrapUtil.getPlaceDataFromPosList(
            this.version.get(), webPosList, new ArrayList<>(),
            player.getEntityPos(), player.getEyeHeight(player.getPose()),
            this.scanBorder.get(), this.placeRange.get());
        List<GcorePlaceData> placeObsList = GcoreTrapUtil.getPlaceDataFromPosList(
            this.version.get(), obsidianPosList, webPosList,
            player.getEntityPos(), player.getEyeHeight(player.getPose()),
            this.scanBorder.get(), this.placeRange.get());

        if (placeWebList.isEmpty() && placeObsList.isEmpty()) {
            return;
        }

        boolean placeObs = this.obsidianAmount.get() > 0;
        Item finalObsItem = obsItem;

        executeOnMainThread(() -> {
            GcoreNewItemUtil.swapUse(this.swapItemOption, Items.COBWEB, () -> {
                for (GcorePlaceData data : placeWebList) {
                    GcorePlayerActionUtil.doPlaceAt(Hand.MAIN_HAND,
                        data.getPlacePos(), data.getPlaceFacing());
                }
            });
            if (placeObs) {
                GcoreNewItemUtil.swapUse(this.swapItemOption, finalObsItem, () -> {
                    for (GcorePlaceData data : placeObsList) {
                        GcorePlayerActionUtil.doPlaceAt(Hand.MAIN_HAND,
                            data.getPlacePos(), data.getPlaceFacing());
                    }
                });
            }
            if (this.swingHand.get() && this.mc.player != null) {
                this.mc.player.swingHand(Hand.MAIN_HAND);
            }
        });
    }

    private void executeOnMainThread(Runnable body) {
        GcoreMainThread.runAndWait(() -> {
            try (var ignored = GcoreServerEquivMove.enter(MODULE_NAME)) {
                body.run();
            }
        });
    }
}
