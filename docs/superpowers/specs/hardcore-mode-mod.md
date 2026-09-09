# Hardcore Mode Mod - VerkkuCraft

## Overview
A hardcore mode system for the VerkkuCraft Minecraft server where each player gets 3 lives. Losing all 3 lives triggers a full world regeneration with a new random seed. Lives are displayed as 3 large hearts on the client HUD.

## Architecture

### Server Setup
- **Server**: Fabric 26.2 (Docker: `itzg/minecraft-server:java21`)
- **Mods**: Fabric mods run server-side, client mods run on player clients
- **Player Revive**: Already installed, handles KO/revival mechanics

### Components
1. **Server-side Fabric Mod** (`verkku-hardcore`) - Lives management, persistence, world regeneration
2. **Client-side Fabric Mod** (`verkku-hardcore-hud`) - Hearts display on HUD
3. **Player Revive** (existing) - KO timeout and revival between players

## Server-Side Mod: verkku-hardcore

### Project Structure
```
verkku-hardcore/
├── build.gradle
├── gradle.properties
├── settings.gradle
├── src/main/
│   ├── fabric.mod.json
│   ├── java/com/verkku/hardcore/
│   │   ├── HardcoreMod.java              # Entry point, event registration
│   │   ├── LifeManager.java              # Lives per player management
│   │   ├── WorldRegenManager.java        # World regeneration logic
│   │   ├── KOHandler.java                # KO state handling
│   │   ├── PlayerLifeConfig.java         # YAML persistence
│   │   ├── NetworkHandler.java           # Client-server communication
│   │   └── commands/
│   │       └── HardcoreCommand.java      # /hardcore commands
│   └── resources/
│       ├── fabric.mod.json
│       ├── hardcore.mod.json
│       └── hardcore.yaml                 # Default config
```

### Life Management
- Each player starts with 3 lives (configurable via `max_lives`)
- Lives stored in `player-lives.yml` (persists across server restarts)
- UUID-based tracking (survives name changes)

### KO Flow
1. Player dies → Player Revive activates KO (45s timeout)
2. Broadcast: `"{player} está KO - revívelo"` to all players
3. If revived by another player → No life lost
4. If not revived in 45s → Dies definitively → Life decremented
5. If had 1 life → World regeneration triggered

### World Regeneration
- When ANY player loses all 3 lives
- Generate new random seed
- Delete old world files
- Create new world with fresh seed
- Teleport ALL players to new world spawn
- All inventories, advancements, progress lost
- Player lives reset to 3

### Commands
- `/hardcore status` - Show lives of all players
- `/hardcore lives [player]` - Show lives of specific player
- `/hardcore admin reset [player]` - Reset lives (all players or specific player, op only)

### Configuration (`hardcore.yaml`)
```yaml
hardcore:
  max_lives: 3
  ko_timeout_seconds: 45
  regen_world_on_last_life: true
  broadcast_ko_message: true
  ko_message: "{player} está KO - revívelo"
```

### Persistence (`player-lives.yml`)
```yaml
players:
  "uuid-jugador-1":
    name: "PlayerName"
    lives: 3
  "uuid-jugador-2":
    name: "PlayerName2"
    lives: 2
```

### Networking
- Custom channel: `verkku:hardcore`
- Packet types:
  - `lives_sync` - Server→Client: Sync all players' lives
  - `ko_state` - Server→Client: Player KO state (for HUD blink effect)
  - `world_regen` - Server→Client: Trigger client-side effects

## Client-Side Mod: verkku-hardcore-hud

### Project Structure
```
verkku-hardcore-hud/
├── build.gradle
├── gradle.properties
├── settings.gradle
├── src/main/
│   ├── fabric.mod.json
│   ├── java/com/verkku/hardcore/client/
│   │   ├── HardcoreHudMod.java           # Entry point
│   │   ├── HeartHudRenderer.java         # Hearts rendering
│   │   └── NetworkHandler.java           # Receives server packets
│   └── resources/
│       ├── fabric.mod.json
│       └── assets/verkku-hardcore-hud/
│           └── textures/
│               ├── heart_full.png        # Full heart texture
│               ├── heart_half.png        # Half heart texture
│               ├── heart_empty.png       # Empty heart texture
│               └── heart_ko.png          # KO/blinking heart texture
```

### HUD Rendering
- **Position**: Top-left corner, below player name
- **Size**: 3 large hearts (bigger than vanilla)
- **Colors**:
  - Full life: Red heart
  - Last life: Red heart with warning effect
  - KO state: Blinking/pulsing heart
  - Empty: Gray heart (for visual reference)

### Networking
- Listens on `verkku:hardcore` channel
- Receives `lives_sync` packets → Updates local lives state
- Receives `ko_state` packets → Triggers blink effect

## Integration Points

### With Player Revive
- Player Revive handles the KO timeout mechanics
- HardcoreMod listens for final death events (after KO timeout)
- When player dies definitively → HardcoreMod decrements life
- Integration via Fabric events (no direct mod dependency)

### With Server
- Mod registers as server entrypoint in `fabric.mod.json`
- No changes needed to existing server configuration
- Mod loads automatically on server startup

## Build Configuration

### Server Mod (`build.gradle`)
```groovy
plugins {
    id 'fabric-loom' version '1.7-SNAPSHOT'
}
dependencies {
    minecraft "com.mojang:minecraft:1.21.1"
    mappings "net.fabricmc:yarn:1.21.1+build.3:v2"
    modImplementation "net.fabricmc:fabric-loader:0.16.0"
    modImplementation "net.fabricmc.fabric-api:fabric-api:0.100.1+1.21.1"
    implementation "org.yaml:snakeyaml:2.2"
}
```

### Client Mod (`build.gradle`)
```groovy
plugins {
    id 'fabric-loom' version '1.7-SNAPSHOT'
}
dependencies {
    minecraft "com.mojang:minecraft:1.21.1"
    mappings "net.fabricmc:yarn:1.21.1+build.3:v2"
    modImplementation "net.fabricmc:fabric-loader:0.16.0"
    modImplementation "net.fabricmc.fabric-api:fabric-api:0.100.1+1.21.1"
}
```

## Success Criteria
1. Players start with 3 lives shown as hearts on HUD
2. KO timeout is 45 seconds with broadcast message
3. Reviving a KO'd player costs no lives
4. Losing all 3 lives regenerates the entire world
5. Lives persist across server restarts
6. All players lose progress on world regeneration
7. Admin can reset lives with `/hardcore admin reset [player]`
