package com.outsharded.railwaymapper;

import org.bukkit.Material;

/**
 * Represents a single rail block in the world
 */
public class RailBlock {
    private final int x, y, z;
    private final Material type;
    private final String world;
    private final String placer;
    private int networkId;
    
    public RailBlock(int x, int y, int z, Material type, String world, String placer) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.type = type;
        this.world = world;
        this.placer = placer;
        this.networkId = -1;
    }
    
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public Material getType() { return type; }
    public String getWorld() { return world; }
    public String getPlacer() { return placer; }
    public int getNetworkId() { return networkId; }
    
    public void setNetworkId(int networkId) {
        this.networkId = networkId;
    }
    
    @Override
    public String toString() {
        return String.format("RailBlock{x=%d, y=%d, z=%d, type=%s, placer=%s, network=%d}",
            x, y, z, type, placer, networkId);
    }
}

/**
 * Holds statistics about the railway system
 */
class RailwayStats {
    private int totalRails;
    private int networkCount;
    private int activeMinecarts;
    private int uniqueBuilders;
    
    public int getTotalRails() { return totalRails; }
    public int getNetworkCount() { return networkCount; }
    public int getActiveMinecarts() { return activeMinecarts; }
    public int getUniqueBuilders() { return uniqueBuilders; }
    
    public void setTotalRails(int totalRails) { this.totalRails = totalRails; }
    public void setNetworkCount(int networkCount) { this.networkCount = networkCount; }
    public void setActiveMinecarts(int activeMinecarts) { this.activeMinecarts = activeMinecarts; }
    public void setUniqueBuilders(int uniqueBuilders) { this.uniqueBuilders = uniqueBuilders; }
}

/**
 * Represents a railway network (connected rails)
 */
class RailwayNetwork {
    private final int id;
    private final String world;
    private final int railCount;
    private final String mainBuilder;
    
    public RailwayNetwork(int id, String world, int railCount, String mainBuilder) {
        this.id = id;
        this.world = world;
        this.railCount = railCount;
        this.mainBuilder = mainBuilder;
    }
    
    public int getId() { return id; }
    public String getWorld() { return world; }
    public int getRailCount() { return railCount; }
    public String getMainBuilder() { return mainBuilder; }
}