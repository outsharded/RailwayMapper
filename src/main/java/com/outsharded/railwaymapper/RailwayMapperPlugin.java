package com.outsharded.railwaymapper;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.World;
import org.bukkit.Bukkit;
import java.util.logging.Level;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;

public class RailwayMapperPlugin extends JavaPlugin {
    
    private RailwayScanner scanner;
    private MinecartTracker tracker;
    private MapGenerator mapGenerator;
    private CoreProtectIntegration coreProtect;
    private RailwayDatabase database;
    
    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();
        
        // Initialize database
        database = new RailwayDatabase(this);
        database.initialize();
        
        // Initialize CoreProtect integration
        coreProtect = new CoreProtectIntegration(this);
        if (!coreProtect.isEnabled()) {
            getLogger().warning("CoreProtect not found! Railway filtering by player will be disabled.");
        }
        
        // Initialize components
        scanner = new RailwayScanner(this, database, coreProtect);
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
            sender.sendMessage("§e/railmap station §7- Manage stations");
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
                
            case "station":
                if (!sender.hasPermission("railwaymapper.scan")) {
                    sender.sendMessage("§cYou don't have permission to use this command.");
                    return true;
                }
                handleStationCommand(sender, args);
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

    public void saveDefaultConfig() {
        File dataFolder = getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File configFile = new File(dataFolder, "config.yml");
        if (configFile.exists()) return;

        try (InputStream in = getResource("config.yml")) {
            if (in == null) {
                getLogger().warning("config.yml not found in resources!");
                return;
            }
            Files.copy(in, configFile.toPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void handleScanCommand(CommandSender sender, String[] args) {
        sender.sendMessage("§aStarting railway scan...");
        
        String worldName = args.length > 1 ? args[1] : 
            (sender instanceof Player ? ((Player) sender).getWorld().getName() : "world");
        
        boolean fullScan = args.length > 2 && args[2].equalsIgnoreCase("full");
        
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    if (fullScan) {
                        scanner.scanWorldFull(worldName);
                    } else {
                        scanner.scanWorld(worldName);
                    }
                    
                    // Generate map after scan completes
                    try {
                        mapGenerator.generateMap(worldName);
                    } catch (Exception e) {
                        getLogger().log(Level.WARNING, "Error generating map", e);
                    }
                    
                    sender.sendMessage("§aRailway scan complete! Use /railmap stats to see results.");
                } catch (Exception e) {
                    sender.sendMessage("§cError during scan: " + e.getMessage());
                    getLogger().log(Level.SEVERE, "Error scanning railways", e);
                }
            }
        }.runTaskAsynchronously(this);
    }
    
    private void handleStationCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return;
        }
        
        Player player = (Player) sender;
        
        if (args.length < 2) {
            player.sendMessage("§6=== Station Commands ===");
            player.sendMessage("§e/railmap station add <name> §7- Add station at your location");
            player.sendMessage("§e/railmap station remove §7- Remove station at your location");
            player.sendMessage("§e/railmap station list §7- List all stations");
            return;
        }
        
        String subcommand = args[1].toLowerCase();
        
        switch (subcommand) {
            case "add":
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /railmap station add <name>");
                    return;
                }
                String stationName = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
                int x = player.getLocation().getBlockX();
                int y = player.getLocation().getBlockY();
                int z = player.getLocation().getBlockZ();
                database.addStation(player.getWorld().getName(), x, y, z, stationName, player.getName());
                player.sendMessage("§aStation '§e" + stationName + "§a' added at your location!");
                break;
                
            case "remove":
                int rx = player.getLocation().getBlockX();
                int ry = player.getLocation().getBlockY();
                int rz = player.getLocation().getBlockZ();
                database.removeStation(player.getWorld().getName(), rx, ry, rz);
                player.sendMessage("§aStation removed!");
                break;
                
            case "list":
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        java.util.List<RailwayDatabase.Station> stations = database.getStations(player.getWorld().getName());
                        if (stations.isEmpty()) {
                            player.sendMessage("§eNo stations in this world.");
                            return;
                        }
                        player.sendMessage("§6=== Stations ===");
                        for (RailwayDatabase.Station station : stations) {
                            player.sendMessage("§e" + station.name + " §7at " + station.x + ", " + station.y + ", " + station.z);
                        }
                    }
                }.runTaskAsynchronously(this);
                break;
                
            default:
                player.sendMessage("§cUnknown subcommand. Use /railmap station for help.");
        }
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
        boolean autoUpdateMap = getConfig().getBoolean("map.auto-update", true);
        
        new BukkitRunnable() {
            @Override
            public void run() {
                tracker.updateMinecartPositions();
                
                // Auto-update map every N ticks (if tracking enabled and map auto-update enabled)
                if (autoUpdateMap) {
                    try {
                        for (World world : Bukkit.getWorlds()) {
                            mapGenerator.generateMap(world.getName());
                        }
                    } catch (Exception e) {
                        getLogger().log(Level.FINE, "Error auto-updating map", e);
                    }
                }
            }
        }.runTaskTimer(this, 20L, updateInterval);
    }
    
    public RailwayDatabase getDatabase() {
        return database;
    }
    
    public CoreProtectIntegration getCoreProtect() {
        return coreProtect;
    }
}