package com.codigohasta.addon.modules.slimefunhelper;

import com.codigohasta.addon.utils.slimefunhelper.hacks.MovTasks;
import com.codigohasta.addon.utils.slimefunhelper.hacks.utils.move.MovementSupport;
import com.codigohasta.addon.utils.slimefunhelper.hacks.api.BaseModule;
import com.codigohasta.addon.utils.slimefunhelper.hacks.api.ModulePath;
import com.codigohasta.addon.utils.slimefunhelper.hacks.utils.HotKeyUtils;
import com.codigohasta.addon.utils.slimefunhelper.managers.Configs;
import com.codigohasta.addon.utils.slimefunhelper.managers.config.DoubleRef;
import com.codigohasta.addon.utils.slimefunhelper.managers.config.FlagRef;
import com.codigohasta.addon.utils.slimefunhelper.managers.config.KeyBindRef;
import com.codigohasta.addon.utils.slimefunhelper.managers.input.MultiKeyBind;
import com.codigohasta.addon.utils.slimefunhelper.utils.Debug;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

public class ForwardTp extends BaseModule implements com.codigohasta.addon.utils.slimefunhelper.hacks.utils.entity.LegalMovementManager.MovementModifier {
    private static com.codigohasta.addon.utils.slimefunhelper.hacks.utils.entity.LegalMovementManager.DelegateMovementModifier delegate;
    private boolean resetThisTick;
    public final ModulePath quickMove = makePath(Configs.MOV_CONFIG, "quick-move");

    public ForwardTp() {
        super("ForwardTp");
        bindFlag(enable);
        if (delegate == null) {
            delegate = new com.codigohasta.addon.utils.slimefunhelper.hacks.utils.entity.LegalMovementManager.DelegateMovementModifier(this::cast);
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> delegate);
        }
        delegate.setDelegate(this::cast);
    }

    public final FlagRef enable = flagBuilder(quickMove.addEnable()).build();

    public final KeyBindRef frontKey = hotkey(quickMove.add("quick-move"), new MultiKeyBind())
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::quickMovFront))
            .build();

    public final KeyBindRef wallKey = hotkey(quickMove.add("quick-to-wall"), new MultiKeyBind())
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::quickMovTowardsWall))
            .build();

    public final DoubleRef maxDistance = builder(quickMove.add("max-distance"), DoubleRef.TYPE)
            .defaultValue(120.0D)
            .validator(Configs.doubleRange(0.0D, 114514.0D))
            .build();

    public final FlagRef useTpMethod = builder(quickMove.add("ignore-move-collision"), FlagRef.TYPE)
            .defaultValue(true)
            .build();

    final double distance = 0.1;

    public boolean quickMovFront() {
        ClientPlayerEntity player = mc.player;
        if (!isActive() || player == null || mc.world == null) return false;
        Vec3d vec3d = player.getEntityPos();
        Vec3d lookat = player.getRotationVector().normalize();
        Vec3d lastAvailablePos = calculateAvailableMovPlace(mc.player, vec3d, lookat, distance, maxDistance.get());

        if (lastAvailablePos != vec3d) {
            MovementSupport.resetRuntimeState();
            if (useTpMethod.get()) {
                MovementSupport.executeTp(lastAvailablePos, 2147483647, false, true);
            } else {
                MovementSupport.farawayMoveTo(lastAvailablePos, true);
            }
            return true;
        } else {
            Debug.chat("no available position in front of you!");
            return true;
        }
    }

    public boolean quickMovTowardsWall() {
        ClientPlayerEntity player = mc.player;
        if (!isActive() || player == null || mc.world == null) return false;
        Vec3d vec3d = player.getEntityPos();
        Vec3d lookat = player.getRotationVector().normalize();
        Vec3d lastAvailablePos = calculateNextWallPosition(mc.player, vec3d, lookat, distance, maxDistance.get());
        if (lastAvailablePos != vec3d) {
            MovementSupport.resetRuntimeState();
            if (useTpMethod.get()) {
                MovementSupport.executeTp(lastAvailablePos, 2147483647, false, true);
            } else {
                MovementSupport.farawayMoveTo(lastAvailablePos, true);
            }
            return true;
        } else {
            Debug.chat("no available position in front of you!");
            return true;
        }
    }

    public Vec3d calculateAvailableMovPlace(Entity executor, Vec3d curPose, Vec3d lookAt, double delta, double max) {
        lookAt = lookAt.normalize();
        Vec3d maxinumMovement = lookAt.multiply(max);
        MovTasks.CollisionContext engin =
                new MovTasks.CollisionCache(executor, curPose, curPose.add(maxinumMovement), false);

        lookAt = lookAt.multiply(delta);
        Vec3d originalPos = curPose;
        Vec3d lastAvailablePos = curPose;
        boolean hasWall = false;
        for (double i = 0; i < max; i += delta) {
            curPose = curPose.add(lookAt);
            boolean noCollision = useTpMethod.get();
            boolean value;
            if (noCollision) {
                value = true;
            } else {
                Vec3d totalMovement = curPose.subtract(originalPos);
                Vec3d sim = engin.simulateMovement(
                        executor,
                        originalPos,
                        totalMovement); // collideWithTrustedList(originalBox, totalMovement, involvedVoxel,
                value = MovTasks.validMovementAsServer(totalMovement, sim);
            }

            boolean pass = value && !engin.checkEnvironmentCollision(executor, curPose, true);

            if (pass) {
                lastAvailablePos = curPose;
                if (hasWall) {
                    break;
                } else {
                    continue;
                }
            } else {
                hasWall = true;
            }
        }
        return lastAvailablePos;
    }

    public static Vec3d calculateNextWallPosition(
            Entity executor, Vec3d curPose, Vec3d lookAt, double delta, double max) {
        lookAt = lookAt.normalize();
        Vec3d maxinumMovement = lookAt.multiply(max);
        MovTasks.CollisionContext engin =
                new MovTasks.CollisionCache(executor, curPose, curPose.add(maxinumMovement), false);

        lookAt = lookAt.multiply(delta);
        Vec3d originalPos = curPose;
        Vec3d lastAvailablePos = curPose;
        for (double i = 0; i < max; i += delta) {
            curPose = curPose.add(lookAt);

            boolean value;

            Vec3d totalMovement = curPose.subtract(originalPos);
            Vec3d sim = engin.simulateMovement(
                    executor,
                    originalPos,
                    totalMovement); // collideWithTrustedList(originalBox, totalMovement, involvedVoxel, involvedAABB,
            value = MovTasks.validMovementAsServer(totalMovement, sim);

            boolean pass = value && !engin.checkEnvironmentCollision(executor, curPose, true);

            if (pass) {
                lastAvailablePos = curPose;
            } else {
                break;
            }
        }
        return lastAvailablePos;
    }

    @Override public int priority() { return 0; }
    @Override public void registerAll() {
        registerListener(com.codigohasta.addon.utils.slimefunhelper.events.Listener.getPacketPoint()
            .getChannel(net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.class), event -> {
                if (MovementSupport.doingTp) event.cancel();
            });
        registerListener(com.codigohasta.addon.utils.slimefunhelper.events.Listener.getClientPlayerSendMovementPoint(), event -> {
            if (MovementSupport.doingTp) event.cancel();
        });
    }
    public void resetRuntimeState() { resetThisTick = false; MovementSupport.resetRuntimeState(); }
    @Override public void onDisableModule() { resetRuntimeState(); }
    @Override public void applyPreTickModify(com.codigohasta.addon.utils.slimefunhelper.events.Event<com.codigohasta.addon.utils.slimefunhelper.hacks.utils.entity.LegalMovementManager> event) {
        resetThisTick = MovementSupport.doingTp;
    }
    @Override public void applyBeforeMovementPacketModify(com.codigohasta.addon.utils.slimefunhelper.events.Event<com.codigohasta.addon.utils.slimefunhelper.hacks.utils.entity.LegalMovementManager> event) {
        if (resetThisTick) event.cancel();
    }
    @Override public void applyBeforeInputPacketModify(com.codigohasta.addon.utils.slimefunhelper.events.Event<com.codigohasta.addon.utils.slimefunhelper.hacks.utils.entity.LegalMovementManager> event) {
        if (resetThisTick) event.cancel();
    }
    @Override public boolean postModify(com.codigohasta.addon.utils.slimefunhelper.events.Event<com.codigohasta.addon.utils.slimefunhelper.hacks.utils.entity.LegalMovementManager> event, boolean enabledThisTick) {
        if (resetThisTick) {
            resetThisTick = false;
            event.context.playerStatus.restorePos();
            event.context.playerStatus.restoreOnGround();
        }
        return true;
    }
}
