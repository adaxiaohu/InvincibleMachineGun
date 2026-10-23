package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.utils.gcore.GcoreAnchorData;
import com.codigohasta.addon.utils.gcore.GcoreBlockUtil;
import com.codigohasta.addon.utils.gcore.GcoreCrystalUtils;
import com.codigohasta.addon.utils.gcore.GcoreExplosionUtils;
import com.codigohasta.addon.utils.gcore.GcoreMainThread;
import com.codigohasta.addon.utils.gcore.GcoreNewItemUtil;
import com.codigohasta.addon.utils.gcore.GcorePlayerActionUtil;
import com.codigohasta.addon.utils.gcore.GcoreServerEquivMove;
import com.codigohasta.addon.utils.gcore.GcoreSwapItemOption;
import com.codigohasta.addon.utils.gcore.GcoreTpAnchorUtil;
import com.codigohasta.addon.utils.gcore.GcoreTpCrystalUtil;
import com.codigohasta.addon.utils.gcore.GcoreTpOption;
import com.codigohasta.addon.utils.gcore.GcoreTpUtil;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
public class GAntiExplosion extends Module {

    /** 内部 ID：{@link GcoreServerEquivMove} 的注册键；与源模块名一致。 */
    private static final String MODULE_NAME = "GAntiExplosion";


    private final SettingGroup sgCrystal = this.settings.createGroup("反水晶");
    private final SettingGroup sgAnchor = this.settings.createGroup("反锚");
    private final SettingGroup sgGeneral = this.settings.createGroup("通用");

    private final Setting<Boolean> antiCrystalEnabled;
    private final Setting<Integer> crystalScanBorder;
    private final Setting<Double> crystalHitRange;
    private final Setting<Double> crystalThroughWallRange;
    private final Setting<Double> crystalMaxDamageToSelf;

    private final Setting<Boolean> antiAnchorEnabled;
    private final GcoreSwapItemOption anchorSwapItemOption;
    private final Setting<Integer> anchorScanBorder;
    private final Setting<Double> anchorClickRange;
    private final Setting<Double> anchorMaxDamageToSelf;
    private final GcoreTpOption tpOption;
    private final Setting<Boolean> asyncProcess;
    private Entity crystal;

    private final AtomicBoolean busy = new AtomicBoolean(false);

    public GAntiExplosion() {
        super(AddonTemplate.CATEGORY, "如来真掌·凌波微步", """
            爆炸伤害落到身上之前，先一步移开。
            水晶生成、重生锚刚被放下时预判伤害，
            瞬移到安全点先手打爆，再回到原处。
            开启TP服务器等价模块从而发挥真威力。""");

        this.antiCrystalEnabled = this.sgCrystal.add(new BoolSetting.Builder()
            .name("启用")
            .description("开启反水晶。")
            .defaultValue(true)
            .build());

        this.crystalScanBorder = this.sgCrystal.add(new IntSetting.Builder()
            .name("搜索半径")
            .description("找安全站位时在水晶周围搜索的半径。8 表示 17x17x17。")
            .defaultValue(5)
            .range(1, 8)
            .sliderRange(1, 8)
            .build());

        this.crystalHitRange = this.sgCrystal.add(new DoubleSetting.Builder()
            .name("命中距离")
            .description("能直接看到水晶时的最大攻击距离。")
            .defaultValue(6.0)
            .range(0.1, 6.0)
            .sliderRange(0.1, 6.0)
            .build());

        this.crystalThroughWallRange = this.sgCrystal.add(new DoubleSetting.Builder()
            .name("穿墙距离")
            .description("中间隔着方块时的最大攻击距离。")
            .defaultValue(6.0)
            .range(0.1, 6.0)
            .sliderRange(0.1, 6.0)
            .build());

        this.crystalMaxDamageToSelf = this.sgCrystal.add(new DoubleSetting.Builder()
            .name("对己最大伤害")
            .description("预判自己会吃到多少伤害；超过这个值才动手。")
            .defaultValue(0.3)
            .range(0.01, 1.0)
            .sliderRange(0.01, 1.0)
            .build());

        this.antiAnchorEnabled = this.sgAnchor.add(new BoolSetting.Builder()
            .name("启用")
            .description("开启反锚。")
            .defaultValue(true)
            .build());

        this.anchorSwapItemOption = new GcoreSwapItemOption(this.sgAnchor);

        this.anchorScanBorder = this.sgAnchor.add(new IntSetting.Builder()
            .name("搜索半径")
            .description("找安全站位时在锚周围搜索的半径。8 表示 17x17x17。")
            .defaultValue(5)
            .range(1, 8)
            .sliderRange(1, 8)
            .build());

        this.anchorClickRange = this.sgAnchor.add(new DoubleSetting.Builder()
            .name("点击距离")
            .description("能点到锚的最大距离。")
            .defaultValue(6.0)
            .range(0.1, 6.0)
            .sliderRange(0.1, 6.0)
            .build());

        this.anchorMaxDamageToSelf = this.sgAnchor.add(new DoubleSetting.Builder()
            .name("对己最大伤害")
            .description("预判自己会吃到多少伤害；达到这个值才动手。")
            .defaultValue(0.3)
            .range(0.01, 1.0)
            .sliderRange(0.01, 1.0)
            .build());

        this.tpOption = new GcoreTpOption(this.sgGeneral);

        this.asyncProcess = this.sgGeneral.add(new BoolSetting.Builder()
            .name("异步处理")
            .description("把重计算丢到后台线程")
            .defaultValue(true)
            .build());
    }

    @Override
    public void onActivate() {
        this.crystal = (this.mc.world != null)
            ? new EndCrystalEntity(this.mc.world, 0.0, 0.0, 0.0)
            : null;
        this.busy.set(false);
    }

    @Override
    public void onDeactivate() {
        this.crystal = null;
        this.busy.set(false);
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (this.mc.player == null || this.mc.world == null) {
            return;
        }

        // ── 反水晶 ──
        if (event.packet instanceof EntitySpawnS2CPacket packet) {
            if (!this.antiCrystalEnabled.get()) {
                return;
            }
            if (packet.getEntityType() != EntityType.END_CRYSTAL) {
                return;
            }
            Vec3d crystalVec = new Vec3d(packet.getX(), packet.getY(), packet.getZ());
            int entityId = packet.getEntityId();
            dispatch(() -> doAntiCrystal(crystalVec, entityId));
            return;
        }

        // ── 反锚 ──
        if (event.packet instanceof BlockUpdateS2CPacket packet) {
            if (!this.antiAnchorEnabled.get()) {
                return;
            }
            BlockState state = packet.getState();
            if (state.getBlock() != Blocks.RESPAWN_ANCHOR) {
                return;
            }
//试验的只在0充能的时候触发
            if (state.get(RespawnAnchorBlock.CHARGES) != 0) {
                return;
            }
            BlockPos anchorPos = packet.getPos();
            dispatch(() -> doAntiAnchor(anchorPos));
        }
    }

    private void dispatch(Runnable body) {
        if (!this.asyncProcess.get()) {
            body.run();
            return;
        }

        if (!this.busy.compareAndSet(false, true)) {
            return;   
        }

        Thread.ofVirtual().name("gcore-antiexplosion").start(() -> {
            try {
                body.run();
            } catch (Throwable ignored) {
                // 后台线程的异常不能影响游戏
            } finally {
                this.busy.set(false);
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

    // ─────────────────────── ① 反水晶 ───────────────────────

    private void doAntiCrystal(Vec3d crystalVec, int entityId) {
        var player = this.mc.player;
        if (player == null) {
            return;
        }

        // ── 威胁评估 ──
        float damageToSelf = GcoreExplosionUtils.getExpDamage(
            crystalVec, player, GcoreCrystalUtils.getExplosion());
        if (damageToSelf <= this.crystalMaxDamageToSelf.get()) {
            return;
        }

        // ── 找安全站位 ──
        Vec3d safeVec = GcoreTpCrystalUtil.getSafePosToAttackCrystal(
            crystalVec,
            this.crystalScanBorder.get(),
            this.crystalHitRange.get(),
            this.crystalThroughWallRange.get());

        if (safeVec == null) {
            return;
        }

        // ── 执行段 ──
        Vec3d fromVec = player.getEntityPos();
        Map<BlockPos, BlockState> extraPosMap = GcoreBlockUtil.getBufferMap();

        executeOnMainThread(() -> {
            if (this.crystal == null && this.mc.world != null) {
                this.crystal = new EndCrystalEntity(this.mc.world, 0.0, 0.0, 0.0);
            }
            Entity dummy = this.crystal;
            if (dummy == null) {
                return;
            }
            dummy.setId(entityId);
            dummy.setPos(crystalVec.x, crystalVec.y, crystalVec.z);

            GcoreTpUtil.doTpMove(this.tpOption, fromVec, safeVec, extraPosMap,
                (counter, curVec) -> GcorePlayerActionUtil.doAttackEntity(dummy));
        });
    }
    private void doAntiAnchor(BlockPos anchorPos) {
        var player = this.mc.player;
        var world = this.mc.world;
        if (player == null || world == null) {
            return;
        }
        if (GcoreNewItemUtil.findItem(this.anchorSwapItemOption, Items.GLOWSTONE) == -1) {
            return;
        }
        Function<BlockPos, BlockState> provider = pos ->
            pos.equals(anchorPos) ? Blocks.AIR.getDefaultState() : world.getBlockState(pos);

        float damageToSelf = GcoreExplosionUtils.getExpDamage(
            anchorPos.toCenterPos(), player, player.getEntityPos(), provider,
            GcoreCrystalUtils.getExplosion());
        if (damageToSelf < this.anchorMaxDamageToSelf.get()) {
            return;
        }

        // ── 找安全站位 ──
        GcoreAnchorData anchorData = GcoreTpAnchorUtil.getSafePosToIgniteAnchor(
            anchorPos, player,
            this.anchorScanBorder.get(),
            this.anchorClickRange.get());

        if (anchorData == null) {
            return;
        }

        Vec3d fromVec = player.getEntityPos();
        Vec3d toVec = anchorData.getExpVec();
        Map<BlockPos, BlockState> extraPosMap = GcoreBlockUtil.getBufferMap();

        executeOnMainThread(() -> GcoreTpUtil.doTpMove(this.tpOption, fromVec, toVec, extraPosMap,
            (counter, curVec) -> {
                // ── 第一步：放萤石（给锚充能）──
                GcoreNewItemUtil.swapUse(this.anchorSwapItemOption, Items.GLOWSTONE, () ->
                    GcorePlayerActionUtil.doPlaceAt(Hand.MAIN_HAND, anchorPos, Direction.DOWN));

                // ── 第二步：换任意「非萤石」物品（空手也算）再点一次 → 引爆 
                GcoreNewItemUtil.swapUse(this.anchorSwapItemOption,
                    i -> GcoreNewItemUtil.realStack(i).getItem() != Items.GLOWSTONE, () ->
                        GcorePlayerActionUtil.doPlaceAt(Hand.MAIN_HAND, anchorPos, Direction.DOWN));
            }));
    }
}
