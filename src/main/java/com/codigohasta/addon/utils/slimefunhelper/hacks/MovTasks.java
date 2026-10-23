package com.codigohasta.addon.utils.slimefunhelper.hacks;

import com.codigohasta.addon.modules.slimefunhelper.*;
import com.codigohasta.addon.utils.slimefunhelper.hacks.utils.entity.LegalMovementManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.*;
import static com.codigohasta.addon.utils.slimefunhelper.hacks.RenderTasks.*;
import static com.codigohasta.addon.utils.slimefunhelper.utils.CollisionUtil.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import com.codigohasta.addon.utils.slimefunhelper.accessors.access.ClientPlayerAccess;

import com.codigohasta.addon.modules.slimefunhelper.*;
import com.codigohasta.addon.utils.slimefunhelper.hacks.utils.entity.EntityMovementStatus;
import com.codigohasta.addon.utils.slimefunhelper.hacks.utils.entity.LegalMovementManager;
import com.codigohasta.addon.utils.slimefunhelper.managers.Tasks;
import com.codigohasta.addon.utils.slimefunhelper.utils.*;
import com.codigohasta.addon.utils.slimefunhelper.versioned.api.VPacket;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import org.apache.commons.lang3.mutable.MutableObject;
public final class MovTasks {
private static final net.minecraft.client.MinecraftClient mc=net.minecraft.client.MinecraftClient.getInstance();
    public static boolean hasHorizontalCollision(Entity entity,Vec3d move){
        // the comparison uses MathHelper.approximatelyEquals against a server-mode simulation.
        if (entity.hasPassengers()) {
            return false;
        } else {
            Vec3d movement = simulateMovement(entity, entity.getEntityPos(), move, true);
            return !MathHelper.approximatelyEquals(movement.x, move.x)
                    || !MathHelper.approximatelyEquals(movement.z, move.z);
        }
    }
    public static final LegalMovementManager.ModifierPipeline PLAYER_PIPELINE_0=new LegalMovementManager.ModifierPipeline(0);
    public static Sprint getSprint(){return meteordevelopment.meteorclient.systems.modules.Modules.get().get(Sprint.class);}
    public static Velocity getVelocity(){return meteordevelopment.meteorclient.systems.modules.Modules.get().get(Velocity.class);}
    public static MovExtra getMovExtra(){return MovExtra.INSTANCE;}
    public static ElytraExtra getElytraExtra(){return ElytraExtra.INSTANCE;}
    public static ElytraFlight getElytraFlight(){return ElytraFlight.INSTANCE;}
    public static ElytraGrimAccelerate getElytraGrimAccelerate(){return ElytraGrimAccelerate.INSTANCE;}
    public static ElytraJump getElytraJump(){return meteordevelopment.meteorclient.systems.modules.Modules.get().get(ElytraJump.class);}
    public static FloatingUtils getFloatingUtils(){return FloatingUtils.INSTANCE;}
    public static NoFall getNoFall(){return meteordevelopment.meteorclient.systems.modules.Modules.get().get(NoFall.class);}
    public static boolean isCollidingWithEnvironment(Entity entity){
        // with COLLISION_FLAG_CHECK_BORDER instead of vanilla isSpaceEmpty, so the result matches
        // the server's collision set (world border included).
        final List<Box> collisionsBB = new java.util.ArrayList<>();
        final List<VoxelShape> collisionsVoxel = new java.util.ArrayList<>();
        return CollisionUtil.getCollisionsForBlocksOrWorldBorder(
                entity.getEntityWorld(),
                entity,
                entity.getBoundingBox(),
                collisionsVoxel,
                collisionsBB,
                null,
                CollisionUtil.COLLISION_FLAG_CHECK_BORDER,
                null,
                null,
                true);
    }

    public static boolean isCollidingWithEnvironment(Entity entity,Box box){
        final List<Box> collisionsBB = new java.util.ArrayList<>();
        final List<VoxelShape> collisionsVoxel = new java.util.ArrayList<>();
        return CollisionUtil.getCollisionsForBlocksOrWorldBorder(
                mc.world,
                entity,
                box,
                collisionsVoxel,
                collisionsBB,
                null,
                CollisionUtil.COLLISION_FLAG_CHECK_BORDER,
                null,
                null,
                true);
    }
    public static List<Vec3d> tpAttackSearch(
            Vec3d from, Box to, double availableRange, double maxAtOnce, int maxAttempt) {
        double avRS = MathUtils.s2(availableRange);
        List<Vec3d> vec = new ArrayList<>();
        // store player information
        Vec3d playerVec = mc.player.getVelocity();
        Vec3d playerPos = mc.player.getEntityPos();

        Vec3d currentPos = from;
        for_loop:
        for (int i = 0; i < maxAttempt; ++i) {
            // Debug.chat("on loop", i);
            if (!vec.isEmpty()) {
                currentPos = vec.get(vec.size() - 1);
            }
            Vec3d currentTry = to.getHorizontalCenter().subtract(currentPos);
            double len = Math.max(1.0F, currentTry.length() - availableRange + 1.0F);
            currentTry = currentTry.normalize().multiply(Math.min(maxAtOnce, len));
            if (len >= maxAtOnce) {
                currentTry = currentTry.multiply(maxAtOnce / len);
            }
            // mc.player.move(MovementType.PLAYER, currentTry);
            switch (validMovToEntity(ENGIN, currentPos, currentTry, to, avRS, vec)) {
                case 2:
                    break for_loop;
                case 1:
                    continue;
            }
            //            for (int s = -1; s <= 1; ++s){
            //                for (int t = -1; t <= 1; ++t){
            //                    if(s != 0 || t!= 0){
            //                        //rotate for more try
            //                        Vec3d currentFacingTry = EntityUtils.rotateVec(currentTry, 20 * s , 30* t);
            //                        switch (validMov(currentPos, currentFacingTry, to, avRS, vec)){
            //                            case 2:break for_loop;
            //                            case 1: continue for_loop;
            //                        }
            //                    }
            //                }
            //            }
            // java.util.Random rand = new java.util.Random();
            // random select
            //            for (int s = 0; s< 10; ++s ){
            //                Vec3d currentFacingTry = EntityUtils.rotateVec(currentTry, rand.nextFloat(- 50,
            // 50),rand.nextFloat(- 50, 50) );
            //                switch (validMov(currentPos, currentFacingTry, to, avRS, vec)){
            //                    case 2:break for_loop;
            //                    case 1: continue for_loop;
            //                }
            //            }
            break for_loop;
            // can not continue move
            // back to currentPos
        }
        mc.player.setVelocity(playerVec);
        mc.player.setPosition(playerPos);
        return vec;
    }
    public static List<Vec3d> generateTpSequence(MovingContext context, Vec3d target, boolean considerEnvironment) {
        return generateTpSequence(context.from().get(), target, false, 200, considerEnvironment);
    }
    public static List<Vec3d> generateTpSequence(
            Vec3d current, Vec3d target, boolean command, double farawayTp, boolean considerEnvironment) {
        return ENGIN.generateTpSequence(current, target, command, farawayTp, considerEnvironment);
    }
    public static double searchFirstNoCollisionSpaceYHeight(Vec3d origin, double min, double max, boolean upOrDown) {
        Box boundingBox = mc.player.dimensions.getBoxAt(origin);
        double tmin = upOrDown ? min : -max;
        double tmax = upOrDown ? max : -min;
        Box involved = CollisionUtil.resetY(
                boundingBox,
                origin.y + tmin,
                origin.y + tmax); //  boundingBox.stretch(0, max * (upOrDown? 1.0D : -1.0D), 0).stretch();
        DEBUG_RENDER_COLLISION_RENDERING = true;
        STATIC_DEBUG_COLOR = Color.RED;
        debugBox(involved);
        DEBUG_RENDER_COLLISION_RENDERING = false;
        final List<Box> collisionsBB = new java.util.ArrayList<>();
        final List<VoxelShape> collisionsVoxel = new java.util.ArrayList<>();
        CollisionUtil.getCollisions(
                mc.world,
                mc.player,
                involved,
                collisionsVoxel,
                collisionsBB,
                // CollisionUtil.COLLISION_FLAG_COLLIDE_WITH_UNLOADED_CHUNKS |
                CollisionUtil.COLLISION_FLAG_CHECK_BORDER,
                null,
                null,
                null);
        return searchFirstNoYConflictYHeight(origin.y, collisionsBB, collisionsVoxel, min, max, upOrDown);
    }
    private static int validMovToEntity(
            CollisionContext engin, Vec3d currentPos, Vec3d currentTry, Box to, double avRS, List<Vec3d> vec) {
        Vec3d testMov = engin.simulateMovement(mc.player, currentPos, currentTry);
        Vec3d testPos = currentPos.add(testMov);

        // test back
        // can back
        Vec3d expectedBack = Vec3d.ZERO.subtract(testMov);
        Vec3d backTry = engin.simulateMovement(mc.player, testPos, expectedBack);
        //        if(collisionDebugRender()){
        //            Vec3d backPos = testPos.add(backTry);
        //            RenderTasks.registerVirtualRenderTask(new RenderTasks.BoxRenderingTask(testPos.add(new Vec3d(-0.5,
        // 0, -0.5)), testPos.add(new Vec3d(0.5, 2, 0.5)), DEBUG_TICK));
        //            RenderTasks.registerVirtualRenderTask(new RenderTasks.BoxRenderingTask(backPos.add(new Vec3d(-0.5,
        // 0, -0.5)), backPos.add(new Vec3d(0.5, 2, 0.5)), DEBUG_TICK));
        //            Debug.chat("Boundback", backPos.squaredDistanceTo(currentPos));
        //        }

        // there should be bug, but it works well, that's because only the y is unlimited
        if (validMovementAsServer(expectedBack, backTry)) {
            // distance available
            // check collision for safety
            if (to.squaredMagnitude(testPos) < avRS && !engin.checkEnvironmentCollision(mc.player, testPos, false)) {
                vec.add(testPos);
                //  Debug.chat("add finish pos", RenderTasks.getDisplayedLocationDouble(testPos), "move",
                // RenderTasks.getDisplayedLocationDouble(testMov));
                return 2;
            }
            // valid move
            // move available

            else if (validMovementAsServer(currentTry, testMov)) {
                Vec3d nextPos = currentPos.add(currentTry);
                // fixme use expected Pos as target
                // fixme no need to check expected pos because of movement mech
                // check collision for safety
                if (!engin.checkEnvironmentCollision(mc.player, nextPos, false)) {
                    vec.add(nextPos);
                    return 1;
                }
                //  Debug.chat("add avail pos", RenderTasks.getDisplayedLocationDouble(testPos), "move",
                // RenderTasks.getDisplayedLocationDouble(testMov));
            }
        }
        // Debug.chat("fail check to ", RenderTasks.getDisplayedLocationDouble(testPos));
        return 0;
    }
    public static void setupAutoResync(Vec3d pos) {
        getAutoResync().setAutoResyncSchedule(Optional.of(pos));
    }
    public static void setupAutoResync() {
        getAutoResync().setAutoResyncSchedule(Optional.empty());
    }
    public static boolean doMovementInvolveStepheight(Entity entity, Vec3d movement) {
        boolean stepheightFlag = getMovExtra().noStepHeightFeature.get();
        // enable stepheight feature to simulate
        getMovExtra().noStepHeightFeature.set(false);
        INTERNAL_VALUE_USE_STEPHEIGHT.set(false);
        DEBUG_RENDER_COLLISION_RENDERING = true;
        Vec3d c = collide(entity, movement);
        DEBUG_RENDER_COLLISION_RENDERING = false;
        boolean useStepheight = INTERNAL_VALUE_USE_STEPHEIGHT.get();
        getMovExtra().noStepHeightFeature.set(stepheightFlag);
        return useStepheight;
    }

    public static Vec3d simulateMovement(Entity entity, Vec3d from, Vec3d vec3d, boolean serverMode) {
        // var simu = startSimulation();
        Entity rootEnity = entity.getRootVehicle();
        double deltaY = rootEnity.getY() - entity.getY();
        //        entity.setPosition(from);
        Vec3d rootVec = rootEnity.getEntityPos();
        rootEnity.setPosition(from.add(0, deltaY, 0));
        // allow down velocity
        //

        //        vec3d = ((PlayerEntity)mc.player).adjustMovementForSneaking(vec3d, MovementType.PLAYER);
        //        Vec3d movement = mc.player.adjustMovementForCollisions(vec3d);
        // mc.player.move(MovementType.PLAYER, vec3d);

        Vec3d result;
        RenderTasks.DEBUG_RENDER_COLLISION_RENDERING = true;

        //        if(!serverMode){
        //            boolean onGround = mc.player.isOnGround();
        //            mc.player.setOnGround(false);
        //            Box box = mc.player.getBoundingBox();
        //            List<VoxelShape> list = mc.player.getWorld().getEntityCollisions(mc.player, box.stretch(vec3d));
        //            //0
        //            list =Entity.findCollisionsForMovement(mc.player, mc.player.getWorld(), list, box.stretch(vec3d));
        //
        //            if(RenderTasks.DEBUG_RENDER_COLLISION){
        //                for (var boxShape0 : list){
        //                    var box0 = boxShape0.getBoundingBox();
        //                    RenderTasks.registerVirtualRenderTask(new RenderTasks.BoxRenderingTask(box0.getMinPos(),
        // box0.getMaxPos(), DEBUG_TICK, Color.YELLOW));
        //                }
        //            }
        //
        //            result = Entity.adjustMovementForCollisions(vec3d, box, (List<VoxelShape>) list);
        //            mc.player.setOnGround(onGround);
        //
        //        }else{
        result = collide(rootEnity, vec3d);
        //        }
        RenderTasks.DEBUG_RENDER_COLLISION_RENDERING = false;
        rootEnity.setPosition(rootVec);
        //  simu.restore();
        return result;
    }
    public static List<StepActionBundle> createMovingPacketsForMovSequence(
            MovingContext context, List<MovInfo> deltaMovements, boolean allowFailure, boolean considerNoFall) {
        //        boolean shouldCheckSpeed = ! mc.player.isGliding() || (!
        // mc.world.getGameRules().getBoolean(GameRules.DISABLE_ELYTRA_MOVEMENT_CHECK));
        //        double maxOnceLen = (mc.player.isGliding()? (10 * Math.sqrt(3)) : 10) - 1E-2;
        // filter front not-needed packets
        List<StepActionBundle> packets = new ArrayList<>();
        int emptyMoveCnt = 0;
        // prepare for all movements,
        for (int i = 0; i <= deltaMovements.size(); ++i) {
            packets.add(new StepActionBundle());
        }
        {
            boolean startFirstMove = false;
            List<MovInfo> moveList = new ArrayList<>();
            Vec3d currentVec330 = context.from.getValue();
            for (int i = 0; i < deltaMovements.size(); ++i) {
                // filter packets that are not removing at the front
                if (!startFirstMove && deltaMovements.get(i).isEmptyTo(currentVec330)) {
                    emptyMoveCnt += 1;
                    continue;
                } else {
                    startFirstMove = true;
                    moveList.add(deltaMovements.get(i));
                }
            }
            if (moveList.isEmpty()) return packets;
            deltaMovements = moveList;
        }

        Vec3d originalPositionTick = context.tickFirstGoodVec.getValue();
        boolean riding = mc.player.hasVehicle();
        Entity rootEntity = mc.player.getRootVehicle();
        double deltaY = rootEntity.getY() - mc.player.getY();
        double minY = context.from.getValue().y;
        double maxY = minY;
        boolean currentOnGround = !considerNoFall && mc.player.isOnGround();
        Vec2f currentPitchYaw = new Vec2f(mc.player.getPitch(), mc.player.getYaw());
        // calculate current tokens if it is the first move of the tick
        if (context.currentTokenInTick.get() == 0) {
            // gain tokens
            int maxTokenNeeded = 0;
            Vec3d lastPos = originalPositionTick;
            int packetCount = 0;
            for (var move : deltaMovements) {
                packetCount += 1;
                Vec3d movingPos = move.vec3d();
                Vec3d movement = movingPos.subtract(lastPos);

                lastPos = movingPos;
                double d7 = movement.length();
                double d8 = movingPos.subtract(originalPositionTick).length();
                double d10 = Math.max(d7, d8);
                // 比如
                // 使用 9 9 9 9 9作为移动的， 每次packet + 1
                // 即使不用token,d10也不会超过packetNum * (...)

                int tokenNeeded = ((int) Math.ceil(d10 / 9.9)) - packetCount;
                maxTokenNeeded = Math.max(maxTokenNeeded, tokenNeeded);
            }
            //            Debug.info("check ", maxTokenNeeded);
            if (maxTokenNeeded > 0) {
                // 粗略估计
                // remove check, just do it 对的
                //                if(maxTokenNeeded > 20 - deltaMovements.size()){
                //                    //unable to reach so faraway
                ////                    Debug.info("UnReachable ");
                //                    return packets;
                //                }
                if (maxTokenNeeded > 20 - deltaMovements.size()) {
                    // unable to reach so faraway
                    //                    Debug.info("UnReachable ");
                    return packets;
                }
                for (int i = 0; i < maxTokenNeeded; ++i) {
                    context.currentTokenLimit.set(20);
                    context.currentTokenInTick.incrementAndGet();
                    //  Debug.chat("send zero packet !",context.currentTokenInTick.get(),
                    // context.currentTokenLimit.get());
                    if (riding) {
                        packets.get(emptyMoveCnt).add(VPacket.newVehicleMove(rootEntity));

                    } else {
                        packets.get(emptyMoveCnt).add(VPacket.newOnGroundOnly(currentOnGround, false));
                        //
                        // mc.getNetworkHandler().sendPacket(VPacket.newOnGroundOnly(mc.player.isOnGround()));
                    }
                }
            }
        }
        // calculate tokens depends on paper sourceocode
        for (int i = 0; i < deltaMovements.size(); ++i) {
            MovInfo info = deltaMovements.get(i);
            Vec3d fromNow = context.from.getValue();
            Vec3d vec3d = info.vec3d().subtract(fromNow);
            maxY = Math.max(maxY, info.vec3d().y);
            minY = Math.min(minY, info.vec3d().y);
            double len = vec3d.length();
            double len2 = originalPositionTick.subtract(info.vec3d()).length();
            double speedArg = 9.8;
            if (len == 0 && len2 == 0) {
                // d10 is 0 server side, can regain token
                context.currentTokenLimit.set(20);
                context.currentTokenInTick.incrementAndGet();
                if (info.oGroundOverride != null) {
                    currentOnGround = info.oGroundOverride;
                    final boolean of = currentOnGround;
                    packets.get(i + emptyMoveCnt).add(() -> mc.player.setOnGround(of));
                    //                    mc.player.setOnGround(info.oGroundOverride);
                }
                boolean hasRot =
                        info.rotationOverride != null && !Objects.equals(info.rotationOverride, currentPitchYaw);
                if (hasRot) {
                    currentPitchYaw = new Vec2f(info.rotationOverride.x, info.rotationOverride.y);
                    final float px = currentPitchYaw.x;
                    final float py = currentPitchYaw.y;
                    packets.get(i + emptyMoveCnt).add(() -> {
                        mc.player.setPitch(px);
                        mc.player.setYaw(py);
                    });
                }
                if (riding) {
                    packets.get(i + emptyMoveCnt).add(VPacket.newVehicleMove(rootEntity));
                } else {
                    if (hasRot) {
                        packets.get(i + emptyMoveCnt)
                                .add(VPacket.newLookAndOnGround(
                                        currentPitchYaw.y, currentPitchYaw.x, currentOnGround, false));
                    } else {
                        packets.get(i + emptyMoveCnt).add(VPacket.newOnGroundOnly(currentOnGround, false));
                    }
                }
            } else {
                int ExtraTokenNeeded = (int) Math.ceil(((Math.max(len, len2)) / speedArg));
                // +1代表这个包发出去之后的结果
                int tokenNow = context.currentTokenInTick.get() + 1;
                //            tokenLimit = Math.max(tokenLimit, 1);
                // 超出了tokenLimit, 会被强制重置为1（server side)
                // 需要把剩下的移动到下一个tick执行
                if ((Math.min(ExtraTokenNeeded, tokenNow) >= Math.max(5, context.currentTokenLimit.get()))) {
                    if (allowFailure
                            && context.currentTokenInTick.get() > Math.max(5, context.currentTokenLimit.get())) {
                        //
                        packets.get(i + emptyMoveCnt).failure();
                        if (considerNoFall && i > 0) {
                            //                            if (Math.abs(maxY - minY)
                            //                                    >
                            // mc.player.getAttributeValue(EntityAttributes.SAFE_FALL_DISTANCE) - 1) {

                            //                                mc.player.fallDistance = MovTasks.
                            // FORCE_RESET_DISTANCE;
                            // in case that resync packet cause OnGround falldamage
                            packets.get(i - 1 + emptyMoveCnt).addPost(() -> {
                                ClientPlayerAccess.of(mc.player).setForceNoFall(true);
                                mc.player.setOnGround(false);
                            });
                            // }
                        }
                        // return here because of MovingContext currentPosition
                        return packets;
                    }
                }
                context.currentTokenLimit.decrementAndGet();
                context.currentTokenInTick.incrementAndGet();
                //  Debug.chat("before move", context.currentTokenInTick.get(), context.currentTokenLimit.get());
                if (info.oGroundOverride() != null) {
                    currentOnGround = info.oGroundOverride();
                    final boolean of = currentOnGround;
                    packets.get(i + emptyMoveCnt).add(() -> mc.player.setOnGround(of));
                }
                boolean hasRot =
                        info.rotationOverride != null && !Objects.equals(info.rotationOverride, currentPitchYaw);
                if (hasRot) {
                    currentPitchYaw = new Vec2f(info.rotationOverride.x, info.rotationOverride.y);
                    final float px = currentPitchYaw.x;
                    final float py = currentPitchYaw.y;
                    packets.get(i + emptyMoveCnt).add(() -> {
                        mc.player.setPitch(px);
                        mc.player.setYaw(py);
                    });
                }
                Vec3d to = info.vec3d();
                if (riding) {
                    packets.get(i + emptyMoveCnt).add(() -> {
                        rootEntity.setPosition(to.add(0, deltaY, 0));
                        if (info.updatePlayer()) {
                            mc.player.setPosition(to);
                        }
                    });
                    packets.get(i + emptyMoveCnt).add(VPacket.newVehicleMove(rootEntity));
                    context.from.setValue(to);
                } else {
                    var packet = hasRot
                            ? VPacket.newFull(
                                    to.getX(),
                                    to.getY(),
                                    to.getZ(),
                                    currentPitchYaw.y,
                                    currentPitchYaw.x,
                                    currentOnGround,
                                    false)
                            : VPacket.newPositionAndOnGround(to.getX(), to.getY(), to.getZ(), currentOnGround, false);
                    packets.get(i + emptyMoveCnt).add(packet);
                    if (info.updatePlayer()) {
                        packets.get(i + emptyMoveCnt).add(() -> {
                            mc.player.setPosition(to);
                        });
                    }
                    context.from.setValue(to);
                }
            }
        }
        if (considerNoFall) {
            // if (Math.abs(maxY - minY) > mc.player.getAttributeValue(EntityAttributes.SAFE_FALL_DISTANCE) - 1) {
            packets.get(packets.size() - 1).add(() -> {
                ClientPlayerAccess.of(mc.player).setForceNoFall(true); // = MovTasks. FORCE_RESET_DISTANCE;
                // in case that resync packet cause OnGround falldamage
                mc.player.setOnGround(false);
            });
            // }
        }
        return packets;
    }
    private static boolean checkEnvironmentCollision(
            Entity entity, Vec3d pos, boolean checkLiquid, boolean ignoreChunkBorder) {
        // should also consider entity collision, shit shulker
        // set position for bounding box update!
        final List<Box> collisionsBB = new java.util.ArrayList<>();
        final List<VoxelShape> collisionsVoxel = new java.util.ArrayList<>();
        Box oldBox = entity.getBoundingBox();
        Box newBox = entity.dimensions.getBoxAt(pos);
        BiFunction<BlockState, BlockPos, Box> currentFromToPredicateFilter = checkLiquid
                ? (blockstate, blockpos) -> {
                    if (blockstate != null) {
                        Block block = blockstate.getBlock();
                        // I DO NOT WANT BY TP SEQUENCE ENTER ANY OF THESE FIRE BLOCKS BECAUSE IT MAY LIT ME UP!
                        if (block == Blocks.LAVA || block == Blocks.SOUL_FIRE || block == Blocks.FIRE) {
                            // filter lava blocks, we should be careful
                            Box lavaBlockBox = Box.enclosing(blockpos, blockpos);
                            if (CollisionUtil.voxelShapeIntersectHorizontal(lavaBlockBox, newBox)
                                    || CollisionUtil.voxelShapeIntersectHorizontal(lavaBlockBox, oldBox)) {
                                return lavaBlockBox;
                            }
                        }
                    }
                    return null;
                }
                : null;
        CollisionUtil.getCollisions(
                entity.getEntityWorld(),
                entity,
                newBox,
                collisionsVoxel,
                collisionsBB,
                // may cancel unloaded chunks?
                // COLLISION_FLAG_COLLIDE_WITH_UNLOADED_CHUNKS |
                ignoreChunkBorder
                        ? CollisionUtil.COLLISION_FLAG_CHECK_BORDER
                        : (COLLISION_FLAG_COLLIDE_WITH_UNLOADED_CHUNKS | CollisionUtil.COLLISION_FLAG_CHECK_BORDER),
                null,
                null,
                currentFromToPredicateFilter);

        for (int i = 0, len = collisionsBB.size(); i < len; ++i) {
            final Box box = collisionsBB.get(i);
            if (!CollisionUtil.voxelShapeIntersect(box, oldBox)) {
                return true;
            }
        }

        for (int i = 0, len = collisionsVoxel.size(); i < len; ++i) {
            final VoxelShape voxel = collisionsVoxel.get(i);
            if (!CollisionUtil.voxelShapeIntersectNoEmpty(voxel, oldBox)) {
                return true;
            }
        }
        return false;
    }
    public static void scheduleFarawayMoveInternal(
            List<MovInfo> deltaMovements, boolean allowNextTick, MovingContext context, boolean considerNoFall) {

        //        boolean shouldCheckSpeed = ! mc.player.isGliding() || (!
        // mc.world.getGameRules().getBoolean(GameRules.DISABLE_ELYTRA_MOVEMENT_CHECK));
        //        double maxOnceLen = (mc.player.isGliding()? (10 * Math.sqrt(3)) : 10) - 1E-2;
        // filter front not-needed packets
        if (true) {
            List<StepActionBundle> bundleList =
                    createMovingPacketsForMovSequence(context, deltaMovements, allowNextTick, considerNoFall);
            for (int i = 0; i < bundleList.size(); ++i) {
                if (bundleList.get(i).success) {
                    bundleList.get(i).run();
                } else {
                    if (allowNextTick) {
                        List<MovInfo> leftTasks = deltaMovements.subList(i, deltaMovements.size());
                        Tasks.scheduleDelayed(
                                () -> {
                                    scheduleFarawayMoveInternal(
                                            leftTasks, allowNextTick, context.resetTick(), considerNoFall);
                                },
                                1);
                        return;
                    } else {
                        bundleList.get(i).run();
                    }
                }
            }
            return;
        }
    }
    public static EntityMovementStatus<ClientPlayerEntity> startSimulation() {
        return new EntityMovementStatus<>(mc.player);
    }
    public static double searchFirstNoYConflictYHeight(
            double originY,
            List<Box> collisionsBB,
            List<VoxelShape> collisionShapes,
            double min,
            double max,
            boolean upOrDown) {
        List<Box> allBoxes = new ArrayList<>(collisionsBB);
        for (VoxelShape shape : collisionShapes) {
            allBoxes.addAll(shape.getBoundingBoxes());
        }
        allBoxes.sort(Comparator.comparingDouble(b -> b.minY * (upOrDown ? 1.0D : -1.0D)));
        DEBUG_RENDER_COLLISION_RENDERING = true;
        STATIC_DEBUG_COLOR = Color.BLUE;
        for (var box : allBoxes) {
            debugBox(box);
        }
        DEBUG_RENDER_COLLISION_RENDERING = false;
        if (upOrDown) {
            // up sort minY from small to big
            double levelY = originY + min;
            // give 1E-7 more space
            double playerBoxHeight = mc.player.dimensions.height() + 2E-7;
            // double lastStableY;
            for (var re : allBoxes) {
                // 高度差上已经有碰撞了
                if (levelY + playerBoxHeight > re.minY && levelY < re.maxY) {
                    if (re.maxY < originY + max) {
                        levelY = re.maxY;
                    } else {
                        // new Y out of bound
                        break;
                    }
                } else if (levelY + playerBoxHeight <= re.minY) {
                    // 搜索结束
                    // 因为按re.minY从小到大排,这个位置将不会和任意后面的碰撞
                    break;
                }
            }
            // fixme returning y value out of bound (? need test, havn't seen problem )
            return levelY - originY;
        } else {
            double levelY = originY - min;
            double playerBoxHeight = mc.player.dimensions.height() + 2E-7;
            // 由大到小排
            for (var re : allBoxes) {
                // 高度差上已经有碰撞了
                if (levelY < re.maxY && levelY + playerBoxHeight > re.minY) {
                    double newLevelY = re.minY - playerBoxHeight;
                    if (newLevelY > originY - max) {
                        levelY = newLevelY;
                    } else {
                        // new Y out of bound
                        break;
                    }
                } else if (levelY >= re.maxY) {
                    // 搜索结束
                    break;
                }
            }
            return levelY - originY;
        }
    }
    public static boolean validMovementAsServer(Vec3d expect, Vec3d sim) {
        return mc.interactionManager.getCurrentGameMode().isCreative()
                || MathUtils.s2(expect.x - sim.x) + MathUtils.s2(expect.z - sim.z) < 0.0625D;
    }
    public static Vec3d collide(Entity entity, Vec3d movement) {
        // Paper start - optimise collisions
        final boolean xZero = movement.x == 0.0;
        final boolean yZero = movement.y == 0.0;
        final boolean zZero = movement.z == 0.0;
        if (xZero & yZero & zZero) {
            return movement;
        }
        final double stepHeight = (double) entity.getStepHeight();

        final Box currBoundingBox = entity.getBoundingBox();
        final List<Box> potentialCollisionsBB = new ArrayList<>();
        final List<VoxelShape> potentialCollisionsVoxel = new ArrayList<>();
        collectBoxInvolvingInMovements(
                entity, entity.getEntityPos(), movement, potentialCollisionsBB, potentialCollisionsVoxel, true);
        //        if (CollisionUtil.isEmpty(currBoundingBox)) {
        //            return movement;
        //        }
        //        Box collisionBox = makeCollectorBoxInvolvingCollision(currBoundingBox, movement, stepHeight,
        // onGround);
        //
        //        CollisionUtil.getCollisions(
        //            world, entity, collisionBox, potentialCollisionsVoxel, potentialCollisionsBB,
        //            CollisionUtil.COLLISION_FLAG_CHECK_BORDER,
        //            null, null, null
        //        );
        return collideWithTrustedList(
                currBoundingBox,
                movement,
                potentialCollisionsVoxel,
                potentialCollisionsBB,
                stepHeight,
                entity.isOnGround());
        // Paper end - optimise collisions
    }
    private static void collectBoxInvolvingInMovements(
            Entity entity,
            Vec3d startPos,
            Vec3d movement,
            List<Box> intoAABB,
            List<VoxelShape> intoVoxels,
            boolean ignoreUnloadedChunk) {
        final Box currBoundingBox = entity.dimensions.getBoxAt(startPos);
        if (CollisionUtil.isEmpty(currBoundingBox)) return;
        Box collisionBox = makeCollectorBoxInvolvingCollision(
                currBoundingBox, movement, entity.getStepHeight(), entity.isOnGround());
        CollisionUtil.getCollisions(
                entity.getEntityWorld(),
                entity,
                collisionBox,
                intoVoxels,
                intoAABB,
                ignoreUnloadedChunk
                        ? COLLISION_FLAG_CHECK_BORDER
                        : (COLLISION_FLAG_CHECK_BORDER | COLLISION_FLAG_COLLIDE_WITH_UNLOADED_CHUNKS),
                null,
                null,
                null);
        if (DEBUG_RENDER_COLLISION_RENDERING) {
            for (var coll : intoAABB) {
                debugBox(coll);
            }
            for (var voxel : intoVoxels) {
                for (var box : voxel.getBoundingBoxes()) {

                    debugBox(box);
                }
            }
        }
    }
    public static Box makeCollectorBoxInvolvingCollision(
            Box currBoundingBox, Vec3d movement, double stepHeight, boolean onGround) {

        final boolean xZero = movement.x == 0.0;
        final boolean yZero = movement.y == 0.0;
        final boolean zZero = movement.z == 0.0;
        final Box collisionBox;

        if (xZero & zZero) {
            if (movement.y > 0.0) {
                collisionBox = CollisionUtil.cutUpwards(currBoundingBox, movement.y);
            } else {
                collisionBox = CollisionUtil.cutDownwards(currBoundingBox, movement.y);
            }
        } else {
            // note: xZero == false or zZero == false
            // stepheight check 1.21.6
            if (!getMovExtra().noStepHeightFeature.get() && stepHeight > 0.0 && (onGround || (movement.y < 0.0))) {
                // don't bother getting the collisions if we don't need them.
                if (movement.y <= 0.0) {
                    collisionBox = CollisionUtil.expandUpwards(
                            currBoundingBox.stretch(movement.x, movement.y, movement.z), stepHeight);
                } else {
                    collisionBox = currBoundingBox.stretch(movement.x, Math.max(stepHeight, movement.y), movement.z);
                }
            } else {
                collisionBox = currBoundingBox.stretch(movement.x, movement.y, movement.z);
            }
        }
        return collisionBox;
    }
    public static Vec3d collideWithTrustedList(
            Box currBoundingBox,
            Vec3d movement,
            List<VoxelShape> potentialCollisionsVoxel,
            List<Box> potentialCollisionsBB,
            double stepHeight,
            boolean onGround) {
        if (potentialCollisionsVoxel.isEmpty() && potentialCollisionsBB.isEmpty()) {
            INTERNAL_VALUE_USE_STEPHEIGHT.set(false);
            return movement;
        }

        STATIC_DEBUG_COLOR = Color.YELLOW;
        final Vec3d limitedMoveVector = CollisionUtil.performCollisions(
                movement, currBoundingBox, potentialCollisionsVoxel, potentialCollisionsBB);

        if (!getMovExtra().noStepHeightFeature.get()
                && stepHeight > 0.0
                && (onGround || (limitedMoveVector.y != movement.y && movement.y < 0.0))
                && (limitedMoveVector.x != movement.x || limitedMoveVector.z != movement.z)) {
            // stepheight cause movement invalid in higher version
            // mark as not planned in 1.21.1; will do it in 1.21.6 version
            // auto jump to go across, do not move
            STATIC_DEBUG_COLOR = Color.BLUE;
            Vec3d vec3d2 = CollisionUtil.performCollisions(
                    new Vec3d(movement.x, stepHeight, movement.z),
                    currBoundingBox,
                    potentialCollisionsVoxel,
                    potentialCollisionsBB);
            boolean debug = RenderTasks.DEBUG_RENDER_COLLISION_RENDERING;
            RenderTasks.DEBUG_RENDER_COLLISION_RENDERING = false;
            // speed up jumping head

            final Vec3d vec3d3 = CollisionUtil.performCollisions(
                    new Vec3d(0.0, stepHeight, 0.0),
                    currBoundingBox.stretch(movement.x, 0.0, movement.z),
                    potentialCollisionsVoxel,
                    potentialCollisionsBB);

            if (vec3d3.y < stepHeight) {
                final Vec3d vec3d4 = CollisionUtil.performCollisions(
                                new Vec3d(movement.x, 0.0D, movement.z),
                                currBoundingBox.offset(vec3d3),
                                potentialCollisionsVoxel,
                                potentialCollisionsBB)
                        .add(vec3d3);

                if (vec3d4.horizontalLengthSquared() > vec3d2.horizontalLengthSquared()) {
                    vec3d2 = vec3d4;
                }
            }
            RenderTasks.DEBUG_RENDER_COLLISION_RENDERING = debug;
            if (vec3d2.horizontalLengthSquared() > limitedMoveVector.horizontalLengthSquared()) {
                //                STATIC_DEBUG_COLOR = Color.PINK;
                INTERNAL_VALUE_USE_STEPHEIGHT.set(true);
                return vec3d2.add(CollisionUtil.performCollisions(
                        new Vec3d(0.0D, -vec3d2.y + movement.y, 0.0D),
                        currBoundingBox.offset(vec3d2),
                        potentialCollisionsVoxel,
                        potentialCollisionsBB));
            }
            INTERNAL_VALUE_USE_STEPHEIGHT.set(false);
            return limitedMoveVector;
        } else {
            INTERNAL_VALUE_USE_STEPHEIGHT.set(false);
            return limitedMoveVector;
        }
    }
    public static record MovInfo(Vec3d vec3d, Boolean oGroundOverride, boolean updatePlayer, Vec2f rotationOverride) {
        public static MovInfo create(Vec3d to) {
            return new MovInfo(to, null, true, null);
        }

        public static MovInfo createNotOnGround(Vec3d to) {
            return new MovInfo(to, Boolean.FALSE, true, null);
        }

        public static MovInfo createNoUpdate(Vec3d to) {
            return new MovInfo(to, null, false, null);
        }

        public boolean isEmptyTo(Vec3d currentPos) {
            return vec3d.squaredDistanceTo(currentPos) < 1e-4 && rotationOverride == null && oGroundOverride == null;
        }
    }
    public static record MovingContext(
            MutableObject<Vec3d> from,
            MutableObject<Vec3d> tickFirstGoodVec,
            AtomicInteger currentTokenLimit,
            AtomicInteger currentTokenInTick) {
        public static MovingContext create(Vec3d from) {
            return new MovingContext(
                    new MutableObject<>(from), new MutableObject<>(from), new AtomicInteger(0), new AtomicInteger(0));
        }

        public MovingContext resetTick() {
            currentTokenInTick.set(0);

            currentTokenLimit.set(0);
            tickFirstGoodVec.setValue(from.getValue());
            return this;
        }
    }
    public static final class StepActionBundle {
        public ArrayList<Packet<?>> packets = new ArrayList<>(4);
        public ArrayList<Runnable> tasks = new ArrayList<>(1);
        public ArrayList<Runnable> postTasks = new ArrayList<>(1);
        public boolean success = true;

        public void failure() {
            success = false;
        }

        public StepActionBundle() {}

        public void add(Packet<?> packet) {
            packets.add(packet);
        }

        public void add(Runnable task) {
            tasks.add(task);
        }

        public void addPost(Runnable task) {
            postTasks.add(task);
        }

        public void run() {
            tasks.forEach(Runnable::run);
            for (var packet : packets) {
                mc.getNetworkHandler().sendPacket(packet);
            }
            postTasks.forEach(Runnable::run);
            // post move task
        }
    }
    public static final CollisionContext ENGIN = new CollisionContext() {};
    public static final CollisionContext ENGIN_LOADED = new CollisionContext() {
        @Override
        public boolean checkEnvironmentCollision(Entity entity, Vec3d vec, boolean checkLiquid) {
            return MovTasks.checkEnvironmentCollision(entity, vec, checkLiquid, false);
        }
    };

    public static interface CollisionContext {
        default Vec3d simulateMovement(Entity entity, Vec3d currentPos, Vec3d currentTry) {
            return MovTasks.simulateMovement(entity, currentPos, currentTry, true);
        }

        default boolean checkEnvironmentCollision(Entity entity, Vec3d vec, boolean checkLiquid) {
            return MovTasks.checkEnvironmentCollision(entity, vec, checkLiquid, true);
        }

        default List<Vec3d> generateTpSequence(
                Vec3d current, Vec3d target, boolean command, double farawayTp, boolean considerEnvironment) {
            return MovTasks.generateTpSequenceInternal(current, target, command, farawayTp, considerEnvironment);
        }
        // todo: add raycast methods
    }


    private static List<Vec3d> generateTpSequenceInternal(
            Vec3d current, Vec3d target, boolean command, double farawayTp, boolean considerEnvironment) {
        boolean collideAtTarget = checkEnvironmentCollision(mc.player, target, false, true);
        if (collideAtTarget) {
            if (command) {
                Debug.chat("目标位置存在方块碰撞冲突, 无法执行tp");
            }
            return List.of();
        }

        Vec3d movement0 = target.subtract(current);
        Vec3d sim = simulateMovement(mc.player, current, movement0, true);
        if (validMovementAsServer(movement0, sim)) {
            return List.of(current, current.add(movement0));
        }
        Vec3d horizontalMovement;
        horizontalMovement = new Vec3d(movement0.x, 0, movement0.z);

        double len = horizontalMovement.length();
        //        Box tpHorizontalPlate = mc.player.dimensions.getBoxAt(current).stretch(horizontalMovement);
        Box tpSmallerAxisPlate;
        Box tpLargerAxisPlate;
        if (Math.abs(movement0.x) > Math.abs(movement0.z)) {
            tpSmallerAxisPlate = mc.player.dimensions.getBoxAt(current).stretch(movement0.x, 0, 0);
            tpLargerAxisPlate = mc.player
                    .dimensions
                    .getBoxAt(current)
                    .offset(movement0.x, 0, 0)
                    .stretch(0, 0, movement0.z);
        } else {
            tpSmallerAxisPlate = mc.player.dimensions.getBoxAt(current).stretch(0, 0, movement0.z);
            tpLargerAxisPlate = mc.player
                    .dimensions
                    .getBoxAt(current)
                    .offset(0, 0, movement0.z)
                    .stretch(movement0.x, 0, 0);
        }

        double currentY = current.y;
        double targetY = target.y;
        net.minecraft.world.World world = mc.world;
        double horizontalY;

        if (len <= farawayTp) {
            if (currentY >= world.getBottomY() && currentY <= world.getBottomY() + world.getHeight()) {
                Box boundariesFromBox = mc.player.dimensions.getBoxAt(current);
                Box boundariesToBox = mc.player.dimensions.getBoxAt(target);
                Box boundariesSmallAxis;
                Box boundariesLargeAxis;
                boundariesSmallAxis = CollisionUtil.resetY(
                        tpSmallerAxisPlate, world.getBottomY(), world.getBottomY() + world.getHeight());
                boundariesLargeAxis = CollisionUtil.resetY(
                        tpLargerAxisPlate, world.getBottomY(), world.getBottomY() + world.getHeight());
                boolean debug0 = DEBUG_RENDER_COLLISION_RENDERING;
                // DEBUG_RENDER_COLLISION_RENDERING = true;
                STATIC_DEBUG_COLOR = Color.CYAN;
                debugBox(tpSmallerAxisPlate);
                debugBox(tpLargerAxisPlate);
                STATIC_DEBUG_COLOR = Color.MAGENTA;
                debugBox(boundariesSmallAxis);
                debugBox(boundariesLargeAxis);
                //  DEBUG_RENDER_COLLISION_RENDERING = debug0;
                //
                ArrayList<Box> intoAABB = new ArrayList<>();
                ArrayList<VoxelShape> intoVoxels = new ArrayList<>();
                // should add LAVA with current/target xz coord into to avoid searching TP into them
                BiFunction<BlockState, BlockPos, Box> currentFromToPredicateFilter = considerEnvironment
                        ? (blockstate, blockpos) -> {
                            if (blockstate != null) {
                                Block block = blockstate.getBlock();
                                // I DO NOT WANT BY TP SEQUENCE ENTER ANY OF THESE FIRE BLOCKS BECAUSE IT MAY LIT ME UP!
                                if (block == Blocks.LAVA || block == Blocks.SOUL_FIRE || block == Blocks.FIRE) {
                                    // filter lava blocks, we should be careful
                                    Box lavaBlockBox = Box.enclosing(blockpos, blockpos);
                                    if (CollisionUtil.voxelShapeIntersectHorizontal(lavaBlockBox, boundariesFromBox)
                                            || CollisionUtil.voxelShapeIntersectHorizontal(
                                                    lavaBlockBox, boundariesToBox)) {
                                        return lavaBlockBox;
                                    }
                                }
                            }
                            return null;
                        }
                        : null;
                CollisionUtil.getCollisions(
                        world,
                        mc.player,
                        boundariesLargeAxis,
                        intoVoxels,
                        intoAABB,
                        COLLISION_FLAG_CHECK_BORDER,
                        null,
                        null,
                        currentFromToPredicateFilter);
                CollisionUtil.getCollisions(
                        world,
                        mc.player,
                        boundariesSmallAxis,
                        intoVoxels,
                        intoAABB,
                        COLLISION_FLAG_CHECK_BORDER,
                        null,
                        null,
                        currentFromToPredicateFilter);
                debug0 = DEBUG_RENDER_COLLISION_RENDERING;
                DEBUG_RENDER_COLLISION_RENDERING = true;
                STATIC_DEBUG_COLOR = Color.BLUE;
                for (var box : intoAABB) {
                    if (box.minY > 20) debugBox(box);
                }
                if (considerEnvironment) {
                    // some blocks are dangerous , so we should consider them as not passable
                    // for example LAVA , shit LAVA
                }
                DEBUG_RENDER_COLLISION_RENDERING = debug0;
                STATIC_DEBUG_COLOR = Color.CYAN;
                if (!intoAABB.isEmpty() || !intoVoxels.isEmpty()) {
                    //                        Box startPlace;
                    //                        Vec3d movement ;
                    Vec3d result;
                    //                        Box lastPlace;
                    double height1 = searchFirstNoYConflictYHeight(currentY, intoAABB, intoVoxels, 0, 180, true);
                    double height2 = searchFirstNoYConflictYHeight(currentY, intoAABB, intoVoxels, 0, 180, false);
                    //                        Debug.chat(height1, height2);
                    double height;
                    if (Math.abs(height1) > Math.abs(height2)) {
                        height = height2;
                    } else {
                        height = height1;
                    }
                    result = new Vec3d(0, height, 0);
                    boolean debug = DEBUG_RENDER_COLLISION_RENDERING;
                    RenderTasks.DEBUG_RENDER_COLLISION_RENDERING = true;
                    RenderTasks.STATIC_DEBUG_COLOR = Color.WHITE;
                    debugBox(tpSmallerAxisPlate.offset(0, height, 0));
                    debugBox(tpLargerAxisPlate.offset(0, height, 0));
                    RenderTasks.DEBUG_RENDER_COLLISION_RENDERING = debug;
                    horizontalY = height + currentY;

                    // tp
                } else {
                    horizontalY = currentY;
                }
                // small use
                //

            } else {
                horizontalY = currentY;
            }
        } else {
            if (command) Debug.chat("水平差距过大,当前tp模式无法完成");
            return List.of();
        }
        if (Math.abs(horizontalY - currentY) > maxYDelta || Math.abs(horizontalY - targetY) > maxYDelta) {
            if (command) Debug.chat("y 差距过大,当前tp模式无法完成");
            return List.of();
        }
        Vec3d vec3d1 = current;
        Vec3d vec3d2 = vec3d1.add(new Vec3d(0, horizontalY - currentY, 0));
        Vec3d vec3d3 = vec3d2.add(horizontalMovement);
        Vec3d vec3d4 = vec3d3.add(new Vec3d(0, targetY - horizontalY, 0));
        return List.of(vec3d1, vec3d2, vec3d3, vec3d4);
    }
private static final double maxYDelta=190;private static final Random rand=Random.create();private static final ThreadLocal<Boolean> INTERNAL_VALUE_USE_STEPHEIGHT=ThreadLocal.withInitial(()->false);
public static NoSlowDown getNoSlowDown(){return NoSlowDown.INSTANCE;} public static AutoResync getAutoResync(){return AutoResync.INSTANCE;}
public static boolean doingTp;
    public static void scheduleTpInternal(
            MovingContext context,
            Vec3d target,
            double farawayThreshold,
            boolean command,
            boolean fastMode,
            boolean considerNoFall) {
        if (mc.player == null) return;
        if (command) Debug.chat("正在向", ChatUtils.getDisplayedLocationDouble(target), "执行tp行为");
        Vec3d current = context.from.getValue();
        // simulate direct move
        Vec3d movement0 = target.subtract(current);
        // flying into lava fuck, so we consider Environment
        List<Vec3d> vc3d0 = generateTpSequence(current, target, command, farawayThreshold, true);
        if (vc3d0.size() == 2) {
            boolean downward = movement0.y < -4;
            Vec3d target0 = vc3d0.get(1);
            MovInfo mainMove = new MovInfo(target0, downward ? Boolean.FALSE : null, true, null);
            List<MovInfo> movements =
                    // downward ? List.of(mainMove, MovInfo.create(target0.add(0, 9E-8,0))):
                    List.of(mainMove);
            // direct tp should also consider about setBack falldistance, passing considerNoFall arguments to do that
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
            context.from.setValue(vec3d1);
            //            boolean currentOnGround = mc.player.isOnGround();
            mc.player.setOnGround(false);
            if (fastMode) {
                List<MovInfo> movingPositions = new ArrayList<>();
                movingPositions.add(MovInfo.createNotOnGround(vec3d2));
                if (downWard) movingPositions.add(MovInfo.create(vec3d2));
                movingPositions.add(MovInfo.create(
                        vec3d3
                        //  .add(0, downWard ? 9E-8: 0,0)
                        ));
                movingPositions.add(MovInfo.createNotOnGround(vec3d4));
                //                if(downWard0)
                //                    movingPositions.add();
                if (downWard0) movingPositions.add(MovInfo.create(vec3d4));
                scheduleFarawayMoveInternal(movingPositions, true, context, considerNoFall);
            } else {
                context.from.setValue(vec3d1);
                // it should be ignored consider No Fall
                scheduleFarawayMoveInternal(List.of(MovInfo.createNotOnGround(vec3d2)), false, context, true);
                // farawayMoveFromTo(vec3d1 , vec3d2, downWard? Boolean.FALSE: null, true);
                doingTp = true;

                Tasks.scheduleDelayed(
                        () -> {
                            doingTp = false;
                            scheduleFarawayMoveInternal(
                                    List.of(MovInfo.create(vec3d3)), false, context.resetTick(), false);
                            // farawayMoveFromTo(vec3d2, vec3d3, null, true);
                            doingTp = true;
                        },
                        2);
                ;
                Tasks.scheduleDelayed(
                        () -> {
                            doingTp = false;
                            scheduleFarawayMoveInternal(
                                    List.of(downWard0 ? MovInfo.createNotOnGround(vec3d4) : MovInfo.create(vec3d4)),
                                    false,
                                    context.resetTick(),
                                    false);
                            // reset fall distance after scheduleTpInternal
                            if (considerNoFall && downWard0 && mc.player != null) {
                                ClientPlayerAccess.of(mc.player).setForceNoFall(true);
                                mc.player.setOnGround(false);
                            }
                            //                    farawayMoveFromTo(vec3d3, vec3d4,  downWard0? Boolean.FALSE: null,
                            // true);
                            //                    if(downWard0)
                            //
                            // scheduleFarawayMoveInternal(Iterators.singletonIterator(MovInfo.create(vec3d4.add(0,
                            // 9E-8, 0))), false, context);
                        },
                        3);
            }

        } else {
            return;
        }

        if (command) Debug.chat("tp行为已经执行, 若出现回弹或者位置不变,则目标位置不可达");

        //        if(currentY > world.getBottomY() + 64){
        //            //most likely
        //            tpHorizontalPlate = world.getBottomY() + world.getHeight()
        //        }
    }
    public static MovingContext createPlayerMovContext() {
        return MovingContext.create(mc.player.getEntityPos());
    }
    public static class CollisionCache implements CollisionContext {
        Entity entity;
        Vec3d startPos;
        Vec3d endPos;
        List<Box> intoAABBs;
        List<VoxelShape> intoVoxels;
        List<Box> allBoxes;
        boolean ignoreChunkBorder;

        public CollisionCache(Entity entity, Vec3d startPos, Vec3d endPos, boolean ignoreChunkBorder) {
            this.entity = entity;
            this.startPos = startPos;
            this.endPos = endPos;
            this.ignoreChunkBorder = ignoreChunkBorder;
            intoAABBs = new ArrayList<>();
            intoVoxels = new ArrayList<>();
            collectBoxInvolvingInMovements(
                    entity, startPos, endPos.subtract(startPos), this.intoAABBs, this.intoVoxels, ignoreChunkBorder);
            // makeCollectorBoxInvolvingCollision(entity.dimensions.getBoxAt(startPos), endPos.subtract(startPos))
            // collected all aabbs
            allBoxes = new ArrayList<>();
            allBoxes.addAll(intoAABBs);
            for (var voxel : intoVoxels) {
                allBoxes.addAll(voxel.getBoundingBoxes());
            }
        }

        public Vec3d simulateMovement(Entity entity, Vec3d currentPos, Vec3d currentTry) {
            Entity rootEntity = entity.getRootVehicle();
            //  double y = entity.getY() - rootEntity.getY();
            //            Vec3d originRoot = rootEntity.getEntityPos();
            //            rootEntity.setPosition(currentPos);
            RenderTasks.DEBUG_RENDER_COLLISION_RENDERING = true;
            Vec3d simu = collideWithTrustedList(
                    rootEntity.dimensions.getBoxAt(currentPos),
                    currentTry,
                    this.intoVoxels,
                    this.intoAABBs,
                    rootEntity.getStepHeight(),
                    rootEntity.isOnGround());
            RenderTasks.DEBUG_RENDER_COLLISION_RENDERING = false;
            //            rootEntity.setPosition(originRoot);
            return simu;
        }

        public boolean checkEnvironmentCollision(Entity entity, Vec3d vec, boolean checkLiquid) {
            return MovTasks.checkEnvironmentCollision(entity, vec, checkLiquid, this.ignoreChunkBorder);
        }

        @Override
        public List<Vec3d> generateTpSequence(
                Vec3d current, Vec3d target, boolean command, double farawayTp, boolean considerEnvironment) {
            return CollisionContext.super.generateTpSequence(current, target, command, farawayTp, considerEnvironment);
        }
    }

    public static boolean validMoveTo(CollisionContext engin, Vec3d currentPos, Vec3d currentTry) {
        // fixme: currentPos may not be a suitable place for player to stay
        if (engin.checkEnvironmentCollision(mc.player, currentPos, false)) {
            return false;
        }
        Vec3d testMov = engin.simulateMovement(mc.player, currentPos, currentTry);

        if (validMovementAsServer(currentTry, testMov)) {
            Vec3d currentForward = currentPos.add(currentTry);
            // check collision for safety
            if (!engin.checkEnvironmentCollision(mc.player, currentForward, false)) {
                return true;
            }
        }
        return false;
    }

    public static boolean validMoveToAndBack(CollisionContext engin, Vec3d currentPos, Vec3d currentTry) {
        // fixme: currentPos may not be a suitable place for player to stay
        if (engin.checkEnvironmentCollision(mc.player, currentPos, false)) {
            return false;
        }
        Vec3d testMov = engin.simulateMovement(mc.player, currentPos, currentTry);

        if (validMovementAsServer(currentTry, testMov)) {
            // use expected position as server success position
            Vec3d currentForward = currentPos.add(currentTry);
            // check collision for safety
            if (!engin.checkEnvironmentCollision(mc.player, currentForward, false)) {
                Vec3d backMov = Vec3d.ZERO.subtract(currentTry);
                Vec3d testBackMov = engin.simulateMovement(mc.player, currentForward, backMov);
                if (validMovementAsServer(backMov, testBackMov)) {
                    return true;
                }
            }
        }
        return false;
    }

public static void scheduleMoveSequence(
            MovingContext context, List<MovInfo> deltaMovements, boolean allowNextTick, boolean noFall) {
        scheduleFarawayMoveInternal(deltaMovements, allowNextTick, context, noFall);
    }
}
