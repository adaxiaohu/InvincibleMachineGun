package com.codigohasta.addon.utils.gcore;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.Packet;

public final class GcoreDoActionUtil {

    private GcoreDoActionUtil() {
    }

    /** 直接把包发给服务端。 */
    public static void send(Packet<?> packet) {
        MinecraftClient.getInstance().getNetworkHandler().sendPacket(packet);
    }
}
