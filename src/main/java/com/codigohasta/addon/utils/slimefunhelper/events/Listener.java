package com.codigohasta.addon.utils.slimefunhelper.events;
import java.util.*;
import com.codigohasta.addon.utils.slimefunhelper.events.catchers.*;

import net.minecraft.entity.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;
import java.util.ArrayList;
import java.util.List;
public final class Listener {
    private static final Channels<java.util.Map<net.minecraft.registry.tag.TagKey<?>,java.util.List<net.minecraft.registry.entry.RegistryEntry<?>>>> registryTagReload=new Channels<>();
    public static Channels<java.util.Map<net.minecraft.registry.tag.TagKey<?>,java.util.List<net.minecraft.registry.entry.RegistryEntry<?>>>> getRegistryTagKeyReload(){return registryTagReload;}
    private static final Channel<com.codigohasta.addon.utils.slimefunhelper.events.impl.UseItemOnBlock> prePlayerUseItemAtBlock = new Channel<>();
    private static final Channel<com.codigohasta.addon.utils.slimefunhelper.events.impl.UseItemOnBlock> postPlayerUseItemAtBlock = new Channel<>();
    public static Channel<com.codigohasta.addon.utils.slimefunhelper.events.impl.UseItemOnBlock> getPrePlayerUseItemAtBlock() { return prePlayerUseItemAtBlock; }
    public static Channel<com.codigohasta.addon.utils.slimefunhelper.events.impl.UseItemOnBlock> getPostPlayerUseItemAtBlock() { return postPlayerUseItemAtBlock; }
    public static com.codigohasta.addon.utils.slimefunhelper.accessors.access.ClientConnectionAccess getConnectionAccess(){
        return (com.codigohasta.addon.utils.slimefunhelper.accessors.access.ClientConnectionAccess) net.minecraft.client.MinecraftClient.getInstance().getNetworkHandler().getConnection();
    }
    private static final Channel<com.codigohasta.addon.utils.slimefunhelper.events.impl.SlotClickAction> preClickSlot=new Channel<>(),postClickSlot=new Channel<>();
    public static Channel<com.codigohasta.addon.utils.slimefunhelper.events.impl.SlotClickAction> getPreClickSlot(){return preClickSlot;}
    public static Channel<com.codigohasta.addon.utils.slimefunhelper.events.impl.SlotClickAction> getPostClickSlot(){return postClickSlot;}
    private static final Channel<com.codigohasta.addon.utils.slimefunhelper.events.impl.UseItem> postPlayerUseItem=new Channel<>();
    public static Channel<com.codigohasta.addon.utils.slimefunhelper.events.impl.UseItem> getPostPlayerUseItem(){return postPlayerUseItem;}
    // Source receive dispatcher, including recursive bundle filtering and unchanged identity.
    public static Packet<?> acceptS2CPacket(ClientConnection connection, Packet<?> packet) {
        return unpackMultiPacket(connection, packet, true);
    }

    private static Packet<?> onSinglePacketListen(ClientConnection connection, Packet<?> packet, boolean s2c) {
        Event<Packet<?>> packetEvent = new Event<>(packet, true, true, connection);
        getPacketPoint().handleValue(packetEvent);
        if (packetEvent.isCancelled()) {
            return null;
        } else {
            return packetEvent.context();
        }
    }

    @SuppressWarnings("unchecked")
    private static Packet<?> unpackMultiPacket(ClientConnection connection, Packet<?> packet, boolean isS2C) {
        if (packet instanceof BundleS2CPacket bundle) {
            var iter = bundle.getPackets();
            List<Packet<? super ClientPlayPacketListener>> packets = new ArrayList<>();
            boolean recreate = false;
            for (var pkt : iter) {
                Packet<? super ClientPlayPacketListener> p =
                        (Packet<? super ClientPlayPacketListener>) unpackMultiPacket(connection, pkt, isS2C);
                if (p != null) {
                    packets.add(p);
                    if (p != pkt) {
                        recreate = true;
                    }
                } else {
                    recreate = true;
                }
            }
            if (recreate) {
                return packets.isEmpty() ? null : new BundleS2CPacket(packets);
            } else {
                return packet;
            }
        } else {
            return onSinglePacketListen(connection, packet, isS2C);
        }
    }

    private static final Channels<Vec3d> EntityClientVelocityUpdate=new Channels<>();
    public static Channels<Vec3d> getEntityClientVelocityUpdate(){return EntityClientVelocityUpdate;}
    private static final Channel<Vec3d> PlayerFluidVelocityPoint=new Channel<>();
    public static Channel<Vec3d> getPlayerFluidVelocityPoint(){return PlayerFluidVelocityPoint;}
    private static final Channel<Integer> PlayerFallFlyingTick=new Channel<>();
    public static Channel<Integer> getPlayerFallFlyingTick(){return PlayerFallFlyingTick;}
    private static final Channels<com.codigohasta.addon.utils.slimefunhelper.events.impl.MetadataUpdate> EntityTrackDataUpdate=
            new Channels<>(e -> e.context.entity().getType());
    public static Channels<com.codigohasta.addon.utils.slimefunhelper.events.impl.MetadataUpdate> getEntityTrackDataUpdate(){return EntityTrackDataUpdate;}
    private static final Channel<Boolean> PlayerSwitchFallFlying=new Channel<>();
    public static Channel<Boolean> getPlayerSwitchFallFlying(){return PlayerSwitchFallFlying;}
    private static final EntityChannels EntityRemoveListener=new EntityChannels();
    public static EntityChannels getEntityRemoveListener(){return EntityRemoveListener;}
    private static final Channel<World> WorldSwitchPoint=new Channel<>();
    public static Channel<World> getWorldSwitchPoint(){return WorldSwitchPoint;}
    private static final Channel<ClientPlayerEntity> playerRespawnPoint=new Channel<>();
    public static Channel<ClientPlayerEntity> getPlayerRespawnPoint(){return playerRespawnPoint;}
    private static final Channel<Vec3d> playerTravelingTick=new Channel<>();
    public static Channel<Vec3d> getPlayerTravelingTick(){return playerTravelingTick;}
    private static final Channel<ClientPlayerEntity> GameJoinPoint=new Channel<>();
    public static Channel<ClientPlayerEntity> getGameJoinPoint(){return GameJoinPoint;}
    private static final Channel<ClientPlayerEntity> PostGameTick=new Channel<>();
    public static Channel<ClientPlayerEntity> getPostGameTick(){return PostGameTick;}
    private static final Channel<Void> PreTick=new Channel<>();
    public static Channel<Void> getPreTick(){return PreTick;}
    private static final Channel<Void> PreHandleInputEvents=new Channel<>();
    public static Channel<Void> getPreHandleInputEvents(){return PreHandleInputEvents;}
    private static final Channel<Integer> PlayerNotFlyJumpPoint=new Channel<>();
    public static Channel<Integer> getPlayerNotFlyJumpPoint(){return PlayerNotFlyJumpPoint;}
    private static final Channel<Vec3d> PlayerWebSlowPoint=new Channel<>();
    public static Channel<Vec3d> getPlayerWebSlowPoint(){return PlayerWebSlowPoint;}
    private static final Channel<com.codigohasta.addon.utils.slimefunhelper.events.impl.Teleportation> TeleportationConfirm=new Channel<>();
    public static Channel<com.codigohasta.addon.utils.slimefunhelper.events.impl.Teleportation> getTeleportationConfirm(){return TeleportationConfirm;}
    private static final Channel<ClientPlayerEntity> PlayerInitConfiguration=new Channel<>();
    public static Channel<ClientPlayerEntity> getPlayerInitConfiguration(){return PlayerInitConfiguration;}
    private static final EntityChannels EntityPreTickListener=new EntityChannels();
    public static EntityChannels getEntityPreTickListener(){return EntityPreTickListener;}
    private static final Channel<net.minecraft.client.network.PlayerListEntry> otherPlayerExit=new Channel<>();
    public static Channel<net.minecraft.client.network.PlayerListEntry> getOtherPlayerExitPoint(){return otherPlayerExit;}
    private static final PacketChannels PacketPoint=new PacketChannels();
    public static PacketChannels getPacketPoint(){return PacketPoint;}
    private static final PacketChannels PacketPostHandlePoint=new PacketChannels();
    public static PacketChannels getPacketPostHandlePoint(){return PacketPostHandlePoint;}
    private static final PacketChannels PacketPostSendPoint=new PacketChannels();
    public static PacketChannels getPacketPostSendPoint(){return PacketPostSendPoint;}
    private static final PacketChannels PacketPostScheduleSendPoint=new PacketChannels();
    public static PacketChannels getPacketPostScheduleSendPoint(){return PacketPostScheduleSendPoint;}
    private static final CustomChannels CustomListener=new CustomChannels();
    public static CustomChannels getCustomListener(){return CustomListener;}
    public static void sendPacketNoEvents(Packet<?> packet){PacketManager.sendCompat(packet);}
private static final Channel<net.minecraft.util.hit.HitResult> attackAction=new Channel<>();
public static Channel<net.minecraft.util.hit.HitResult> getAttackAction(){return attackAction;}
private static final Channel<ClientPlayerEntity> preGameTick=new Channel<>();
public static Channel<ClientPlayerEntity> getPreGameTick(){return preGameTick;}

private static final PacketChannels preHandle=new PacketChannels();public static PacketChannels getPacketPreHandlePoint(){return preHandle;}
private static final Channel<net.minecraft.util.math.ChunkPos> chunkUpdate=new Channel<>();public static Channel<net.minecraft.util.math.ChunkPos> getChunkUpdateListener(){return chunkUpdate;}

private static final Channel<ClientPlayerEntity> postMovement=new Channel<>();public static Channel<ClientPlayerEntity> getClientPlayerPostSendMovementPoint(){return postMovement;}
private static final Channel<ClientPlayerEntity> preMovement=new Channel<>();public static Channel<ClientPlayerEntity> getClientPlayerSendMovementPoint(){return preMovement;}
private static final Channel<Void> postTick=new Channel<>(),serverLeave=new Channel<>();public static Channel<Void> getPostTick(){return postTick;}public static Channel<Void> getServerLeavePoint(){return serverLeave;}
private static final Channel<Void> postInput=new Channel<>();public static Channel<Void> getPostHandleInputEvents(){return postInput;}
private static final Channel<net.minecraft.util.hit.HitResult> mineBlock=new Channel<>();public static Channel<net.minecraft.util.hit.HitResult> getMineBlockAction(){return mineBlock;}
private static final Channel<Integer> useCooldown=new Channel<>();public static Channel<Integer> getUseItemCooldownReset(){return useCooldown;}


private static final EntityChannels entityPostTick=new EntityChannels();
public static EntityChannels getEntityPostTickListener(){return entityPostTick;}

private static final Channel<com.codigohasta.addon.utils.slimefunhelper.events.impl.KeyboardAction> keyboardInput=new Channel<>();
public static Channel<com.codigohasta.addon.utils.slimefunhelper.events.impl.KeyboardAction> getKeyboardInput(){return keyboardInput;}
private static final Channel<net.minecraft.client.gui.screen.Screen> postSetScreen=new Channel<>();
public static Channel<net.minecraft.client.gui.screen.Screen> getPostSetScreen(){return postSetScreen;}
private static final Channel<Vec3d> explosionVelocity=new Channel<>();
public static Channel<Vec3d> getPlayerExplosionVelocity(){return explosionVelocity;}
private static final Channel<Boolean> preWorldScan=new Channel<>();
public static Channel<Boolean> getPreWorldScannListener(){return preWorldScan;}
private static final Channel<Void> resetWorldScan=new Channel<>();
public static Channel<Void> getResetWorldScannListener(){return resetWorldScan;}
private static final Channel<com.codigohasta.addon.utils.slimefunhelper.events.impl.BlockUpdate> blockUpdate=new Channel<>();
public static Channel<com.codigohasta.addon.utils.slimefunhelper.events.impl.BlockUpdate> getBlockUpdateListener(){return blockUpdate;}
private static final Channel<java.util.List<java.util.function.BiPredicate<net.minecraft.util.math.BlockPos,net.minecraft.block.BlockState>>> scanFilters=new Channel<>();
public static Channel<java.util.List<java.util.function.BiPredicate<net.minecraft.util.math.BlockPos,net.minecraft.block.BlockState>>> getWorldScannChunkBlockFilterList(){return scanFilters;}
private static final Channel<java.util.Map<net.minecraft.util.math.BlockPos,net.minecraft.block.BlockState>> scanChunks=new Channel<>();
public static Channel<java.util.Map<net.minecraft.util.math.BlockPos,net.minecraft.block.BlockState>> getWorldScannChunkResult(){return scanChunks;}
private static final Channels<net.minecraft.block.BlockState> scanBlocks=new Channels<>();
public static Channels<net.minecraft.block.BlockState> getWorldScannBlockResult(){return scanBlocks;}
public static void sendPacketNoEvents(ClientConnection connection,Packet<?> packet){PacketManager.sendCompat(connection,packet);}

    private static final Map<Class<?>, ArrayDeque<PacketCatcher>> preCatchers = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<Class<?>, ArrayDeque<PacketCatcher>> postCatchers = new java.util.concurrent.ConcurrentHashMap<>();

    public static <T extends Packet<?>> void addPrePacketCatcher(PacketCatcher packet) {
        Class<?> dequeCls =
                packet instanceof AbstractTypedPacketCatcher abstractType ? abstractType.packetClass : Packet.class;
        var re = preCatchers.computeIfAbsent(dequeCls, k -> new ArrayDeque<>());
        synchronized (re) {
            re.addLast(packet);
        }
    }

    public static <T extends Packet<?>> void addPostPacketCatcher(PacketCatcher packet) {
        Class<?> dequeCls =
                packet instanceof AbstractTypedPacketCatcher abstractType ? abstractType.packetClass : Packet.class;
        var re = postCatchers.computeIfAbsent(dequeCls, k -> new ArrayDeque<>());
        synchronized (re) {
            re.addLast(packet);
        }
    }

    public static void onPacketEventCatch(
            Map<Class<?>, ArrayDeque<PacketCatcher>> packetCatchers, Event<? extends Packet<?>> packet) {
        Packet<?> pkt = packet.context();
        ArrayDeque<PacketCatcher> re = packetCatchers.get(Packet.class);
        if (re != null) {
            onPacketCatcherArrayWalk(re, packet);
        }
        if (packet.isCancelled()) return;
        ArrayDeque<PacketCatcher> re2 = packetCatchers.get(Listener.getMappedPacketClass(pkt.getClass()));
        if (re2 != null) {
            onPacketCatcherArrayWalk(re2, packet);
        }
    }

    private static void onPacketCatcherArrayWalk(ArrayDeque<PacketCatcher> re, Event<? extends Packet<?>> packet) {
        if (packet.isCancelled()) return;
        synchronized (re) {
            var iter = re.iterator();
            while (iter.hasNext()) {
                var handler = iter.next();
                boolean removal = handler.catchEvent(packet);
                if (removal) {
                    iter.remove();
                }
                if (packet.isCancelled()) {
                    return;
                }
            }
        }
    }

    public static Class<?> getMappedPacketClass(Class<?> type) {
        while (type.getSuperclass() != null && Packet.class.isAssignableFrom(type.getSuperclass())) type = type.getSuperclass();
        return type;
    }
    public static void clearPacketCatchers() { preCatchers.clear(); postCatchers.clear(); }
    static {
        getPacketPoint().getPacketSendChannel().add(event -> onPacketEventCatch(preCatchers, event), Integer.MIN_VALUE);
        getPacketPreHandlePoint().add(event -> onPacketEventCatch(preCatchers, event), Integer.MIN_VALUE);
        getPacketPostHandlePoint().add(event -> onPacketEventCatch(postCatchers, event), Integer.MIN_VALUE);
        getPacketPostSendPoint().add(event -> onPacketEventCatch(postCatchers, event), Integer.MIN_VALUE);
    }

}
