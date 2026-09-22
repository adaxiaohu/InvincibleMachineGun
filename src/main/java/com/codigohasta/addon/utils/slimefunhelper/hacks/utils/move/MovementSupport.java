package com.codigohasta.addon.utils.slimefunhelper.hacks.utils.move;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import java.util.*;
import com.codigohasta.addon.utils.slimefunhelper.hacks.MovTasks;
import com.codigohasta.addon.utils.slimefunhelper.hacks.MovTasks.MovingContext;
import com.codigohasta.addon.utils.slimefunhelper.hacks.MovTasks.MovInfo;
import com.codigohasta.addon.utils.slimefunhelper.hacks.MovTasks.StepActionBundle;
import com.codigohasta.addon.utils.slimefunhelper.accessors.access.ClientPlayerAccess;
import com.codigohasta.addon.utils.slimefunhelper.utils.ChatUtils;
import com.codigohasta.addon.utils.slimefunhelper.utils.Debug;
import com.codigohasta.addon.utils.slimefunhelper.managers.Tasks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.world.GameMode;
import net.minecraft.util.math.*;
import net.minecraft.block.BlockState;
import com.codigohasta.addon.utils.slimefunhelper.versioned.api.VPacket;
/** Source movement primitives used by ForwardTp and Flight. */
public final class MovementSupport {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    public static void farawayMoveTo(Vec3d vec3d, boolean updatePlayer) {
        farawayMoveFromTo(mc.player.getEntityPos(), vec3d, null, updatePlayer);
    }

    public static void farawayMove(Vec3d vec3d, boolean updatePlayer) {
        farawayMoveFromTo(mc.player.getEntityPos(), mc.player.getEntityPos().add(vec3d), null, updatePlayer);
    }

    public static void farawayMoveFromTo(Vec3d from, Vec3d to, Boolean onGroundOverride, boolean updatePlayer) {
        Vec3d vec3d = to.subtract(from);
        double len = vec3d.length();

        if (mc.player.hasVehicle()) {
            Entity vehicle = mc.player.getVehicle();
            double maxOnceLen = 10 - 1E-2;
            if (len >= maxOnceLen) {
                double speedArg = 10;
                int packetNum = (int) (((len + 1) / speedArg));
                if (packetNum > 0)
                    for (var p = 0; p <= packetNum; ++p)
                        mc.getNetworkHandler().sendPacket(VPacket.newVehicleMove(vehicle));
            }
            double deltaY = vehicle.getY() - mc.player.getY();
            vehicle.setPosition(to.add(0, deltaY, 0));
            mc.getNetworkHandler().sendPacket(VPacket.newVehicleMove(vehicle));
            if (updatePlayer) {
                mc.player.setPosition(to);
            }
        } else {
            double maxOnceLen = (mc.player.isGliding() ? (10 * Math.sqrt(3)) : 10) - 1E-2;

            if (len >= maxOnceLen) {
                double speedArg = 10;
                int packetNum = (int) (((len + 1) / speedArg));
                if (packetNum > 0)
                    for (var p = 0; p <= packetNum; ++p)
                        mc.getNetworkHandler().sendPacket(VPacket.newOnGroundOnly(mc.player.isOnGround(), false));
            }

            if (onGroundOverride != null) {
                mc.player.setOnGround(onGroundOverride);
            }
            mc.getNetworkHandler()
                    .sendPacket(VPacket.newPositionAndOnGround(
                            to.getX(), to.getY(), to.getZ(), mc.player.isOnGround(), false));
            if (updatePlayer) mc.player.setPosition(to);
        }
    }

    private static boolean noBlocksAround(Entity entity) {
        Box box = entity.getBoundingBox().expand(0.0625D).stretch(0.0D, -0.55D, 0.0D);
        int minX = MathHelper.floor(box.minX);
        int minY = MathHelper.floor(box.minY);
        int minZ = MathHelper.floor(box.minZ);
        int maxX = MathHelper.floor(box.maxX);
        int maxY = MathHelper.floor(box.maxY);
        int maxZ = MathHelper.floor(box.maxZ);

        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int y = minY; y <= maxY; ++y) {
            for (int z = minZ; z <= maxZ; ++z) {
                for (int x = minX; x <= maxX; ++x) {
                    pos.set(x, y, z);
                    BlockState type = mc.world.getBlockState(pos);

                    if (type != null && !type.isAir()) {

                        return false;
                    }
                }
            }
        }

        return true;
    }

    public static boolean seenAsFloating(boolean fakeGilding) {
        return !com.codigohasta.addon.modules.slimefunhelper.Flight.INSTANCE.serverSideCanFly
                && mc.player.getVelocity().y >= -0.03125D
                && mc.interactionManager.getCurrentGameMode() != GameMode.SPECTATOR
                && !mc.player.hasStatusEffect(StatusEffects.LEVITATION)
                && (fakeGilding || !mc.player.isGliding())
                && !mc.player.isUsingRiptide()
                && !mc.player.isSleeping()
                && !mc.player.hasVehicle()
                && !mc.player.isDead()
                && noBlocksAround(mc.player);
    }

    private static int generation;
    private static ClientPlayerEntity teleportOwner;
    public static Vec3d LAST_TP_REQUEST, LAST_TP_FROM;
    public static boolean doingTp;

    public static void resetRuntimeState() {
        generation++;
        doingTp = false;
        teleportOwner = null;
        LAST_TP_REQUEST = null;
        LAST_TP_FROM = null;
    }
    private static void scheduleDelayed(Runnable action, int delay) {
        int ticket = generation;
        ClientPlayerEntity owner = teleportOwner;
        Tasks.scheduleDelayed(() -> {
            if (generation == ticket && owner != null && mc.player == owner && mc.world != null) action.run();
        }, delay);
    }
    public static void executeTp(Vec3d target, double farawayTp, boolean command, boolean considerNoFall) {
        if (mc.player == null) return;
        generation++;
        doingTp = false;
        teleportOwner = mc.player;
        LAST_TP_FROM = mc.player.getEntityPos();
        LAST_TP_REQUEST = Vec3d.ZERO.add(target);
        scheduleTpInternal(MovingContext.create(mc.player.getEntityPos()), target, farawayTp, command, false, considerNoFall);
    }
    private static void scheduleFarawayMoveInternal(List<MovInfo> movements, boolean allowNextTick,
            MovingContext context, boolean considerNoFall) {
        List<StepActionBundle> bundles = MovTasks.createMovingPacketsForMovSequence(context, movements, allowNextTick, considerNoFall);
        for (int i = 0; i < bundles.size(); i++) {
            if (bundles.get(i).success || !allowNextTick) bundles.get(i).run();
            else {
                List<MovInfo> remaining = movements.subList(i, movements.size());
                scheduleDelayed(() -> scheduleFarawayMoveInternal(remaining, allowNextTick, context.resetTick(), considerNoFall), 1);
                return;
            }
        }
    }
    public static void scheduleTpInternal(
            MovingContext context,
            Vec3d target,
            double farawayThreshold,
            boolean command,
            boolean fastMode,
            boolean considerNoFall) {
        if (mc.player == null) return;
        if (command) Debug.chat("正在向", ChatUtils.getDisplayedLocationDouble(target), "执行tp行为");
        Vec3d current = context.from().get();
        Vec3d movement0 = target.subtract(current);
        List<Vec3d> vc3d0 = MovTasks.generateTpSequence(current, target, command, farawayThreshold, true);
        if (vc3d0.size() == 2) {
            boolean downward = movement0.y < -4;
            Vec3d target0 = vc3d0.get(1);
            MovInfo mainMove = new MovInfo(target0, downward ? Boolean.FALSE : null, true, null);
            List<MovInfo> movements =
                    List.of(mainMove);
            scheduleFarawayMoveInternal(movements, true, context, considerNoFall);
            if (considerNoFall) {
                ClientPlayerAccess.of(mc.player).setForceNoFall(true);
            }
        } else if (vc3d0.size() == 4) {
            if (command) {
                Debug.chat("执行TP序列");
            }
            Vec3d vec3d1 = vc3d0.get(0);
            Vec3d vec3d2 = vc3d0.get(1);
            Vec3d vec3d3 = vc3d0.get(2);
            Vec3d vec3d4 = vc3d0.get(3);
            boolean downWard = vec3d1.y > vec3d2.y + 4;
            boolean downWard0 = vec3d3.y > vec3d4.y + 4;
            context.from().setValue(vec3d1);
            mc.player.setOnGround(false);
            if (fastMode) {
                List<MovInfo> movingPositions = new ArrayList<>();
                movingPositions.add(MovInfo.createNotOnGround(vec3d2));
                if (downWard) movingPositions.add(MovInfo.create(vec3d2));
                movingPositions.add(MovInfo.create(
                        vec3d3
                        ));
                movingPositions.add(MovInfo.createNotOnGround(vec3d4));
                if (downWard0) movingPositions.add(MovInfo.create(vec3d4));
                scheduleFarawayMoveInternal(movingPositions, true, context, considerNoFall);
            } else {
                context.from().setValue(vec3d1);
                scheduleFarawayMoveInternal(List.of(MovInfo.createNotOnGround(vec3d2)), false, context, true);
                doingTp = true;

                scheduleDelayed(
                        () -> {
                            doingTp = false;
                            scheduleFarawayMoveInternal(
                                    List.of(MovInfo.create(vec3d3)), false, context.resetTick(), false);
                            doingTp = true;
                        },
                        2);
                ;
                scheduleDelayed(
                        () -> {
                            doingTp = false;
                            scheduleFarawayMoveInternal(
                                    List.of(downWard0 ? MovInfo.createNotOnGround(vec3d4) : MovInfo.create(vec3d4)),
                                    false,
                                    context.resetTick(),
                                    false);
                            if (considerNoFall && downWard0 && mc.player != null) {
                                ClientPlayerAccess.of(mc.player).setForceNoFall(true);
                                mc.player.setOnGround(false);
                            }
                        },
                        3);
            }

        } else {
            return;
        }

        if (command) Debug.chat("tp行为已经执行, 若出现回弹或者位置不变,则目标位置不可达");

    }

}
