package com.codigohasta.addon.mixin.slimefunhelper;

import com.codigohasta.addon.utils.slimefunhelper.SlimefunRuntime;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ClientCommonNetworkHandler.class)
public abstract class CommonNetworkHandlerMixin {
    @WrapMethod(method = "onPing")
    private void sf$ping(CommonPingS2CPacket packet, Operation<Void> original) {
        // 1.21.11 uses PacketApplyBatcher for incoming packets. Let vanilla enqueue
        // the ping there, alongside teleports. MinecraftClient.execute uses another
        // queue and can move both pong confirmations across a teleport response.
        if (!MinecraftClient.getInstance().isOnThread() || !SlimefunRuntime.modifying()) {
            original.call(packet);
            return;
        }

        var updated = SlimefunRuntime.beforeReceived(packet);
        if (updated == null) return;
        original.call((CommonPingS2CPacket) updated);
        SlimefunRuntime.received(updated);
    }
}
