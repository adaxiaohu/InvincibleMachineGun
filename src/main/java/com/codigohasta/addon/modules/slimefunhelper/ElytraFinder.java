package com.codigohasta.addon.modules.slimefunhelper;

import com.google.gson.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.BiPredicate;
import com.codigohasta.addon.utils.slimefunhelper.events.Event;
import com.codigohasta.addon.utils.slimefunhelper.events.Listener;
import com.codigohasta.addon.utils.slimefunhelper.events.RenderListener;
import com.codigohasta.addon.utils.slimefunhelper.hacks.MovTasks;
import com.codigohasta.addon.utils.slimefunhelper.hacks.RenderTasks;
import com.codigohasta.addon.utils.slimefunhelper.hacks.WorldTasks;
import com.codigohasta.addon.utils.slimefunhelper.hacks.api.BaseModule;
import com.codigohasta.addon.utils.slimefunhelper.hacks.api.ModulePath;
import com.codigohasta.addon.modules.slimefunhelper.PlayerStateManager;
import com.codigohasta.addon.utils.slimefunhelper.hacks.utils.entity.LegalMovementManager;
import com.codigohasta.addon.utils.slimefunhelper.managers.Configs;
import com.codigohasta.addon.utils.slimefunhelper.managers.Tasks;
import com.codigohasta.addon.utils.slimefunhelper.managers.config.FlagRef;
import com.codigohasta.addon.utils.slimefunhelper.utils.*;

import net.minecraft.block.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.*;

public class ElytraFinder extends BaseModule implements LegalMovementManager.MovementModifier {
    public final ModulePath travellingControl = makePath(Configs.SURVIVAL_CONFIG, "travelling-control");
    public final ModulePath elytraFinder = travellingControl.add("elytra-finder");

    static LegalMovementManager.DelegateMovementModifier instance;

    public ElytraFinder() {
        super("ElytraFinder");
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
        bindFlag(enable);
    }

    public FlagRef enable = flagBuilder(elytraFinder.add("enable")).build();

    public FlagRef autoPilot = flagBuilder(elytraFinder.add("auto-pilot")).build();

    public FlagRef render = flagBuilder(elytraFinder.add("render")).build();

    private volatile long searchEpoch;
    Map<String, Set<BlockPos>> locatedPlaces = new LinkedHashMap<>();

    public void updateStore(String json) {
        locatedPlaces = new LinkedHashMap<>();
        JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            String key = entry.getKey();
            JsonArray obj = entry.getValue().getAsJsonArray();
            Set<BlockPos> places = new LinkedHashSet<>();
            for (var ent : obj) {
                long pos = ent.getAsLong();
                BlockPos bp = BlockPos.fromLong(pos);
                places.add(bp);
            }
            locatedPlaces.put(key, places);
        }
    }

    @Override
    public void onDisableModule() {
        super.onDisableModule();
        searchEpoch++;
        WorldTasks.cancelAllPendingChunkTasks();
        currentStep = null;
        currentShipStructure = null;
        currentDirection = null;
    }

    @Override
    public void onEnableModule() {
        super.onEnableModule();
        if (checkNull()) return;
        WorldTasks.restartWorldScanner();
    }
    // todo: add grab elytra steps
    // todo: add populate ships algorithm
    // todo: add schedule travel steps
    // todo: add command
    // todo: add pull up actions

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreWorldScannListener(), this::onPreScann);
        registerListener(Listener.getWorldScannChunkBlockFilterList(), this::onScanPredicate);
        registerListener(
                Listener.getWorldScannBlockResult().getChannel(Blocks.DRAGON_WALL_HEAD), this::onDragonHeadLoad);
        registerListener(Listener.getWorldScannChunkResult(), this::onDragonHeadChunkLoad);
        registerListener(RenderListener.getRender3DEvent(), this::onRender);
    }

    BlockPos currentShipStructure;
    Direction currentDirection;

    public void onPreScann(Event<Boolean> event) {
        if (shouldLocateDragonHead()) {
            event.context(Boolean.TRUE);
        }
    }

    public boolean shouldLocateDragonHead() {
        return enable.get() && (currentStep == null || currentStep == Step.FLYING || currentStep == Step.TARGETING);
    }

    public void onScanPredicate(Event<List<BiPredicate<BlockPos, BlockState>>> event) {
        if (shouldLocateDragonHead()) {
            event.context().add((s, b) -> b.getBlock() == Blocks.DRAGON_WALL_HEAD);
        }
    }

    public void onDragonHeadLoad(Event<BlockState> scann) {
        if (shouldLocateDragonHead()) {
            onDragonHead(scann.context, scann.getArgs(0));
        }
    }

    public void onDragonHeadChunkLoad(Event<Map<BlockPos, BlockState>> chunk) {
        if (shouldLocateDragonHead()) {
            for (var entry : chunk.context.entrySet()) {
                if (entry.getValue().getBlock() == Blocks.DRAGON_WALL_HEAD) {
                    onDragonHead(entry.getValue(), entry.getKey());
                    return;
                }
            }
        }
    }

    public void onDragonHead(BlockState state, BlockPos pos) {
        Set<BlockPos> bps = locatedPlaces.get(CommonUtils.getWorldName());
        if (bps != null && bps.contains(pos)) {
            return;
        }
        long epoch = searchEpoch;
        var world = mc.world;
        Tasks.scheduleDelayed(() -> {
            if (isActive() && epoch == searchEpoch && world == mc.world && mc.player != null) this.onLocateShipStructure(state, pos);
        }, 0);
    }

    public void onLocateShipStructure(BlockState state, BlockPos pos) {
        if (mc.world.getBlockState(pos) == state) {
            Direction dir = state.get(WallSkullBlock.FACING);
            Debug.chat("Locate Head");
            Direction searchDirection = dir.getOpposite();
            for (var entry : offsets.entrySet()) {
                Vec3i off = rotateOffset(searchDirection, entry.getKey());
                BlockPos ps = pos.add(off);
                RenderTasks.drawBox(Box.from(new BlockBox(ps)), 200, Color.MAGENTA);
                BlockState st = mc.world.getBlockState(ps);
                if (st.getBlock() != entry.getValue()) {
                    return;
                }
            }
            // is valid
            Debug.chat("Locate EndShip");
            currentShipStructure = pos;
            currentDirection = searchDirection;
        }
    }

    private Vec3i rotateOffset(Direction direction, Vec3i offset) {
        switch (direction) {
            case NORTH:
                return offset;
            case EAST:
                return new Vec3i(-offset.getZ(), offset.getY(), offset.getX());
            case SOUTH:
                return new Vec3i(-offset.getX(), offset.getY(), -offset.getZ());
            case WEST:
                return new Vec3i(offset.getZ(), offset.getY(), -offset.getX());
            default:
                throw new IllegalArgumentException("Only horizontal directions supported");
        }
    }

    Map<Vec3i, Block> offsets = new LinkedHashMap<>();

    {
        offsets.put(new Vec3i(0, 0, -1), Blocks.PURPUR_STAIRS);
        offsets.put(new Vec3i(0, -1, 0), Blocks.AIR);
        offsets.put(new Vec3i(0, 0, -2), Blocks.AIR);
        offsets.put(new Vec3i(0, 0, -3), Blocks.AIR);
        offsets.put(new Vec3i(0, -1, -2), Blocks.PURPUR_PILLAR);
        offsets.put(new Vec3i(0, -1, -3), Blocks.PURPUR_PILLAR);
        offsets.put(new Vec3i(0, 3, 0), Blocks.AIR);
    }

    public void onRender(Event<MatrixStack> event) {
        if(enable.get()&&render.get()&&currentShipStructure!=null){
            Vec3d head=currentShipStructure.toCenterPos();
            RenderUtils.drawOutlinedBox(event.context(),head.add(RenderTasks.FROM),head.add(RenderTasks.TO),Color.MAGENTA);
            Vec3d above=head.add(0,3,0);
            RenderUtils.drawOutlinedBox(event.context(),above.add(RenderTasks.FROM),above.add(RenderTasks.TO),Color.MAGENTA);
        }
    }

    Step currentStep = null;

    public void onPreInputEvent(Event<Void> event) {}

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
        if (enable.get()) {
            boolean needPullUp = false;
            if (currentShipStructure != null && mc.player.getY() < currentShipStructure.getY()) {
                needPullUp = true;
            } else if (currentStep != null && mc.player.getY() < 64) {
                needPullUp = true;
            }
            if (needPullUp) {
                currentStep = Step.PULL_UP;
            } else {
                if (currentStep == Step.PULL_UP) {
                    currentStep = null;
                }
                if (currentShipStructure != null) {
                    if (currentStep == null || currentStep == Step.FLYING || currentStep == Step.TARGETING) {
                        currentStep = Step.LOCATE_SHIP_HEAD;
                    }
                    // locate ship head
                    if (currentStep == Step.LOCATE_SHIP_HEAD) {
                        if (mc.player.isGliding()) {
                            Vec3d vec3d = currentShipStructure.toCenterPos().add(0, 2, 0);
                            Vec3d ppos = mc.player.getEntityPos();
                            Vec3d playerLook = vec3d.subtract(ppos);
                            if (MathUtils.isInBox(playerLook, 0.6)) {
                                currentStep = Step.LOCATE_SHIP_LAND;
                            } else {
                                double closeEnough = playerLook.horizontalLengthSquared();
                                EntityUtils.setEntityPitchSafe(
                                        mc.player, 0
                                        // (closeEnough > 100|| mc.player.getY() <vec3d.y +10)? 0 : 20
                                        );
                                if (closeEnough > 0.36) {
                                    float yaw = EntityUtils.rotationToYaw(playerLook);
                                    PlayerStateManager.setPlayerYawSafe(mc.player, yaw);

                                } else {
                                    PlayerStateManager.setPlayerYawSafe(mc.player, mc.player.getYaw() + 180);
                                }
                            }
                        } else {
                            currentStep = null;
                        }
                    }
                    // close enough
                    if (currentStep == Step.LOCATE_SHIP_LAND) {
                        // if(cur)
                        BlockPos locatePos = currentShipStructure.offset(currentDirection, 3);
                        Vec3d target = locatePos.toCenterPos();
                        Vec3d ppos = mc.player.getEntityPos();
                        Vec3d playerLook = target.subtract(ppos);
                        if (MathUtils.isInBox(playerLook, 0.6)) {
                            currentStep = Step.GRAB_ELYTRA;
                            currentShipStructure = null;
                        } else {
                            EntityUtils.setEntityPitchSafe(mc.player, 0);
                            if (playerLook.horizontalLengthSquared() > 0.36) {
                                float yaw = EntityUtils.rotationToYaw(playerLook);
                                PlayerStateManager.setPlayerYawSafe(mc.player, yaw);

                            } else {
                                PlayerStateManager.setPlayerYawSafe(mc.player, mc.player.getYaw() + 180);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
        if (enable.get()) {
            movementManagerEvent.context.playerStatus.restoreRotation();
        }
        return true;
    }

    public static enum Step {
        FLYING,
        TARGETING,
        LOCATE_SHIP_HEAD,
        LOCATE_SHIP_LAND,
        GRAB_ELYTRA,
        PULL_UP
    }
}
