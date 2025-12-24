package com.outsharded.railwaymapper;

import com.outsharded.railwaymapper.MinecartTracker.MinecartData;
import org.bukkit.Material;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Level;

public class MapGenerator {
    
    private final RailwayMapperPlugin plugin;
    private final RailwayDatabase database;
    
    // Colors for different rail types
    private static final Map<Material, String> RAIL_COLORS = new HashMap<>();
    static {
        RAIL_COLORS.put(Material.RAIL, "#888888");
        RAIL_COLORS.put(Material.POWERED_RAIL, "#FFD700");
        RAIL_COLORS.put(Material.DETECTOR_RAIL, "#FF4444");
        RAIL_COLORS.put(Material.ACTIVATOR_RAIL, "#4444FF");
    }
    
    // Network colors (cycling through a palette)
    private static final String[] NETWORK_COLORS = {
        "#FF6B6B", "#4ECDC4", "#45B7D1", "#FFA07A", "#98D8C8",
        "#F7DC6F", "#BB8FCE", "#85C1E2", "#F8B88B", "#76D7C4"
    };
    
    public MapGenerator(RailwayMapperPlugin plugin, RailwayDatabase database) {
        this.plugin = plugin;
        this.database = database;
    }
    
    public void generateMap(String worldName) throws IOException {
        plugin.getLogger().info("Generating map for world: " + worldName);
        
        // Load railway data
        List<RailBlock> rails = database.getAllRails(worldName);
        List<MinecartData> minecarts = database.getActiveMinecarts(worldName);
        
        if (rails.isEmpty()) {
            plugin.getLogger().warning("No railway data found for world: " + worldName);
            return;
        }
        
        // Calculate bounds
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        
        for (RailBlock rail : rails) {
            minX = Math.min(minX, rail.getX());
            maxX = Math.max(maxX, rail.getX());
            minZ = Math.min(minZ, rail.getZ());
            maxZ = Math.max(maxZ, rail.getZ());
        }
        
        // Generate HTML map
        String html = generateHTMLMap(rails, minecarts, minX, maxX, minZ, maxZ, worldName);
        
        // Save to file
        File webDir = new File(plugin.getDataFolder(), "web");
        if (!webDir.exists()) {
            webDir.mkdirs();
        }
        
        File mapFile = new File(webDir, "railmap.html");
        Files.write(mapFile.toPath(), html.getBytes());
        
        // Generate JSON data file for dynamic updates
        String json = generateJSONData(rails, minecarts);
        File jsonFile = new File(webDir, "raildata.json");
        Files.write(jsonFile.toPath(), json.getBytes());
        
        plugin.getLogger().info("Map generated successfully at: " + mapFile.getAbsolutePath());
    }
    
    private String generateHTMLMap(List<RailBlock> rails, List<MinecartData> minecarts,
                                   int minX, int maxX, int minZ, int maxZ, String worldName) {
        
        StringBuilder html = new StringBuilder();
        
        html.append("<!DOCTYPE html>\n");
        html.append("<html>\n<head>\n");
        html.append("<title>Railway Map - ").append(worldName).append("</title>\n");
        html.append("<meta charset='UTF-8'>\n");
        html.append("<style>\n");
        html.append(getCSS());
        html.append("</style>\n");
        html.append("</head>\n<body>\n");
        
        html.append("<div class='header'>\n");
        html.append("<h1>Railway Map: ").append(worldName).append("</h1>\n");
        html.append("<div class='stats'>\n");
        html.append("<span>Rails: ").append(rails.size()).append("</span>\n");
        html.append("<span>Active Minecarts: ").append(minecarts.size()).append("</span>\n");
        html.append("</div>\n");
        html.append("</div>\n");
        
        html.append("<div class='legend'>\n");
        html.append("<h3>Legend</h3>\n");
        html.append("<div class='legend-item'><span class='color-box' style='background:#888888'></span> Normal Rail</div>\n");
        html.append("<div class='legend-item'><span class='color-box' style='background:#FFD700'></span> Powered Rail</div>\n");
        html.append("<div class='legend-item'><span class='color-box' style='background:#FF4444'></span> Detector Rail</div>\n");
        html.append("<div class='legend-item'><span class='color-box' style='background:#4444FF'></span> Activator Rail</div>\n");
        html.append("<div class='legend-item'><span class='color-box minecart-marker'></span> Minecart</div>\n");
        html.append("</div>\n");
        
        html.append("<div class='map-container'>\n");
        html.append("<canvas id='railmap' width='2000' height='2000'></canvas>\n");
        html.append("</div>\n");
        
        html.append("<script>\n");
        html.append(getJavaScript(rails, minecarts, minX, maxX, minZ, maxZ));
        html.append("</script>\n");
        
        html.append("</body>\n</html>");
        
        return html.toString();
    }
    
    private String getCSS() {
        return "body { margin: 0; padding: 20px; font-family: Arial, sans-serif; background: #1a1a1a; color: #fff; }\n" +
               ".header { text-align: center; margin-bottom: 20px; }\n" +
               ".header h1 { margin: 0; color: #4ECDC4; }\n" +
               ".stats { margin-top: 10px; }\n" +
               ".stats span { margin: 0 15px; padding: 5px 15px; background: #2a2a2a; border-radius: 5px; }\n" +
               ".legend { position: fixed; top: 20px; right: 20px; background: #2a2a2a; padding: 15px; border-radius: 10px; }\n" +
               ".legend h3 { margin: 0 0 10px 0; }\n" +
               ".legend-item { margin: 8px 0; display: flex; align-items: center; }\n" +
               ".color-box { width: 20px; height: 20px; border-radius: 3px; margin-right: 10px; display: inline-block; }\n" +
               ".minecart-marker { background: #FF1744; border: 2px solid #fff; border-radius: 50%; }\n" +
               ".map-container { display: flex; justify-content: center; margin-top: 20px; overflow: auto; }\n" +
               "canvas { border: 2px solid #4ECDC4; background: #0a0a0a; cursor: move; }\n";
    }
    
    private String getJavaScript(List<RailBlock> rails, List<MinecartData> minecarts,
                                 int minX, int maxX, int minZ, int maxZ) {
        
        StringBuilder js = new StringBuilder();
        
        js.append("const canvas = document.getElementById('railmap');\n");
        js.append("const ctx = canvas.getContext('2d');\n");
        
        js.append("const bounds = {minX: ").append(minX).append(", maxX: ").append(maxX)
          .append(", minZ: ").append(minZ).append(", maxZ: ").append(maxZ).append("};\n");
        
        js.append("const scale = Math.min(canvas.width / (bounds.maxX - bounds.minX + 100), " +
                  "canvas.height / (bounds.maxZ - bounds.minZ + 100));\n");
        
        js.append("function worldToCanvas(x, z) {\n");
        js.append("  return {\n");
        js.append("    x: (x - bounds.minX + 50) * scale,\n");
        js.append("    y: (z - bounds.minZ + 50) * scale\n");
        js.append("  };\n");
        js.append("}\n\n");
        
        js.append("// Draw rails\n");
        js.append("ctx.lineWidth = 2;\n");
        
        // Group rails by network for coloring
        Map<Integer, List<RailBlock>> railsByNetwork = new HashMap<>();
        for (RailBlock rail : rails) {
            railsByNetwork.computeIfAbsent(rail.getNetworkId(), k -> new ArrayList<>()).add(rail);
        }
        
        int networkIndex = 0;
        for (Map.Entry<Integer, List<RailBlock>> entry : railsByNetwork.entrySet()) {
            String color = NETWORK_COLORS[networkIndex % NETWORK_COLORS.length];
            networkIndex++;
            
            js.append("ctx.strokeStyle = '").append(color).append("';\n");
            js.append("ctx.beginPath();\n");
            
            for (RailBlock rail : entry.getValue()) {
                js.append("const p").append(rail.hashCode()).append(" = worldToCanvas(")
                  .append(rail.getX()).append(", ").append(rail.getZ()).append(");\n");
                js.append("ctx.fillRect(p").append(rail.hashCode())
                  .append(".x-1, p").append(rail.hashCode()).append(".y-1, 2, 2);\n");
            }
            
            js.append("ctx.stroke();\n");
        }
        
        // Draw minecarts
        js.append("\n// Draw minecarts\n");
        for (MinecartData cart : minecarts) {
            js.append("const cart").append(cart.getId().hashCode()).append(" = worldToCanvas(")
              .append((int)cart.getX()).append(", ").append((int)cart.getZ()).append(");\n");
            js.append("ctx.fillStyle = '#FF1744';\n");
            js.append("ctx.beginPath();\n");
            js.append("ctx.arc(cart").append(cart.getId().hashCode()).append(".x, cart")
              .append(cart.getId().hashCode()).append(".y, 5, 0, Math.PI * 2);\n");
            js.append("ctx.fill();\n");
            js.append("ctx.strokeStyle = '#fff';\n");
            js.append("ctx.lineWidth = 2;\n");
            js.append("ctx.stroke();\n");
        }
        
        // Add pan and zoom functionality
        js.append("\n// Pan and zoom\n");
        js.append("let offsetX = 0, offsetY = 0, isDragging = false, startX, startY;\n");
        js.append("canvas.addEventListener('mousedown', e => { isDragging = true; startX = e.clientX - offsetX; startY = e.clientY - offsetY; });\n");
        js.append("canvas.addEventListener('mousemove', e => { if (isDragging) { offsetX = e.clientX - startX; offsetY = e.clientY - startY; } });\n");
        js.append("canvas.addEventListener('mouseup', () => isDragging = false);\n");
        
        return js.toString();
    }
    
    private String generateJSONData(List<RailBlock> rails, List<MinecartData> minecarts) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"rails\": [\n");
        
        for (int i = 0; i < rails.size(); i++) {
            RailBlock rail = rails.get(i);
            json.append("    {")
                .append("\"x\":").append(rail.getX())
                .append(",\"y\":").append(rail.getY())
                .append(",\"z\":").append(rail.getZ())
                .append(",\"type\":\"").append(rail.getType().name()).append("\"")
                .append(",\"network\":").append(rail.getNetworkId())
                .append(",\"placer\":\"").append(rail.getPlacer()).append("\"")
                .append("}");
            
            if (i < rails.size() - 1) json.append(",");
            json.append("\n");
        }
        
        json.append("  ],\n");
        json.append("  \"minecarts\": [\n");
        
        for (int i = 0; i < minecarts.size(); i++) {
            MinecartData cart = minecarts.get(i);
            json.append("    {")
                .append("\"id\":\"").append(cart.getId()).append("\"")
                .append(",\"x\":").append(cart.getX())
                .append(",\"y\":").append(cart.getY())
                .append(",\"z\":").append(cart.getZ())
                .append(",\"direction\":\"").append(cart.getDirection()).append("\"")
                .append(",\"speed\":").append(String.format("%.2f", cart.getSpeed()))
                .append(",\"occupied\":").append(cart.isOccupied())
                .append(",\"passenger\":").append(cart.getPassenger() != null ? "\"" + cart.getPassenger() + "\"" : "null")
                .append("}");
            
            if (i < minecarts.size() - 1) json.append(",");
            json.append("\n");
        }
        
        json.append("  ],\n");
        json.append("  \"timestamp\": ").append(System.currentTimeMillis()).append("\n");
        json.append("}\n");
        
        return json.toString();
    }
}