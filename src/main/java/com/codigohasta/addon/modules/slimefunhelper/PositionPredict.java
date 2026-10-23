package com.codigohasta.addon.modules.slimefunhelper;
import com.codigohasta.addon.utils.slimefunhelper.hacks.api.*;
import com.codigohasta.addon.utils.slimefunhelper.managers.config.*;
import com.codigohasta.addon.utils.slimefunhelper.events.*;
import com.codigohasta.addon.utils.slimefunhelper.managers.Tasks;
import com.codigohasta.addon.utils.slimefunhelper.utils.*;
import net.minecraft.util.math.*;


import com.codigohasta.addon.utils.slimefunhelper.accessors.hacks.EntityInternalAccess;
import com.codigohasta.addon.utils.slimefunhelper.accessors.hacks.PlayerInternalAccess;
import com.codigohasta.addon.utils.slimefunhelper.hacks.utils.entity.EntityMovementStatus;
import com.codigohasta.addon.utils.slimefunhelper.utils.entity.PlayerInputUtils;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.client.util.math.MatrixStack;

import java.util.*;
import com.codigohasta.addon.utils.slimefunhelper.hacks.utils.entity.PredictorImpl;
import com.codigohasta.addon.utils.slimefunhelper.hacks.utils.entity.Predictor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ShieldItem;
import net.minecraft.entity.mob.ShulkerEntity;
import com.codigohasta.addon.utils.slimefunhelper.hacks.MovTasks;
public class PositionPredict extends BaseModule {
    public static PositionPredict INSTANCE;

    public final NBTRef<PredictArgument> attackPredictArgument=builder(new ModulePath("prediction.attack"),PredictArgument.class).defaultValue(new PredictArgument(2,5,Mode.NO_PREDICT)).build();
    public final NBTRef<PredictArgument> flyPredictArgument=builder(new ModulePath("prediction.fly"),PredictArgument.class).defaultValue(new PredictArgument(2,5,Mode.PREDICTOR_NV)).build();
    public final NBTRef<PredictArgument> spearPredictArgument=builder(new ModulePath("prediction.spear"),PredictArgument.class).defaultValue(new PredictArgument(2,5,Mode.PREDICTOR_NV)).build();
    public PositionPredict(){super("PositionPredict");INSTANCE=this;}
    public Predictor getPredictor(Entity entity) {
        return EntityInternalAccess.of(entity).getPositionPredictor();
    }

    public Vec3d predictKnownMovement(Entity entity) {
        return EntityInternalAccess.of(entity).getPositionPredictor().getKnownDeltaMovement();
    }

    public void onPostEntity(Event<EntityS2CPacket> event) {
        if (checkNull()) return;
        if (event.context.getEntity(mc.world) instanceof PlayerInternalAccess internal) {
            internal.getPredictorImpl().onEntityPositionMove(event);
            onPlayerEntityUpdate((PlayerEntity) internal);
        }
    }

    public void onPostEntityPos(Event<EntityPositionS2CPacket> event) {
        if (checkNull()) return;
        if (mc.world.getEntityById(event.context.entityId()) instanceof PlayerInternalAccess internal) {
            internal.getPredictorImpl().onEntityPositionPost(event);
            onPlayerEntityUpdate((PlayerEntity) internal);
        }
    }

    public void onPostEntityTeleport(Event<EntityPositionSyncS2CPacket> event) {
        if (checkNull()) return;
        if (mc.world.getEntityById(event.context.id()) instanceof PlayerInternalAccess internal) {
            internal.getPredictorImpl().onEntityPositionSyncPost(event);
            onPlayerEntityUpdate((PlayerEntity) internal);
        }
    }

    public void onPlayerEntityUpdate(PlayerEntity player) {
        if (placeRecorder.get()) {
            recordedPoints
                    .computeIfAbsent(player.getId(), (v) -> new ArrayList<>())
                    .add(player.getEntityPos());
        }
    }

    public Vec3d predictPlayerMove(PlayerInputUtils.Input input) {
        EntityMovementStatus<Entity> entityMovementStatus = new EntityMovementStatus<>(mc.player);
        if (mc.player.isGliding()) {
            return mc.player.getVelocity();
        } else if (mc.player.isInFluid()) {
            return mc.player.getVelocity();
        } else {
            return entityMovementStatus.calculateLastMoveVelocity(input.forwardSpeed(), input.sidewaysSpeed());
        }
    }

    public Vec3d predictAimPositionForEntity(Entity entity, float finalVelocity) {
        Vec3d estimatedDelta = entity.getEntityPos().subtract(mc.player.getEntityPos());
        double estimateSpeed = estimatedDelta.length() / (finalVelocity);
        int estimateTick;
        if (estimateSpeed < 2.0) {
            estimateTick = 0;
        } else if (estimateSpeed > 20.0) {
            estimateTick = 20;
        } else {
            estimateTick = (int) (estimateSpeed - 2.0D);
        }

        return entity.getEyePos()
                .subtract(entity.getEntityPos())
                .multiply(0.75)
                .add(flyPredictArgument.get().predictWithExtraTicks(entity, estimateTick));
    }
    public void reset(){recordedPoints.clear();}
    public record PredictArgument(double ticksLater,int ticksHistory,Mode mode){
        public Vec3d predict(Entity entity){return predict0(entity,ticksLater);}
    public Vec3d predict0(Entity entity, double ticksLater) {
            int floor = (int) Math.floor(ticksLater);
            Vec3d floorPos =
                    EntityInternalAccess.of(entity).getPositionPredictor().predict(floor, mode.ordinal(), ticksHistory);
            if (Math.abs(floor - ticksLater) < 1E-2) {
                return floorPos;
            }
            Vec3d roofPos = EntityInternalAccess.of(entity)
                    .getPositionPredictor()
                    .predict(floor + 1, mode.ordinal(), ticksHistory);
            return floorPos.multiply(floor + 1 - ticksLater).add(roofPos.multiply(ticksLater - floor));
        }
        public Vec3d predictWithExtraTicks(Entity entity,double ticks){return predict0(entity,ticksLater+ticks);}
    }
    public enum Mode implements ConfigEnum {NO_PREDICT,LINEAR,QUADRATIC,PREDICTOR_NV,PREDICTOR_ROTATION,PREDICTOR_ACCELERATE;public String getConfigEnumType(){return "predict_mode";}}
public final FlagRef enableNoShield=flagBuilder(new ModulePath("position-predict.exact-tp-anti-shield")).build();
    public Vec3d getExactAttackPosition(Entity target) {
        if (mc.player == null) return null;
        if (target instanceof ShulkerEntity) {
            // consider wtf shit , this entity collides with player
            // consider all collisions use bounding box not directions
            Vec3d vec3 = target.getEntityPos();
            //            BlockPos posAt = BlockPos.ofFloored(vec3);
            Box boundingBox = target.getBoundingBox();
            for (Direction dir : Direction.values()) {

                Vec3d testPos =
                        switch (dir) {
                            case UP -> vec3.withAxis(Direction.Axis.Y, boundingBox.maxY + 0.1);
                            case DOWN -> vec3.withAxis(Direction.Axis.Y, boundingBox.minY - 2);
                            case NORTH -> vec3.withAxis(Direction.Axis.Z, boundingBox.minZ - 0.5);
                            case SOUTH -> vec3.withAxis(Direction.Axis.Z, boundingBox.maxZ + 0.5);
                            case EAST -> vec3.withAxis(Direction.Axis.X, boundingBox.maxX + 0.5);
                            case WEST -> vec3.withAxis(Direction.Axis.X, boundingBox.minX - 0.5);
                        };

                if (!MovTasks.ENGIN.checkEnvironmentCollision(mc.player, testPos, true)) {
                    return testPos;
                }
            }
            return null;
        } else {
            boolean considerAntiShield = considerAntiShield(target);
            Vec3d deltaMovments;
            if (considerAntiShield) {
                deltaMovments = target.getRotationVector().normalize().multiply(-0.2);
            } else if (target instanceof PlayerEntity playerEntity) {
                var re = attackPredictArgument.get();

                Vec3d predictedPosition = re.predict(
                        playerEntity); /// predictAttackPosition(playerEntity, re.ticksLater(), re.ticksHistory(),
                // re.mode());
                deltaMovments = predictedPosition.subtract(target.getEntityPos());
            } else {
                Vec3d targetFacing = mc.player.getEntityPos().subtract(target.getEntityPos());
                Vec3d targetFacingHorizontal = new Vec3d(targetFacing.x, 0.0d, targetFacing.z);
                double multiply = 0.5;
                deltaMovments = targetFacingHorizontal.normalize().multiply(multiply);
            }

            Vec3d targetPos = target.getEntityPos();
            Vec3d actualMove = MovTasks.ENGIN.simulateMovement(mc.player, targetPos, deltaMovments);
            return targetPos.add(actualMove);
        }
    }
    public boolean considerAntiShield(Entity target) {
        return enableNoShield.get()
                && target instanceof LivingEntity livingEntity
                && livingEntity.isUsingItem()
                && livingEntity.getActiveItem().getItem() instanceof ShieldItem;
    }
    public final FlagRef debugRender=flagBuilder(new ModulePath("attack.debug-render-prediction")).build();
    private final Map<Integer,List<Vec3d>> recordedPoints=new LinkedHashMap<>();
    public final FlagRef placeRecorder=flagBuilder(new ModulePath("att-bot.place-recorder")).updateListener(v->recordedPoints.clear()).build();
    public final KeyBindRef placeRecorderHotkey=toggleHotkey(new ModulePath("att-bot.place-recorder-hotkey"),new com.codigohasta.addon.utils.slimefunhelper.managers.input.MultiKeyBind(),new ModulePath("att-bot.place-recorder")).build();
    @Override public void registerAll(){
        registerServiceListener(Listener.getPacketPostHandlePoint().getChannel(EntityS2CPacket.class),this::onPostEntity);
        registerServiceListener(Listener.getPacketPostHandlePoint().getChannel(EntityPositionS2CPacket.class),this::onPostEntityPos);
        registerServiceListener(Listener.getPacketPostHandlePoint().getChannel(EntityPositionSyncS2CPacket.class),this::onPostEntityTeleport);
        registerListener(RenderListener.getRender3DEvent(),this::onRender);
    }
    public void onRender(Event<MatrixStack> event){
        if(!debugRender.get())return;
        for(var player:mc.world.getPlayers()){
            if(player==mc.getCameraEntity())continue;
            var box=mc.player.dimensions.getBoxAt(flyPredictArgument.get().predict(player));
            RenderUtils.drawOutlinedBox(event.context,box.getMinPos(),box.getMaxPos(),java.awt.Color.MAGENTA);
        }
        for(var entry:recordedPoints.entrySet()){
            var color=new java.awt.Color(com.google.common.hash.Hashing.sha256().hashInt(entry.getKey()).hashCode());
            Vec3d previous=null;
            for(var point:entry.getValue()){
                RenderUtils.drawOutlinedBox(event.context,point.add(-0.2,-0.2,-0.2),point.add(0.2,0.2,0.2),color);
                if(previous!=null&&RenderUtils.current!=null)RenderUtils.current.renderer.line(previous.x,previous.y,previous.z,point.x,point.y,point.z,new meteordevelopment.meteorclient.utils.render.color.Color(color.getRGB()));
                previous=point;
            }
        }
    }
}
