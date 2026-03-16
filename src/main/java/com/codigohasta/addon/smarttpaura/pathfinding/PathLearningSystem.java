package com.codigohasta.addon.smarttpaura.pathfinding;

import net.minecraft.util.math.BlockPos;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class PathLearningSystem {
    private static final String DATA_FILE = "smart_tpaura_learning.dat";
    private final Map<PathKey, PathData> pathCache = new HashMap<>();
    
    public void learnPath(BlockPos start, BlockPos end, List<BlockPos> path, 
                         boolean success, long duration) {
        PathKey key = new PathKey(start, end);
        PathData data = pathCache.get(key);
        
        if (data == null) {
            data = new PathData(path, success ? 1 : 0, 1, duration);
        } else {
            data.update(path, success, duration);
        }
        
        pathCache.put(key, data);
        
        // 定期保存
        if (pathCache.size() % 100 == 0) {
            saveData();
        }
    }
    
    public List<BlockPos> getCachedPath(BlockPos start, BlockPos end) {
        PathKey key = new PathKey(start, end);
        PathData data = pathCache.get(key);
        
        if (data != null && data.successRate > 0.7) {
            return new ArrayList<>(data.path);
        }
        
        return null;
    }
    
    public void saveData() {
        try (ObjectOutputStream oos = new ObjectOutputStream(
             new GZIPOutputStream(new FileOutputStream(DATA_FILE)))) {
            oos.writeObject(pathCache);
        } catch (IOException e) {
            System.err.println("保存学习数据失败: " + e.getMessage());
        }
    }
    
    @SuppressWarnings("unchecked")
    public void loadData() {
        File file = new File(DATA_FILE);
        if (!file.exists()) return;
        
        try (ObjectInputStream ois = new ObjectInputStream(
             new GZIPInputStream(new FileInputStream(DATA_FILE)))) {
            pathCache.putAll((Map<PathKey, PathData>) ois.readObject());
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("加载学习数据失败: " + e.getMessage());
        }
    }
    
    // 内部类
    static class PathKey implements Serializable {
        final BlockPos start, end;
        
        PathKey(BlockPos start, BlockPos end) {
            this.start = start;
            this.end = end;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof PathKey)) return false;
            PathKey other = (PathKey) obj;
            return start.equals(other.start) && end.equals(other.end);
        }
        
        @Override
        public int hashCode() {
            return start.hashCode() * 31 + end.hashCode();
        }
    }
    
    static class PathData implements Serializable {
        List<BlockPos> path;
        double successRate;
        int usageCount;
        double averageTime;
        
        PathData(List<BlockPos> path, double successRate, int usageCount, double averageTime) {
            this.path = new ArrayList<>(path);
            this.successRate = successRate;
            this.usageCount = usageCount;
            this.averageTime = averageTime;
        }
        
        void update(List<BlockPos> newPath, boolean success, long duration) {
            this.path = new ArrayList<>(newPath);
            this.successRate = (successRate * usageCount + (success ? 1 : 0)) / (usageCount + 1);
            this.averageTime = (averageTime * usageCount + duration) / (usageCount + 1);
            this.usageCount++;
        }
    }
}