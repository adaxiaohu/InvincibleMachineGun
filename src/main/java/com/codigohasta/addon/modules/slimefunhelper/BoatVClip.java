package com.codigohasta.addon.modules.slimefunhelper;

import com.codigohasta.addon.utils.BlockStateCompat;

import java.util.*;
import com.codigohasta.addon.utils.slimefunhelper.events.Event;
import com.codigohasta.addon.utils.slimefunhelper.events.Listener;
import com.codigohasta.addon.utils.slimefunhelper.hacks.MovTasks;
import com.codigohasta.addon.utils.slimefunhelper.hacks.api.BaseModule;
import com.codigohasta.addon.utils.slimefunhelper.hacks.api.ModulePath;
import com.codigohasta.addon.modules.slimefunhelper.CombatExtra;
import com.codigohasta.addon.modules.slimefunhelper.FakeBlockManager;
import com.codigohasta.addon.utils.slimefunhelper.hacks.utils.entity.LegalMovementManager;
import com.codigohasta.addon.utils.slimefunhelper.managers.Configs;
import com.codigohasta.addon.utils.slimefunhelper.managers.config.FlagRef;
import com.codigohasta.addon.utils.slimefunhelper.managers.config.KeyBindRef;
import com.codigohasta.addon.utils.slimefunhelper.managers.input.MultiKeyBind;
import com.codigohasta.addon.utils.slimefunhelper.utils.CollisionUtil;
import com.codigohasta.addon.utils.slimefunhelper.utils.EntityUtils;
import com.codigohasta.addon.utils.slimefunhelper.utils.InteractUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.VehicleEntity;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class BoatVClip extends BaseModule implements LegalMovementManager.MovementModifier {
    private static LegalMovementManager.DelegateMovementModifier instance;

    public BoatVClip() {
        super("BoatVClip");
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
        bindFlag(enable);
    }

    public final ModulePath boatVClip = makePath(Configs.EXTRA_CONFIG, "other.boat-vclip");

    public final FlagRef enable = flagBuilder(boatVClip.addEnable()).build();
    public final KeyBindRef hotkey = moduleEntry(boatVClip.addHotkey(), new MultiKeyBind(), boatVClip.addEnable())
            .build();

    @Override
    public void onDisableModule() {
        super.onDisableModule();
        restoreBlocks();
        minedBlockPos.clear();
        blockStateMap.clear();
        lastVehicle = null;
        vec3d = Vec3d.ZERO;
        cd = 0;
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPacketPostHandlePoint().getChannel(BlockUpdateS2CPacket.class), this::onBlockUpdate);
        Listener.getWorldSwitchPoint().add(event -> onDisableModule(), 0);
    }

    Map<BlockPos, BlockState> blockStateMap = new HashMap<>();
    Set<BlockPos> minedBlockPos = new HashSet<>();
    private net.minecraft.client.world.ClientWorld modifiedWorld;
    private void restoreBlocks() {
        if (modifiedWorld == mc.world && modifiedWorld != null) {
            blockStateMap.forEach((pos, state) -> {
                if (modifiedWorld.getBlockState(pos).isAir()) modifiedWorld.setBlockState(pos, state);
            });
        }
        modifiedWorld = null;
    }

    public void onBlockUpdate(Event<BlockUpdateS2CPacket> update) {
        if (enable.get()) {
            // minedBlockPos.remove(update.context.getPos());
            BlockPos pos = update.context.getPos();

            if (blockStateMap.containsKey(pos)) {
                blockStateMap.put(pos, update.context.getState());
                //                if(mc.player.hasVehicle()){
                //                    Tasks.scheduleDelayedPre(()->{
                //                        Listener.sendPacketNoEvents(new
                // PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, Direction.UP,
                // NetworkUtils.generateNextSequence()));
                //                        Listener.sendPacketNoEvents(new PlayerActionC2SPacket(
                //                            PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, Direction.UP,
                // NetworkUtils.generateNextSequence()));
                //                    }, 0);
                //                }
                //                update.cancel();
            }
        }
    }

    Entity lastVehicle;
    int cd = 0;
    Vec3d vec3d = Vec3d.ZERO;

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
        if (enable.get()) {
            if (!EntityUtils.isEntityValid(lastVehicle)
                    || lastVehicle.getBoundingBox().squaredMagnitude(mc.player.getEyePos())
                            > CombatExtra.INSTANCE.getAttackRange()) {
                lastVehicle = null;
            }
            if (lastVehicle == null) {
                lastVehicle =
                        mc
                                .world
                                .getOtherEntities(
                                        mc.player, mc.player.getBoundingBox().expand(1.5, 1.5, 1.5))
                                .stream()
                                .filter(s -> s instanceof VehicleEntity)
                                .findAny()
                                .orElse(null);
            }
            if (lastVehicle != null) {
                if (!mc.player.hasVehicle()) {
                    if (vec3d == null || vec3d.squaredDistanceTo(lastVehicle.getEntityPos()) > 0.25) {
                        restoreBlocks();
                        modifiedWorld = mc.world;
                        vec3d = lastVehicle.getEntityPos();
                        blockStateMap.clear();
                        ;
                        Box box = lastVehicle.getBoundingBox();
                        Box expand = box.withMinY(box.minY - 0.5).withMaxY(box.minY + 0.5);
                        List<BlockPos> boxes = CollisionUtil.getIntersectingBlockPositions(mc.world, expand, false);
                        for (BlockPos boxPos : boxes) {
                            BlockState state = mc.world.getBlockState(boxPos);
                            if (!state.isAir() && !BlockStateCompat.isLiquid(state)) {
                                blockStateMap.put(boxPos, state);
                            }
                        }
                        for (var re : blockStateMap.entrySet()) {
                            BlockPos pos = re.getKey();
                            //                            Listener.sendPacketNoEvents(new PlayerActionC2SPacket(
                            //                                    PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,
                            //                                    pos,
                            //                                    Direction.UP,
                            //                                    NetworkUtils.generateNextSequence()));
                            FakeBlockManager.INSTANCE.addFakeCompensateState(pos);
                            cd = 0;
                        }
                    }

                    if (cd > 4) {
                        //
                        cd = 0;
                        Box boxxx = lastVehicle.getBoundingBox();
                        var re = new EntityHitResult(
                                lastVehicle, boxxx.getCenter().add(0, boxxx.getLengthY() / 2, 0));
                        InteractUtils.simulateInteract(re);
                    } else {
                        cd += 1;
                    }
                } else {
                    vec3d = null;
                }
                for (var re : blockStateMap.entrySet()) {
                    BlockPos pos = re.getKey();
                    mc.world.setBlockState(pos, Blocks.AIR.getDefaultState());
                }
            }
        }
    }

    @Override
    public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
        if (enable.get()) {
            //            for(var pos : blockStateMap.entrySet()){
            //                mc.world.setBlockState(pos.getKey(), pos.getValue());
            //            }
            //            blockStateMap.clear();
        }
        return true;
    }
}
