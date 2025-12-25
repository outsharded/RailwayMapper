# RailwayMapper - Copilot Instructions

## Project Overview
RailwayMapper is a **Minecraft Paper 1.21.8 plugin** that scans worlds for minecart railways, filters by player placement via CoreProtect integration, and generates dynamic HTML maps showing live minecart positions with color-coded networks and custom station markers. Java 21 + Maven stack.

## Architecture

### Core Components (Package: `com.outsharded.railwaymapper`)

1. **RailwayMapperPlugin** - Main entry point
   - Coordinates plugin lifecycle (onEnable/onDisable)
   - Initializes scanner, tracker, database, map generator, CoreProtect integration
   - Handles `/railmap` command routing (scan, view, stats, station, reload)
   - Manages async tasks (minecart tracking + dynamic map updates)
   - Dynamic map updates are triggered every tracking interval if `map.auto-update: true`

2. **RailwayScanner** - World scanning engine
   - Scans all generated chunks in a world for rail blocks (uses `world.getLoadedChunks()`)
   - Detects 4 rail types: RAIL, POWERED_RAIL, DETECTOR_RAIL, ACTIVATOR_RAIL
   - Filters blocks via CoreProtect if enabled (player-placed only, age filters, ignore lists)
   - Groups connected rails into network IDs via BFS adjacency detection
   - Assigns cycled colors to each network from `display.network-colors` config list
   - Logs progress every 100 chunks processed

3. **CoreProtectIntegration** - Block history filtering (FULLY IMPLEMENTED)
   - Queries CoreProtect API to identify block placers
   - Supports filtering by: player-placed vs natural, age range, ignore lists
   - Gracefully degrades if CoreProtect unavailable (scans all rails)
   - Configuration in `config.yml` under `coreprotect.*`

4. **RailwayDatabase** - SQLite data layer
   - Four main tables: `rail_blocks`, `networks`, `minecart_positions`, `stations`
   - Async operations for position updates
   - Stores placer info, network associations, velocity data, and custom stations
   - Networks table now includes `color` field for network-specific coloring

5. **MinecartTracker** - Real-time position tracking
   - Updates minecart positions every tick (configurable interval)
   - Tracks velocity, occupancy, passenger names
   - Uses `ConcurrentHashMap` for thread-safe entity tracking

6. **MapGenerator** - HTML/Canvas map output with colors and stations
   - Requires Dynmap plugin for web directory
   - Outputs `railmap.html` (visual map with zoom/pan controls)
   - Outputs `raildata.json` (rails with network colors, minecarts, stations)
   - Calculates dynamic bounds from rail extents
   - Renders stations as labeled yellow pins on the map
   - Called automatically every tracking interval if `map.auto-update: true`

7. **RailBlock** - Data model
   - Immutable representation of a single rail block
   - Properties: x, y, z, type (Material), world, placer, networkId

### Data Flow
```
World Chunks → RailwayScanner → CoreProtect Filter → Filtered RailBlocks
    ↓
RailwayDatabase (rail_blocks table)
    ↓
RailwayScanner (BFS grouping) → Database (networks table with assigned colors)

Live Minecarts → MinecartTracker (every N ticks) → Database (minecart_positions)
    ↓
[Auto-Update Trigger] → MapGenerator (queries rail colors + stations) → HTML + JSON output
```

## Build & Development

### Build Command
```bash
mvn clean package
```
Produces JAR in `target/RailwayMapper-1.0.0.jar` with shaded dependencies (sqlite-jdbc embedded).

### Key Dependencies
- `paper-api` 1.21.3 (provided) - Bukkit plugin framework
- `sqlite-jdbc` 3.44.1.0 - Embedded database
- `coreprotect` 22.2 (provided, optional) - Block history tracking

### Key Files
- [pom.xml](pom.xml) - Maven config, shade plugin for fat JAR
- [plugin.yml](src/main/resources/plugin.yml) - Bukkit metadata, commands, permissions
- [config.yml](src/main/resources/config.yml) - Runtime configuration (scanning, tracking intervals, map settings, CoreProtect filters, network colors)

## Common Workflows

### Modifying Scanning Logic
1. Core scan loop: [RailwayScanner.scanWorld()](src/main/java/com/outsharded/railwaymapper/RailwayScanner.java#L30)
2. Rail type detection: `RAIL_TYPES` EnumSet at class level
3. CoreProtect filtering: `coreProtect.getBlockPlacer()` and `coreProtect.matchesFilters()` calls
4. Network grouping: `findNetworks()` uses BFS to find adjacent rails and assigns colors
5. Database save: `database.saveRailBlocks()` and `database.saveNetwork()`

### Extending Minecart Tracking
- Update entity properties: [MinecartTracker.updateMinecartPositions()](src/main/java/com/outsharded/railwaymapper/MinecartTracker.java#L27)
- Modify `MinecartData` inner class to store additional fields
- Add database columns to [minecart_positions table](src/main/java/com/outsharded/railwaymapper/RailwayDatabase.java#L61)

### Adding Custom Stations
- Command: `/railmap station add <name>` adds station at player location
- Command: `/railmap station remove` removes station at player location
- Command: `/railmap station list` shows all stations in world
- Database methods: `addStation()`, `removeStation()`, `getStations()`
- Stations are rendered as yellow labeled pins on the map

### Enabling/Disabling Dynamic Map Updates
- Toggle in [config.yml](src/main/resources/config.yml#L32): `map.auto-update: true/false`
- Update logic in [RailwayMapperPlugin.startMinecartTracking()](src/main/java/com/outsharded/railwaymapper/RailwayMapperPlugin.java#L150) - runs map generation every tracking interval

### Network Coloring System
- Colors defined in [config.yml](src/main/resources/config.yml#L85): `display.network-colors` list
- Colors cycle based on network ID (networkId % color_list_size)
- Each network's color stored in `networks.color` field
- [MapGenerator](src/main/java/com/outsharded/railwaymapper/MapGenerator.java#L80) queries network color via `database.getNetworkColor(networkId)`

### CoreProtect Configuration
All filters live in [config.yml](src/main/resources/config.yml#L3):
```yaml
coreprotect:
  enabled: true
  player-placed-only: true        # Skip natural/admin blocks
  min-age-days: 0                 # Minimum block age
  max-age-days: 0                 # Maximum block age (0=no limit)
  ignore-players:
    - "WorldEdit"
    - "#worldedit"
```

## Project Conventions

- **Async Operations**: Database writes and scans are async; use `BukkitRunnable.runTaskAsynchronously()` for long ops
- **Error Logging**: All exceptions logged via `plugin.getLogger().log(Level.SEVERE, msg, exception)`
- **Routine Logging**: Use `Level.FINE` for routine operations (auto-update map generation) to avoid console spam
- **Configuration**: All runtime tunables in [config.yml](src/main/resources/config.yml) - no hardcoded values in code
- **Package Structure**: Single package `com.outsharded.railwaymapper` - no sub-packages (small plugin)
- **Database IDs**: rail_blocks use auto-increment; networks/minecarts use explicit IDs; stations auto-increment
- **CoreProtect Graceful Degradation**: Plugin works without CoreProtect; if unavailable, scans all rails
- **Map Interactivity**: HTML canvas supports pan (drag) and zoom (scroll wheel)

## Permissions Model
- `railwaymapper.use` - Basic commands (default: all players)
- `railwaymapper.scan` - Trigger scans and manage stations (default: ops only)
- `railwaymapper.reload` - Reload config (default: ops only)

## Performance Notes
- Scanning all chunks in a world can be memory/CPU intensive for large servers
- Configure `tracking.update-interval` (ticks) to balance responsiveness vs performance
- Map auto-updates tied to tracking interval; disable with `map.auto-update: false` if problematic
- CoreProtect queries are per-block; large rail networks will see some overhead
- Consider reducing `map.canvas-width/height` if HTML generation is slow
- Each network gets a unique color from the cycled color list - no color conflicts
- Routine map updates logged at FINE level to keep console clean

## Testing Hints
- Plugin runs in Minecraft server context - test by running Paper server + plugin JAR
- SQLite DB stored at `plugins/RailwayMapper/railways.db`
- Minecart tracking relies on tick events - must be in live server environment
- Dynmap integration optional; map generation fails gracefully if missing
- CoreProtect integration optional; plugin works without it (scans all blocks)
- Test map by opening `railmap.html` in browser - supports drag to pan and scroll to zoom
- Test stations with `/railmap station add TestStation` then check map for yellow labeled pins


