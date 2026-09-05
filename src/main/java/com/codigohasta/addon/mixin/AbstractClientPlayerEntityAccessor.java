package com.codigohasta.addon.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractClientPlayer.class)
public interface AbstractClientPlayerEntityAccessor {
    @Invoker("getPlayerInfo")
    PlayerInfo invokeGetPlayerListEntry();
}
