package com.codigohasta.addon.mixin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractClientPlayerEntity.class)
public interface AbstractClientPlayerEntityAccessor {
    @Invoker("getPlayerListEntry")
    PlayerListEntry invokeGetPlayerListEntry();
}
