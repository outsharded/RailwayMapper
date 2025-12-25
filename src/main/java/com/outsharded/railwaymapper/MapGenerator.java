package com.outsharded.railwaymapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.bukkit.plugin.Plugin;

public class MapGenerator {

    private final RailwayMapperPlugin plugin;
    private final RailwayDatabase database;

    public MapGenerator(RailwayMapperPlugin plugin, RailwayDatabase database) {
        this.plugin = plugin;
        this.database = database;
    }

    public void generateMap(String worldName) throws IOException {
        plugin.getLogger().info("Generating map HTML for world: " + worldName);

        // Get Dynmap plugin for web directory
        Plugin dynmap = plugin.getServer().getPluginManager().getPlugin("dynmap");
        if (dynmap == null) {
            plugin.getLogger().severe("Dynmap not found! Cannot save web files.");
            return;
        }

        File webDir = new File(dynmap.getDataFolder(), "web/railwaymapper");
        if (!webDir.exists() && !webDir.mkdirs()) {
            plugin.getLogger().severe("Failed to create Dynmap web directory: " + webDir.getAbsolutePath());
            return;
        }

        // Generate static HTML with embedded world list (generated fresh each time)
        File mapFile = new File(webDir, "railmap.html");
        String html = generateHTMLWithWorldList();
        Files.writeString(mapFile.toPath(), html);
        plugin.getLogger().info("✓ HTML saved to: " + mapFile.getAbsolutePath());
        
        // Also generate the world-specific data JSON file in Dynmap folder so HTML can load it
        File dataFile = new File(webDir, worldName + "_data.json");
        String jsonData = generateWorldData(worldName);
        Files.writeString(dataFile.toPath(), jsonData);
        plugin.getLogger().info("✓ JSON saved to: " + dataFile.getAbsolutePath());

        plugin.getLogger().info("Map HTML and data generated for world: " + worldName);
    }

    private String generateWorldData(String worldName) {
        String railNetworks = database.getRailNetworks(worldName);
        java.util.List<RailwayDatabase.Station> stations = database.getStations(worldName);
        
        StringBuilder json = new StringBuilder();
        json.append("{\"world\":\"").append(worldName).append("\",\"railLines\": ");
        json.append(railNetworks);
        json.append(", \"stations\": [");
        
        for (int i = 0; i < stations.size(); i++) {
            RailwayDatabase.Station s = stations.get(i);
            if (i > 0) json.append(",");
            json.append(String.format("{\"x\":%d,\"y\":%d,\"z\":%d,\"name\":\"%s\"}", s.x, s.y, s.z, s.name));
        }
        
        json.append("]}");
        return json.toString();
    }

    private String generateHTMLWithWorldList() {
        // Get all available worlds from database
        java.util.List<String> worlds = database.getAllWorlds();
        
        // Build world options HTML
        StringBuilder worldOptions = new StringBuilder();
        for (String world : worlds) {
            worldOptions.append("        <option value='").append(world).append("'>").append(world).append("</option>\n");
        }

        return "<!DOCTYPE html>\n" +
           "<html>\n<head>\n" +
           "<title>Railway Map</title>\n" +
           "<meta charset='UTF-8'>\n" +
           "<style>\n" +
           "* { box-sizing: border-box; }\n" +
           "body { margin: 0; padding: 20px; font-family: 'Arial', sans-serif; background: #f5f5f5; color: #333; }\n" +
           ".header { text-align: center; margin-bottom: 20px; }\n" +
           ".header h1 { margin: 0; color: #E21836; font-size: 28px; font-weight: bold; }\n" +
           ".controls-bar { text-align: center; margin-bottom: 20px; }\n" +
           ".controls-bar select { padding: 10px 15px; font-size: 16px; border-radius: 5px; border: 1px solid #ccc; }\n" +
           ".map-container { display: flex; justify-content: center; margin-top: 20px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); }\n" +
           "canvas { border: 3px solid #333; background: #fff; cursor: move; display: block; }\n" +
           ".legend { margin-top: 20px; padding: 15px; background: #fff; border-radius: 5px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); }\n" +
           ".legend h3 { margin: 0 0 10px 0; color: #E21836; }\n" +
           ".legend-content { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }\n" +
           ".legend-item { font-size: 12px; display: flex; align-items: center; }\n" +
           ".legend-item-icon { width: 20px; height: 3px; margin-right: 8px; }\n" +
           ".legend-item-circle { width: 12px; height: 12px; border-radius: 50%; margin-right: 8px; }\n" +
           ".controls { margin-top: 10px; font-size: 12px; color: #666; }\n" +
           "</style>\n" +
           "</head>\n<body>\n" +
           "<div class='header'>\n" +
           "  <h1>🚆 Railway Map</h1>\n" +
           "</div>\n" +
           "<div class='controls-bar'>\n" +
           "  <label for='world-select'>Select World:</label>\n" +
           "  <select id='world-select' onchange='loadWorld(this.value)'>\n" +
           "    <option value=''>-- Select a world --</option>\n" +
           worldOptions.toString() +
           "  </select>\n" +
           "</div>\n" +
           "<div class='map-container'><canvas id='railmap' width='2000' height='2000'></canvas></div>\n" +
           "<div class='legend'>\n" +
           "  <h3>Map Guide</h3>\n" +
           "  <div class='legend-content'>\n" +
           "    <div class='legend-item'><div class='legend-item-icon' style='background: #E21836;'></div>Rail Networks</div>\n" +
           "    <div class='legend-item'><div class='legend-item-circle' style='background: #FFD700; border: 2px solid #333;'></div>Stations</div>\n" +
           "  </div>\n" +
           "  <div class='controls'>\n" +
           "    <strong>Controls:</strong> Drag to pan • Scroll to zoom • Hover over stations to see names\n" +
           "  </div>\n" +
           "</div>\n" +
           "<script>\n" +
           "const canvas = document.getElementById('railmap');\n" +
           "const ctx = canvas.getContext('2d');\n" +
           "const worldSelect = document.getElementById('world-select');\n" +
           "let scale = 1, offsetX = 0, offsetY = 0, isDragging = false, startX, startY;\n" +
           "let hoveredStation = null, mapData = null, currentWorld = null;\n" +
           "\n" +
           "function loadWorld(world) {\n" +
           "  if (!world) return;\n" +
           "  currentWorld = world;\n" +
           "  scale = 1; offsetX = 0; offsetY = 0; hoveredStation = null;\n" +
           "  const dataPath = '/plugins/dynmap/web/railwaymapper/' + world + '_data.json';\n" +
           "  fetch(dataPath)\n" +
           "    .then(res => {\n" +
           "      if (!res.ok) throw new Error('Failed to load ' + dataPath + ': ' + res.status);\n" +
           "      return res.json();\n" +
           "    })\n" +
           "    .then(data => { mapData = data; drawMap(); })\n" +
           "    .catch(err => {\n" +
           "      console.error('Error loading world data:', err);\n" +
           "      alert('Failed to load map data for ' + world + '. Check console for details.');\n" +
           "    });\n" +
           "}\n" +
           "\n" +
           "canvas.addEventListener('mousedown', e => { isDragging = true; startX = e.clientX - offsetX; startY = e.clientY - offsetY; });\n" +
           "canvas.addEventListener('mousemove', e => { if (isDragging) { offsetX = e.clientX - startX; offsetY = e.clientY - startY; drawMap(); } else { checkStationHover(e); } });\n" +
           "canvas.addEventListener('mouseup', () => isDragging = false);\n" +
           "canvas.addEventListener('mouseleave', () => { isDragging = false; hoveredStation = null; drawMap(); });\n" +
           "canvas.addEventListener('wheel', e => { e.preventDefault(); const factor = e.deltaY > 0 ? 0.85 : 1.15; scale *= factor; drawMap(); }, {passive: false});\n" +
           "\n" +
           "function worldToCanvas(x, z) { return { x: x * scale + offsetX, y: z * scale + offsetY }; }\n" +
           "\n" +
           "function checkStationHover(e) {\n" +
           "  if (!mapData) return;\n" +
           "  const rect = canvas.getBoundingClientRect();\n" +
           "  const mouseX = e.clientX - rect.left;\n" +
           "  const mouseY = e.clientY - rect.top;\n" +
           "  hoveredStation = null;\n" +
           "  for (let s of (mapData.stations || [])) {\n" +
           "    const pos = worldToCanvas(s.x, s.z);\n" +
           "    const dist = Math.sqrt((mouseX - pos.x) ** 2 + (mouseY - pos.y) ** 2);\n" +
           "    if (dist < 12) {\n" +
           "      hoveredStation = s;\n" +
           "      canvas.style.cursor = 'pointer';\n" +
           "      drawMap();\n" +
           "      return;\n" +
           "    }\n" +
           "  }\n" +
           "  canvas.style.cursor = 'move';\n" +
           "}\n" +
           "\n" +
           "function drawMap() {\n" +
           "  if (!mapData) return;\n" +
           "  ctx.clearRect(0, 0, canvas.width, canvas.height);\n" +
           "  ctx.fillStyle = '#fff';\n" +
           "  ctx.fillRect(0, 0, canvas.width, canvas.height);\n" +
           "  drawRailLines();\n" +
           "  drawStations();\n" +
           "  if (hoveredStation) drawStationTooltip();\n" +
           "}\n" +
           "\n" +
           "function drawRailLines() {\n" +
           "  if (!mapData.railLines) return;\n" +
           "  for (let line of mapData.railLines) {\n" +
           "    ctx.strokeStyle = line.color;\n" +
           "    ctx.lineWidth = 8;\n" +
           "    ctx.lineCap = 'round';\n" +
           "    ctx.lineJoin = 'round';\n" +
           "    if (line.vertices.length < 2) continue;\n" +
           "    ctx.beginPath();\n" +
           "    const start = worldToCanvas(line.vertices[0][0], line.vertices[0][2]);\n" +
           "    ctx.moveTo(start.x, start.y);\n" +
           "    for (let i = 1; i < line.vertices.length; i++) {\n" +
           "      const pos = worldToCanvas(line.vertices[i][0], line.vertices[i][2]);\n" +
           "      ctx.lineTo(pos.x, pos.y);\n" +
           "    }\n" +
           "    ctx.stroke();\n" +
           "  }\n" +
           "}\n" +
           "\n" +
           "function drawStations() {\n" +
           "  for (let s of (mapData.stations || [])) {\n" +
           "    const pos = worldToCanvas(s.x, s.z);\n" +
           "    ctx.fillStyle = '#FFD700';\n" +
           "    ctx.beginPath();\n" +
           "    ctx.arc(pos.x, pos.y, 10, 0, Math.PI * 2);\n" +
           "    ctx.fill();\n" +
           "    ctx.strokeStyle = '#333';\n" +
           "    ctx.lineWidth = 2;\n" +
           "    ctx.stroke();\n" +
           "  }\n" +
           "}\n" +
           "\n" +
           "function drawStationTooltip() {\n" +
           "  const pos = worldToCanvas(hoveredStation.x, hoveredStation.z);\n" +
           "  ctx.fillStyle = 'rgba(0, 0, 0, 0.8)';\n" +
           "  ctx.fillRect(pos.x - 60, pos.y - 25, 120, 24);\n" +
           "  ctx.fillStyle = '#FFD700';\n" +
           "  ctx.font = 'bold 12px Arial';\n" +
           "  ctx.textAlign = 'center';\n" +
           "  ctx.fillText(hoveredStation.name, pos.x, pos.y - 7);\n" +
           "}\n" +
           "</script>\n" +
           "</body>\n</html>";
    }


}
