package com.outsharded.railwaymapper;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MinecartTracker {
    
    private final RailwayMapperPlugin plugin;
    private final RailwayDatabase database;
    
    // Track minecart positions and data
    private final Map<UUID, MinecartData> trackedMinecarts;
    
    public MinecartTracker(RailwayMapperPlugin plugin, RailwayDatabase database) {
        this.plugin = plugin;
        this.database = database;
        this.trackedMinecarts = new ConcurrentHashMap<>();
    }
    
    /**
     * Updates positions of all minecarts across all worlds
     */
    public void updateMinecartPositions() {
        Set<UUID> currentMinecarts = new HashSet<>();
        
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Minecart) {
                    Minecart cart = (Minecart) entity;
                    UUID cartId = cart.getUniqueId();
                    
                    currentMinecarts.add(cartId);
                    
                    MinecartData data = new MinecartData(
                        cartId,
                        cart.getLocation().getX(),
                        cart.getLocation().getY(),
                        cart.getLocation().getZ(),
                        world.getName(),
                        cart.getVelocity(),
                        !cart.isEmpty(),
                        getPassengerName(cart),
                        System.currentTimeMillis()
                    );
                    
                    trackedMinecarts.put(cartId, data);
                }
            }
        }
        
        // Remove minecarts that no longer exist
        trackedMinecarts.keySet().retainAll(currentMinecarts);
        
        // Save to database (async)
        if (!trackedMinecarts.isEmpty()) {
            database.updateMinecartPositions(new ArrayList<>(trackedMinecarts.values()));
        }
    }
    
    private String getPassengerName(Minecart cart) {
        if (cart.isEmpty()) {
            return null;
        }
        
        for (Entity passenger : cart.getPassengers()) {
            if (passenger instanceof Player) {
                return ((Player) passenger).getName();
            }
        }
        
        return "entity";
    }
    
    /**
     * Get all currently tracked minecarts
     */
    public Collection<MinecartData> getTrackedMinecarts() {
        return new ArrayList<>(trackedMinecarts.values());
    }
    
    /**
     * Get tracked minecarts in a specific world
     */
    public List<MinecartData> getMinecarts(String worldName) {
        List<MinecartData> result = new ArrayList<>();
        for (MinecartData data : trackedMinecarts.values()) {
            if (data.getWorld().equals(worldName)) {
                result.add(data);
            }
        }
        return result;
    }
    
    /**
     * Get the total number of active minecarts
     */
    public int getActiveMinecartCount() {
        return trackedMinecarts.size();
    }
    
    /**
     * Get the number of occupied minecarts
     */
    public int getOccupiedMinecartCount() {
        int count = 0;
        for (MinecartData data : trackedMinecarts.values()) {
            if (data.isOccupied()) {
                count++;
            }
        }
        return count;
    }
    
    public void shutdown() {
        trackedMinecarts.clear();
    }
    
    /**
     * Data class to hold minecart information
     */
    public static class MinecartData {
        private final UUID id;
        private final double x, y, z;
        private final String world;
        private final Vector velocity;
        private final boolean occupied;
        private final String passenger;
        private final long timestamp;
        
        public MinecartData(UUID id, double x, double y, double z, String world,
                           Vector velocity, boolean occupied, String passenger, long timestamp) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.z = z;
            this.world = world;
            this.velocity = velocity;
            this.occupied = occupied;
            this.passenger = passenger;
            this.timestamp = timestamp;
        }
        
        public UUID getId() { return id; }
        public double getX() { return x; }
        public double getY() { return y; }
        public double getZ() { return z; }
        public String getWorld() { return world; }
        public Vector getVelocity() { return velocity; }
        public boolean isOccupied() { return occupied; }
        public String getPassenger() { return passenger; }
        public long getTimestamp() { return timestamp; }
        
        public double getSpeed() {
            return velocity.length();
        }
        
        public String getDirection() {
            double x = velocity.getX();
            double z = velocity.getZ();
            
            if (Math.abs(x) < 0.01 && Math.abs(z) < 0.01) {
                return "stopped";
            }
            
            double angle = Math.toDegrees(Math.atan2(z, x));
            
            if (angle < 0) angle += 360;
            
            if (angle >= 337.5 || angle < 22.5) return "east";
            if (angle >= 22.5 && angle < 67.5) return "southeast";
            if (angle >= 67.5 && angle < 112.5) return "south";
            if (angle >= 112.5 && angle < 157.5) return "southwest";
            if (angle >= 157.5 && angle < 202.5) return "west";
            if (angle >= 202.5 && angle < 247.5) return "northwest";
            if (angle >= 247.5 && angle < 292.5) return "north";
            if (angle >= 292.5 && angle < 337.5) return "northeast";
            
            return "unknown";
        }
    }
}