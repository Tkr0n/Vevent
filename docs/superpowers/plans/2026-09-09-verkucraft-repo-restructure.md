# VerkkuCraft Repository Restructure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restructure the VEventDrops repository into a clean VerkkuCraft project with separate directories for server-plugin, client-mods (with custom/third-party separation), launcher, and server config. Update pipeline to auto-generate manifest from mods folders.

**Architecture:** Move files to type-based directories, update all build paths, create `.gitattributes` for line endings, update `.gitignore` for new structure, and rewrite `release.yml` to scan mods folders and auto-generate manifest entries.

**Tech Stack:** Git, GitHub Actions, Python (for manifest generation script), Gradle, .NET/WPF

---

## File Structure Changes

### Files to Create
- `server-plugin/build.gradle` (moved from root)
- `server-plugin/settings.gradle` (new, references vEventDrops)
- `client-mods/mods/` (moved from root `mods/`)
- `client-mods/custom-mods/fabric-mod/` (moved from root `fabric-mod/`)
- `client-mods/custom-mods/fabric-mod/build.gradle` (updated paths)
- `client-mods/custom-mods/fabric-mod/gradle.properties` (unchanged)
- `client-mods/custom-mods/fabric-mod/settings.gradle` (moved)
- `client-mods/custom-mods/fabric-mod/src/` (moved)
- `launcher/src/VerkkuCraftLauncher/` (moved from `src/VerkkuCraftLauncher/`)
- `launcher/VerkkuCraftLauncher.slnx` (moved from root)
- `launcher/build.ps1` (moved from `VerkkuCraftLauncher/build.ps1`)
- `.gitattributes` (new)
- `scripts/generate_manifest.py` (new - auto-generates manifest from mods)

### Files to Modify
- `.github/workflows/release.yml` (major rewrite)
- `.gitignore` (update paths)
- `manifest.json` (will be auto-generated, but update structure)
- `README.md` (update documentation)

### Files to Delete (after move)
- Root `build.gradle`
- Root `settings.gradle`
- Root `fabric-mod/` directory
- Root `mods/` directory
- `src/` directory (after moving launcher and server plugin)

---

## Task 1: Create `.gitattributes`

**Files:**
- Create: `.gitattributes`

- [ ] **Step 1: Create `.gitattributes` for line ending normalization**

```gitattributes
# Auto detect text files and perform LF normalization
* text=auto

# Java sources
*.java text diff=java
*.gradle text diff=gradle

# Kotlin
*.kt text diff=kotlin
*.kts text diff=kotlin

# XML
*.xml text
*.yml text
*.yaml text

# JSON
*.json text

# Properties
*.properties text

# C# / .NET
*.cs text diff=csharp
*.csproj text
*.slnx text

# PowerShell
*.ps1 text eol=crlf

# Batch
*.bat text eol=crlf
*.cmd text eol=crlf

# Markdown
*.md text diff=markdown

# Binary files (no diff, no merge, no line ending conversion)
*.jar binary
*.png binary
*.jpg binary
*.jpeg binary
*.gif binary
*.ico binary
*.svg text
*.pdf binary
*.zip binary
*.exe binary
*.pdb binary
```

- [ ] **Step 2: Verify gitattributes is valid**

Run: `git check-attr -a .gitattributes`
Expected: no errors

- [ ] **Step 3: Commit**

```bash
git add .gitattributes
git commit -m "chore: add .gitattributes for line ending normalization"
```

---

## Task 2: Create directory structure

**Files:**
- Create: `server-plugin/`
- Create: `client-mods/mods/`
- Create: `client-mods/custom-mods/`
- Create: `launcher/`
- Create: `scripts/`

- [ ] **Step 1: Create all new directories**

```powershell
mkdir server-plugin
mkdir client-mods\mods
mkdir client-mods\custom-mods
mkdir launcher
mkdir scripts
```

- [ ] **Step 2: Verify directories exist**

```powershell
Get-ChildItem -Directory | Select-Object Name
```

Expected output should show: `server-plugin`, `client-mods`, `launcher`, `scripts` along with existing dirs.

- [ ] **Step 3: Commit**

```bash
git add server-plugin/ client-mods/ launcher/ scripts/
git commit -m "chore: create new directory structure for VerkkuCraft"
```

---

## Task 3: Move server plugin

**Files:**
- Move: `build.gradle` → `server-plugin/build.gradle`
- Move: `settings.gradle` → `server-plugin/settings.gradle`
- Move: `src/main/` → `server-plugin/src/main/`
- Create: `server-plugin/settings.gradle` (new content)

- [ ] **Step 1: Move root build.gradle to server-plugin/**

```powershell
Move-Item build.gradle server-plugin\build.gradle
```

- [ ] **Step 2: Create server-plugin/settings.gradle**

Write to `server-plugin/settings.gradle`:
```groovy
rootProject.name = 'vEventDrops'
```

- [ ] **Step 3: Move src/main/ to server-plugin/src/main/**

```powershell
mkdir server-plugin\src
Move-Item src\main server-plugin\src\main
```

- [ ] **Step 4: Remove empty src/ directory**

```powershell
Remove-Item src -Recurse -Force
```

- [ ] **Step 5: Verify server-plugin structure**

```powershell
Get-ChildItem server-plugin -Recurse -File | Select-Object FullName
```

Expected: `build.gradle`, `settings.gradle`, `src/main/java/com/vevent/...`, `src/main/resources/...`

- [ ] **Step 6: Commit**

```bash
git add server-plugin/
git commit -m "refactor: move server plugin to server-plugin/ directory"
```

---

## Task 4: Move client mods (third-party)

**Files:**
- Move: `mods/*` → `client-mods/mods/`

- [ ] **Step 1: Move all JAR files from mods/ to client-mods/mods/**

```powershell
Move-Item mods\* client-mods\mods\
```

- [ ] **Step 2: Remove empty mods/ directory**

```powershell
Remove-Item mods -Recurse -Force
```

- [ ] **Step 3: Verify mods moved correctly**

```powershell
Get-ChildItem client-mods\mods\*.jar | Measure-Object | Select-Object Count
```

Expected: Count = 19

- [ ] **Step 4: Commit**

```bash
git add client-mods/mods/
git commit -m "refactor: move third-party mods to client-mods/mods/"
```

---

## Task 5: Move custom mods (fabric-mod)

**Files:**
- Move: `fabric-mod/` → `client-mods/custom-mods/fabric-mod/`

- [ ] **Step 1: Move fabric-mod to custom-mods/**

```powershell
Move-Item fabric-mod client-mods\custom-mods\fabric-mod
```

- [ ] **Step 2: Verify fabric-mod structure**

```powershell
Get-ChildItem client-mods\custom-mods\fabric-mod -Recurse -File -Exclude "*.class","*.jar" | Select-Object FullName | Where-Object { $_.FullName -notmatch '\\\.gradle\\|\\build\\|\\obj\\' }
```

Expected: `build.gradle`, `gradle.properties`, `settings.gradle`, `src/` contents

- [ ] **Step 3: Commit**

```bash
git add client-mods/custom-mods/fabric-mod/
git commit -m "refactor: move fabric mod to client-mods/custom-mods/fabric-mod/"
```

---

## Task 6: Move launcher

**Files:**
- Move: `src/VerkkuCraftLauncher/` → `launcher/src/VerkkuCraftLauncher/`
- Move: `VerkkuCraftLauncher.slnx` → `launcher/VerkkuCraftLauncher.slnx`
- Move: `VerkkuCraftLauncher/build.ps1` → `launcher/build.ps1`
- Delete: `VerkkuCraftLauncher/` directory (after move)

- [ ] **Step 1: Create launcher/src/ directory**

```powershell
mkdir launcher\src
```

- [ ] **Step 2: Move VerkkuCraftLauncher/ from src/ to launcher/src/**

```powershell
Move-Item src\VerkkuCraftLauncher launcher\src\VerkkuCraftLauncher
```

- [ ] **Step 3: Move solution file**

```powershell
Move-Item VerkkuCraftLauncher.slnx launcher\VerkkuCraftLauncher.slnx
```

- [ ] **Step 4: Move build.ps1**

```powershell
Move-Item VerkkuCraftLauncher\build.ps1 launcher\build.ps1
```

- [ ] **Step 5: Remove old VerkkuCraftLauncher/ directory**

```powershell
Remove-Item VerkkuCraftLauncher -Recurse -Force
```

- [ ] **Step 6: Verify launcher structure**

```powershell
Get-ChildItem launcher -Recurse -File | Where-Object { $_.FullName -notmatch '\\obj\\|\\\.vs\\' } | Select-Object FullName
```

Expected: `VerkkuCraftLauncher.slnx`, `build.ps1`, `src/VerkkuCraftLauncher/...`

- [ ] **Step 7: Commit**

```bash
git add launcher/
git commit -m "refactor: move WPF launcher to launcher/ directory"
```

---

## Task 7: Create manifest generation script

**Files:**
- Create: `scripts/generate_manifest.py`

- [ ] **Step 1: Write the Python script**

Write to `scripts/generate_manifest.py`:
```python
#!/usr/bin/env python3
"""
Generate manifest.json entries from mods/ directories.

Scans client-mods/mods/ and client-mods/custom-mods/ for .jar files,
extracts metadata from filenames, calculates hashes, and updates
the manifest.json with the discovered mods.
"""

import json
import os
import re
import hashlib
import sys
from pathlib import Path


def parse_mod_filename(filename: str) -> tuple[str, str]:
    """Extract display name and version from mod filename.
    
    Examples:
        lithium-fabric-0.25.3+mc26.2.jar → ("Lithium", "0.25.3")
        fabric-api-0.160.0+26.2.jar → ("Fabric API", "0.160.0")
        verkku-title-1.1.0.jar → ("VerkkuCraft Title Screen", "1.1.0")
        Cardinal Components API-8.0.1.jar → ("Cardinal Components API", "8.0.1")
    """
    # Remove .jar extension
    name = filename.replace('.jar', '')
    
    # Special cases for known mods
    special_names = {
        'verkku-title': 'VerkkuCraft Title Screen',
        'voicechat': 'Simple Voice Chat',
        'travelersbackpack': "Traveler's Backpack",
    }
    
    # Check if filename starts with a special name
    for key, display_name in special_names.items():
        if name.lower().startswith(key):
            # Extract version from remaining part
            remaining = name[len(key):].lstrip('-_')
            version_match = re.search(r'(\d+\.\d+(?:\.\d+)*)', remaining)
            version = version_match.group(1) if version_match else ''
            return display_name, version
    
    # Generic parsing: split by common delimiters
    parts = re.split(r'[-_]', name)
    
    # Filter out common platform/version suffixes
    skip_parts = {'fabric', 'forge', 'quilt', 'mc'}
    version_pattern = re.compile(r'^\d+\.\d+')
    
    main_parts = []
    version = ''
    
    for part in parts:
        if version_pattern.match(part):
            version = part
            break
        if part.lower() not in skip_parts and not part.startswith('mc'):
            main_parts.append(part)
    
    # Handle names that are already readable (e.g., "Cardinal Components API")
    if len(main_parts) == 1 and ' ' in main_parts[0]:
        display_name = main_parts[0]
    else:
        display_name = ' '.join(main_parts).title()
    
    return display_name, version


def calculate_sha256(filepath: str) -> str:
    """Calculate SHA256 hash of a file."""
    sha256_hash = hashlib.sha256()
    with open(filepath, "rb") as f:
        for byte_block in iter(lambda: f.read(4096), b""):
            sha256_hash.update(byte_block)
    return sha256_hash.hexdigest()


def scan_mods_directory(folder_path: str, is_custom: bool = False) -> list[dict]:
    """Scan a mods folder and return manifest entries."""
    mods = []
    folder = Path(folder_path)
    
    if not folder.exists():
        print(f"Warning: Directory {folder_path} does not exist")
        return mods
    
    for f in sorted(folder.glob("*.jar")):
        name, version = parse_mod_filename(f.name)
        sha256 = calculate_sha256(str(f))
        size = f.stat().st_size
        
        mod_entry = {
            'name': name,
            'version': version,
            'fileName': f.name,
            'sha256': sha256,
            'fileSize': size,
            'isCustom': is_custom
        }
        mods.append(mod_entry)
        print(f"  Found: {name} v{version} ({f.name})")
    
    return mods


def scan_plugins_directory(folder_path: str) -> list[dict]:
    """Scan custom-plugins folder and return manifest entries."""
    plugins = []
    folder = Path(folder_path)
    
    if not folder.exists():
        print(f"Warning: Directory {folder_path} does not exist")
        return plugins
    
    for plugin_dir in sorted(folder.iterdir()):
        if not plugin_dir.is_dir():
            continue
        
        # Look for build output JAR
        build_libs = plugin_dir / "build" / "libs"
        if build_libs.exists():
            for jar in build_libs.glob("*.jar"):
                name = plugin_dir.name
                version_match = re.search(r'-(\d+\.\d+(?:\.\d+)*)', jar.name)
                version = version_match.group(1) if version_match else '1.0.0'
                sha256 = calculate_sha256(str(jar))
                size = jar.stat().st_size
                
                plugins.append({
                    'name': name,
                    'fileName': jar.name,
                    'version': version,
                    'sha256': sha256,
                    'fileSize': size
                })
                print(f"  Found plugin: {name} v{version} ({jar.name})")
    
    return plugins


def generate_download_url(filename: str, tag: str, repo: str = "Tkr0n/Vevent") -> str:
    """Generate GitHub release download URL."""
    return f"https://github.com/{repo}/releases/download/{tag}/{filename}"


def update_manifest(
    manifest_path: str,
    client_mods: list[dict],
    plugins: list[dict],
    tag: str,
    repo: str = "Tkr0n/Vevent"
) -> None:
    """Update manifest.json with scanned mods and plugins."""
    # Load existing manifest
    with open(manifest_path, 'r', encoding='utf-8') as f:
        manifest = json.load(f)
    
    # Update clientMods - keep Modrinth-based entries, add/update scanned entries
    existing_mods = {m['fileName']: m for m in manifest.get('clientMods', [])}
    
    for mod in client_mods:
        if mod['fileName'] in existing_mods:
            # Update existing entry
            existing = existing_mods[mod['fileName']]
            existing['version'] = mod['version']
            existing['sha256'] = mod['sha256']
            existing['fileSize'] = mod['fileSize']
            existing['downloadUrl'] = generate_download_url(mod['fileName'], tag, repo)
        else:
            # Add new entry
            new_entry = {
                'name': mod['name'],
                'modrinthProjectId': '',
                'version': mod['version'],
                'fileName': mod['fileName'],
                'downloadUrl': generate_download_url(mod['fileName'], tag, repo),
                'sha256': mod['sha256'],
                'fileSize': mod['fileSize']
            }
            manifest.setdefault('clientMods', []).append(new_entry)
    
    # Update plugins
    existing_plugins = {p['fileName']: p for p in manifest.get('plugins', [])}
    
    for plugin in plugins:
        if plugin['fileName'] not in existing_plugins:
            new_entry = {
                'name': plugin['name'],
                'fileName': plugin['fileName'],
                'downloadUrl': generate_download_url(plugin['fileName'], tag, repo),
                'version': plugin['version'],
                'fileSize': plugin['fileSize'],
                'sha256': plugin['sha256']
            }
            manifest.setdefault('plugins', []).append(new_entry)
    
    # Write updated manifest
    with open(manifest_path, 'w', encoding='utf-8') as f:
        json.dump(manifest, f, indent=2, ensure_ascii=False)
    
    print(f"\nManifest updated: {manifest_path}")


def main():
    # Parse arguments
    tag = os.environ.get('GITHUB_REF_NAME', 'v0.0.0')
    repo = os.environ.get('GITHUB_REPOSITORY', 'Tkr0n/Vevent')
    
    # Allow command line overrides
    if len(sys.argv) > 1:
        tag = sys.argv[1]
    if len(sys.argv) > 2:
        repo = sys.argv[2]
    
    print(f"Generating manifest for {repo} @ {tag}\n")
    
    # Scan directories
    print("Scanning client-mods/mods/ (third-party):")
    third_party_mods = scan_mods_directory('client-mods/mods', is_custom=False)
    
    print("\nScanning client-mods/custom-mods/ (custom):")
    custom_mods = scan_mods_directory('client-mods/custom-mods', is_custom=True)
    
    print("\nScanning server-plugin/custom-plugins/ (plugins):")
    plugins = scan_plugins_directory('server-plugin/custom-plugins')
    
    # Combine mods
    all_mods = third_party_mods + custom_mods
    
    # Update manifest
    print(f"\nTotal: {len(all_mods)} client mods, {len(plugins)} plugins")
    update_manifest('manifest.json', all_mods, plugins, tag, repo)


if __name__ == '__main__':
    main()
```

- [ ] **Step 2: Test the script locally**

Run: `python scripts/generate_manifest.py v1.0.0 Tkr0n/Vevent`
Expected: Should scan mods and update manifest.json without errors

- [ ] **Step 3: Verify manifest was updated**

Run: `cat manifest.json | python -m json.tool | head -50`
Expected: Should show updated manifest with sha256 and fileSize populated

- [ ] **Step 4: Commit**

```bash
git add scripts/generate_manifest.py
git commit -m "feat: add manifest generation script for auto-syncing mods"
```

---

## Task 8: Update `.gitignore`

**Files:**
- Modify: `.gitignore`

- [ ] **Step 1: Rewrite `.gitignore` for new structure**

Write to `.gitignore`:
```gitignore
# ==================================
# Gradle
# ==================================
.gradle/
**/build/
!**/build/libs/
**/build/libs/*
!**/build/libs/*.jar

# Gradle wrapper (allow the jar)
!**/gradle/wrapper/gradle-wrapper.jar

# ==================================
# Java
# ==================================
*.class
*.log
*.war
*.ear
hs_err_pid*
replay_pid*

# ==================================
# .NET / C# / WPF
# ==================================
**/bin/
**/obj/
**/out/
*.user
*.suo
*.userosscache
*.sln.docstates
*.nupkg
*.snupkg
project.nuget.cache

# ==================================
# IDEs
# ==================================
# VS Code / Cursor / OpenCode
.vscode/
.factorypath

# IntelliJ IDEA
.idea/
*.iml
*.iws
*.ipr

# Eclipse
.settings/
.classpath
.project

# Visual Studio
.vs/

# ==================================
# OS
# ==================================
.DS_Store
Thumbs.db
desktop.ini

# ==================================
# Secrets & Local Config
# ==================================
config.yml
.env
TestServer

# ==================================
# Build artifacts (root level)
# ==================================
publish/
```

- [ ] **Step 2: Verify gitignore is correct**

Run: `git status --ignored`
Expected: Should ignore build dirs, IDE files, etc. but track `client-mods/mods/*.jar`

- [ ] **Step 3: Commit**

```bash
git add .gitignore
git commit -m "chore: update .gitignore for new VerkkuCraft directory structure"
```

---

## Task 9: Update `manifest.json` structure

**Files:**
- Modify: `manifest.json`

- [ ] **Step 1: Update manifest.json with proper URLs and fields**

The manifest needs to be updated to reflect the new GitHub release URLs. Since the pipeline will auto-generate entries, we need to update the existing entries to use the correct download URLs.

Read current `manifest.json` and update:
- `launcherDownloadUrl` to point to new release structure
- `serverJarUrl` and version info
- Ensure all `clientMods` entries have proper fields

```json
{
  "launcherVersion": "1.0.0",
  "launcherDownloadUrl": "https://github.com/Tkr0n/Vevent/releases/latest/download/VerkkuCraftLauncher.exe",
  "serverJarUrl": "https://fill-data.papermc.io/v1/objects/cabed3ae77cf55deba7c7d8722bc9cfd5e991201c211665f9265616d9fe5c77b/paper-1.20.4-499.jar",
  "serverVersion": "1.21.1",
  "paperBuild": "499",
  "plugins": [
    {
      "name": "vEventDrops",
      "fileName": "vEventDrops-1.0.0.jar",
      "downloadUrl": "https://github.com/Tkr0n/Vevent/releases/download/v1.0.0/vEventDrops-1.0.0.jar",
      "version": "1.0.0",
      "fileSize": 0,
      "sha256": ""
    },
    {
      "name": "GravesX",
      "fileName": "GravesX-2026.4.9.1.jar",
      "downloadUrl": "https://github.com/Tkr0n/Vevent/releases/download/plugins/GravesX-2026.4.9.1.jar",
      "version": "2026.4.9.1",
      "fileSize": 0,
      "sha256": ""
    },
    {
      "name": "spark",
      "fileName": "spark-2.8.119.jar",
      "downloadUrl": "https://github.com/Tkr0n/Vevent/releases/download/plugins/spark-2.8.119.jar",
      "version": "2.8.119",
      "fileSize": 0,
      "sha256": ""
    }
  ],
  "clientMods": [],
  "shaderMods": [
    {
      "name": "Iris",
      "modrinthProjectId": "iris",
      "version": "",
      "fileName": "",
      "downloadUrl": ""
    },
    {
      "name": "Sodium",
      "modrinthProjectId": "sodium",
      "version": "",
      "fileName": "",
      "downloadUrl": ""
    }
  ],
  "tailscaleAuthKey": "",
  "defaultServerConfig": {
    "serverName": "VerkkuCraft",
    "serverAddress": "verkku.taild0c659.ts.net",
    "motd": "§6Bienvenido a §bVerkkuCraft§6! §7Servidor de la comunidad",
    "maxPlayers": 20,
    "serverPort": 25565,
    "gameMode": "survival",
    "onlineMode": false,
    "allowFlight": true,
    "spawnProtection": 0,
    "viewDistance": 10,
    "difficulty": "normal",
    "whitelist": false,
    "ipWhitelist": ""
  }
}
```

Note: The `clientMods` array will be populated by the pipeline script.

- [ ] **Step 2: Commit**

```bash
git add manifest.json
git commit -m "chore: update manifest.json structure for new release pipeline"
```

---

## Task 10: Rewrite `release.yml` workflow

**Files:**
- Modify: `.github/workflows/release.yml`

- [ ] **Step 1: Rewrite the release workflow**

Write to `.github/workflows/release.yml`:
```yaml
name: Build & Release VerkkuCraft

# Runs on tag push (e.g., v1.0.1):
#   git tag v1.0.1 && git push origin v1.0.1
# Builds all components, generates manifest from mods/, and uploads to GitHub Release.
on:
  push:
    tags:
      - "v*"
  workflow_dispatch:

permissions:
  contents: write

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Setup Java 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21

      - name: Setup Gradle 8.10
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: "8.10"

      - name: Build server plugin
        working-directory: server-plugin
        run: gradle build -PpluginVersion="${GITHUB_REF_NAME#v}"

      - name: Build custom Fabric mod
        working-directory: client-mods/custom-mods/fabric-mod
        run: gradle build

      - name: Generate manifest from mods
        run: python3 scripts/generate_manifest.py "${GITHUB_REF_NAME}" "${GITHUB_REPOSITORY}"

      - name: Inject secrets into manifest
        if: env.TAILSCALE_AUTH_KEY != ''
        run: |
          python3 -c "
          import json, os
          with open('manifest.json', 'r') as f: data = json.load(f)
          data['tailscaleAuthKey'] = os.environ['TAILSCALE_AUTH_KEY']
          with open('manifest.json', 'w') as f: json.dump(data, f, indent=2)
          "
        env:
          TAILSCALE_AUTH_KEY: ${{ secrets.TAILSCALE_AUTH_KEY }}

      - name: Upload JARs and manifest to Release
        uses: softprops/action-gh-release@v2
        with:
          files: |
            server-plugin/build/libs/*.jar
            client-mods/custom-mods/fabric-mod/build/libs/*.jar
            client-mods/mods/*.jar
            manifest.json
          generate_release_notes: true
```

- [ ] **Step 2: Verify YAML syntax**

Run: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/release.yml'))"`
Expected: No errors

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/release.yml
git commit -m "feat: rewrite release workflow for VerkkuCraft with auto-manifest generation"
```

---

## Task 11: Update `README.md`

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Rewrite README.md**

Write to `README.md`:
```markdown
# VerkkuCraft Launcher

Launcher personalizado para el servidor VerkkuCraft con mods y plugins custom.

## Estructura del Repositorio

```
VerkkuCraft/
├── .github/workflows/
│   └── release.yml              # Build + genera manifest desde mods/
├── server-plugin/               # Plugin de servidor
│   ├── custom-plugins/          # Plugins hechos por nosotros
│   │   └── vEventDrops/
│   ├── build.gradle
│   └── src/
├── client-mods/                 # Mods del cliente
│   ├── mods/                    # Mods de terceros (Modrinth, etc.)
│   └── custom-mods/             # Mods hechos por nosotros
│       └── fabric-mod/          # Title screen mod
├── launcher/                    # Launcher WPF
│   ├── src/VerkkuCraftLauncher/
│   ├── VerkkuCraftLauncher.slnx
│   └── build.ps1
├── server/                      # Config del server Docker
│   ├── docker-compose.yml
│   ├── .env.example
│   └── voicechat-server.properties
├── scripts/                     # Scripts de utilidad
│   └── generate_manifest.py     # Genera manifest desde mods/
├── manifest.json                # Se auto-genera desde mods/
└── README.md
```

## Components

### Server Plugin (`server-plugin/`)
Plugin de Paper para el servidor Minecraft con eventos, comandos y managers personalizados.

### Client Mods (`client-mods/`)
- **mods/**: Mods de terceros descargados de Modrinth
- **custom-mods/**: Mods desarrollados por el equipo (ej: title screen)

### Launcher (`launcher/`)
Aplicación WPF para Windows que:
- Descarga e instala mods desde el manifest
- Configura el servidor automaticamente
- Detecta e instala shaders (Iris/Sodium)

### Server (`server/`)
Configuración Docker para el servidor Minecraft.

## Development

### Building Server Plugin
```bash
cd server-plugin
gradle build
```

### Building Custom Mods
```bash
cd client-mods/custom-mods/fabric-mod
gradle build
```

### Building Launcher
```powershell
cd launcher
.\build.ps1
```

## Release Process

1. Update version in `manifest.json`
2. Tag the release: `git tag v1.0.0`
3. Push: `git push origin v1.0.0`
4. GitHub Actions will:
   - Build server plugin and custom mods
   - Scan `client-mods/mods/` and generate manifest entries
   - Upload all artifacts to GitHub Release

## Adding New Mods

1. Place the `.jar` file in `client-mods/mods/` (third-party) or `client-mods/custom-mods/` (custom)
2. Push changes
3. The release pipeline will automatically include the mod in the manifest

## License

Private - VerkkuCraft Team
```

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: update README with new VerkkuCraft structure and instructions"
```

---

## Task 12: Final cleanup and verification

**Files:**
- Verify all moves completed
- Verify no broken references

- [ ] **Step 1: Verify directory structure**

```powershell
Get-ChildItem -Directory -Recurse -Depth 1 | Where-Object { $_.FullName -notmatch '\\\.git\\|\\\.gradle\\|\\obj\\|\\\.vs\\|\\build\\' } | Select-Object FullName
```

Expected structure:
```
client-mods/
client-mods/custom-mods/
client-mods/mods/
launcher/
launcher/src/
scripts/
server-plugin/
server-plugin/custom-plugins/
server-plugin/src/
server/
docs/
docs/superpowers/
docs/superpowers/specs/
docs/superpowers/plans/
```

- [ ] **Step 2: Verify no old directories remain**

```powershell
Test-Path src
Test-Path mods
Test-Path fabric-mod
```

Expected: All should return `False`

- [ ] **Step 3: Run git status to verify all changes**

```bash
git status
```

Expected: Should show all new/modified/deleted files correctly

- [ ] **Step 4: Final commit with all changes**

```bash
git add -A
git commit -m "refactor: complete VerkkuCraft repository restructure

- Move server plugin to server-plugin/
- Move third-party mods to client-mods/mods/
- Move custom mods to client-mods/custom-mods/
- Move launcher to launcher/
- Add .gitattributes for line endings
- Add scripts/generate_manifest.py for auto-manifest generation
- Rewrite release.yml for new structure
- Update .gitignore for new paths
- Update README.md with new documentation"
```

---

## Verification Checklist

After all tasks are complete, verify:

- [ ] `server-plugin/` contains build.gradle and src/ with plugin code
- [ ] `client-mods/mods/` contains all 19 third-party mod JARs
- [ ] `client-mods/custom-mods/fabric-mod/` contains the title screen mod
- [ ] `launcher/` contains the WPF application
- [ ] `scripts/generate_manifest.py` works correctly
- [ ] `.github/workflows/release.yml` builds all components
- [ ] `.gitignore` properly ignores build artifacts but tracks mod JARs
- [ ] `.gitattributes` is configured for line endings
- [ ] `README.md` documents the new structure
- [ ] No broken paths or references remain
