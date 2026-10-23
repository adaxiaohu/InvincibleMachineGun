package com.codigohasta.addon.utils.gcore;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.Packet;

/**
 * 发包快捷方法。
 *
 * <p>移植自 {@code dev.godden.core.Utils.DoActionUtilKt}（GCore）。
 * 源是 Kotlin 的 `Packet.send()` 扩展函数，Java 里改为静态方法。
 */
public final class GcoreDoActionUtil {

    private GcoreDoActionUtil() {
    }

    /** 直接把包发给服务端。 */
    public static void send(Packet<?> packet) {
        MinecraftClient.getInstance().getNetworkHandler().sendPacket(packet);
    }
}
