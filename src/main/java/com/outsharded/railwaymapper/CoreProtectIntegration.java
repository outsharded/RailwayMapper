package com.outsharded.railwaymapper;

import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import java.util.List;
import java.util.logging.Level;

/**
 * Wrapper for CoreProtect API to query block placement history
 */
public class CoreProtectIntegration {
    
    private final RailwayMapperPlugin plugin;
    private CoreProtectAPI coreProtectAPI;
    private boolean enabled = false;
    
    public CoreProtectIntegration(RailwayMapperPlugin plugin) {
        this.plugin = plugin;
        initialize();
    }
    
    private void initialize() {
        try {
            // Get CoreProtect plugin
            Plugin coreProtectPlugin = plugin.getServer().getPluginManager().getPlugin("CoreProtect");
            
            if (coreProtectPlugin == null) {
                plugin.getLogger().warning("CoreProtect not found - block filtering disabled");
                enabled = false;
                return;
            }
            
            if (!coreProtectPlugin.isEnabled()) {
                plugin.getLogger().warning("CoreProtect is not enabled - block filtering disabled");
                enabled = false;
                return;
            }
            
            // Get CoreProtect API through the plugin instance
            if (!(coreProtectPlugin instanceof CoreProtect)) {
                plugin.getLogger().warning("CoreProtect plugin is not the expected type - block filtering disabled");
                enabled = false;
                return;
            }
            
            CoreProtect coreProtect = (CoreProtect) coreProtectPlugin;
            coreProtectAPI = coreProtect.getAPI();
            
            if (coreProtectAPI == null) {
                plugin.getLogger().warning("Failed to get CoreProtect API - block filtering disabled");
                enabled = false;
                return;
            }
            
            if (!coreProtectAPI.isEnabled()) {
                plugin.getLogger().warning("CoreProtect API is disabled - block filtering disabled");
                enabled = false;
                return;
            }
            
            enabled = true;
            plugin.getLogger().info("CoreProtect integration enabled successfully!");
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to initialize CoreProtect integration", e);
            enabled = false;
        }
    }
    
    /**
     * Checks if CoreProtect integration is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Gets the name of the player who placed a block
     * Returns null if block was naturally generated or placed by non-player
     */
    public String getBlockPlacer(Block block) {
        if (!enabled || coreProtectAPI == null) {
            return null;
        }
        
        try {
            Location location = block.getLocation();
            
            // Query CoreProtect for block lookup (action 0 = block place)
            // The API returns a list of data maps with placement info
            List<String[]> data = coreProtectAPI.blockLookup(
                block,
                0  // 0 = block place action
            );
            
            if (data != null && !data.isEmpty()) {
                // Most recent entry (index 0)
                String[] entry = data.get(0);
                
                // Entry format: [username, action, location_x, location_y, location_z, time, type, rolled_back]
                // We need the username (index 0)
                if (entry.length > 0) {
                    String username = entry[0];
                    
                    // Skip if natural generation or admin markers
                    if ("#natural".equals(username) || username.startsWith("#") || 
                        "worldedit".equalsIgnoreCase(username) || "#worldedit".equals(username)) {
                        return null;
                    }
                    
                    return username;
                }
            }
            
            return null;
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "Failed to query CoreProtect for block at " + 
                block.getLocation(), e);
            return null;
        }
    }
    
    /**
     * Checks if a block placement matches the configured filters
     */
    public boolean matchesFilters(Block block, String placer) {
        if (!enabled) {
            return true;  // If CoreProtect disabled, accept all blocks
        }
        
        if (placer == null) {
            return false;  // Natural/admin blocks rejected
        }
        
        // Get config settings
        if (!plugin.getConfig().getBoolean("coreprotect.player-placed-only", false)) {
            return true;  // Filter disabled
        }
        
        // Check age filters if configured
        int minAgeDays = plugin.getConfig().getInt("coreprotect.min-age-days", 0);
        int maxAgeDays = plugin.getConfig().getInt("coreprotect.max-age-days", 0);
        
        if (minAgeDays > 0 || maxAgeDays > 0) {
            try {
                List<String[]> data = coreProtectAPI.blockLookup(block, 0);
                if (data != null && !data.isEmpty()) {
                    String[] entry = data.get(0);
                    long timestamp = Long.parseLong(entry[5]) * 1000;  // Convert to milliseconds
                    long ageMs = System.currentTimeMillis() - timestamp;
                    long ageDays = ageMs / (1000 * 60 * 60 * 24);
                    
                    if (minAgeDays > 0 && ageDays < minAgeDays) {
                        return false;  // Too new
                    }
                    
                    if (maxAgeDays > 0 && ageDays > maxAgeDays) {
                        return false;  // Too old
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.FINE, "Failed to check block age", e);
                return true;  // Allow on error
            }
        }
        
        // Check ignore list
        List<String> ignorePlayers = plugin.getConfig().getStringList("coreprotect.ignore-players");
        if (ignorePlayers.contains(placer) || ignorePlayers.contains("#" + placer.toLowerCase())) {
            return false;
        }
        
        return true;
    }
}
