package com.codigohasta.addon.smarttpaura.pathfinding;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

public class TerrainAnalyzer {
    private final World world;
    private final Map<ChunkPos, ChunkAnalysis> chunkCache = new HashMap<>();
    
    public TerrainAnalyzer(World world) {
        this.world = world;
    }
    
    public void preAnalyzeAroundPlayer(BlockPos center, int radiusChunks) {
        // 简化：只缓存
        ChunkPos centerChunk = new ChunkPos(center);
        
        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                ChunkPos chunkPos = new ChunkPos(centerChunk.x + dx, centerChunk.z + dz);
                analyzeChunk(chunkPos);
            }
        }
    }
    
    private void analyzeChunk(ChunkPos chunkPos) {
        // 简单分析：计算通行度
        int passableBlocks = 0;
        int totalBlocks = 0;
        
        int startX = chunkPos.getStartX();
        int startZ = chunkPos.getStartZ();
        
        // 采样分析
        for (int x = startX; x < startX + 16; x += 4) {
            for (int z = startZ; z < startZ + 16; z += 4) {
                int y = world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, x, z);
                BlockPos pos = new BlockPos(x, y, z);
                
                if (world.isAir(pos) && world.isAir(pos.up())) {
                    passableBlocks++;
                }
                totalBlocks++;
            }
        }
        
        double passability = totalBlocks > 0 ? (double)passableBlocks / totalBlocks : 0;
        chunkCache.put(chunkPos, new ChunkAnalysis(chunkPos, passability));
    }
    
    public double getChunkPassability(BlockPos pos) {
        ChunkPos chunkPos = new ChunkPos(pos);
        ChunkAnalysis analysis = chunkCache.get(chunkPos);
        
        if (analysis == null) {
            analyzeChunk(chunkPos);
            analysis = chunkCache.get(chunkPos);
        }
        
        return analysis != null ? analysis.passability : 0.5;
    }
    
    public void shutdown() {
        chunkCache.clear();
    }
    
    // 内部类
    static class ChunkAnalysis {
        final ChunkPos chunkPos;
        final double passability;
        
        ChunkAnalysis(ChunkPos chunkPos, double passability) {
            this.chunkPos = chunkPos;
            this.passability = passability;
        }
    }
}