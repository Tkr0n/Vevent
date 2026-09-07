# Standalone Launch + Fabric Mods Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** El launcher descarga e instala todo solo (cliente MC 1.20.4, librerías, Fabric, mod de voz) y lanza el juego sin depender de otro launcher, pre-rellenando el nombre de usuario detectado.

**Architecture:** Nuevo `GameInstallerService` orquesta 3 resolvers de metadatos (Mojang piston-meta, Fabric meta, Modrinth API) que descargan a `%appdata%/VerkkuCraft/`; `MinecraftLauncher` se reescribe para lanzar `KnotClient` con classpath completo; `LauncherAccountService` lee el nombre del launcher oficial.

**Tech Stack:** .NET 8, System.Text.Json, APIs REST (piston-meta.mojang.com, meta.fabricmc.net, api.modrinth.com), lanzamientos offline.

---

## File map

| Archivo | Acción |
|---|---|
| `src/VerkkuCraftLauncher/Models/MojangVersionInfo.cs` | Crear: modelos del version manifest (client jar, libraries, assetIndex) |
| `src/VerkkuCraftLauncher/Services/MojangMetaService.cs` | Crear: resuelve y descarga cliente + librerías + natives + assets |
| `src/VerkkuCraftLauncher/Services/FabricService.cs` | Crear: resuelve loader version + profile (libraries, mainClass) |
| `src/VerkkuCraftLauncher/Services/ModrinthService.cs` | Crear: resuelve archivo exacto de un mod (voicechat) para fabric/1.20.4 |
| `src/VerkkuCraftLauncher/Services/GameInstallerService.cs` | Crear: orquesta instalación completa en `%appdata%/VerkkuCraft/` |
| `src/VerkkuCraftLauncher/Services/LauncherAccountService.cs` | Crear: detecta username del launcher oficial/HMCL |
| `src/VerkkuCraftLauncher/Services/MinecraftLauncher.cs` | Reescribir `BuildJvmArguments`: classpath completo + KnotClient |
| `src/VerkkuCraftLauncher/Models/LauncherManifest.cs` | Agregar `List<ClientModInfo> ClientMods` |
| `src/VerkkuCraftLauncher/ViewModels/MainViewModel.cs` | Cablear installer + username detectado en `InitializeAsync`/`JugarAsync` |
| `manifest.json` (raíz) | Agregar sección `clientMods` con voicechat |

---

### Task 1: Modelos Mojang + MojangMetaService (cliente vanilla completo)

**Files:**
- Create: `src/VerkkuCraftLauncher/Models/MojangVersionInfo.cs`
- Create: `src/VerkkuCraftLauncher/Services/MojangMetaService.cs`

- [ ] **Step 1: Crear modelos**

```csharp
namespace VerkkuCraftLauncher.Models;

public class MojangLibrary
{
    public string Name { get; set; } = string.Empty; // group:artifact:version
    public MojangArtifact? Downloads_Artifact { get; set; }
    public Dictionary<string, MojangArtifact>? Downloads_Classifiers { get; set; }
    public List<MojangRule>? Rules { get; set; }
    public Dictionary<string, string>? Natives { get; set; }
}

public class MojangArtifact
{
    public string Path { get; set; } = string.Empty;
    public string Url { get; set; } = string.Empty;
    public string Sha1 { get; set; } = string.Empty;
    public long Size { get; set; }
}

public class MojangRule
{
    public string Action { get; set; } = "allow";
    public Dictionary<string, string>? Os { get; set; }
}

public class MojangVersionInfo
{
    public string Id { get; set; } = string.Empty;
    public string ClientJarUrl { get; set; } = string.Empty;
    public string AssetIndexId { get; set; } = string.Empty;
    public string AssetIndexUrl { get; set; } = string.Empty;
    public List<MojangLibrary> Libraries { get; set; } = new();
}
```

- [ ] **Step 2: Implementar MojangMetaService**

```csharp
using System.Text.Json;
using VerkkuCraftLauncher.Helpers;
using VerkkuCraftLauncher.Models;

namespace VerkkuCraftLauncher.Services;

public class MojangMetaService
{
    private const string VersionManifestUrl = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private readonly HttpDownloader _downloader;

    public MojangMetaService(HttpDownloader downloader) { _downloader = downloader; }

    public async Task<MojangVersionInfo> ResolveAsync(string mcVersion, CancellationToken ct = default)
    {
        var manifestJson = await _downloader.DownloadStringAsync(VersionManifestUrl, ct);
        using var manifest = JsonDocument.Parse(manifestJson);
        string? versionUrl = null;
        foreach (var v in manifest.RootElement.GetProperty("versions").EnumerateArray())
        {
            if (v.GetProperty("id").GetString() == mcVersion)
            { versionUrl = v.GetProperty("url").GetString(); break; }
        }
        if (versionUrl == null) throw new InvalidOperationException($"Versión {mcVersion} no encontrada en Mojang");
        var versionJson = await _downloader.DownloadStringAsync(versionUrl, ct);
        using var doc = JsonDocument.Parse(versionJson);
        var root = doc.RootElement;
        var info = new MojangVersionInfo
        {
            Id = mcVersion,
            ClientJarUrl = root.GetProperty("downloads").GetProperty("client").GetProperty("url").GetString()!,
            AssetIndexId = root.GetProperty("assetIndex").GetProperty("id").GetString()!,
            AssetIndexUrl = root.GetProperty("assetIndex").GetProperty("url").GetString()!,
        };
        foreach (var lib in root.GetProperty("libraries").EnumerateArray())
        {
            var model = new MojangLibrary { Name = lib.GetProperty("name").GetString()! };
            if (lib.TryGetProperty("rules", out var rules))
                model.Rules = rules.EnumerateArray().Select(r => new MojangRule
                {
                    Action = r.GetProperty("action").GetString()!,
                    Os = r.TryGetProperty("os", out var os)
                        ? os.EnumerateObject().ToDictionary(p => p.Name, p => p.Value.GetString()!)
                        : null
                }).ToList();
            if (lib.TryGetProperty("natives", out var natives))
                model.Natives = natives.EnumerateObject().ToDictionary(p => p.Name, p => p.Value.GetString()!);
            if (lib.TryGetProperty("downloads", out var dl))
            {
                if (dl.TryGetProperty("artifact", out var art))
                    model.Downloads_Artifact = ParseArtifact(art);
                if (dl.TryGetProperty("classifiers", out var cls))
                    model.Downloads_Classifiers = cls.EnumerateObject()
                        .ToDictionary(p => p.Name, p => ParseArtifact(p.Value));
            }
            info.Libraries.Add(model);
        }
        return info;
    }

    private static MojangArtifact ParseArtifact(JsonElement e) => new()
    {
        Path = e.GetProperty("path").GetString()!,
        Url = e.GetProperty("url").GetString()!,
        Sha1 = e.GetProperty("sha1").GetString()!,
        Size = e.GetProperty("size").GetInt64()
    };

    public static bool IsAllowedOnWindows(MojangLibrary lib)
    {
        if (lib.Rules == null || lib.Rules.Count == 0) return true;
        var allowed = false;
        foreach (var rule in lib.Rules)
        {
            var applies = rule.Os == null ||
                (rule.Os.TryGetValue("name", out var osName) && osName == "windows");
            if (!applies) continue;
            allowed = rule.Action == "allow";
        }
        return allowed;
    }
}
```

- [ ] **Step 3: Compilar**

Run: `dotnet build VerkkuCraftLauncher.slnx --nologo -v q`
Expected: `0 Errores`

- [ ] **Step 4: Commit**

```bash
git add src/VerkkuCraftLauncher/Models/MojangVersionInfo.cs src/VerkkuCraftLauncher/Services/MojangMetaService.cs
git commit -m "feat: add MojangMetaService resolving vanilla client metadata"
```

---

### Task 2: Descarga de cliente, librerías, natives y assets

**Files:**
- Modify: `src/VerkkuCraftLauncher/Services/MojangMetaService.cs` (agregar método `DownloadGameAsync`)

- [ ] **Step 1: Agregar método de descarga** ( SharpCompress ya está referenciado para extraer natives )

```csharp
// Agregar a MojangMetaService:
public async Task<GameFiles> DownloadGameAsync(
    MojangVersionInfo info, string baseDir,
    IProgress<int>? progress = null, CancellationToken ct = default)
{
    var versionDir = Path.Combine(baseDir, "versions", info.Id);
    var libsDir = Path.Combine(baseDir, "libraries");
    var nativesDir = Path.Combine(versionDir, "natives");
    var assetsDir = Path.Combine(baseDir, "assets");
    Directory.CreateDirectory(versionDir);
    Directory.CreateDirectory(libsDir);
    Directory.CreateDirectory(nativesDir);

    var clientJar = Path.Combine(versionDir, $"{info.Id}.jar");
    if (!File.Exists(clientJar))
        await _downloader.DownloadFileAsync(info.ClientJarUrl, clientJar, null, ct);

    var classpath = new List<string> { clientJar };
    var allowed = info.Libraries.Where(IsAllowedOnWindows).ToList();
    int done = 0;
    foreach (var lib in allowed)
    {
        string? classifier = null;
        if (lib.Natives != null && lib.Natives.TryGetValue("windows", out var nat))
            classifier = nat.Replace("${arch}", "64");
        if (classifier != null && lib.Downloads_Classifiers != null &&
            lib.Downloads_Classifiers.TryGetValue(classifier, out var natArt))
        {
            var tmp = Path.Combine(Path.GetTempPath(), Path.GetFileName(natArt.Path));
            if (!File.Exists(tmp)) await _downloader.DownloadFileAsync(natArt.Url, tmp, null, ct);
            System.IO.Compression.ZipFile.ExtractToDirectory(tmp, nativesDir, overwriteFiles: true);
        }
        else if (lib.Downloads_Artifact != null)
        {
            var dest = Path.Combine(libsDir, lib.Downloads_Artifact.Path.Replace('/', Path.DirectorySeparatorChar));
            Directory.CreateDirectory(Path.GetDirectoryName(dest)!);
            if (!File.Exists(dest))
                await _downloader.DownloadFileAsync(lib.Downloads_Artifact.Url, dest, null, ct);
            classpath.Add(dest);
        }
        done++;
        progress?.Report(done * 100 / allowed.Count);
    }

    // Assets: índice + objetos referenciados
    var indexPath = Path.Combine(assetsDir, "indexes", $"{info.AssetIndexId}.json");
    Directory.CreateDirectory(Path.GetDirectoryName(indexPath)!);
    if (!File.Exists(indexPath))
        await _downloader.DownloadFileAsync(info.AssetIndexUrl, indexPath, null, ct);
    using var index = JsonDocument.Parse(await File.ReadAllTextAsync(indexPath, ct));
    foreach (var obj in index.RootElement.GetProperty("objects").EnumerateObject())
    {
        var hash = obj.Value.GetProperty("hash").GetString()!;
        var dest = Path.Combine(assetsDir, "objects", hash[..2], hash);
        if (File.Exists(dest)) continue;
        Directory.CreateDirectory(Path.GetDirectoryName(dest)!);
        await _downloader.DownloadFileAsync(
            $"https://resources.download.minecraft.net/{hash[..2]}/{hash}", dest, null, ct);
    }

    return new GameFiles { Classpath = classpath, NativesDir = nativesDir, AssetsDir = assetsDir };
}

public class GameFiles
{
    public List<string> Classpath { get; set; } = new();
    public string NativesDir { get; set; } = string.Empty;
    public string AssetsDir { get; set; } = string.Empty;
}
```

- [ ] **Step 2: Compilar**

Run: `dotnet build VerkkuCraftLauncher.slnx --nologo -v q`
Expected: `0 Errores`

- [ ] **Step 3: Commit**

```bash
git add src/VerkkuCraftLauncher/Services/MojangMetaService.cs
git commit -m "feat: download vanilla client, libraries, natives and assets"
```

---

### Task 3: FabricService (loader + librerías)

**Files:**
- Create: `src/VerkkuCraftLauncher/Services/FabricService.cs`

- [ ] **Step 1: Implementar resolución vía meta.fabricmc.net**

```csharp
using System.Text.Json;
using VerkkuCraftLauncher.Helpers;

namespace VerkkuCraftLauncher.Services;

public class FabricInfo
{
    public string LoaderVersion { get; set; } = string.Empty;
    public string MainClass { get; set; } = "net.fabricmc.loader.impl.launch.knot.KnotClient";
    public List<(string Url, string Path)> Libraries { get; set; } = new();
}

public class FabricService
{
    private readonly HttpDownloader _downloader;
    public FabricService(HttpDownloader downloader) { _downloader = downloader; }

    public async Task<FabricInfo> ResolveAsync(string mcVersion, CancellationToken ct = default)
    {
        // Último loader estable para esta versión de MC
        var loadersJson = await _downloader.DownloadStringAsync(
            $"https://meta.fabricmc.net/v2/versions/loader/{mcVersion}", ct);
        using var loaders = JsonDocument.Parse(loadersJson);
        var loaderVersion = loaders.RootElement.EnumerateArray().First()
            .GetProperty("loader").GetProperty("version").GetString()!;

        var profileJson = await _downloader.DownloadStringAsync(
            $"https://meta.fabricmc.net/v2/versions/loader/{mcVersion}/{loaderVersion}/profile/json", ct);
        using var profile = JsonDocument.Parse(profileJson);
        var info = new FabricInfo { LoaderVersion = loaderVersion };
        var mavenBase = "https://maven.fabricmc.net/";
        foreach (var lib in profile.RootElement.GetProperty("libraries").EnumerateArray())
        {
            var name = lib.GetProperty("name").GetString()!;
            var parts = name.Split(':');
            var path = $"{parts[0].Replace('.', '/')}/{parts[1]}/{parts[2]}/{parts[1]}-{parts[2]}.jar";
            var url = lib.TryGetProperty("url", out var u)
                ? u.GetString()!.TrimEnd('/') + "/" + path
                : mavenBase + path;
            info.Libraries.Add((url, path));
        }
        return info;
    }

    public async Task<List<string>> DownloadLibrariesAsync(
        FabricInfo info, string libsDir, CancellationToken ct = default)
    {
        var result = new List<string>();
        foreach (var (url, path) in info.Libraries)
        {
            var dest = Path.Combine(libsDir, path.Replace('/', Path.DirectorySeparatorChar));
            Directory.CreateDirectory(Path.GetDirectoryName(dest)!);
            if (!File.Exists(dest))
                await _downloader.DownloadFileAsync(url, dest, null, ct);
            result.Add(dest);
        }
        return result;
    }
}
```

- [ ] **Step 2: Compilar**

Run: `dotnet build VerkkuCraftLauncher.slnx --nologo -v q`
Expected: `0 Errores`

- [ ] **Step 3: Commit**

```bash
git add src/VerkkuCraftLauncher/Services/FabricService.cs
git commit -m "feat: add FabricService resolving loader libraries"
```

---

### Task 4: ModrinthService + mod de voz en el manifiesto

**Files:**
- Create: `src/VerkkuCraftLauncher/Services/ModrinthService.cs`
- Modify: `src/VerkkuCraftLauncher/Models/LauncherManifest.cs` (agregar `ClientModInfo` + lista)
- Modify: `manifest.json` (agregar `clientMods`)

- [ ] **Step 1: Implementar ModrinthService** ( resuelve el jar exacto para fabric+1.20.4 )

```csharp
using System.Text.Json;
using VerkkuCraftLauncher.Helpers;

namespace VerkkuCraftLauncher.Services;

public class ModrinthService
{
    private readonly HttpDownloader _downloader;
    public ModrinthService(HttpDownloader downloader) { _downloader = downloader; }

    public async Task<(string Url, string FileName, string Sha512)> ResolveFileAsync(
        string projectId, string mcVersion, string loader, CancellationToken ct = default)
    {
        var url = $"https://api.modrinth.com/v2/project/{projectId}/version" +
                  $"?game_versions=%5B%22{mcVersion}%22%5D&loaders=%5B%22{loader}%22%5D";
        var json = await _downloader.DownloadStringAsync(url, ct);
        using var doc = JsonDocument.Parse(json);
        var version = doc.RootElement.EnumerateArray().First();
        var file = version.GetProperty("files").EnumerateArray()
            .First(f => f.GetProperty("primary").GetBoolean());
        return (
            file.GetProperty("url").GetString()!,
            file.GetProperty("filename").GetString()!,
            file.GetProperty("hashes").GetProperty("sha512").GetString()!
        );
    }

    public async Task<string> DownloadModAsync(
        string projectId, string mcVersion, string loader, string modsDir,
        CancellationToken ct = default)
    {
        var (url, fileName, _) = await ResolveFileAsync(projectId, mcVersion, loader, ct);
        Directory.CreateDirectory(modsDir);
        var dest = Path.Combine(modsDir, fileName);
        if (!File.Exists(dest))
            await _downloader.DownloadFileAsync(url, dest, null, ct);
        return dest;
    }
}
```

- [ ] **Step 2: Agregar modelo al manifiesto**

```csharp
// En LauncherManifest.cs agregar:
public List<ClientModInfo> ClientMods { get; set; } = new();

// Nueva clase en el mismo archivo:
public class ClientModInfo
{
    public string Name { get; set; } = string.Empty;
    public string ModrinthProjectId { get; set; } = string.Empty;
    public string Version { get; set; } = string.Empty;
}
```

- [ ] **Step 3: Agregar sección al manifest.json raíz**

```json
"clientMods": [
  {
    "name": "Simple Voice Chat",
    "modrinthProjectId": "9eGKb6K1",
    "version": "2.6.21"
  }
]
```

- [ ] **Step 4: Compilar**

Run: `dotnet build VerkkuCraftLauncher.slnx --nologo -v q`
Expected: `0 Errores`

- [ ] **Step 5: Commit**

```bash
git add src/VerkkuCraftLauncher/Services/ModrinthService.cs src/VerkkuCraftLauncher/Models/LauncherManifest.cs manifest.json
git commit -m "feat: add ModrinthService and clientMods manifest section"
```

---

### Task 5: Reescribir el lanzamiento (KnotClient, classpath completo)

**Files:**
- Modify: `src/VerkkuCraftLauncher/Services/MinecraftLauncher.cs` (método `BuildJvmArguments` y firma)
- Modify: `src/VerkkuCraftLauncher/Services/GameInstallerService.cs` (crearlo aquí si no existe: orquesta Tasks 1-4)

- [ ] **Step 1: Crear GameInstallerService**

```csharp
using VerkkuCraftLauncher.Models;

namespace VerkkuCraftLauncher.Services;

public class InstalledGame
{
    public List<string> Classpath { get; set; } = new();
    public string NativesDir { get; set; } = string.Empty;
    public string AssetsDir { get; set; } = string.Empty;
    public string AssetIndexId { get; set; } = string.Empty;
    public string MainClass { get; set; } = "net.fabricmc.loader.impl.launch.knot.KnotClient";
}

public class GameInstallerService
{
    private readonly MojangMetaService _mojang;
    private readonly FabricService _fabric;
    private readonly ModrinthService _modrinth;

    public GameInstallerService(MojangMetaService mojang, FabricService fabric, ModrinthService modrinth)
    { _mojang = mojang; _fabric = fabric; _modrinth = modrinth; }

    public async Task<InstalledGame> EnsureInstalledAsync(
        string mcVersion, List<ClientModInfo> mods,
        IProgress<string>? status = null, IProgress<int>? progress = null,
        CancellationToken ct = default)
    {
        var baseDir = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "VerkkuCraft");
        status?.Report("Resolviendo cliente de Minecraft...");
        var info = await _mojang.ResolveAsync(mcVersion, ct);
        status?.Report("Descargando cliente y librerías...");
        var files = await _mojang.DownloadGameAsync(info, baseDir, progress, ct);
        status?.Report("Instalando Fabric...");
        var fabric = await _fabric.ResolveAsync(mcVersion, ct);
        var fabricLibs = await _fabric.DownloadLibrariesAsync(
            fabric, Path.Combine(baseDir, "libraries"), ct);
        status?.Report("Descargando mods...");
        var modsDir = Path.Combine(baseDir, "mods");
        foreach (var mod in mods)
            await _modrinth.DownloadModAsync(mod.ModrinthProjectId, mcVersion, "fabric", modsDir, ct);
        return new InstalledGame
        {
            Classpath = files.Classpath.Concat(fabricLibs).ToList(),
            NativesDir = files.NativesDir,
            AssetsDir = files.AssetsDir,
            AssetIndexId = info.AssetIndexId
        };
    }
}
```

- [ ] **Step 2: Reescribir BuildJvmArguments en MinecraftLauncher.cs**

```csharp
// Reemplazar firma de LaunchMinecraftAsync: recibe InstalledGame + gameDir + username.
private string BuildJvmArguments(InstalledGame game, string gameDir, string username, string server, int port)
{
    var uuid = GenerateOfflineUuid(username);
    var cp = string.Join(Path.PathSeparator, game.Classpath);
    var serverArg = string.IsNullOrEmpty(server) ? string.Empty : $"--server {server} --port {port}";
    return $"-Xms512M -Xmx2048M " +
           $"-Djava.library.path=\"{game.NativesDir}\" " +
           $"-cp \"{cp}\" " +
           $"{game.MainClass} " +
           $"--username {username} " +
           $"--version 1.20.4 " +
           $"--gameDir \"{gameDir}\" " +
           $"--assetsDir \"{game.AssetsDir}\" " +
           $"--assetIndex {game.AssetIndexId} " +
           $"--uuid {uuid} " +
           $"--accessToken 0 " +
           $"--userType legacy " +
           serverArg;
}
```

- [ ] **Step 3: Compilar y ajustar llamadas en MainViewModel**

Run: `dotnet build VerkkuCraftLauncher.slnx --nologo -v q`
Expected: `0 Errores` (ajustar `JugarAsync` a la nueva firma en esta misma tarea)

- [ ] **Step 4: Commit**

```bash
git add src/VerkkuCraftLauncher/Services/GameInstallerService.cs src/VerkkuCraftLauncher/Services/MinecraftLauncher.cs src/VerkkuCraftLauncher/ViewModels/MainViewModel.cs
git commit -m "feat: standalone Fabric launch with full classpath"
```

---

### Task 6: Detección de username + cableado final

**Files:**
- Create: `src/VerkkuCraftLauncher/Services/LauncherAccountService.cs`
- Modify: `src/VerkkuCraftLauncher/ViewModels/MainViewModel.cs` (prefill + flujo installer)
- Modify: `src/VerkkuCraftLauncher/App.xaml.cs` (registrar nuevos servicios en DI)

- [ ] **Step 1: Implementar detección**

```csharp
using System.Text.Json;

namespace VerkkuCraftLauncher.Services;

public class LauncherAccountService
{
    public string? DetectUsername()
    {
        // 1. Launcher oficial de Minecraft
        var official = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
            ".minecraft", "launcher_accounts.json");
        var name = ReadOfficialUsername(official);
        if (!string.IsNullOrEmpty(name)) return name;

        // 2. HMCL (best-effort: hmcl.json guarda el personaje seleccionado)
        var hmcl = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
            ".minecraft", "hmcl.json");
        return ReadHmclUsername(hmcl);
    }

    private static string? ReadOfficialUsername(string path)
    {
        try
        {
            if (!File.Exists(path)) return null;
            using var doc = JsonDocument.Parse(File.ReadAllText(path));
            var root = doc.RootElement;
            if (!root.TryGetProperty("activeAccount", out var active)) return null;
            var key = active.GetProperty("localId").GetString();
            if (key == null) return null;
            return root.GetProperty("accounts").GetProperty(key)
                .GetProperty("minecraftProfile").GetProperty("name").GetString();
        }
        catch { return null; }
    }

    private static string? ReadHmclUsername(string path)
    {
        try
        {
            if (!File.Exists(path)) return null;
            using var doc = JsonDocument.Parse(File.ReadAllText(path));
            if (doc.RootElement.TryGetProperty("selectedCharacter", out var c))
                return c.GetString();
            return null;
        }
        catch { return null; }
    }
}
```

- [ ] **Step 2: Cablear en MainViewModel.InitializeAsync**

Al inicio de `InitializeAsync`, antes de descargar nada:

```csharp
Username = _accountService.DetectUsername() ?? string.Empty;
```

Y reemplazar el bloque "Download server / Configure server" (pensado para host local) por:

```csharp
StatusMessage = "Instalando juego y mods...";
_installedGame = await _gameInstaller.EnsureInstalledAsync(
    Manifest.ServerVersion, Manifest.ClientMods, CreateStringProgress(), CreateProgress());
```

Guardar `_installedGame` en campo privado y usarlo en `JugarAsync`.

- [ ] **Step 3: Registrar servicios en App.xaml.cs**

Agregar construcción manual de `MojangMetaService`, `FabricService`, `ModrinthService`, `GameInstallerService`, `LauncherAccountService` y pasarlos al `MainViewModel`.

- [ ] **Step 4: Compilar**

Run: `dotnet build VerkkuCraftLauncher.slnx --nologo -v q`
Expected: `0 Errores`

- [ ] **Step 5: Prueba manual end-to-end**

Run: ejecutar el `.exe`, verificar que descarga cliente+Fabric+mod, que el username aparece pre-rellenado y que JUGAR abre Minecraft 1.20.4 con voz disponible.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: username autodetect and installer wiring"
```

---

## Self-Review

1. **Spec coverage:** Standalone (Tasks 1,2,5) ✓, Fabric+mods (Tasks 3,4) ✓, username detectado (Task 6) ✓, cableado manifiesto (Tasks 4,6) ✓.
2. **Placeholders:** Sin TBD/TODO; URLs y endpoints exactos incluidos. La versión del loader de Fabric se resuelve dinámicamente (no hardcodeada).
3. **Type consistency:** `GameFiles` (Task 2) vs `InstalledGame` (Task 5): `DownloadGameAsync` retorna `GameFiles`, `EnsureInstalledAsync` lo consume y retorna `InstalledGame` — nombres distintos, sin colisión. `ClientModInfo` definido en Task 4 y consumido en Task 5 ✓.
