package com.codigohasta.addon.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor for PlayerInteractEntityC2SPacket - only provides entityId access.
 * For type detection, use Meteor's IPlayerInteractEntityC2SPacket interface.
 */
@Environment(EnvType.CLIENT)
@Mixin(PlayerInteractEntityC2SPacket.class)
public interface PlayerInteractEntityC2SPacketAccessor {
    @Mutable
    @Accessor("entityId")
    void setEntityId(int entityId);

    @Accessor("entityId")
    int getEntityId();

    @Mutable
    @Accessor("playerSneaking")
    void setPlayerSneaking(boolean playerSneaking);
}
