package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.utils.gcore.GcoreAnchorUtils;
import com.codigohasta.addon.utils.gcore.GcoreBlockUtil;
import com.codigohasta.addon.utils.gcore.GcoreEnumType;
import com.codigohasta.addon.utils.gcore.GcoreEntityUtilKt;
import com.codigohasta.addon.utils.gcore.GcoreMainThread;
import com.codigohasta.addon.utils.gcore.GcoreMSTimer;
import com.codigohasta.addon.utils.gcore.GcoreNewItemUtil;
import com.codigohasta.addon.utils.gcore.GcorePlaceData;
import com.codigohasta.addon.utils.gcore.GcorePlayerActionUtil;
import com.codigohasta.addon.utils.gcore.GcoreSwapItemOption;
import com.codigohasta.addon.utils.gcore.GcoreTargetUtil;
import com.codigohasta.addon.utils.gcore.GcoreTickUpdate;
import com.codigohasta.addon.utils.gcore.GcoreTpAnchorPlaceData;
import com.codigohasta.addon.utils.gcore.GcoreTpAnchorUtil;
import com.codigohasta.addon.utils.gcore.GcoreTpOption;
import com.codigohasta.addon.utils.gcore.GcoreTpSelectTargetsConfig;
import com.codigohasta.addon.utils.gcore.GcoreTpUtil;
import com.codigohasta.addon.utils.gcore.GcoreServerEquivMove;
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
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class GLightTpAnchorAura extends Module {

    public enum ProtectBlock {
        GlowStone("萤石"),
        Obsidian("黑曜石");

        private final String choiceName;

        ProtectBlock(String choiceName) {
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

    private final SettingGroup sgTarget = this.settings.createGroup("目标");
    private final SettingGroup sgGeneral = this.settings.createGroup("通用");
    private final SettingGroup sgPlace = this.settings.createGroup("放置");
    private final SettingGroup sgDamage = this.settings.createGroup("伤害");
    private final SettingGroup sgOther = this.settings.createGroup("其它");

    private final GcoreTpSelectTargetsConfig targetSelectOption;
    private final GcoreSwapItemOption swapItemOption;
    private final GcoreTpOption tpOption;

    private final Setting<GcoreEnumType.BlockPlaceVersion> version;

    private final Setting<Integer> placeDelay;
    private final Setting<Integer> scanBorder;
    private final Setting<Double> placeRange;
    private final Setting<Boolean> allowClickAfterPlace;
    private final Setting<Boolean> allowPlaceProtect;
    private final Setting<Boolean> onlyPlaceProtect;
    private final Setting<Boolean> replace;
    private final Setting<ProtectBlock> protectBlock;

    private final Setting<Double> minDamageToTarget;
    private final Setting<Double> maxDamageToSelf;

    private final Setting<Double> prev;
    private final Setting<Boolean> bvr;
    private final Setting<Boolean> swingHand;
    private final Setting<Boolean> render;

    private final GcoreMSTimer placeTimer = new GcoreMSTimer();

    private Entity target;

    private BlockPos lastAnchorPos;
    private BlockPos lastProtectPos;
    private Vec3d lastVec;

    public GLightTpAnchorAura() {
        super(AddonTemplate.CATEGORY, "轻量·生锚打击",
            "TP锚Light版，合法通路版本");

        this.targetSelectOption = new GcoreTpSelectTargetsConfig(this.sgTarget);
        this.swapItemOption = new GcoreSwapItemOption(this.sgGeneral);
        this.tpOption = new GcoreTpOption(this.sgGeneral);

        this.version = this.sgPlace.add(new EnumSetting.Builder<GcoreEnumType.BlockPlaceVersion>()
            .name("放置版本")
            .description("方块放置的版本行为。")
            .defaultValue(GcoreEnumType.BlockPlaceVersion.MC1_14)
            .build());

        this.placeDelay = this.sgPlace.add(new IntSetting.Builder()
            .name("放置间隔")
            .description("两次放置之间的间隔（毫秒）。")
            .defaultValue(200)
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
            .description("放置距离。")
            .defaultValue(5.5)
            .range(0.1, 6.0)
            .sliderRange(0.1, 6.0)
            .build());

        this.allowClickAfterPlace = this.sgPlace.add(new BoolSetting.Builder()
            .name("放完点火")
            .description("放完锚后立刻用萤石点火。")
            .defaultValue(true)
            .build());

        this.allowPlaceProtect = this.sgPlace.add(new BoolSetting.Builder()
            .name("放保护方块")
            .description("在锚旁边放保护方块（减少自伤）。")
            .defaultValue(true)
            .build());

        this.onlyPlaceProtect = this.sgPlace.add(new BoolSetting.Builder()
            .name("只放保护方块")
            .description("只放保护方块，不引爆。")
            .defaultValue(false)
            .build());

        this.replace = this.sgPlace.add(new BoolSetting.Builder()
            .name("替换已有方块")
            .description("锚位已有方块时替换掉。")
            .defaultValue(true)
            .build());

        this.protectBlock = this.sgPlace.add(new EnumSetting.Builder<ProtectBlock>()
            .name("保护方块类型")
            .description("保护方块用萤石还是黑曜石。")
            .defaultValue(ProtectBlock.GlowStone)
            .build());

        this.minDamageToTarget = this.sgDamage.add(new DoubleSetting.Builder()
            .name("对目标最小伤害")
            .description("对目标的最小伤害（占其血量的比例）。")
            .defaultValue(0.3)
            .range(0.01, 1.0)
            .sliderRange(0.01, 1.0)
            .build());

        this.maxDamageToSelf = this.sgDamage.add(new DoubleSetting.Builder()
            .name("对自己最大伤害")
            .description("自己能承受的最大伤害（占自己血量的比例）。")
            .defaultValue(0.3)
            .range(0.01, 1.0)
            .sliderRange(0.01, 1.0)
            .build());

        this.prev = this.sgOther.add(new DoubleSetting.Builder()
            .name("位置回溯")
            .description("目标位置回溯系数（抵消网络延迟）。")
            .defaultValue(0.0)
            .range(0.1, 5.0)
            .sliderRange(0.1, 5.0)
            .build());

        this.bvr = this.sgOther.add(new BoolSetting.Builder()
            .name("丢失保留目标")
            .description("目标短暂丢失时保留上一个目标（不丢锁）。")
            .defaultValue(true)
            .build());

        this.swingHand = this.sgOther.add(new BoolSetting.Builder()
            .name("挥手")
            .description("放置时挥手。")
            .defaultValue(true)
            .build());

        this.render = this.sgOther.add(new BoolSetting.Builder()
            .name("渲染")
            .description("渲染锚位 / 保护方块 / TP 落点的盒子。")
            .defaultValue(true)
            .build());
    }

    @Override
    public void onActivate() {
        GcoreTickUpdate.INSTANCE.register();
        this.target = null;
    }

    @Override
    public void onDeactivate() {
        GcoreTickUpdate.INSTANCE.unregister();
        this.target = null;
        this.lastAnchorPos = null;
        this.lastProtectPos = null;
        this.lastVec = null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        Thread.ofVirtual().name("gcore-light-doAura").start(() -> {
            try (var ignored = GcoreServerEquivMove.enter("GLightTpAnchorAura")) {
                updateTarget();
                if (this.target != null) {
                    doPlaceAura();
                }
            } catch (Throwable ignored) {
            }
        });
    }

    private void updateTarget() {
        Entity entity = GcoreTargetUtil.getTarget(this.targetSelectOption);
        if (entity != null || !this.bvr.get()) {
            this.target = entity;
        }
    }

    private void doPlaceAura() {
        if (!this.placeTimer.hasPassTime(this.placeDelay.get())) {
            return;
        }
        this.placeTimer.reset();

        var player = this.mc.player;
        if (player == null || this.mc.world == null) {
            return;
        }

        Entity t = this.target;
        if (t == null) {
            return;
        }

        if (t.distanceTo(player) > this.targetSelectOption.getRange()) {
            return;
        }

        if (GcoreNewItemUtil.findItem(this.swapItemOption, Items.RESPAWN_ANCHOR) == -1) {
            return;
        }

        boolean allowProtect;
        if (this.allowPlaceProtect.get()) {
            Item protectItem = this.protectBlock.get() == ProtectBlock.GlowStone
                ? Items.GLOWSTONE : Items.OBSIDIAN;
            allowProtect = GcoreNewItemUtil.findItem(this.swapItemOption, protectItem) != -1;
        } else {
            allowProtect = false;
        }
        final boolean allowPlaceProtect = allowProtect;

        if (this.allowClickAfterPlace.get()
            && GcoreNewItemUtil.findItem(this.swapItemOption, Items.GLOWSTONE) == -1) {
            return;
        }

        GcoreTpAnchorPlaceData placeResult = GcoreTpAnchorUtil.getLightTpAnchorDataFinal(
            this.version.get(),
            t,
            GcoreEntityUtilKt.getPrevPos(t, this.prev.get().floatValue()),
            player,
            player.getEntityPos(),
            this.scanBorder.get(),
            this.placeRange.get(),
            this.minDamageToTarget.get().floatValue(),
            this.maxDamageToSelf.get().floatValue(),
            true,
            this.allowClickAfterPlace.get(),
            allowPlaceProtect,
            allowPlaceProtect && this.onlyPlaceProtect.get());
        if (placeResult == null) {
            return;
        }

        Vec3d tpVec = placeResult.getTpVec();
        Vec3d playerPos = prevPos(player);
        executeOnMainThread(() -> {
            this.lastVec = tpVec;
            GcoreTpUtil.doTp(playerPos, tpVec, this.tpOption.getMoveDistance(), true);
            doPlaceFromAnchorPlaceData(placeResult.getPlaceData(), tpVec, allowPlaceProtect);
            GcoreTpUtil.sendMovePacket(playerPos, true);
        });
    }

    private void doPlaceFromAnchorPlaceData(com.codigohasta.addon.utils.gcore.GcoreAnchorPlaceData data,
                                            Vec3d placeVec, boolean allowPlaceProtect) {
        var player = this.mc.player;
        if (player == null) {
            return;
        }

        this.lastAnchorPos = data.getAnchorData().getAnchorPos();

        GcoreNewItemUtil.swapUse(this.swapItemOption, Items.RESPAWN_ANCHOR, () -> {
            GcorePlayerActionUtil.doPlaceAt(Hand.MAIN_HAND,
                data.getPlaceData().getPlacePos(),
                data.getPlaceData().getPlaceFacing());
            if (this.swingHand.get()) {
                player.swingHand(Hand.MAIN_HAND);
            }
        });

        if (allowPlaceProtect && data.getPlaceProtect() != null) {
            this.lastProtectPos = data.getPlaceProtect().getPlaceData().getPlacePos();
            Item protectItem = this.protectBlock.get() == ProtectBlock.GlowStone
                ? Items.GLOWSTONE : Items.OBSIDIAN;
            GcoreNewItemUtil.swapUse(this.swapItemOption, protectItem, () -> {
                GcorePlaceData pd = data.getPlaceProtect().getPlaceData();
                GcorePlayerActionUtil.doPlaceAt(Hand.MAIN_HAND, pd.getPlacePos(), pd.getPlaceFacing());
                if (this.swingHand.get()) {
                    player.swingHand(Hand.MAIN_HAND);
                }
            });
        } else {
            this.lastProtectPos = null;
        }

        if (this.allowClickAfterPlace.get()) {
            GcoreNewItemUtil.swapUse(this.swapItemOption, Items.GLOWSTONE, () -> {
                GcorePlayerActionUtil.doPlaceAt(Hand.MAIN_HAND,
                    data.getAnchorData().getAnchorPos(), Direction.DOWN);
                if (this.swingHand.get()) {
                    player.swingHand(Hand.MAIN_HAND);
                }
            });

            GcoreNewItemUtil.swapUse(this.swapItemOption,
                i -> GcoreNewItemUtil.realStack(i).getItem() != Items.GLOWSTONE,
                () -> {
                    GcorePlayerActionUtil.doPlaceAt(Hand.MAIN_HAND,
                        data.getAnchorData().getAnchorPos(), Direction.DOWN);
                    if (this.swingHand.get()) {
                        player.swingHand(Hand.MAIN_HAND);
                    }
                });
        }

        BlockPos anchorPos = data.getAnchorData().getAnchorPos();
        if (this.replace.get()
            && this.mc.world.getBlockState(anchorPos).getBlock() == Blocks.RESPAWN_ANCHOR) {
            GcorePlaceData replaceData = GcoreBlockUtil.getActualPlaceDataToPos(
                this.version.get(), anchorPos, placeVec, 1.62, this.placeRange.get());
            if (replaceData != null) {
                GcoreNewItemUtil.swapUse(this.swapItemOption, Items.RESPAWN_ANCHOR, () -> {
                    GcorePlayerActionUtil.doPlaceAt(Hand.MAIN_HAND,
                        replaceData.getPlacePos(), replaceData.getPlaceFacing());
                    if (this.swingHand.get()) {
                        player.swingHand(Hand.MAIN_HAND);
                    }
                });
            }
        }
    }

    private static final String MODULE_NAME = "GLightTpAnchorAura";

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

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!this.render.get()) {
            return;
        }

        if (this.lastAnchorPos != null) {
            Color c = new Color(255, 100, 100, 200);
            event.renderer.box(
                this.lastAnchorPos.getX(), this.lastAnchorPos.getY(), this.lastAnchorPos.getZ(),
                this.lastAnchorPos.getX() + 1, this.lastAnchorPos.getY() + 1, this.lastAnchorPos.getZ() + 1,
                c, c, ShapeMode.Both, 0);
        }

        if (this.lastProtectPos != null) {
            Color c = new Color(100, 255, 100, 200);
            event.renderer.box(
                this.lastProtectPos.getX(), this.lastProtectPos.getY(), this.lastProtectPos.getZ(),
                this.lastProtectPos.getX() + 1, this.lastProtectPos.getY() + 1, this.lastProtectPos.getZ() + 1,
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
