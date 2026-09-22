package com.codigohasta.addon.modules.slimefunhelper;

import java.util.List;
import com.codigohasta.addon.utils.slimefunhelper.events.Event;
import com.codigohasta.addon.utils.slimefunhelper.hacks.MovTasks;
import com.codigohasta.addon.utils.slimefunhelper.hacks.api.BaseModule;
import com.codigohasta.addon.utils.slimefunhelper.hacks.api.ModulePath;
import com.codigohasta.addon.utils.slimefunhelper.hacks.utils.entity.LegalMovementManager;
import com.codigohasta.addon.utils.slimefunhelper.managers.Configs;
import com.codigohasta.addon.utils.slimefunhelper.managers.Tasks;
import com.codigohasta.addon.utils.slimefunhelper.managers.config.*;
import com.codigohasta.addon.utils.slimefunhelper.managers.input.MultiKeyBind;
import com.codigohasta.addon.utils.slimefunhelper.utils.MathUtils;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public class AntiLiquid extends BaseModule implements LegalMovementManager.MovementModifier {
    static LegalMovementManager.DelegateMovementModifier instance;

    public AntiLiquid() {
        super("AntiLiquid");
        bindFlag(enable);
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
    }

    public final ModulePath moveSafety = makePath(Configs.MOV_CONFIG, "move-safety");
    public final ModulePath antiLiquid = moveSafety.add("anti-liquid");

    public final FlagRef enable = flagBuilder(antiLiquid.addEnable()).build();

    public final KeyBindRef hotkey = moduleEntry(
                    antiLiquid.addHotkey(), new MultiKeyBind(), antiLiquid.addEnable(), moduleMeta(() -> this.mode))
            .build();

    public final EnumRef<Mode> mode =
            builder(antiLiquid.add("mode"), Mode.class).defaultValue(Mode.NONE).build();

    public final DoubleRef liquidCheckExpand = doubleBuilder(antiLiquid.add("water-expand-check"))
            .defaultValue(0.2D)
            .build();

    public final FlagRef enableWhenNotFly =
            flagBuilder(antiLiquid.add("enable-not-fly")).build();

    public final FlagRef enableWhenFly =
            flagBuilder(antiLiquid.add("enable-fly")).build();

    public final FlagRef autoArmorFlyControl =
            flagBuilder(antiLiquid.add("auto-armor-fly-control")).build();

    public final IntRef switchElytraGt = intBuilder(antiLiquid.add("auto-armor-fly-switch-gt"))
            .defaultValue(5)
            .build();

    public final DoubleRef leaveWater = doubleBuilder(antiLiquid.add("leave-water-expand-check"))
            .defaultValue(2.0D)
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
    }

    boolean currentArmorGlidingSaveState;

    @Override
    public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
        if (!isActive()) return;
        if (currentArmorGlidingSaveState) {
            if (!ElytraExtra.INSTANCE.nextPacketResetFallFlying.isEmpty()) {
                if (Tasks.getTick() >= switchElytraGt.get() + taskLast) {
                    ElytraExtra.INSTANCE.nextPacketResetFallFlying.clear();
                } else {
                    movementManagerEvent.cancel();
                    movementManagerEvent.context.markForResetPos();
                    FloatingUtils.INSTANCE.setGrimFloatingTick(true);
                    return;
                }
            } else {
                taskLast = 0;
            }
        }

        if (enable.get() && mode.get() != Mode.NONE) {
            var box = mc.player
                    .getBoundingBox()
                    .expand(liquidCheckExpand.get(), liquidCheckExpand.get(), liquidCheckExpand.get());
            var blocks = MathUtils.getOccupiedBlockPositions(box);
            for (var block : blocks) {
                BlockState state = mc.world.getBlockState(block);
                Fluid fluid = state.getFluidState().getFluid();
                boolean isWater = fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER;
                boolean isLava = fluid == Fluids.LAVA || fluid == Fluids.FLOWING_LAVA;
                Mode mode = this.mode.get();
                boolean lava = (mode.isAntiLava() && isLava);
                boolean water = (mode.isAntiWater() && isWater);
                if (lava || water) {
                    handleMayFlyIntoFluid(movementManagerEvent, lava, water);
                    return;
                }
            }
            handleOutOfWater();
        }
    }

    public void resetRuntimeState() {
        currentArmorGlidingSaveState = false;
        taskLast = 0;
        taskSwitch = 0;
    }

    @Override public void onDisableModule() {
        // Resume the armor flight transaction this module temporarily suspended.
        if (currentArmorGlidingSaveState && !checkNull() && mc.player.isGliding()
                && ElytraExtra.INSTANCE.isActive() && ElytraExtra.INSTANCE.armorFly.get()
                && !ElytraExtra.INSTANCE.isCurrentArmorGliding()) {
            ElytraExtra.INSTANCE.startArmorFlyTransaction(-1);
        }
        resetRuntimeState();
    }

    int taskLast = 0;
    int taskSwitch = 0;

    private void handleMayFlyIntoFluid(Event<LegalMovementManager> eventMove, boolean isLava, boolean isWater) {
        boolean lastNotInWater = !PlayerStateManager.INSTANCE.lastInWater;
        boolean lastNotInLava = !PlayerStateManager.INSTANCE.lastInLava;
        if ((isWater && lastNotInWater) || (isLava && lastNotInLava)) {
            if (mc.player.isGliding()) {
                if (PlayerStateManager.INSTANCE.glidingTicks > 20
                        && !MovTasks.getElytraGrimAccelerate().enable.get()
                        && isWater
                        && autoArmorFlyControl.get()
                        && ElytraExtra.INSTANCE.armorFly.get()
                        && ElytraExtra.INSTANCE.isCurrentArmorGliding()) {
                    eventMove.cancel();
                    eventMove.context.playerStatus.restorePos();
                    ElytraExtra.INSTANCE.endArmorFlyTransaction(true);
                    currentArmorGlidingSaveState = true;
                    taskSwitch = Tasks.getTick();
                    if (!ElytraExtra.INSTANCE.nextPacketResetFallFlying.isEmpty()) {
                        taskLast = Tasks.getTick();
                    }
                    FloatingUtils.INSTANCE.setGrimFloatingTick(true);
                    return;
                }
                if (enableWhenFly.get()) {
                    eventMove.cancel();
                    eventMove.context.markForResetPos();
                    FloatingUtils.INSTANCE.setGrimFloatingTick(true);
                }
            } else {
                if (enableWhenNotFly.get()) {
                    eventMove.cancel();
                    eventMove.context.markForResetPos();
                    FloatingUtils.INSTANCE.setGrimFloatingTick(true);
                }
            }
        }
    }

    private void handleOutOfWater() {
        if (currentArmorGlidingSaveState && Tasks.getTick() > taskSwitch + switchElytraGt.get()) {
            Box leaveWater = mc.player
                    .getBoundingBox()
                    .expand(this.leaveWater.get(), this.leaveWater.get(), this.leaveWater.get());
            List<BlockPos> surroundBlocks = MathUtils.getOccupiedBlockPositions(leaveWater);
            boolean findBlock = false;
            for (var block : surroundBlocks) {
                BlockState state = mc.world.getBlockState(block);
                Fluid fluid = state.getFluidState().getFluid();
                boolean isWater = fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER;
                if (isWater) {
                    findBlock = true;
                    break;
                }
            }
            if (!findBlock) {
                currentArmorGlidingSaveState = false;
                if (mc.player.isGliding()
                        && ElytraExtra.INSTANCE.armorFly.get()
                        && !ElytraExtra.INSTANCE.isCurrentArmorGliding()) {
                    ElytraExtra.INSTANCE.startArmorFlyTransaction(-1);
                }
            }
        }
    }

    public static enum Mode implements ConfigEnum {
        NONE(false, false),
        ANTI_WATER(true, false),
        ANTI_LAVA(false, true),
        ALL(true, true);
        final boolean antiWater;
        final boolean antiLava;
        Mode(boolean water, boolean lava) { antiWater = water; antiLava = lava; }
        public boolean isAntiWater() { return antiWater; }
        public boolean isAntiLava() { return antiLava; }

        @Override
        public String getConfigEnumType() {
            return "anti_liquid_anti_liquid_mode";
        }
    }
}
