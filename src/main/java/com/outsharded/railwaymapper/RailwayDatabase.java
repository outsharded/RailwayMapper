package com.outsharded.railwaymapper;

import org.bukkit.Material;
import com.outsharded.railwaymapper.MinecartTracker.MinecartData;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;

public class RailwayDatabase {
    
    private final RailwayMapperPlugin plugin;
    private Connection connection;
    
    public RailwayDatabase(RailwayMapperPlugin plugin) {
        this.plugin = plugin;
    }
    
    public void initialize() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            
            File dbFile = new File(dataFolder, "railways.db");
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            
            connection = DriverManager.getConnection(url);
            createTables();
            
            plugin.getLogger().info("Database initialized successfully!");
            
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize database", e);
        }
    }
    
    private void createTables() throws SQLException {
        Statement stmt = connection.createStatement();
        
        // Rail blocks table
        stmt.execute(
            "CREATE TABLE IF NOT EXISTS rail_blocks (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "world TEXT NOT NULL," +
            "x INTEGER NOT NULL," +
            "y INTEGER NOT NULL," +
            "z INTEGER NOT NULL," +
            "type TEXT NOT NULL," +
            "placer TEXT," +
            "network_id INTEGER," +
            "UNIQUE(world, x, y, z))"
        );
        
        // Railway networks table
        stmt.execute(
            "CREATE TABLE IF NOT EXISTS networks (" +
            "id INTEGER PRIMARY KEY," +
            "world TEXT NOT NULL," +
            "rail_count INTEGER," +
            "main_builder TEXT," +
            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
        );
        
        // Minecart positions table
        stmt.execute(
            "CREATE TABLE IF NOT EXISTS minecart_positions (" +
            "cart_id TEXT PRIMARY KEY," +
            "world TEXT NOT NULL," +
            "x REAL NOT NULL," +
            "y REAL NOT NULL," +
            "z REAL NOT NULL," +
            "velocity_x REAL," +
            "velocity_y REAL," +
            "velocity_z REAL," +
            "occupied INTEGER," +
            "passenger TEXT," +
            "last_updated TIMESTAMP)"
        );
        
        // Create indexes for faster queries
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_rails_world ON rail_blocks(world)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_rails_network ON rail_blocks(network_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_minecarts_world ON minecart_positions(world)");
        
        stmt.close();
    }
    
    public void clearWorldData(String worldName) {
        try {
            PreparedStatement stmt = connection.prepareStatement(
                "DELETE FROM rail_blocks WHERE world = ?"
            );
            stmt.setString(1, worldName);
            stmt.executeUpdate();
            stmt.close();
            
            stmt = connection.prepareStatement(
                "DELETE FROM networks WHERE world = ?"
            );
            stmt.setString(1, worldName);
            stmt.executeUpdate();
            stmt.close();
            
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error clearing world data", e);
        }
    }
    
    public void saveRailBlocks(List<RailBlock> blocks) {
        try {
            connection.setAutoCommit(false);
            
            PreparedStatement stmt = connection.prepareStatement(
                "INSERT OR REPLACE INTO rail_blocks (world, x, y, z, type, placer) " +
                "VALUES (?, ?, ?, ?, ?, ?)"
            );
            
            for (RailBlock block : blocks) {
                stmt.setString(1, block.getWorld());
                stmt.setInt(2, block.getX());
                stmt.setInt(3, block.getY());
                stmt.setInt(4, block.getZ());
                stmt.setString(5, block.getType().name());
                stmt.setString(6, block.getPlacer());
                stmt.addBatch();
            }
            
            stmt.executeBatch();
            connection.commit();
            connection.setAutoCommit(true);
            stmt.close();
            
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error saving rail blocks", e);
            try {
                connection.rollback();
                connection.setAutoCommit(true);
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "Error rolling back transaction", ex);
            }
        }
    }
    
    public void saveNetwork(int networkId, List<RailBlock> rails, String worldName) {
        try {
            // Count builders
            Map<String, Integer> builderCounts = new HashMap<>();
            for (RailBlock rail : rails) {
                String builder = rail.getPlacer();
                builderCounts.put(builder, builderCounts.getOrDefault(builder, 0) + 1);
            }
            
            // Find main builder
            String mainBuilder = builderCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("unknown");
            
            // Save network info
            PreparedStatement stmt = connection.prepareStatement(
                "INSERT OR REPLACE INTO networks (id, world, rail_count, main_builder) " +
                "VALUES (?, ?, ?, ?)"
            );
            stmt.setInt(1, networkId);
            stmt.setString(2, worldName);
            stmt.setInt(3, rails.size());
            stmt.setString(4, mainBuilder);
            stmt.executeUpdate();
            stmt.close();
            
            // Update rail blocks with network ID
            connection.setAutoCommit(false);
            stmt = connection.prepareStatement(
                "UPDATE rail_blocks SET network_id = ? " +
                "WHERE world = ? AND x = ? AND y = ? AND z = ?"
            );
            
            for (RailBlock rail : rails) {
                stmt.setInt(1, networkId);
                stmt.setString(2, worldName);
                stmt.setInt(3, rail.getX());
                stmt.setInt(4, rail.getY());
                stmt.setInt(5, rail.getZ());
                stmt.addBatch();
            }
            
            stmt.executeBatch();
            connection.commit();
            connection.setAutoCommit(true);
            stmt.close();
            
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error saving network", e);
            try {
                connection.rollback();
                connection.setAutoCommit(true);
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "Error rolling back", ex);
            }
        }
    }
    
    public void updateMinecartPositions(List<MinecartData> minecarts) {
        try {
            connection.setAutoCommit(false);
            
            PreparedStatement stmt = connection.prepareStatement(
                "INSERT OR REPLACE INTO minecart_positions " +
                "(cart_id, world, x, y, z, velocity_x, velocity_y, velocity_z, " +
                "occupied, passenger, last_updated) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
            );
            
            for (MinecartData cart : minecarts) {
                stmt.setString(1, cart.getId().toString());
                stmt.setString(2, cart.getWorld());
                stmt.setDouble(3, cart.getX());
                stmt.setDouble(4, cart.getY());
                stmt.setDouble(5, cart.getZ());
                stmt.setDouble(6, cart.getVelocity().getX());
                stmt.setDouble(7, cart.getVelocity().getY());
                stmt.setDouble(8, cart.getVelocity().getZ());
                stmt.setInt(9, cart.isOccupied() ? 1 : 0);
                stmt.setString(10, cart.getPassenger());
                stmt.setLong(11, cart.getTimestamp());
                stmt.addBatch();
            }
            
            stmt.executeBatch();
            connection.commit();
            connection.setAutoCommit(true);
            stmt.close();
            
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error updating minecart positions", e);
        }
    }
    
    public List<RailBlock> getAllRails(String worldName) {
        List<RailBlock> rails = new ArrayList<>();
        
        try {
            PreparedStatement stmt = connection.prepareStatement(
                "SELECT x, y, z, type, placer, network_id FROM rail_blocks WHERE world = ?"
            );
            stmt.setString(1, worldName);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                RailBlock rail = new RailBlock(
                    rs.getInt("x"),
                    rs.getInt("y"),
                    rs.getInt("z"),
                    Material.valueOf(rs.getString("type")),
                    worldName,
                    rs.getString("placer")
                );
                rail.setNetworkId(rs.getInt("network_id"));
                rails.add(rail);
            }
            
            rs.close();
            stmt.close();
            
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error loading rails", e);
        }
        
        return rails;
    }
    
    public List<MinecartData> getActiveMinecarts(String worldName) {
        List<MinecartData> carts = new ArrayList<>();
        
        try {
            PreparedStatement stmt = connection.prepareStatement(
                "SELECT * FROM minecart_positions WHERE world = ?"
            );
            stmt.setString(1, worldName);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                MinecartData cart = new MinecartData(
                    UUID.fromString(rs.getString("cart_id")),
                    rs.getDouble("x"),
                    rs.getDouble("y"),
                    rs.getDouble("z"),
                    rs.getString("world"),
                    new org.bukkit.util.Vector(
                        rs.getDouble("velocity_x"),
                        rs.getDouble("velocity_y"),
                        rs.getDouble("velocity_z")
                    ),
                    rs.getInt("occupied") == 1,
                    rs.getString("passenger"),
                    rs.getLong("last_updated")
                );
                carts.add(cart);
            }
            
            rs.close();
            stmt.close();
            
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error loading minecarts", e);
        }
        
        return carts;
    }
    
    public RailwayStats getStats() {
        RailwayStats stats = new RailwayStats();
        
        try {
            Statement stmt = connection.createStatement();
            
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) as count FROM rail_blocks");
            if (rs.next()) {
                stats.setTotalRails(rs.getInt("count"));
            }
            rs.close();
            
            rs = stmt.executeQuery("SELECT COUNT(*) as count FROM networks");
            if (rs.next()) {
                stats.setNetworkCount(rs.getInt("count"));
            }
            rs.close();
            
            rs = stmt.executeQuery("SELECT COUNT(*) as count FROM minecart_positions");
            if (rs.next()) {
                stats.setActiveMinecarts(rs.getInt("count"));
            }
            rs.close();
            
            rs = stmt.executeQuery("SELECT COUNT(DISTINCT placer) as count FROM rail_blocks");
            if (rs.next()) {
                stats.setUniqueBuilders(rs.getInt("count"));
            }
            rs.close();
            
            stmt.close();
            
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error getting stats", e);
        }
        
        return stats;
    }
    
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error closing database", e);
        }
    }
}