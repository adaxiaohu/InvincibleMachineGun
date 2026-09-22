package com.codigohasta.addon.utils.slimefunhelper.accessors.moonrise;

import net.minecraft.world.chunk.ChunkSection;

public interface MoonriseChunkBlockCountingAccess {
    int getSpecialCollidingBlockCount();

    static MoonriseChunkBlockCountingAccess of(ChunkSection chunk) {
        return (MoonriseChunkBlockCountingAccess) chunk;
    }
}
