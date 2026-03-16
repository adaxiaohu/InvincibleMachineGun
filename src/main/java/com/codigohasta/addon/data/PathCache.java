package com.codigohasta.addon.smarttpaura.data;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PathCache {
    private final Map<CacheKey, List<BlockPos>> cache = new HashMap<>();
    private final int maxSize = 1000;
    
    public void put(BlockPos start, BlockPos end, List<BlockPos> path) {
        CacheKey key = new CacheKey(start, end);
        
        if (cache.size() >= maxSize) {
            // 移除最旧的一个
            CacheKey firstKey = cache.keySet().iterator().next();
            cache.remove(firstKey);
        }
        
        cache.put(key, new ArrayList<>(path));
    }
    
    public List<BlockPos> get(BlockPos start, BlockPos end) {
        CacheKey key = new CacheKey(start, end);
        List<BlockPos> path = cache.get(key);
        return path != null ? new ArrayList<>(path) : null;
    }
    
    public void clear() {
        cache.clear();
    }
    
    // 内部类
    static class CacheKey {
        final BlockPos start, end;
        
        CacheKey(BlockPos start, BlockPos end) {
            this.start = start;
            this.end = end;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof CacheKey)) return false;
            CacheKey other = (CacheKey) obj;
            return start.equals(other.start) && end.equals(other.end);
        }
        
        @Override
        public int hashCode() {
            return start.hashCode() * 31 + end.hashCode();
        }
    }
}