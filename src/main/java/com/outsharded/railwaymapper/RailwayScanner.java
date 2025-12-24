package com.outsharded.railwaymapper;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
//import org.bukkit.block.data.Rail;
import java.util.*;
import java.util.logging.Level;

public class RailwayScanner {
    
    private final RailwayMapperPlugin plugin;
    private final RailwayDatabase database;
 //   private final CoreProtectIntegration coreProtect;
    
    private static final Set<Material> RAIL_TYPES = EnumSet.of(
        Material.RAIL,
        Material.POWERED_RAIL,
        Material.DETECTOR_RAIL,
        Material.ACTIVATOR_RAIL
    );
    
    public RailwayScanner(RailwayMapperPlugin plugin, RailwayDatabase database
                          //CoreProtectIntegration coreProtect
                        ) {
        this.plugin = plugin;
        this.database = database;
        //this.coreProtect = coreProtect;
    }
    
    public void scanWorld(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("World not found: " + worldName);
            return;
        }
        
        plugin.getLogger().info("Starting railway scan for world: " + worldName);
        
        // Clear old data for this world
        database.clearWorldData(worldName);
        
        List<RailBlock> railBlocks = new ArrayList<>();
        int scanned = 0;
        
        // Scan all loaded chunks
        Chunk[] chunks = world.getLoadedChunks();
        plugin.getLogger().info("Scanning " + chunks.length + " loaded chunks...");
        
        for (Chunk chunk : chunks) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
                        Block block = chunk.getBlock(x, y, z);
                        
                        if (RAIL_TYPES.contains(block.getType())) {
                            scanned++;
                            
                            // // Check CoreProtect if enabled
                            // if (coreProtect.isEnabled()) {
                            //     String placer = coreProtect.getBlockPlacer(block);
                                
                            //     // Skip if not placed by a player
                            //     if (placer == null || placer.equals("#natural") || 
                            //         placer.startsWith("#")) {
                            //         continue;
                            //     }
                                
                            //     railBlocks.add(new RailBlock(
                            //         block.getX(), 
                            //         block.getY(), 
                            //         block.getZ(),
                            //         block.getType(),
                            //         worldName,
                            //         placer
                            //     ));
                            // } else 
                                {
                                // No CoreProtect, add all rails
                                railBlocks.add(new RailBlock(
                                    block.getX(), 
                                    block.getY(), 
                                    block.getZ(),
                                    block.getType(),
                                    worldName,
                                    "unknown"
                                ));
                            }
                        }
                    }
                }
            }
        }
        
        plugin.getLogger().info("Found " + scanned + " rail blocks, " + 
                               railBlocks.size() + " player-placed");
        
        // Save to database
        database.saveRailBlocks(railBlocks);
        
        // Find connected railway networks
        findNetworks(railBlocks, worldName);
        
        plugin.getLogger().info("Railway scan complete!");
    }
    
    private void findNetworks(List<RailBlock> railBlocks, String worldName) {
        plugin.getLogger().info("Finding railway networks...");
        
        // Create a map for quick lookup
        Map<BlockPos, RailBlock> railMap = new HashMap<>();
        for (RailBlock rail : railBlocks) {
            railMap.put(new BlockPos(rail.getX(), rail.getY(), rail.getZ()), rail);
        }
        
        // Track which rails have been assigned to a network
        Set<BlockPos> visited = new HashSet<>();
        int networkId = 1;
        
        for (RailBlock rail : railBlocks) {
            BlockPos pos = new BlockPos(rail.getX(), rail.getY(), rail.getZ());
            
            if (visited.contains(pos)) {
                continue;
            }
            
            // BFS to find all connected rails
            List<RailBlock> network = new ArrayList<>();
            Queue<BlockPos> queue = new LinkedList<>();
            queue.add(pos);
            visited.add(pos);
            
            while (!queue.isEmpty()) {
                BlockPos current = queue.poll();
                RailBlock currentRail = railMap.get(current);
                
                if (currentRail != null) {
                    network.add(currentRail);
                    
                    // Check all adjacent positions
                    for (BlockPos neighbor : getAdjacentRailPositions(current)) {
                        if (!visited.contains(neighbor) && railMap.containsKey(neighbor)) {
                            visited.add(neighbor);
                            queue.add(neighbor);
                        }
                    }
                }
            }
            
            if (!network.isEmpty()) {
                database.saveNetwork(networkId++, network, worldName);
            }
        }
        
        plugin.getLogger().info("Found " + (networkId - 1) + " railway networks");
    }
    
    private List<BlockPos> getAdjacentRailPositions(BlockPos pos) {
        List<BlockPos> adjacent = new ArrayList<>();
        
        // Check horizontal neighbors
        adjacent.add(new BlockPos(pos.x + 1, pos.y, pos.z));
        adjacent.add(new BlockPos(pos.x - 1, pos.y, pos.z));
        adjacent.add(new BlockPos(pos.x, pos.y, pos.z + 1));
        adjacent.add(new BlockPos(pos.x, pos.y, pos.z - 1));
        
        // Check one block up and down (for slopes)
        adjacent.add(new BlockPos(pos.x + 1, pos.y + 1, pos.z));
        adjacent.add(new BlockPos(pos.x - 1, pos.y + 1, pos.z));
        adjacent.add(new BlockPos(pos.x, pos.y + 1, pos.z + 1));
        adjacent.add(new BlockPos(pos.x, pos.y + 1, pos.z - 1));
        
        adjacent.add(new BlockPos(pos.x + 1, pos.y - 1, pos.z));
        adjacent.add(new BlockPos(pos.x - 1, pos.y - 1, pos.z));
        adjacent.add(new BlockPos(pos.x, pos.y - 1, pos.z + 1));
        adjacent.add(new BlockPos(pos.x, pos.y - 1, pos.z - 1));
        
        return adjacent;
    }
    
    private static class BlockPos {
        final int x, y, z;
        
        BlockPos(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BlockPos)) return false;
            BlockPos pos = (BlockPos) o;
            return x == pos.x && y == pos.y && z == pos.z;
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(x, y, z);
        }
    }
}