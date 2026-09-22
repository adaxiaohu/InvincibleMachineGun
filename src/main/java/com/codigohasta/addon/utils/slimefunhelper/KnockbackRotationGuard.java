package com.codigohasta.addon.utils.slimefunhelper;

import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.IdentityHashMap;
import java.util.Map;

/** Exact-angle validation and per-request receipts from the existing SF connection hooks. */
public final class KnockbackRotationGuard {
    private KnockbackRotationGuard() {}

    public static boolean sameRotation(float pitch, float yaw, float wantedPitch, float wantedYaw) {
        return Float.isFinite(pitch) && Float.isFinite(yaw)
            && Math.abs(pitch - wantedPitch) < 0.001F
            && Math.abs(MathHelper.wrapDegrees(yaw - wantedYaw)) < 0.001F;
    }

    private static boolean finite(Vec3d v) {
        return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }

    public record Receipt(Vec3d feet, float pitch, float yaw, long rotationTick) {}

    /** A prepared/cancelled/queued packet is never treated as a written movement. */
    public static final class SentRotationTracker {
        private final Map<PlayerMoveC2SPacket, Long> pending = new IdentityHashMap<>();
        private long sequence;
        private long written;
        private Vec3d feet = new Vec3d(Double.NaN, Double.NaN, Double.NaN);
        private float pitch = Float.NaN;
        private float yaw = Float.NaN;
        private long rotationTick;
        private boolean invalid;

        public synchronized void prepared(PlayerMoveC2SPacket packet) {
            if (invalid) return;
            if (pending.size() >= 64 || pending.containsKey(packet)) {
                invalid = true;
                pending.clear();
                return;
            }
            pending.put(packet, ++sequence);
        }

        public synchronized void written(PlayerMoveC2SPacket packet) {
            written(packet, 0);
        }

        public synchronized void written(PlayerMoveC2SPacket packet, long tick) {
            Long ticket = pending.remove(packet);
            if (ticket == null || invalid) return;
            // A reordered write invalidates this attack instead of trusting stale rotation.
            if (ticket <= written) { invalid = true; return; }
            written = ticket;
            feet = new Vec3d(packet.getX(feet.x), packet.getY(feet.y), packet.getZ(feet.z));
            // Time the actual connection write, not the request or a queued send.
            // Repeating the same rotation/position does not restart its waiting tick.
            if (packet.changesLook() && !sameRotation(packet.getPitch(pitch), packet.getYaw(yaw), pitch, yaw))
                rotationTick = tick;
            pitch = packet.getPitch(pitch);
            yaw = packet.getYaw(yaw);
        }

        public synchronized Receipt receipt() {
            return !invalid && pending.isEmpty() && written > 0 && written == sequence
                && finite(feet) && Float.isFinite(pitch) && Float.isFinite(yaw)
                ? new Receipt(feet, pitch, yaw, rotationTick) : null;
        }

        public synchronized Receipt receiptBeforeTick(long tick) {
            Receipt receipt = receipt();
            return receipt != null && receipt.rotationTick() < tick ? receipt : null;
        }
    }
}
