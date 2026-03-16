package com.codigohasta.addon.smarttpaura.data;

import net.minecraft.util.math.BlockPos;

public class Node implements Comparable<Node> {
    public final BlockPos pos;
    public Node parent;
    public double gCost;  // 起点到当前
    public double hCost;  // 当前到终点
    public double fCost;  // 总代价
    
    public Node(BlockPos pos, double gCost, double hCost, Node parent) {
        this.pos = pos;
        this.gCost = gCost;
        this.hCost = hCost;
        this.fCost = gCost + hCost;
        this.parent = parent;
    }
    
    @Override
    public int compareTo(Node other) {
        return Double.compare(this.fCost, other.fCost);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Node)) return false;
        Node other = (Node) obj;
        return pos.equals(other.pos);
    }
    
    @Override
    public int hashCode() {
        return pos.hashCode();
    }
}