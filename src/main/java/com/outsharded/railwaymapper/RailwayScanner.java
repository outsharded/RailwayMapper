package com.outsharded.railwaymapper;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
//import org.bukkit.block.data.Rail;
import java.util.*;
import java.util.logging.Level;
import java.util.concurrent.ConcurrentHashMap;

public class RailwayScanner {
    
    private final RailwayMapperPlugin plugin;
    private final RailwayDatabase database;
    private final CoreProtectIntegration coreProtect;
    
    private static final Set<Material> RAIL_TYPES = EnumSet.of(
        Material.RAIL,
        Material.POWERED_RAIL,
        Material.DETECTOR_RAIL,
        Material.ACTIVATOR_RAIL
    );
    
    private static final int[][] ADJACENT_OFFSETS = {
        {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
        {1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1},
        {1, -1, 0}, {-1, -1, 0}, {0, -1, 1}, {0, -1, -1}
    };
    
    public RailwayScanner(RailwayMapperPlugin plugin, RailwayDatabase database,
                          CoreProtectIntegration coreProtect) {
        this.plugin = plugin;
        this.database = database;
        this.coreProtect = coreProtect;
    }
    
    public void scanWorld(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("World not found: " + worldName);
            return;
        }
        
        plugin.getLogger().info("Starting railway scan for world: " + worldName);
        
        Set<String> scannedChunks = ConcurrentHashMap.newKeySet();
        Map<Integer, RailLine> railLines = new ConcurrentHashMap<>();
        Set<String> tracedRails = ConcurrentHashMap.newKeySet();
        List<RailBlock> allRailBlocks = new ArrayList<>();
        int nextNetworkId = 1;
        
        // Scan 3 chunks around each player
        for (Player player : world.getPlayers()) {
            Chunk playerChunk = player.getLocation().getChunk();
            addChunksInRadius(world, playerChunk.getX(), playerChunk.getZ(), 3, scannedChunks, 
                            railLines, tracedRails, worldName, allRailBlocks);
        }
        
        // Scan 3 chunks around each station
        int stationRadius = plugin.getConfig().getInt("scanning.station-radius", 3);
        List<RailwayDatabase.Station> stations = database.getStations(worldName);
        for (RailwayDatabase.Station station : stations) {
            Chunk stationChunk = world.getChunkAt(station.x >> 4, station.z >> 4);
            addChunksInRadius(world, stationChunk.getX(), stationChunk.getZ(), stationRadius, 
                            scannedChunks, railLines, tracedRails, worldName, allRailBlocks);
        }
        
        plugin.getLogger().info("Scanned " + scannedChunks.size() + " chunks, found " + 
                               railLines.size() + " complete rail lines, " + allRailBlocks.size() + " blocks");
        
        // Save individual rail blocks for statistics
        if (!allRailBlocks.isEmpty()) {
            database.saveRailBlocks(allRailBlocks);
        }
        
        // Save rail networks to database
        saveRailNetworks(worldName, railLines.values());
        
        plugin.getLogger().info("Railway scan complete!");
    }
    
    public void scanWorldFull(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("World not found: " + worldName);
            return;
        }
        
        plugin.getLogger().info("Starting FULL railway scan for world: " + worldName);
        
        Set<String> scannedChunks = ConcurrentHashMap.newKeySet();
        Map<Integer, RailLine> railLines = new ConcurrentHashMap<>();
        Set<String> tracedRails = ConcurrentHashMap.newKeySet();
        List<RailBlock> allRailBlocks = new ArrayList<>();
        
        // Scan all loaded chunks in the world
        for (Chunk chunk : world.getLoadedChunks()) {
            String chunkKey = chunk.getX() + "," + chunk.getZ();
            if (scannedChunks.contains(chunkKey)) continue;
            scannedChunks.add(chunkKey);
            
            try {
                scanChunk(chunk, worldName, railLines, tracedRails, allRailBlocks);
            } catch (Exception e) {
                plugin.getLogger().log(Level.FINE, "Error scanning chunk", e);
            }
        }
        
        plugin.getLogger().info("FULL scan: Scanned " + scannedChunks.size() + " chunks, found " + 
                               railLines.size() + " complete rail lines, " + allRailBlocks.size() + " blocks");
        
        // Save individual rail blocks for statistics
        if (!allRailBlocks.isEmpty()) {
            database.saveRailBlocks(allRailBlocks);
        }
        
        // Save rail networks to database
        saveRailNetworks(worldName, railLines.values());
        
        plugin.getLogger().info("FULL railway scan complete!");
    }
    
    private void addChunksInRadius(World world, int centerX, int centerZ, int radius, 
                                    Set<String> scannedChunks, Map<Integer, RailLine> railLines,
                                    Set<String> tracedRails, String worldName, List<RailBlock> allRailBlocks) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int chunkX = centerX + dx;
                int chunkZ = centerZ + dz;
                String chunkKey = chunkX + "," + chunkZ;
                
                if (scannedChunks.contains(chunkKey)) continue;
                scannedChunks.add(chunkKey);
                
                try {
                    Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                    scanChunk(chunk, worldName, railLines, tracedRails, allRailBlocks);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.FINE, "Error scanning chunk", e);
                }
            }
        }
    }
    
    private void scanChunk(Chunk chunk, String worldName, Map<Integer, RailLine> railLines,
                          Set<String> tracedRails, List<RailBlock> allRailBlocks) {
        World world = chunk.getWorld();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
                    Block block = chunk.getBlock(x, y, z);
                    
                    if (!RAIL_TYPES.contains(block.getType())) continue;
                    
                    String blockKey = block.getX() + "," + block.getY() + "," + block.getZ();
                    if (tracedRails.contains(blockKey)) continue;
                    
                    String placer = null;
                    // Check CoreProtect filter
                    if (coreProtect.isEnabled()) {
                        placer = coreProtect.getBlockPlacer(block);
                        if (!coreProtect.matchesFilters(block, placer)) continue;
                    }
                    
                    // Add to rail blocks list
                    allRailBlocks.add(new RailBlock(block.getX(), block.getY(), block.getZ(), 
                                                     block.getType(), worldName, placer));
                    
                    // Trace complete rail line from this block
                    traceRailLine(block, worldName, railLines, tracedRails);
                }
            }
        }
    }
    
    private void traceRailLine(Block startBlock, String worldName, Map<Integer, RailLine> railLines,
                              Set<String> tracedRails) {
        RailLine line = new RailLine(railLines.size() + 1, getNetworkColor(railLines.size() + 1));
        Set<String> visited = new HashSet<>();
        Stack<Block> toProcess = new Stack<>();
        toProcess.push(startBlock);
        
        List<Block> lineBlocks = new ArrayList<>();
        
        // Collect all connected rails
        while (!toProcess.isEmpty()) {
            Block current = toProcess.pop();
            String key = current.getX() + "," + current.getY() + "," + current.getZ();
            
            if (visited.contains(key)) continue;
            visited.add(key);
            tracedRails.add(key);
            lineBlocks.add(current);
            
            // Find adjacent rails
            for (int[] offset : ADJACENT_OFFSETS) {
                Block adjacent = current.getRelative(offset[0], offset[1], offset[2]);
                if (RAIL_TYPES.contains(adjacent.getType())) {
                    String adjKey = adjacent.getX() + "," + adjacent.getY() + "," + adjacent.getZ();
                    if (!visited.contains(adjKey)) {
                        toProcess.push(adjacent);
                    }
                }
            }
        }
        
        // Sort blocks to create a continuous path
        List<Block> orderedBlocks = orderBlocksIntoPath(lineBlocks);
        
        // Add vertices only where direction changes
        int[] prevDir = null;
        for (int i = 0; i < orderedBlocks.size(); i++) {
            Block block = orderedBlocks.get(i);
            
            // Determine direction to next block
            int[] currentDir = null;
            if (i < orderedBlocks.size() - 1) {
                Block next = orderedBlocks.get(i + 1);
                currentDir = new int[]{
                    Integer.compare(next.getX(), block.getX()),
                    Integer.compare(next.getY(), block.getY()),
                    Integer.compare(next.getZ(), block.getZ())
                };
            }
            
            // Add vertex if direction changes or it's the start/end
            if (i == 0 || i == orderedBlocks.size() - 1 || !Arrays.equals(currentDir, prevDir)) {
                line.addVertex(block.getX(), block.getY(), block.getZ());
            }
            
            prevDir = currentDir;
        }
        
        if (line.vertices.size() > 1) {
            railLines.put(line.networkId, line);
        }
    }
    
    private List<Block> orderBlocksIntoPath(List<Block> blocks) {
        if (blocks.size() <= 1) return blocks;
        
        List<Block> ordered = new ArrayList<>();
        Set<String> used = new HashSet<>();
        Block current = blocks.get(0);
        ordered.add(current);
        used.add(current.getX() + "," + current.getY() + "," + current.getZ());
        
        // Greedy path building
        while (ordered.size() < blocks.size()) {
            Block next = null;
            double minDist = Double.MAX_VALUE;
            
            for (Block candidate : blocks) {
                String key = candidate.getX() + "," + candidate.getY() + "," + candidate.getZ();
                if (used.contains(key)) continue;
                
                double dist = current.getLocation().distance(candidate.getLocation());
                if (dist < minDist && dist <= 2.5) {
                    next = candidate;
                    minDist = dist;
                }
            }
            
            if (next == null) break;
            ordered.add(next);
            used.add(next.getX() + "," + next.getY() + "," + next.getZ());
            current = next;
        }
        
        return ordered;
    }
    
    private String getNetworkColor(int networkId) {
        List<String> colors = plugin.getConfig().getStringList("display.network-colors");
        if (colors.isEmpty()) return "#FF6B6B";
        return colors.get(networkId % colors.size());
    }
    
    private void saveRailNetworks(String worldName, Collection<RailLine> lines) {
        try {
            // Build JSON for rail lines
            StringBuilder json = new StringBuilder();
            json.append("[");
            int count = 0;
            for (RailLine line : lines) {
                if (count > 0) json.append(",");
                json.append("\n  {\n");
                json.append("    \"networkId\": ").append(line.networkId).append(",\n");
                json.append("    \"color\": \"").append(line.color).append("\",\n");
                json.append("    \"vertices\": [\n");
                for (int i = 0; i < line.vertices.size(); i++) {
                    int[] v = line.vertices.get(i);
                    if (i > 0) json.append(",\n");
                    json.append("      [").append(v[0]).append(", ").append(v[1]).append(", ").append(v[2]).append("]");
                }
                json.append("\n    ]\n");
                json.append("  }");
                count++;
            }
            json.append("\n]");
            
            // Save to database (in plugin folder)
            database.saveRailNetworks(worldName, json.toString());
            plugin.getLogger().info("Saved " + lines.size() + " rail networks for world '" + worldName + "' to database");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error saving rail networks", e);
        }
    }
}