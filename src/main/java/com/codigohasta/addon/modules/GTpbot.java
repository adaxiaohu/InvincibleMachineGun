package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.utils.gcore.GcoreBlockUtil;
import com.codigohasta.addon.utils.gcore.GcoreEntityUtilKt;
import com.codigohasta.addon.utils.gcore.GcoreMSTimer;
import com.codigohasta.addon.utils.gcore.GcoreServerEquivMove;
import com.codigohasta.addon.utils.gcore.GcoreTargetUtil;
import com.codigohasta.addon.utils.gcore.GcoreTickUpdate;
import com.codigohasta.addon.utils.gcore.GcoreTpOption;
import com.codigohasta.addon.utils.gcore.GcoreTpSelectTargetsConfig;
import com.codigohasta.addon.utils.gcore.GcoreTpUtil;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Map;
public class GTpbot extends Module {
    private static final String MODULE_NAME = "GTpbot";

    private final SettingGroup sgTarget = this.settings.createGroup("目标");
    private final SettingGroup sgGeneral = this.settings.createGroup("通用");

    private final GcoreTpSelectTargetsConfig targetSelectOption;
    private final GcoreTpOption tpOption;

    private final Setting<Boolean> always;
    private final Setting<Boolean> leftClick;
    private final Setting<Boolean> rightClick;
    private final Setting<Integer> delay;
    private final Setting<Double> keepY;
    private final Setting<Boolean> onlyXZ;
    private final Setting<Boolean> random;
    private final Setting<Double> prev;

    private final GcoreMSTimer timer = new GcoreMSTimer();

    public GTpbot() {
        super(AddonTemplate.CATEGORY, "TP机械人",
            "无尽的瞬移到目标身上相当绝望。。）");

        this.targetSelectOption = new GcoreTpSelectTargetsConfig(this.sgTarget);

        this.always = this.sgGeneral.add(new BoolSetting.Builder()
            .name("一直执行")
            .description("一直执行。")
            .defaultValue(true)
            .build());

        this.leftClick = this.sgGeneral.add(new BoolSetting.Builder()
            .name("左键触发")
            .description("按住左键时才执行。")
            .defaultValue(false)
            .build());

        this.rightClick = this.sgGeneral.add(new BoolSetting.Builder()
            .name("右键触发")
            .description("按住右键时才执行。")
            .defaultValue(true)
            .build());

        this.delay = this.sgGeneral.add(new IntSetting.Builder()
            .name("执行间隔")
            .description("两次执行的间隔，单位毫秒。")
            .defaultValue(40)
            .range(1, 2000)
            .sliderRange(1, 2000)
            .build());

        this.keepY = this.sgGeneral.add(new DoubleSetting.Builder()
            .name("高度偏移")
            .description("相对目标的高度偏移。0 = 与目标同高。")
            .defaultValue(0.0)
            .range(0.0, 150.0)
            .sliderRange(0.0, 150.0)
            .build());

        this.onlyXZ = this.sgGeneral.add(new BoolSetting.Builder()
            .name("只跟随水平位置")
            .description("只取目标的 X/Z，Y 保持自己当前的高度。")
            .defaultValue(false)
            .build());

        this.random = this.sgGeneral.add(new BoolSetting.Builder()
            .name("随机目标")
            .description("从目标列表里随机挑一个")
            .defaultValue(false)
            .build());

        this.prev = this.sgGeneral.add(new DoubleSetting.Builder()
            .name("位置回溯")
            .description("目标位置回溯系数，用以抵消网络延迟。0 表示用当前位置。")
            .defaultValue(0.0)
            .range(0.1, 5.0)
            .sliderRange(0.1, 5.0)
            .build());
        this.tpOption = new GcoreTpOption(this.sgGeneral, false);
    }

    @Override
    public void onActivate() {
        GcoreTickUpdate.INSTANCE.register();
        this.timer.reset();
    }

    @Override
    public void onDeactivate() {
        GcoreTickUpdate.INSTANCE.unregister();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (this.mc.player == null || this.mc.world == null) {
            return;
        }

        if (this.leftClick.get() && this.mc.options.leftKey.isPressed()) {
            this.doTpbot();
            return;
        }
        if (this.rightClick.get() && this.mc.options.useKey.isPressed()) {
            this.doTpbot();
            return;
        }
        if (this.always.get()) {
            this.doTpbot();
        }
    }
    private void doTpbot() {
        var player = this.mc.player;
        if (player == null) {
            return;
        }

        if (!this.timer.hasPassTime(this.delay.get())) {
            return;
        }
        this.timer.reset();

        // ── 选目标 ──
        List<Entity> targetList = GcoreTargetUtil.getTargets(this.targetSelectOption);
        if (targetList.isEmpty()) {
            return;
        }

        Entity target = this.random.get()
            ? targetList.get((int) Math.floor(targetList.size() * Math.random()))
            : targetList.get(0);

        Vec3d targetPos = GcoreEntityUtilKt.getPrevPos(target, this.prev.get().floatValue());

        // ── 算落点 ──
        Vec3d keepYPos = new Vec3d(
            targetPos.x,
            this.onlyXZ.get() ? player.getY() : targetPos.y + this.keepY.get(),
            targetPos.z);

        if (!GcoreBlockUtil.checkNoPosCollie(keepYPos)) {
            keepYPos = targetPos;
        }

        if (target.getBoundingBox().contains(
                new Vec3d(player.getX(), targetPos.y, player.getZ()))) {
            return;
        }
        Vec3d fromVec = player.getEntityPos();
        Map<BlockPos, BlockState> extraPosMap = GcoreBlockUtil.getBufferMap();

        try (var ignored = GcoreServerEquivMove.enter(MODULE_NAME)) {
            GcoreTpUtil.doTpMove(this.tpOption, fromVec, keepYPos, extraPosMap,
                (counter, curVec) -> {
                    player.setPosition(targetPos.x, targetPos.y, targetPos.z);
                });
        }
    }
}
