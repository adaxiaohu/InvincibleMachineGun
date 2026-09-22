package com.codigohasta.addon.utils.slimefunhelper;

import net.minecraft.client.network.ClientPlayerEntity;

/** The serverSideCanFly state consumed by source NoFall from source Flight. */
public final class ServerFlightState {
    private static ClientPlayerEntity player;
    private static boolean allowed;

    public static void received(ClientPlayerEntity current, boolean allowFlying) {
        player = current;
        allowed = allowFlying;
    }

    public static boolean allowed(ClientPlayerEntity current) {
        if (player != current) received(current, current.getAbilities().allowFlying);
        return allowed;
    }

    public static void clear() { player = null; allowed = false; }
}
