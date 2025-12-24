package com.outsharded.railwaymapper;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.logging.Level;

public class RailwayMapperPlugin extends JavaPlugin {
    
    private RailwayScanner scanner;
    private MinecartTracker tracker;
    private MapGenerator mapGenerator;
//    private CoreProtectIntegration coreProtect;
    private RailwayDatabase database;
    
    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();
        
        // Initialize database
        database = new RailwayDatabase(this);
        database.initialize();
        
        // Initialize CoreProtect integration
        // coreProtect = new CoreProtectIntegration(this);
        // if (!coreProtect.isEnabled()) {
        //     getLogger().warning("CoreProtect not found! Railway filtering by player will be disabled.");
        // }
        
        // Initialize components
        // scanner = new RailwayScanner(this, database, coreProtect);
        tracker = new MinecartTracker(this, database);
        mapGenerator = new MapGenerator(this, database);
        
        // Register commands
        getCommand("railmap").setExecutor(this);
        
        // Start minecart tracking task
        startMinecartTracking();
        
        getLogger().info("RailwayMapper has been enabled!");
    }
    
    @Override
    public void onDisable() {
        if (tracker != null) {
            tracker.shutdown();
        }
        if (database != null) {
            database.close();
        }
        getLogger().info("RailwayMapper has been disabled!");
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("railmap")) {
            return false;
        }
        
        if (args.length == 0) {
            sender.sendMessage("§6=== RailwayMapper Commands ===");
            sender.sendMessage("§e/railmap scan §7- Scan world for railways");
            sender.sendMessage("§e/railmap view §7- View the railway map");
            sender.sendMessage("§e/railmap stats §7- Show railway statistics");
            sender.sendMessage("§e/railmap reload §7- Reload configuration");
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "scan":
                if (!sender.hasPermission("railwaymapper.scan")) {
                    sender.sendMessage("§cYou don't have permission to use this command.");
                    return true;
                }
                handleScanCommand(sender, args);
                break;
                
            case "view":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cThis command can only be used by players.");
                    return true;
                }
                handleViewCommand((Player) sender);
                break;
                
            case "stats":
                handleStatsCommand(sender);
                break;
                
            case "reload":
                if (!sender.hasPermission("railwaymapper.reload")) {
                    sender.sendMessage("§cYou don't have permission to use this command.");
                    return true;
                }
                reloadConfig();
                sender.sendMessage("§aConfiguration reloaded!");
                break;
                
            default:
                sender.sendMessage("§cUnknown subcommand. Use /railmap for help.");
                break;
        }
        
        return true;
    }
    
    private void handleScanCommand(CommandSender sender, String[] args) {
        sender.sendMessage("§aStarting railway scan...");
        
        String worldName = args.length > 1 ? args[1] : 
            (sender instanceof Player ? ((Player) sender).getWorld().getName() : "world");
        
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    scanner.scanWorld(worldName);
                    sender.sendMessage("§aRailway scan complete! Use /railmap stats to see results.");
                } catch (Exception e) {
                    sender.sendMessage("§cError during scan: " + e.getMessage());
                    getLogger().log(Level.SEVERE, "Error scanning railways", e);
                }
            }
        }.runTaskAsynchronously(this);
    }
    
    private void handleViewCommand(Player player) {
        String mapUrl = getConfig().getString("map.url", "http://localhost:8080/railmap.html");
        
        player.sendMessage("§6=== Railway Map ===");
        player.sendMessage("§eView the interactive map at:");
        player.sendMessage("§b" + mapUrl);
        
        // Generate a fresh map
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    mapGenerator.generateMap(player.getWorld().getName());
                    player.sendMessage("§aMap has been updated!");
                } catch (Exception e) {
                    player.sendMessage("§cError generating map: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(this);
    }
    
    private void handleStatsCommand(CommandSender sender) {
        new BukkitRunnable() {
            @Override
            public void run() {
                RailwayStats stats = database.getStats();
                sender.sendMessage("§6=== Railway Statistics ===");
                sender.sendMessage("§eTotal rail blocks: §f" + stats.getTotalRails());
                sender.sendMessage("§eRailway networks: §f" + stats.getNetworkCount());
                sender.sendMessage("§eActive minecarts: §f" + stats.getActiveMinecarts());
                sender.sendMessage("§eUnique builders: §f" + stats.getUniqueBuilders());
            }
        }.runTaskAsynchronously(this);
    }
    
    private void startMinecartTracking() {
        int updateInterval = getConfig().getInt("tracking.update-interval", 20); // ticks
        
        new BukkitRunnable() {
            @Override
            public void run() {
                tracker.updateMinecartPositions();
            }
        }.runTaskTimer(this, 20L, updateInterval);
    }
    
    public RailwayDatabase getDatabase() {
        return database;
    }
    
    // public CoreProtectIntegration getCoreProtect() {
    //     return coreProtect;
    // }
}