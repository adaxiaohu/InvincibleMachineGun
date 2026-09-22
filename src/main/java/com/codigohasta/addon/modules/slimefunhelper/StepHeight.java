package com.codigohasta.addon.modules.slimefunhelper;

import com.codigohasta.addon.mixin.slimefunhelper.StepHeightEntityAccessor;
import com.codigohasta.addon.utils.slimefunhelper.events.Event;
import com.codigohasta.addon.utils.slimefunhelper.events.Listener;
import com.codigohasta.addon.utils.slimefunhelper.hacks.MovTasks;
import com.codigohasta.addon.utils.slimefunhelper.hacks.api.BaseModule;
import com.codigohasta.addon.utils.slimefunhelper.hacks.api.ModulePath;
import com.codigohasta.addon.utils.slimefunhelper.hacks.utils.entity.LegalMovementManager;
import com.codigohasta.addon.utils.slimefunhelper.managers.Configs;
import com.codigohasta.addon.utils.slimefunhelper.managers.config.FlagRef;
import com.codigohasta.addon.utils.slimefunhelper.utils.EntityUtils;
import com.codigohasta.addon.utils.slimefunhelper.versioned.api.VPacket;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

public class StepHeight extends BaseModule implements LegalMovementManager.MovementModifier {

    public final ModulePath moveSafety = makePath(Configs.MOV_CONFIG, "move-safety");
    public final ModulePath stepHeight = moveSafety.add("enhance-stepheight");

    public static LegalMovementManager.DelegateMovementModifier instance;

    public StepHeight() {
        super("StepHeight");
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
        bindFlag(enable);
    }

    public final FlagRef enable = flagBuilder(stepHeight).build();

    @Override
    public void onEnableModule() {
        super.onEnableModule();
        runTicks = 0;
        toggleRunning = false;
        y = 0.0D;
        ticksEnd = 6;
        jumpTriggerStepHeight = this::triggerJump;
    }

    @Override
    public void onDisableModule() {
        super.onDisableModule();
        jumpTriggerStepHeight = null;
        resetRuntimeState();
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPlayerNotFlyJumpPoint(), this::onJump);
    }

    private ClientPlayerEntity forcedGroundPlayer;
    private boolean previousGround;
    public void resetRuntimeState() {
        if (forcedGroundPlayer != null) forcedGroundPlayer.setOnGround(previousGround);
        forcedGroundPlayer = null;
        runTicks = 0; toggleRunning = false; y = 0; ticksEnd = 6;
    }

    private Runnable jumpTriggerStepHeight;

    private void onJump(Event<Integer> jumpEvent) {
        if (jumpTriggerStepHeight != null) {
            jumpTriggerStepHeight.run();
        }
    }

    boolean toggleRunning;
    int runTicks;
    double y;
    int ticksEnd = 6;

    @Override
    public int priority() {
        return PRIORITY_COMMON;
    }

    public void triggerJump() {
        if (isActive()) {
            double jumpStrength = ((StepHeightEntityAccessor) mc.player).sf$getJumpVelocity(1.0F);
            double gravity = mc.player.getFinalGravity();
            int ticksNeeded = (int) ((jumpStrength - 1E-5) / gravity);
            ticksEnd = ticksNeeded + 1;
            toggleRunning = true;
            runTicks = 0;
        }
    }

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {

        if (isActive()) {
            if (toggleRunning) {

                if (runTicks < 0) {
                    runTicks = -114514;
                    movementManagerEvent.context.playerStatus.entity.setOnGround(true);
                }
            }
        }
    }

    @Override
    public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {

        if (isActive()) {
            if (toggleRunning) {
                runTicks += 1;
                if (runTicks >= ticksEnd) {

                    ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
                    if (!player.isOnGround()) {
                        Vec3d vec3d2 = new Vec3d((double) player.sidewaysSpeed, 0.0, (double) player.forwardSpeed);
                        Vec3d vec3d0 = player.getVelocity();
                        Vec3d movement = // new Vec3d(vec3d0.x, 0, vec3d0.z)
                                vec3d0.add(EntityUtils.movementInputToVelocity(
                                        vec3d2, player.getMovementSpeed(), player.getYaw()));
                        player.setOnGround(true);
                        boolean useStepHeightFeature = MovTasks.doMovementInvolveStepheight(player, movement);
                        if (useStepHeightFeature) {
                            forcedGroundPlayer = player;
                            previousGround = false;
                            runTicks = -200;
                            movementManagerEvent.cancel();
                            movementManagerEvent.context.playerStatus.entity.setOnGround(true);
                            mc.getNetworkHandler()
                                    .sendPacket(VPacket.newOnGroundOnly(true, player.horizontalCollision));
                            return;
                        }
                        player.setOnGround(false);
                    }

                    runTicks = -11451;
                }
            }
        }
    }

    @Override
    public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
        if (isActive()) {
            if (runTicks < -1145) {
                forcedGroundPlayer = null;
                runTicks = 0;
                toggleRunning = false;
            }
        }

        return true;
    }
}
