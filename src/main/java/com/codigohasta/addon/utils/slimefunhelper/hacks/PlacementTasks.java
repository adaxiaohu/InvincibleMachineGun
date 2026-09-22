package com.codigohasta.addon.utils.slimefunhelper.hacks;

import com.codigohasta.addon.utils.slimefunhelper.hacks.api.BaseModule;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;

/** Keeps delayed source placement work tied to the module and world that created it. */
public final class PlacementTasks {
    private static final ConcurrentHashMap<BaseModule, Long> generations = new ConcurrentHashMap<>();
    private static final ThreadLocal<Scope> current = new ThreadLocal<>();
    public record Scope(BaseModule owner, long generation, ClientPlayerEntity player, ClientWorld world) {}

    public static Scope capture() { return current.get(); }
    public static boolean valid(Scope scope) {
        if (scope == null) return true;
        var client = MinecraftClient.getInstance();
        return scope.owner.isActive() && generations.getOrDefault(scope.owner, 0L) == scope.generation
                && client.player == scope.player && client.world == scope.world;
    }
    public static boolean isOwnedBy(BaseModule module) {
        Scope scope = current.get();
        return scope != null && scope.owner == module && valid(scope);
    }
    public static void run(BaseModule owner, Runnable action) {
        Scope previous = current.get();
        var client = MinecraftClient.getInstance();
        Scope scope = previous != null ? previous
                : new Scope(owner, generations.getOrDefault(owner, 0L), client.player, client.world);
        if (!valid(scope)) return;
        current.set(scope);
        try { action.run(); }
        finally {
            if (previous == null) current.remove(); else current.set(previous);
        }
    }
    public static Runnable guard(Runnable action) {
        Scope scope = capture();
        if (scope == null) return action;
        return () -> {
            if (!valid(scope)) return;
            Scope previous = current.get();
            current.set(scope);
            try { action.run(); }
            finally {
                if (previous == null) current.remove(); else current.set(previous);
            }
        };
    }
    public static void cancel(BaseModule owner) { generations.merge(owner, 1L, Long::sum); }
}
