package com.codigohasta.addon.modules.slimefunhelper;

import java.util.HashSet;
import java.util.Set;
import com.codigohasta.addon.utils.slimefunhelper.accessors.hacks.PlayerInteractionAccess;
import com.codigohasta.addon.utils.slimefunhelper.events.Event;
import com.codigohasta.addon.utils.slimefunhelper.events.Listener;
import com.codigohasta.addon.utils.slimefunhelper.hacks.MineTasks;
import com.codigohasta.addon.utils.slimefunhelper.hacks.api.BaseModule;
import com.codigohasta.addon.utils.slimefunhelper.hacks.api.ModulePath;
import com.codigohasta.addon.modules.slimefunhelper.InteractExtra;
import com.codigohasta.addon.modules.slimefunhelper.LegacySnapRotManager;
import com.codigohasta.addon.utils.slimefunhelper.hacks.utils.tasks.TimerExecutor;
import com.codigohasta.addon.utils.slimefunhelper.hooks.ViaFabricPlusHooks;
import com.codigohasta.addon.utils.slimefunhelper.managers.Configs;
import com.codigohasta.addon.utils.slimefunhelper.managers.config.FlagRef;
import com.codigohasta.addon.utils.slimefunhelper.managers.config.IntRef;
import com.codigohasta.addon.utils.slimefunhelper.managers.config.KeyBindRef;
import com.codigohasta.addon.utils.slimefunhelper.managers.input.MultiKeyBind;
import com.codigohasta.addon.utils.slimefunhelper.utils.ApiMethod;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class AntiAXray extends BaseModule {
    public AntiAXray() {
        super("AntiAXray");
        bindFlag(enable);
    }

    public final ModulePath simple = makePath(Configs.SURVIVAL_CONFIG, "survival-mine-utils.aaxray-simple");


    public final FlagRef enable =
            flagBuilder(simple.add("enable")).defaultValue(false).build();

    public final KeyBindRef hotkey = moduleEntry(simple.add("hotkey"), new MultiKeyBind(), simple.add("enable"))
            .build();

    public final IntRef limitation = builder(simple.add("packet-limit"), IntRef.TYPE)
            .defaultValue(30)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final FlagRef legal = flagBuilder(simple.add("legal")).build();

    public final FlagRef rotate = flagBuilder(simple.add("rotate")).build();

    private static final Set<BlockPos> simpleDetection = new HashSet<>();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getWorldSwitchPoint(), this::onWorldSwitch);
        registerListener(Listener.getPreGameTick(), this::onTick);
    }

    @Override
    public void onEnableModule() {
        super.onEnableModule();
        clearSimpleDetectionCache();
    }

    TimerExecutor timer = new TimerExecutor();
    TimerExecutor timerClear = new TimerExecutor();

    public void onTick(Event<ClientPlayerEntity> player) {
        if (enable.get()) {
            timerClear.run(1200, this::clearSimpleDetectionCache);
            timer.run(20, this::doSimpleDetection);
        }
    }

    public void onWorldSwitch(Event<World> event) {
        clearSimpleDetectionCache();
    }

    public void clearSimpleDetectionCache() {
        simpleDetection.clear();
    }

    @ApiMethod
    public void doSimpleDetection() {
        BlockPos currentPlayer = mc.player.getSteppingPos().add(0, 1, 0);
        int limitation = 0;
        for (var posDelta : InteractExtra.INSTANCE.getBlocksAround()) {
            BlockPos testPos = currentPlayer.add(posDelta);
            if (!MineTasks.distanceOutOfReach(testPos, mc.player.getEyePos())) {
                if (!simpleDetection.contains(testPos)) {
                    simpleDetection.add(testPos);
                    if (rotate.get() && ViaFabricPlusHooks.isSupportDupRot()) {
                        Vec3d shouldFacing = testPos.toCenterPos().subtract(mc.player.getEyePos());
                        LegacySnapRotManager.INSTANCE.snapAt(shouldFacing.normalize(), false);
                    }
                    Vec3d shouldFacing = testPos.toCenterPos().subtract(mc.player.getEyePos());
                    Direction dir = Direction.getFacing(shouldFacing).getOpposite();
                    if (legal.get()) {
                        mc.interactionManager.sendSequencedPacket(mc.world, (sequence) -> {
                            // use real direction
                            return new PlayerActionC2SPacket(
                                    PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, testPos, dir, sequence);
                        });
                        PlayerInteractionAccess.of(mc.interactionManager).sendAbortBreakPacket();
                    } else {
                        mc.interactionManager.sendSequencedPacket(mc.world, (sequence) -> {
                            // use real direction
                            return new PlayerActionC2SPacket(
                                    PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, testPos, dir, sequence);
                        });
                    }

                    if (++limitation >= this.limitation.get()) {
                        break;
                    }
                }
            }
        }
    }
}
