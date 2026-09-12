using System.IO;
using System.Net.Http;
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
    private readonly ShaderDetectionService _shaderDetection;

    public GameInstallerService(MojangMetaService mojang, FabricService fabric, 
        ModrinthService modrinth, ShaderDetectionService shaderDetection)
    { 
        _mojang = mojang; 
        _fabric = fabric; 
        _modrinth = modrinth;
        _shaderDetection = shaderDetection;
    }

    public List<DetectedShader> LastDetectedShaders { get; private set; } = new();

    public async Task<InstalledGame> EnsureInstalledAsync(
        string mcVersion, List<ClientModInfo> mods,
        IProgress<DownloadProgress>? progress = null, IProgress<int>? percentProgress = null,
        CancellationToken ct = default)
    {
        var baseDir = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "VerkkuCraft");
        progress?.Report(new DownloadProgress(0, "Resolviendo cliente de Minecraft..."));
        var info = await _mojang.ResolveAsync(mcVersion, ct);
        progress?.Report(new DownloadProgress(5, "Descargando cliente y librerías..."));
        var files = await _mojang.DownloadGameAsync(info, baseDir, progress, ct);
        progress?.Report(new DownloadProgress(92, "Instalando Fabric..."));
        var fabric = await _fabric.ResolveAsync(mcVersion, ct);
        var fabricLibs = await _fabric.DownloadLibrariesAsync(
            fabric, Path.Combine(baseDir, "libraries"), progress, ct);
        progress?.Report(new DownloadProgress(97, "Descargando mods..."));
        var modsDir = Path.Combine(baseDir, "game", mcVersion, "mods");
        await CleanupStaleModsAsync(mods, modsDir, mcVersion, ct);
        foreach (var mod in mods)
        {
            if (!string.IsNullOrEmpty(mod.DownloadUrl) && string.IsNullOrEmpty(mod.ModrinthProjectId))
            {
                var destPath = Path.Combine(modsDir, string.IsNullOrEmpty(mod.FileName) ? Path.GetFileName(new Uri(mod.DownloadUrl).AbsolutePath) : mod.FileName);
                if (!File.Exists(destPath))
                {
                    Directory.CreateDirectory(modsDir);
                    using var http = new HttpClient();
                    var bytes = await http.GetByteArrayAsync(mod.DownloadUrl, ct);
                    await File.WriteAllBytesAsync(destPath, bytes, ct);
                }
                // Nuestro mod cambia de nombre con cada versión (verkku-title-<ver>.jar):
                // borrar copias viejas para que Fabric no cargue el jar obsoleto junto al nuevo.
                var wanted = Path.GetFileName(destPath);
                if (wanted.StartsWith("verkku-title-", StringComparison.OrdinalIgnoreCase)
                    && Directory.Exists(modsDir))
                {
                    foreach (var stale in Directory.GetFiles(modsDir, "verkku-title-*.jar"))
                    {
                        if (!string.Equals(Path.GetFileName(stale), wanted, StringComparison.OrdinalIgnoreCase))
                        {
                            try { File.Delete(stale); } catch { }
                        }
                    }
                }
            }
            else if (!string.IsNullOrEmpty(mod.ModrinthProjectId))
            {
                await _modrinth.DownloadModAsync(mod.ModrinthProjectId, mcVersion, "fabric", modsDir, ct);
            }
        }
        progress?.Report(new DownloadProgress(99, "Detectando shaders en otros launchers..."));
        LastDetectedShaders = _shaderDetection.DetectShaders();
        progress?.Report(new DownloadProgress(100, "Instalación completa"));
        return new InstalledGame
        {
            Classpath = files.Classpath.Concat(fabricLibs).ToList(),
            NativesDir = files.NativesDir,
            AssetsDir = files.AssetsDir,
            AssetIndexId = info.AssetIndexId
        };
    }

    private async Task CleanupStaleModsAsync(
        List<ClientModInfo> mods, string modsDir, string mcVersion, CancellationToken ct)
    {
        if (!Directory.Exists(modsDir)) return;

        var expectedFileNames = new HashSet<string>(StringComparer.OrdinalIgnoreCase);

        // Always preserve Iris and Sodium — they are Modrinth-resolved and we don't
        // want them deleted when the Modrinth API is temporarily unreachable.
        foreach (var file in Directory.GetFiles(modsDir, "*.jar"))
        {
            var name = Path.GetFileName(file);
            if (name.StartsWith("iris-", StringComparison.OrdinalIgnoreCase)
                || name.StartsWith("sodium-", StringComparison.OrdinalIgnoreCase))
            {
                expectedFileNames.Add(name);
            }
        }

        foreach (var mod in mods)
        {
            if (!string.IsNullOrEmpty(mod.FileName))
            {
                expectedFileNames.Add(mod.FileName);
            }
            else if (!string.IsNullOrEmpty(mod.ModrinthProjectId))
            {
                try
                {
                    var (_, fileName, _) = await _modrinth.ResolveFileAsync(
                        mod.ModrinthProjectId, mcVersion, "fabric", ct);
                    if (!string.IsNullOrEmpty(fileName))
                        expectedFileNames.Add(fileName);
                }
                catch
                {
                    // Si no se puede resolver, no limpiamos ese mod
                }
            }
            else if (!string.IsNullOrEmpty(mod.DownloadUrl))
            {
                expectedFileNames.Add(Path.GetFileName(new Uri(mod.DownloadUrl).AbsolutePath));
            }
        }

        foreach (var file in Directory.GetFiles(modsDir, "*.jar"))
        {
            if (!expectedFileNames.Contains(Path.GetFileName(file)))
            {
                try { File.Delete(file); } catch { }
            }
        }
    }
}
