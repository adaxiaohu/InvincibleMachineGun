package com.codigohasta.addon.mixin.slimefunhelper;
import com.codigohasta.addon.utils.slimefunhelper.SlimefunRuntime;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(ClientPlayNetworkHandler.class) public abstract class PlayNetworkHandlerMixin {
@com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method="onExplosion",at=@org.spongepowered.asm.mixin.injection.At(value="INVOKE",target="Ljava/util/Optional;ifPresent(Ljava/util/function/Consumer;)V"))
private void sf$explosionVelocity(java.util.Optional<net.minecraft.util.math.Vec3d> value,java.util.function.Consumer<? super net.minecraft.util.math.Vec3d> action,Operation<Void> original){
    if(!SlimefunRuntime.modifying()){original.call(value,action);return;}
    if(value.isPresent()){
        var event=new com.codigohasta.addon.utils.slimefunhelper.events.Event<>(value.get(),true,true);
        com.codigohasta.addon.utils.slimefunhelper.events.Listener.getPlayerExplosionVelocity().dispatch(event);
        if(!event.isCancelled())original.call(event.context()==value.get()?value:java.util.Optional.ofNullable(event.context()),action);
    }
}
@org.spongepowered.asm.mixin.injection.Inject(method="onChunkData",at=@org.spongepowered.asm.mixin.injection.At(value="INVOKE",target="Lnet/minecraft/client/network/ClientPlayNetworkHandler;loadChunk(IILnet/minecraft/network/packet/s2c/play/ChunkData;)V",shift=org.spongepowered.asm.mixin.injection.At.Shift.AFTER))
private void sf$chunkUpdated(ChunkDataS2CPacket packet,org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci){
    if(SlimefunRuntime.modifying())com.codigohasta.addon.utils.slimefunhelper.events.Listener.getChunkUpdateListener().broadcast(new net.minecraft.util.math.ChunkPos(packet.getChunkX(),packet.getChunkZ()));
}
@org.spongepowered.asm.mixin.injection.Inject(method="onPlayerRemove",at=@org.spongepowered.asm.mixin.injection.At(value="INVOKE",target="Ljava/util/Set;remove(Ljava/lang/Object;)Z",shift=org.spongepowered.asm.mixin.injection.At.Shift.AFTER))
private void sf$playerExit(PlayerRemoveS2CPacket packet,org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci,@com.llamalad7.mixinextras.sugar.Local net.minecraft.client.network.PlayerListEntry entry){
    if(SlimefunRuntime.modifying())com.codigohasta.addon.utils.slimefunhelper.events.Listener.getOtherPlayerExitPoint().broadcast(entry);
}
@WrapMethod(method="onPlayerAbilities")
private void sf$abilities(PlayerAbilitiesS2CPacket packet,Operation<Void> original){
    var client=MinecraftClient.getInstance();
    if(client.isOnThread()&&client.player!=null)com.codigohasta.addon.utils.slimefunhelper.ServerFlightState.received(client.player,packet.allowFlying());
    original.call(packet);
}
@com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method="onPlayerPositionLook",at=@org.spongepowered.asm.mixin.injection.At(value="INVOKE",target="Lnet/minecraft/network/ClientConnection;send(Lnet/minecraft/network/packet/Packet;)V",ordinal=1))
private void sf$teleportResponse(net.minecraft.network.ClientConnection connection,net.minecraft.network.packet.Packet<?> packet,Operation<Void> original,@com.llamalad7.mixinextras.sugar.Local(argsOnly=true)PlayerPositionLookS2CPacket correction){
    if(SlimefunRuntime.ready()&&packet instanceof net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Full full){
        com.codigohasta.addon.utils.slimefunhelper.accessors.access.PlayerMoveC2SPacketAccess.setCause(full,com.codigohasta.addon.utils.slimefunhelper.accessors.access.PlayerMoveC2SPacketAccess.Cause.SET_BACK);
        if(SlimefunRuntime.modifying())com.codigohasta.addon.utils.slimefunhelper.events.Listener.getTeleportationConfirm().fire(new com.codigohasta.addon.utils.slimefunhelper.events.impl.Teleportation(correction.teleportId(),full.getX(0),full.getY(0),full.getZ(0),full.getPitch(0),full.getYaw(0)));
    }
    original.call(connection,packet);
}
@WrapMethod(method="onEntity") private void sf$onEntity(EntityS2CPacket packet,Operation<Void> original){if(!MinecraftClient.getInstance().isOnThread()){original.call(packet);return;}var updated=SlimefunRuntime.beforeReceived(packet);if(updated==null)return;original.call((EntityS2CPacket)updated);SlimefunRuntime.received(updated);}
@WrapMethod(method="onEntityPosition") private void sf$onEntityPosition(EntityPositionS2CPacket packet,Operation<Void> original){if(!MinecraftClient.getInstance().isOnThread()){original.call(packet);return;}var updated=SlimefunRuntime.beforeReceived(packet);if(updated==null)return;original.call((EntityPositionS2CPacket)updated);SlimefunRuntime.received(updated);}
@WrapMethod(method="onEntityPositionSync") private void sf$onEntityPositionSync(EntityPositionSyncS2CPacket packet,Operation<Void> original){if(!MinecraftClient.getInstance().isOnThread()){original.call(packet);return;}var updated=SlimefunRuntime.beforeReceived(packet);if(updated==null)return;original.call((EntityPositionSyncS2CPacket)updated);SlimefunRuntime.received(updated);}
@WrapMethod(method="onPlayerPositionLook") private void sf$onPlayerPositionLook(PlayerPositionLookS2CPacket packet,Operation<Void> original){if(!MinecraftClient.getInstance().isOnThread()){original.call(packet);return;}var updated=SlimefunRuntime.beforeReceived(packet);if(updated==null)return;original.call((PlayerPositionLookS2CPacket)updated);SlimefunRuntime.received(updated);}
@WrapMethod(method="onEntityVelocityUpdate") private void sf$onEntityVelocityUpdate(EntityVelocityUpdateS2CPacket packet,Operation<Void> original){if(!MinecraftClient.getInstance().isOnThread()){original.call(packet);return;}var updated=SlimefunRuntime.beforeReceived(packet);if(updated==null)return;original.call((EntityVelocityUpdateS2CPacket)updated);SlimefunRuntime.received(updated);}
@WrapMethod(method="onExplosion") private void sf$onExplosion(ExplosionS2CPacket packet,Operation<Void> original){if(!MinecraftClient.getInstance().isOnThread()){original.call(packet);return;}var updated=SlimefunRuntime.beforeReceived(packet);if(updated==null)return;original.call((ExplosionS2CPacket)updated);SlimefunRuntime.received(updated);}
@WrapMethod(method="onEntityStatus") private void sf$onEntityStatus(EntityStatusS2CPacket packet,Operation<Void> original){if(!MinecraftClient.getInstance().isOnThread()){original.call(packet);return;}var updated=SlimefunRuntime.beforeReceived(packet);if(updated==null)return;original.call((EntityStatusS2CPacket)updated);SlimefunRuntime.received(updated);}
@WrapMethod(method="onEntityDamage") private void sf$onEntityDamage(EntityDamageS2CPacket packet,Operation<Void> original){if(!MinecraftClient.getInstance().isOnThread()){original.call(packet);return;}var updated=SlimefunRuntime.beforeReceived(packet);if(updated==null)return;original.call((EntityDamageS2CPacket)updated);SlimefunRuntime.received(updated);}
@WrapMethod(method="onPlayerRotation") private void sf$onPlayerRotation(PlayerRotationS2CPacket packet,Operation<Void> original){if(!MinecraftClient.getInstance().isOnThread()){original.call(packet);return;}var updated=SlimefunRuntime.beforeReceived(packet);if(updated==null)return;original.call((PlayerRotationS2CPacket)updated);SlimefunRuntime.received(updated);}
@WrapMethod(method="onCooldownUpdate") private void sf$onCooldownUpdate(CooldownUpdateS2CPacket packet,Operation<Void> original){if(!MinecraftClient.getInstance().isOnThread()){original.call(packet);return;}var updated=SlimefunRuntime.beforeReceived(packet);if(updated==null)return;original.call((CooldownUpdateS2CPacket)updated);SlimefunRuntime.received(updated);}
@WrapMethod(method="onEntityTrackerUpdate") private void sf$onEntityTrackerUpdate(EntityTrackerUpdateS2CPacket packet,Operation<Void> original){if(!MinecraftClient.getInstance().isOnThread()){original.call(packet);return;}var updated=SlimefunRuntime.beforeReceived(packet);if(updated==null)return;original.call((EntityTrackerUpdateS2CPacket)updated);SlimefunRuntime.received(updated);}
@WrapMethod(method="onPlayerActionResponse") private void sf$onPlayerActionResponse(PlayerActionResponseS2CPacket packet,Operation<Void> original){if(!MinecraftClient.getInstance().isOnThread()){original.call(packet);return;}var updated=SlimefunRuntime.beforeReceived(packet);if(updated==null)return;original.call((PlayerActionResponseS2CPacket)updated);SlimefunRuntime.received(updated);}
@WrapMethod(method="onBlockUpdate") private void sf$onBlockUpdate(BlockUpdateS2CPacket packet,Operation<Void> original){if(!MinecraftClient.getInstance().isOnThread()){original.call(packet);return;}var updated=SlimefunRuntime.beforeReceived(packet);if(updated==null)return;original.call((BlockUpdateS2CPacket)updated);SlimefunRuntime.received(updated);}
@WrapMethod(method="onChunkDeltaUpdate") private void sf$onChunkDeltaUpdate(ChunkDeltaUpdateS2CPacket packet,Operation<Void> original){if(!MinecraftClient.getInstance().isOnThread()){original.call(packet);return;}var updated=SlimefunRuntime.beforeReceived(packet);if(updated==null)return;original.call((ChunkDeltaUpdateS2CPacket)updated);SlimefunRuntime.received(updated);}
@com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method="onEntityVelocityUpdate",at=@org.spongepowered.asm.mixin.injection.At(value="INVOKE",target="Lnet/minecraft/entity/Entity;setVelocityClient(Lnet/minecraft/util/math/Vec3d;)V"))
private void sf$velocity(net.minecraft.entity.Entity entity,net.minecraft.util.math.Vec3d v,Operation<Void> original){var e=com.codigohasta.addon.utils.slimefunhelper.events.Listener.getEntityClientVelocityUpdate().getChannel(entity.getType()).fire(v,entity);if(!e.isCancelled())original.call(entity,e.context());}

@WrapMethod(method="onGameMessage") private void sf$onGameMessage(GameMessageS2CPacket packet,Operation<Void> original){if(!MinecraftClient.getInstance().isOnThread()){original.call(packet);return;}var updated=SlimefunRuntime.beforeReceived(packet);if(updated==null)return;original.call((GameMessageS2CPacket)updated);SlimefunRuntime.received(updated);}

@WrapMethod(method="onInventory") private void sf$onInventory(InventoryS2CPacket packet,Operation<Void> original){if(!MinecraftClient.getInstance().isOnThread()){original.call(packet);return;}var updated=SlimefunRuntime.beforeReceived(packet);if(updated==null)return;original.call((InventoryS2CPacket)updated);SlimefunRuntime.received(updated);}

@WrapMethod(method="onScreenHandlerSlotUpdate") private void sf$onScreenHandlerSlotUpdate(ScreenHandlerSlotUpdateS2CPacket packet,Operation<Void> original){if(!MinecraftClient.getInstance().isOnThread()){original.call(packet);return;}var updated=SlimefunRuntime.beforeReceived(packet);if(updated==null)return;original.call((ScreenHandlerSlotUpdateS2CPacket)updated);SlimefunRuntime.received(updated);}
@WrapMethod(method="onEntityStatusEffect") private void sf$onEntityStatusEffect(EntityStatusEffectS2CPacket packet,Operation<Void> original){if(!MinecraftClient.getInstance().isOnThread()){original.call(packet);return;}var updated=SlimefunRuntime.beforeReceived(packet);if(updated==null)return;original.call((EntityStatusEffectS2CPacket)updated);SlimefunRuntime.received(updated);}
@WrapMethod(method="onEntityEquipmentUpdate") private void sf$onEntityEquipmentUpdate(EntityEquipmentUpdateS2CPacket packet,Operation<Void> original){if(!MinecraftClient.getInstance().isOnThread()){original.call(packet);return;}var updated=SlimefunRuntime.beforeReceived(packet);if(updated==null)return;original.call((EntityEquipmentUpdateS2CPacket)updated);SlimefunRuntime.received(updated);}
@WrapMethod(method="onEntitySpawn") private void sf$onEntitySpawn(EntitySpawnS2CPacket packet,Operation<Void> original){if(!MinecraftClient.getInstance().isOnThread()){original.call(packet);return;}var updated=SlimefunRuntime.beforeReceived(packet);if(updated==null)return;original.call((EntitySpawnS2CPacket)updated);SlimefunRuntime.received(updated);}
}
