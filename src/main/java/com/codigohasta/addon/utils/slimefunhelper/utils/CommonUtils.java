package com.codigohasta.addon.utils.slimefunhelper.utils;
import java.io.File;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import com.codigohasta.addon.utils.slimefunhelper.utils.world.ChunkIterator;
public final class CommonUtils {
private static final MinecraftClient mc=MinecraftClient.getInstance();
public static String getWorldName() {
        // Singleplayer
        if (mc.isInSingleplayer()) {
            if (mc.world == null) return "";

            File folder = (mc.getServer())
                    .session
                    .getWorldDirectory(mc.world.getRegistryKey())
                    .toFile();
            if (folder.toPath().relativize(mc.runDirectory.toPath()).getNameCount() != 2) {
                folder = folder.getParentFile();
            }
            return folder.getName() + "|" + mc.world.getRegistryKey().getValue();
        }

        // Multiplayer
        if (mc.getCurrentServerEntry() != null) {
            return (mc.getCurrentServerEntry().isRealm() ? "realms" : mc.getCurrentServerEntry().address)
                    + (mc.world == null ? "" : "|" + mc.world.getRegistryKey().getValue());
        }

        return mc.world == null ? "" : mc.world.getRegistryKey().getValue().toString();
    }
public static Iterable<Chunk> chunks(boolean onlyWithLoadedNeighbours) {
        return () -> new ChunkIterator(onlyWithLoadedNeighbours);
    }
public static ChunkPos toChunk(BlockPos blockPos) {
        return new ChunkPos(blockPos.getX() >> 4, blockPos.getZ() >> 4);
    }
}
