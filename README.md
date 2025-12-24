# RailwayMapper Plugin for Minecraft Paper 1.21.8

A comprehensive plugin that maps all minecart railways on your server, filters them by player placement using CoreProtect, and tracks live minecart positions.

## Features

### 🛤️ Railway Mapping
- Scans your world for all rail types (normal, powered, detector, activator)
- Groups connected rails into railway networks
- Identifies stations and junctions
- Stores data efficiently in SQLite database

### 👥 CoreProtect Integration
- Filters rails by who placed them
- Only shows player-built railways (excludes naturally generated or admin-placed)
- Optional time-based filtering
- Ignores specific players (useful for WorldEdit operations)

### 🚂 Live Minecart Tracking
- Real-time position tracking of all minecarts
- Shows direction and speed of travel
- Identifies occupied vs empty minecarts
- Displays passenger names

### 🗺️ Interactive Map Generation
- Generates beautiful HTML/Canvas maps
- Color-coded railway networks
- Live minecart positions
- Pan and zoom functionality
- JSON export for custom integrations

## Requirements

- Minecraft Server: Paper 1.21.8 or newer
- Java: 21 or newer
- CoreProtect: 22.x or newer (optional but recommended)

## Installation

1. **Download** the plugin JAR file
2. **Place** it in your server's `plugins` folder
3. **Install CoreProtect** (optional) for player-based filtering
4. **Restart** your server
5. **Configure** the plugin (see Configuration section)

## Building from Source

### Prerequisites
- JDK 21 or newer
- Maven 3.6 or newer

### Project Structure
```
RailwayMapper/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/yourname/railwaymapper/
│       │       ├── RailwayMapperPlugin.java
│       │       ├── RailwayScanner.java
│       │       ├── CoreProtectIntegration.java
│       │       ├── MinecartTracker.java
│       │       ├── RailwayDatabase.java
│       │       ├── MapGenerator.java
│       │       └── RailBlock.java
│       └── resources/
│           ├── plugin.yml
│           └── config.yml
├── pom.xml
└── README.md
```

### pom.xml
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.yourname</groupId>
    <artifactId>RailwayMapper</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <name>RailwayMapper</name>

    <properties>
        <java.version>21</java.version>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.11.0</version>
                <configuration>
                    <source>21</source>
                    <target>21</target>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.5.0</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals>
                            <goal>shade</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
        <resources>
            <resource>
                <directory>src/main/resources</directory>
                <filtering>true</filtering>
            </resource>
        </resources>
    </build>

    <repositories>
        <repository>
            <id>papermc-repo</id>
            <url>https://repo.papermc.io/repository/maven-public/</url>
        </repository>
        <repository>
            <id>playpro-repo</id>
            <url>https://maven.playpro.com</url>
        </repository>
    </repositories>

    <dependencies>
        <dependency>
            <groupId>io.papermc.paper</groupId>
            <artifactId>paper-api</artifactId>
            <version>1.21.3-R0.1-SNAPSHOT</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>net.coreprotect</groupId>
            <artifactId>coreprotect</artifactId>
            <version>22.2</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.xerial</groupId>
            <artifactId>sqlite-jdbc</artifactId>
            <version>3.44.1.0</version>
        </dependency>
    </dependencies>
</project>
```

### Build Commands
```bash
# Clone or create your project directory
mkdir RailwayMapper
cd RailwayMapper

# Place all source files in appropriate directories
# (Use the file structure shown above)

# Build the plugin
mvn clean package

# The plugin JAR will be in: target/RailwayMapper-1.0.0.jar
```

## Configuration

Edit `plugins/RailwayMapper/config.yml`:

```yaml
# Enable/disable CoreProtect integration
coreprotect:
  enabled: true
  player-placed-only: true

# Minecart tracking settings
tracking:
  enabled: true
  update-interval: 20  # ticks (20 = 1 second)

# Map generation
map:
  url: "http://yourserver.com:8080/railmap.html"
  canvas-width: 2000
  canvas-height: 2000
```

## Usage

### Commands

#### `/railmap` - Show help
Displays all available commands.

#### `/railmap scan [world]` - Scan for railways
Scans the specified world (or current world) for all railway blocks.

**Example:**
```
/railmap scan world
/railmap scan world_nether
```

**Permissions:** `railwaymapper.scan`

#### `/railmap view` - View the map
Displays the URL to view the interactive railway map.

**Permissions:** `railwaymapper.use`

#### `/railmap stats` - Show statistics
Displays statistics about railways and minecarts.

**Output:**
```
=== Railway Statistics ===
Total rail blocks: 1,247
Railway networks: 5
Active minecarts: 3
Unique builders: 12
```

**Permissions:** `railwaymapper.use`

#### `/railmap reload` - Reload configuration
Reloads the plugin configuration from config.yml.

**Permissions:** `railwaymapper.reload`

### Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `railwaymapper.*` | All permissions | op |
| `railwaymapper.use` | Basic commands | everyone |
| `railwaymapper.scan` | Scan worlds | op |
| `railwaymapper.reload` | Reload config | op |

## How It Works

### 1. Railway Scanning
The plugin scans all loaded chunks for rail blocks:
- Normal rails
- Powered rails
- Detector rails
- Activator rails

### 2. CoreProtect Filtering
If CoreProtect is installed:
- Queries the CoreProtect database for each rail block
- Checks who placed it
- Filters out naturally generated rails (marked as `#natural`)
- Filters out rails placed by ignored players

### 3. Network Detection
Uses breadth-first search (BFS) to find connected railways:
- Groups adjacent rails into networks
- Handles slopes and curves
- Assigns unique IDs to each network
- Identifies the main builder (player who placed the most rails)

### 4. Minecart Tracking
Every tick (configurable):
- Scans all loaded entities for minecarts
- Records position, velocity, direction
- Checks for passengers
- Updates database

### 5. Map Generation
Creates an interactive HTML5 canvas map:
- Calculates world bounds
- Scales railways to fit canvas
- Color-codes networks
- Plots minecart positions
- Adds pan/zoom controls

## Viewing the Map

### Option 1: Local File
Open `plugins/RailwayMapper/web/railmap.html` in your browser.

### Option 2: Web Server
Serve the map files with any web server:

**Using Python:**
```bash
cd plugins/RailwayMapper/web
python3 -m http.server 8080
```

Then visit: `http://localhost:8080/railmap.html`

**Using Node.js:**
```bash
cd plugins/RailwayMapper/web
npx http-server -p 8080
```

### Option 3: Integrate with Dynmap/BlueMap
Copy `railmap.html` to your Dynmap/BlueMap web directory and link to it.

## Performance Considerations

### Scanning
- Runs asynchronously to avoid lag
- Only scans loaded chunks by default
- Configurable chunks-per-tick for balance

### Database
- Uses SQLite for efficient storage
- Indexes on frequently queried columns
- Batch inserts for large datasets

### Minecart Tracking
- Lightweight entity iteration
- Configurable update interval
- No impact when no minecarts are active

## Troubleshooting

### "CoreProtect not found" warning
- Install CoreProtect if you want player-based filtering
- Or disable it in config: `coreprotect.enabled: false`

### Map shows no railways
- Run `/railmap scan` first
- Check if CoreProtect filtering is too strict
- Verify rails were placed by players (not generated)

### Map is blank
- Check console for errors
- Ensure scan completed successfully
- Verify `web/railmap.html` exists

### Minecarts not showing
- Enable tracking: `tracking.enabled: true`
- Minecarts must be on loaded chunks
- Check update interval isn't too high

## Advanced Features

### JSON Data Export
The plugin exports JSON data to `web/raildata.json`:

```json
{
  "rails": [
    {"x": 100, "y": 64, "z": 200, "type": "RAIL", "network": 1, "placer": "Steve"}
  ],
  "minecarts": [
    {"id": "uuid", "x": 102, "y": 64, "z": 201, "direction": "north", "speed": 0.4}
  ],
  "timestamp": 1234567890
}
```

Use this for custom integrations or external maps.

### Custom Queries
Access the database directly:
```
plugins/RailwayMapper/railways.db
```

**Example queries:**
```sql
-- Find longest railway network
SELECT network_id, COUNT(*) as length 
FROM rail_blocks 
GROUP BY network_id 
ORDER BY length DESC 
LIMIT 1;

-- Top railway builders
SELECT placer, COUNT(*) as rails 
FROM rail_blocks 
GROUP BY placer 
ORDER BY rails DESC;
```

## Future Enhancements

Potential features for future versions:
- Web-based admin panel
- Real-time WebSocket updates for live minecart positions
- Railway route naming and labeling
- Performance metrics (busiest routes)
- Integration with economy plugins (toll systems)
- Automated cart dispatching

## Support

For issues, questions, or suggestions:
- GitHub: Create an issue
- Discord: Join our community server
- Forum: Post in the support section

## License

This plugin is provided as-is under the MIT License.

## Credits

- **Author:** YourName
- **CoreProtect:** Intelli
- **Paper:** PaperMC Team