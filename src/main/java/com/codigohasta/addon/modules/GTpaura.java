package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.utils.gcore.GcoreBlockUtil;
import com.codigohasta.addon.utils.gcore.GcoreEntityUtilKt;
import com.codigohasta.addon.utils.gcore.GcoreMSTimer;
import com.codigohasta.addon.utils.gcore.GcoreNewItemUtil;
import com.codigohasta.addon.utils.gcore.GcorePlayerActionUtil;
import com.codigohasta.addon.utils.gcore.GcoreServerEquivMove;
import com.codigohasta.addon.utils.gcore.GcoreSwapItemOption;
import com.codigohasta.addon.utils.gcore.GcoreTargetUtil;
import com.codigohasta.addon.utils.gcore.GcoreTickUpdate;
import com.codigohasta.addon.utils.gcore.GcoreTpOption;
import com.codigohasta.addon.utils.gcore.GcoreTpSelectTargetsConfig;
import com.codigohasta.addon.utils.gcore.GcoreTpUtil;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Map;

public class GTpaura extends Module {

    
    private static final String MODULE_NAME = "GTpaura";
    private static final double CRIT_OFFSET = 0.001;
    private static final String MACE_NAME = "1.21 Mace";
    private final SettingGroup sgTarget = this.settings.createGroup("目标");
    private final SettingGroup sgGeneral = this.settings.createGroup("通用");
    private final SettingGroup sgOther = this.settings.createGroup("其它");

    private final GcoreTpSelectTargetsConfig selectTargets;
    private final GcoreTpOption tpOption;
    private final GcoreSwapItemOption maceSlotOption;

    
    private final Setting<Integer> attackDelay;
    
    private final Setting<Integer> attackTimes;
    
    private final Setting<Boolean> useCooldown;
    
    private final Setting<Double> useCooldownBaseTime;
    
    private final Setting<Boolean> critical;
    
    private final Setting<Boolean> swingHand;
    
    private final Setting<Boolean> findVecToAttack;
    
    private final Setting<Boolean> bvr;
    private final Setting<Double> prev;
    private final Setting<Boolean> render;
    private final Setting<Boolean> useMace;
    private final GcoreMSTimer attackTimer = new GcoreMSTimer();
    private Entity target;

    public GTpaura() {
        super(AddonTemplate.CATEGORY, "TP光环",
            "瞬移到目标身边连续攻击，并伪造暴击。可选用重锤、带目标渲染框。没修这个不好用");

        this.prev = this.sgGeneral.add(new DoubleSetting.Builder()
            .name("位置回溯")
            .description("目标位置回溯系数，用以抵消网络延迟。0 表示用当前位置。")
            .defaultValue(0.0)
            .range(0.1, 5.0)
            .sliderRange(0.1, 5.0)
            .build());

        this.attackDelay = this.sgGeneral.add(new IntSetting.Builder()
            .name("攻击间隔")
            .description("关闭「用攻击冷却」时的固定攻击间隔，单位毫秒。")
            .defaultValue(800)
            .range(1, 2000)
            .sliderRange(1, 2000)
            .build());

        this.attackTimes = this.sgGeneral.add(new IntSetting.Builder()
            .name("攻击次数")
            .description("一次瞬移打几下。")
            .defaultValue(1)
            .range(1, 200)
            .sliderRange(1, 200)
            .build());

        this.useCooldown = this.sgGeneral.add(new BoolSetting.Builder()
            .name("用攻击冷却")
            .description("按原版攻击冷却判断是否出手；关闭则用固定攻击间隔。")
            .defaultValue(true)
            .build());

        this.useCooldownBaseTime = this.sgGeneral.add(new DoubleSetting.Builder()
            .name("冷却阈值")
            .description("攻击冷却达到这个比例才出手。")
            .defaultValue(0.75)
            .range(0.1, 1.0)
            .sliderRange(0.1, 1.0)
            .build());

        this.critical = this.sgGeneral.add(new BoolSetting.Builder()
            .name("暴击")
            .description("伪造下落状态，让攻击判为暴击。")
            .defaultValue(true)
            .build());

        this.swingHand = this.sgGeneral.add(new BoolSetting.Builder()
            .name("挥手")
            .description("攻击时播放挥手动画。")
            .defaultValue(true)
            .build());

        this.findVecToAttack = this.sgGeneral.add(new BoolSetting.Builder()
            .name("自动找攻击站位")
            .description("目标脚下站不住时，在附近找一个「站得住又打得到」的位置。")
            .defaultValue(true)
            .build());

        this.bvr = this.sgOther.add(new BoolSetting.Builder()
            .name("丢失保留目标")
            .description("目标消失时保留旧目标（继续朝最后已知位置打）。")
            .defaultValue(true)
            .build());

        this.render = this.sgOther.add(new BoolSetting.Builder()
            .name("渲染")
            .description("渲染目标的碰撞箱（半透明红）。")
            .defaultValue(true)
            .build());

        
        this.useMace = this.sgOther.add(new BoolSetting.Builder()
            .name("用重锤")
            .description("改用重锤攻击（按物品显示名 " + MACE_NAME + " 查找）。")
            .defaultValue(false)
            .build());

        this.maceSlotOption = new GcoreSwapItemOption(this.sgOther);

        this.selectTargets = new GcoreTpSelectTargetsConfig(this.sgTarget);
        this.tpOption = new GcoreTpOption(this.sgGeneral);
    }

    @Override
    public void onActivate() {
        GcoreTickUpdate.INSTANCE.register();
        this.target = null;
        this.attackTimer.reset();
    }

    
    private static Vec3d prevPos(Entity entity) {
        return new Vec3d(entity.lastX, entity.lastY, entity.lastZ);
    }

    @Override
    public void onDeactivate() {
        GcoreTickUpdate.INSTANCE.unregister();
        this.target = null;
    }
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (this.mc.player == null || this.mc.world == null) {
            return;
        }

        this.updateTarget();
        this.doAura();
    }
    private void updateTarget() {
        Entity entity = GcoreTargetUtil.getTarget(this.selectTargets);
        if (entity != null || !this.bvr.get()) {
            this.target = entity;
        }
    }
    private boolean isReadyToAttack() {
        var player = this.mc.player;
        if (player == null) {
            return false;
        }
        return this.useCooldown.get()
            ? player.getAttackCooldownProgress(-1.0F) >= this.useCooldownBaseTime.get()
            : this.attackTimer.hasPassTime(this.attackDelay.get());
    }

    private void doAura() {
        var player = this.mc.player;
        if (player == null) {
            return;
        }

        Entity t = this.target;
        if (t == null || !this.isReadyToAttack()) {
            return;
        }

        
        if (player.distanceTo(t) > this.selectTargets.getRange()) {
            return;
        }

        Vec3d targetVec = GcoreEntityUtilKt.getPrevPos(t, this.prev.get().floatValue());
        this.attackTimer.reset();

        
        boolean critFlag = this.critical.get()
            && GcoreBlockUtil.checkNoPosCollie(targetVec.x, targetVec.y - CRIT_OFFSET, targetVec.z);

        Vec3d tpVec = targetVec;
        if (this.findVecToAttack.get() && !GcoreBlockUtil.checkNoPosCollie(targetVec)) {
            Vec3d found = GcoreTpUtil.findVecToAttack(targetVec, t.getStandingEyeHeight());
            if (found == null) {
                return;
            }
            tpVec = found;
        }
        boolean doUseMace = this.useMace.get()
            && GcoreNewItemUtil.findItem(this.maceSlotOption,
                   i -> GcoreNewItemUtil.realStack(i).getName().getString().equals(MACE_NAME)) != -1;
        Vec3d fromVec = prevPos(player);
        Map<BlockPos, BlockState> extraPosMap = GcoreBlockUtil.getBufferMap();
        Vec3d finalTpVec = tpVec;

        try (var ignored = GcoreServerEquivMove.enter(MODULE_NAME)) {
            GcoreTpUtil.doTpMove(this.tpOption, fromVec, finalTpVec, extraPosMap,
                (counter, curVec) -> this.doAttack(t, finalTpVec, critFlag, doUseMace, player));
        }

        
        player.resetTicksSince();
    }
    private void doAttack(Entity target, Vec3d tpVec, boolean critFlag,
                          boolean doUseMace, net.minecraft.entity.player.PlayerEntity player) {
        
        if (!doUseMace && this.critical.get()) {
            if (critFlag) {
                
                GcoreTpUtil.sendMovePacket(tpVec.x, tpVec.y - CRIT_OFFSET, tpVec.z, false);
            } else {
                
                GcoreTpUtil.sendMovePacket(tpVec.x, tpVec.y + CRIT_OFFSET, tpVec.z, true);
                GcoreTpUtil.sendMovePacket(tpVec.x, tpVec.y, tpVec.z, false);
            }
        }

        
        if (doUseMace) {
            
            GcoreNewItemUtil.swapUse(this.maceSlotOption,
                i -> GcoreNewItemUtil.realStack(i).getName().getString().equals(MACE_NAME),
                () -> GcorePlayerActionUtil.doAttackEntity(target));
        } else {
            for (int i = 1; i <= this.attackTimes.get(); i++) {
                GcorePlayerActionUtil.doAttackEntity(target);
            }
        }

        if (this.swingHand.get()) {
            player.swingHand(Hand.MAIN_HAND);
        }
    }
        @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!this.render.get()) {
            return;
        }

        Entity t = this.target;
        if (t == null) {
            return;
        }

        Color c = new Color(255, 100, 100, 100);
        event.renderer.box(t.getBoundingBox(), c, c, ShapeMode.Both, 0);
    }
}
