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
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.LinkedHashMap;
import java.util.Map;
  //此模块参考了gcore的tp锚
public class TpAnchorAura extends Module {

    /** 保护方块类型枚举）。 */
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

    // ─────────────────────── 配置 ───────────────────────

    private final SettingGroup sgTarget = this.settings.createGroup("目标");
    private final SettingGroup sgGeneral = this.settings.createGroup("通用");
    private final SettingGroup sgPlace = this.settings.createGroup("放置");
    private final SettingGroup sgDamage = this.settings.createGroup("伤害");
    private final SettingGroup sgOther = this.settings.createGroup("其它");

    private final GcoreTpSelectTargetsConfig targetSelectOption;
    private final GcoreSwapItemOption swapItemOption;
    private final GcoreTpOption tpOption;

    /** 方块放置版本。默认MC1_14}。 */
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

    // ─────────────────────── 状态 ───────────────────────

    private final GcoreMSTimer placeTimer = new GcoreMSTimer();

    private Entity target;

    /** 上次放锚位置（渲染用）。 */
    private BlockPos lastAnchorPos;
    /** 上次放保护方块位置（渲染用）。 */
    private BlockPos lastProtectPos;
    /** 上次 TP 落点（渲染用）。 */
    private Vec3d lastVec;

  public TpAnchorAura() {
    super(AddonTemplate.CATEGORY, "如来真掌·生锚打击", """
        你记不记得有一招从天而降的掌法。。让你用重生锚远程打击别人
        开启TP服务器等价模块从而发挥真威力。""");

        this.targetSelectOption = new GcoreTpSelectTargetsConfig(this.sgTarget);
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
            .description("放置距离。上限受服务端交互距离限制（6 格）。")
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
            .description("目标位置回溯系数0 为当前位置。")
            .defaultValue(0.0)
            .range(0.1, 5.0)
            .sliderRange(0.1, 5.0)
            .build());

        this.bvr = this.sgOther.add(new BoolSetting.Builder()
            .name("丢失保留目标")
            .description("目标短暂丢失时保留上一个目标。")
            .defaultValue(true)
            .build());

        this.swingHand = this.sgOther.add(new BoolSetting.Builder()
            .name("挥手")
            .description("放置时挥手。")
            .defaultValue(true)
            .build());

        this.render = this.sgOther.add(new BoolSetting.Builder()
            .name("渲染")
            .description("渲染锚位 / 保护方块 / TP 落点。")
            .defaultValue(true)
            .build());
    }

    // ─────────────────────── 生命周期 ───────────────────────

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

    // ─────────────────────── 主循环 ───────────────────────

    @EventHandler
    private void onTick(TickEvent.Post event) {
        updateTarget();
        if (this.target != null) {
            doAura();
        }
    }
    /**
     * 刷新目标。
     */
    private void updateTarget() {
        Entity entity = GcoreTargetUtil.getTarget(this.targetSelectOption);
        if (entity != null || !this.bvr.get()) {
            this.target = entity;
        }
    }

    private static final String MODULE_NAME = "TpAnchorAura";

    /**
     * 开虚拟线程跑 Aura。
     * 一次 TP 要跑成千上万次碰撞采样，在主线程算会掉帧。
     */
    private void doAura() {
        Entity t = this.target;
        Thread.ofVirtual().name("gcore-doAura").start(() -> {
            try (var ignored = GcoreServerEquivMove.enter(MODULE_NAME)) {
                doPlaceAura(t);
            } catch (Throwable ignored) {
                // 虚拟线程里的异常不能影响主线程
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

    /**
     * 核心：找方案 → 瞬移 → 放锚 → 回来。
     */
    private void doPlaceAura(Entity t) {
        if (!this.placeTimer.hasPassTime(this.placeDelay.get())) {
            return;
        }
        this.placeTimer.reset();

        var player = this.mc.player;
        if (player == null || this.mc.world == null) {
            return;
        }

        if (t.distanceTo(player) > this.targetSelectOption.getRange()) {
            return;
        }

        // ── 物品检查
        //   仍是只读，放虚拟线程没问题；重活在下面的「找方案」。
        if (GcoreNewItemUtil.findItem(this.swapItemOption, Items.RESPAWN_ANCHOR) == -1) {
            return;
        }

        int glowStoneSlot = GcoreNewItemUtil.findItem(this.swapItemOption, Items.GLOWSTONE);
        if (this.allowClickAfterPlace.get() && glowStoneSlot == -1) {
            return;
        }

        Item protectItem = this.protectBlock.get() == ProtectBlock.GlowStone
            ? Items.GLOWSTONE : Items.OBSIDIAN;
        boolean allowPlaceProtect = this.allowPlaceProtect.get()
            && GcoreNewItemUtil.findItem(this.swapItemOption, protectItem) != -1;

        // ── 找方案（扫描候选锚位 + 伤害计算）—— 留虚拟线程 ──
        GcoreTpAnchorPlaceData placeResult = GcoreTpAnchorUtil.getTpAnchorDataFinal(
            this.version.get(),
            t,
            GcoreEntityUtilKt.getPrevPos(t, this.prev.get().floatValue()),
            player,
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

        // ── 虚拟方块图：登记「即将放置的方块」──
        Map<BlockPos, BlockState> extraPosMap = new LinkedHashMap<>();
        extraPosMap.put(placeResult.getPlaceData().getAnchorData().getAnchorPos(),
            Blocks.RESPAWN_ANCHOR.getDefaultState());
        if (placeResult.getPlaceData().getPlaceProtect() != null) {
            extraPosMap.put(placeResult.getPlaceData().getPlaceProtect().getProtectPos(),
                Blocks.OBSIDIAN.getDefaultState());
        }

        // ── 执行段（发包 + 切真实背包 + 放锚点火）—— 回主线程 ──
        //   规划留在虚拟线程，只有这一段上主线程。
        Vec3d tpVec = placeResult.getTpVec();
        Vec3d playerPos = prevPos(player);
        executeOnMainThread(() -> {
            this.lastVec = tpVec;
            GcoreTpUtil.doTpMove(this.tpOption, playerPos, tpVec, extraPosMap,
                (counter, curVec) -> doPlaceFromAnchorPlaceData(
                    placeResult.getPlaceData(), tpVec, allowPlaceProtect));
        });
    }

    private void doPlaceFromAnchorPlaceData(com.codigohasta.addon.utils.gcore.GcoreAnchorPlaceData data,
                                            Vec3d placeVec,
                                            boolean allowPlaceProtect) {
        var player = this.mc.player;
        if (player == null) {
            return;
        }

        this.lastAnchorPos = data.getAnchorData().getAnchorPos();

        // A) 放重生锚
        GcoreNewItemUtil.swapUse(this.swapItemOption, Items.RESPAWN_ANCHOR, () -> {
            GcorePlayerActionUtil.doPlaceAt(Hand.MAIN_HAND,
                data.getPlaceData().getPlacePos(),
                data.getPlaceData().getPlaceFacing());
            if (this.swingHand.get()) {
                player.swingHand(Hand.MAIN_HAND);
            }
        });

        // B) 放保护方块
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

        // D) Replace：锚位已有方块时替换
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
