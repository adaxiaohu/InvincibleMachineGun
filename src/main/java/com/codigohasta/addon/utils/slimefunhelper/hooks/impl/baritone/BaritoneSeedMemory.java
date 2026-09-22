package com.codigohasta.addon.utils.slimefunhelper.hooks.impl.baritone;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.biome.source.BiomeAccess;

/** The source SeedOre world-keyed seed cache used by BaritoneFix, without ore rendering dependencies. */
public final class BaritoneSeedMemory {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    public Map<String, Long> seeds = new LinkedHashMap<>();
    public boolean hasCurrentSeed() { return seeds.containsKey(worldName()); }
    public long getCurrentSeed() { return seeds.getOrDefault(worldName(), 0L); }
    public void putSeed(String key, long seed) { seeds.put(key, seed); }
    public void removeSeed(String key) { seeds.remove(key); }
    public void setWorldSeed(long seed) { putSeed(worldName(), seed); }
    public void removeWorldSeed() { removeSeed(worldName()); }
    public static boolean isSeedValid(long seed) {
        return mc.world != null && BiomeAccess.hashSeed(seed) == mc.world.getBiomeAccess().seed;
    }
    public static String worldName() {
        String dimension = mc.world == null ? "" : mc.world.getRegistryKey().getValue().toString();
        if (mc.isInSingleplayer() && mc.getServer() != null) {
            return mc.getServer().getSavePath(WorldSavePath.ROOT).normalize().getFileName() + "|" + dimension;
        }
        var server = mc.getCurrentServerEntry();
        if (server != null) return (server.isRealm() ? "realms" : server.address) + (mc.world == null ? "" : "|" + dimension);
        return dimension;
    }
}
