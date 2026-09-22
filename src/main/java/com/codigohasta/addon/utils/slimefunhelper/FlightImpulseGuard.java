package com.codigohasta.addon.utils.slimefunhelper;

import com.codigohasta.addon.modules.slimefunhelper.ElytraBot;
import com.codigohasta.addon.modules.slimefunhelper.ElytraExtra;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;

/** Lets vanilla consume a received impulse before mace flight resumes overriding velocity. */
public final class FlightImpulseGuard {
    private static ClientPlayerEntity pendingPlayer;
    private static long revision;
    private static long travelledRevision = -1;

    private FlightImpulseGuard() {}

    private static boolean maceBotActive() {
        var bot = ElytraBot.INSTANCE;
        return bot != null && bot.isActive() && bot.mode.get() == ElytraBot.Mode.MACE_ARUA;
    }

    /** Called after vanilla applied the packet, on the client thread. */
    public static void received(Packet<?> packet) {
        var player = MinecraftClient.getInstance().player;
        if (player == null || !maceBotActive()) return;
        boolean impulse = packet instanceof EntityVelocityUpdateS2CPacket velocity
            && velocity.getEntityId() == player.getId();
        if (packet instanceof ExplosionS2CPacket explosion) {
            impulse = explosion.playerKnockback().filter(v -> v.lengthSquared() > 0).isPresent();
        }
        if (!impulse) return;
        pendingPlayer = player;
        revision++;
        if (ElytraExtra.INSTANCE != null) ElytraExtra.INSTANCE.setOverridingFireworkVelocity(null);
    }

    public static boolean preserving() {
        return pendingPlayer != null && pendingPlayer == MinecraftClient.getInstance().player && maceBotActive();
    }

    public static void afterTravel() {
        if (preserving()) travelledRevision = revision;
    }

    public static void finishTick() {
        if (travelledRevision == revision) pendingPlayer = null;
        travelledRevision = -1;
    }

    public static void clear() {
        pendingPlayer = null;
        revision = 0;
        travelledRevision = -1;
    }
}
