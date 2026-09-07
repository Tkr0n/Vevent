# VerkkuCraft Launcher - Design Spec

## Overview

A Windows-only desktop launcher (.exe) for the VerkkuCraft Minecraft server. Friends download a single .exe, and the launcher handles everything: Java installation, server files, plugins, VPN (Tailscale), and Minecraft launch. Zero manual configuration required.

**Server:** Paper 1.20.4 on Raspberry Pi  
**Plugins:** vEventDrops, GravesX, spark  
**Network:** Cloudflare tunnel (TCP) + Tailscale mesh VPN (UDP for voice chat)  
**Branding:** VerkkuCraft  

---

## Architecture

### Stack

- **Framework:** .NET 8 + WPF
- **UI Library:** WPF-UI (Fluent Design style)
- **HTTP:** `System.Net.Http.HttpClient`
- **Archive:** `SharpCompress` (zip/tar.gz extraction)
- **JSON:** `System.Text.Json` or `Newtonsoft.Json`
- **Packaging:** .NET self-contained publish (single .exe, no .NET install required)

### Project Structure

```
VerkkuCraftLauncher/
├── VerkkuCraftLauncher.sln
├── src/
│   └── VerkkuCraftLauncher/
│       ├── App.xaml                          # App entry, theme setup
│       ├── MainWindow.xaml                   # Main window shell
│       ├── ViewModels/
│       │   ├── MainViewModel.cs              # Navigation + state
│       │   └── ProgressViewModel.cs          # Download progress
│       ├── Views/
│       │   ├── WelcomeView.xaml              # First-run / loading
│       │   ├── InstallView.xaml              # Installation progress
│       │   ├── PlayView.xaml                 # "Ready to play" screen
│       │   └── SettingsView.xaml             # Configuration (future)
│       ├── Services/
│       │   ├── JavaManager.cs                # Detect + install Java 17
│       │   ├── ServerManager.cs              # Download paper.jar + configs
│       │   ├── PluginManager.cs              # Download + update plugins
│       │   ├── VpnManager.cs                 # Tailscale install + connect
│       │   ├── UpdateManager.cs              # Check manifest + update
│       │   ├── MinecraftLauncher.cs          # Detect + launch MC
│       │   └── ManifestService.cs            # Fetch remote manifest
│       ├── Models/
│       │   ├── LauncherManifest.cs           # Remote manifest model
│       │   ├── ServerConfig.cs               # Server config model
│       │   └── LauncherState.cs              # Current state enum
│       ├── Helpers/
│       │   ├── HttpDownloader.cs             # Download with progress
│       │   └── FileExtractor.cs              # Unzip/untar
│       └── Assets/
│           ├── logo.png
│           └── styles.xaml
├── publish.bat                               # Build script
└── README.md
```

---

## User Experience Flow

### Flow 1: First Launch (nothing installed)

```
┌──────────────────────────────────┐
│          VerkkuCraft             │
│         [Logo 200px]             │
│                                  │
│    Configurando tu juego...      │
│                                  │
│  ✅ Java 17 detectado            │
│  ✅ Paper 1.20.4 descargado     │
│  ✅ Plugins instalados           │
│  ⏳ Conectando VPN...           │
│  ⬜ Preparando Minecraft        │
│                                  │
│  [████████████░░░░░] 65%         │
│  Descargando Tailscale...        │
└──────────────────────────────────┘
```

### Flow 2: Subsequent Launch (everything installed)

```
┌──────────────────────────────────┐
│          VerkkuCraft             │
│         [Logo 200px]             │
│                                  │
│      ¡Todo listo para jugar!     │
│                                  │
│  Server: ● Online               │
│  Jugadores: 3/20                │
│  Tu IP VPN: 100.x.x.x          │
│                                  │
│       [ 🔵 JUGAR ]              │
│                                  │
│     ⚙️ Configuración            │
└──────────────────────────────────┘
```

### Critical Rule: Always Update

The launcher **always** checks for updates on startup. If updates exist, they download automatically before the user can click "JUGAR". There is no "skip update" option.

---

## Components

### 1. JavaManager

**Responsibility:** Detect Java 17 installation, install if missing.

- Check registry and common paths for Java 17+
- If not found, download Adoptium JDK 17 `.msi` from official mirror
- Install silently: `msiexec /i adoptium.msi ADDLOCAL=FeatureMain,FeatureJarFileRunWith,FeatureJavaHome`
- Verify installation by running `java -version`

### 2. ServerManager

**Responsibility:** Download Paper server JAR and configuration files.

- Download `paper.jar` from PaperMC API
- Download config files from GitHub Releases: `server.properties`, `bukkit.yml`, `spigot.yml`
- Store in `%LOCALAPPDATA%\VerkkuCraft\server\`
- On update: compare versions in manifest, re-download if newer

### 3. PluginManager

**Responsibility:** Download and update server plugins.

- Read plugin list from manifest
- Download each `.jar` from GitHub Releases
- Place in `server/plugins/`
- On update: replace old `.jar` with new version

### 4. VpnManager

**Responsibility:** Install and configure Tailscale for UDP voice chat.

**Flow:**
1. Check if Tailscale is installed (`tailscale version`)
2. If not, download Tailscale `.msi` from official site
3. Install silently: `msiexec /i tailscale.msi /quiet`
4. Fetch auth key from remote URL (manifest → `vpn.tailscale_authkey_url`)
5. Login: `tailscale login --authkey=<key>`
6. Verify connection: `tailscale status`
7. Get local Tailscale IP: `tailscale ip -4`

**Auth Key Rotation:**
- Auth key is fetched from a remote URL each launch
- Rotate keys periodically in Tailscale admin console
- No need to redistribute the launcher

### 5. UpdateManager

**Responsibility:** Check and apply updates on every launch.

**Flow:**
1. Download `manifest.json` from GitHub Releases API
2. Compare each component version with local manifest cache
3. If any version differs → download new files
4. Replace old files
5. Update local manifest cache

**Manifest Location (remote):**
- GitHub Releases: `https://api.github.com/repos/{owner}/{repo}/releases/latest`
- Asset: `manifest.json`

### 6. MinecraftLauncher

**Responsibility:** Detect and launch Minecraft with server pre-configured.

**Priority order:**
1. Check for official Minecraft launcher (`MinecraftLauncher.exe` in common paths)
2. If found → open it with server argument
3. If not found → download HMCL portable from GitHub
4. If user has HMCL or Prism → use their existing installation
5. Configure server: `100.x.x.x:25565` (Tailscale IP)

**HMCL Configuration:**
- Store config in `%LOCALAPPDATA%\VerkkuCraft\minecraft\hmcl\`
- Pre-set Minecraft version: 1.20.4
- Pre-set server: VerkkuCraft at Tailscale IP

---

## Manifest Format

```json
{
  "manifest_version": 1,
  "launcher_version": "1.0.0",
  "server": {
    "name": "VerkkuCraft",
    "minecraft_version": "1.20.4",
    "paper_build": "218",
    "paper_url": "https://api.papermc.io/v2/projects/paper/versions/1.20.4/builds/218/downloads/paper-1.20.4-218.jar"
  },
  "plugins": [
    {
      "name": "vEventDrops",
      "version": "1.0.0",
      "filename": "vEventDrops-1.0.0.jar",
      "download_url": "https://github.com/{owner}/{repo}/releases/download/v1.0.0/vEventDrops-1.0.0.jar"
    },
    {
      "name": "GravesX",
      "version": "2026.4.9.1",
      "filename": "GravesX-2026.4.9.1.jar",
      "download_url": "https://github.com/{owner}/{repo}/releases/download/v1.0.0/GravesX-2026.4.9.1.jar"
    },
    {
      "name": "spark",
      "version": "2.8.1",
      "filename": "spark-2.8.1.jar",
      "download_url": "https://github.com/{owner}/{repo}/releases/download/v1.0.0/spark-2.8.1.jar"
    }
  ],
  "config_files": [
    {
      "name": "server.properties",
      "download_url": "https://github.com/{owner}/{repo}/releases/download/v1.0.0/server.properties"
    },
    {
      "name": "bukkit.yml",
      "download_url": "https://github.com/{owner}/{repo}/releases/download/v1.0.0/bukkit.yml"
    },
    {
      "name": "spigot.yml",
      "download_url": "https://github.com/{owner}/{repo}/releases/download/v1.0.0/spigot.yml"
    }
  ],
  "vpn": {
    "tailscale_authkey_url": "https://raw.githubusercontent.com/{owner}/{repo}/main/auth/vpn-key.txt",
    "network_name": "VerkkuCraft"
  },
  "minecraft": {
    "hmcl_url": "https://github.com/huanghongxun/HMCL/releases/download/v3.5.8/HMCL-3.5.8.exe",
    "version": "1.20.4"
  }
}
```

---

## File System Layout (User Machine)

```
%LOCALAPPDATA%\VerkkuCraft\
├── launcher/
│   ├── VerkkuCraftLauncher.exe          # The launcher itself
│   └── manifest.local.json              # Cached manifest
├── java/
│   └── jdk-17.x.x\                     # Installed JDK (if auto-installed)
├── tailscale/
│   └── tailscale.exe                    # Tailscale client
├── server/
│   ├── paper.jar
│   ├── server.properties
│   ├── bukkit.yml
│   ├── spigot.yml
│   ├── plugins/
│   │   ├── vEventDrops-1.0.0.jar
│   │   ├── GravesX-2026.4.9.1.jar
│   │   └── spark-2.8.1.jar
│   ├── libraries/                       # Paper libraries
│   └── world/                           # Server world (created on first run)
└── minecraft/
    ├── hmcl.exe                         # HMCL portable (if needed)
    └── hmcl/                            # HMCL data
```

---

## Branding

- **Name:** VerkkuCraft
- **Colors:** Dark theme (dark blue/purple background, cyan/teal accent)
- **Logo:** Custom logo centered on welcome screen
- **Font:** Segoe UI (Windows native) or Inter
- **Window:** 600x450px, no resize, rounded corners (via WPF-UI)

---

## Security Considerations

1. **Auth Key:** Never hardcode. Always fetch from remote URL.
2. **HTTPS:** All downloads over HTTPS only.
3. **Checksums:** Manifest includes SHA256 hashes for each file. Verify after download.
4. **Antivirus:** .NET self-contained .exe may trigger false positives. Code-sign the .exe with a certificate.
5. **No secrets in code:** All config lives in remote manifest or environment variables.

---

## Build & Distribution

### Build Command

```bash
dotnet publish -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true
```

This produces a single `VerkkuCraftLauncher.exe` (~60-80MB) that runs on any Windows 10/11 without .NET installed.

### Distribution

1. Build the .exe
2. Upload to GitHub Releases
3. Share the direct download link with friends
4. Friends download the single .exe and run it

---

## Future Enhancements (Not in v1)

- Server status directly in launcher (query Minecraft server)
- Custom player skin preview
- Voice chat integration status
- Multi-server support
- Automatic backup system
- In-launcher chat

---

## Open Questions

1. **GitHub repo for releases:** Need to create `{owner}/VerkkuCraft-releases` repo
2. **Tailscale network:** Need to create Tailscale network and generate auth key
3. **Server config files:** Need to export current `server.properties`, `bukkit.yml`, `spigot.yml` from TestServer
4. **Plugin JARs:** Need to compile vEventDrops and package with other plugins for GitHub Releases
