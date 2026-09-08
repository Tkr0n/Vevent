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

    public GameInstallerService(MojangMetaService mojang, FabricService fabric, ModrinthService modrinth)
    { _mojang = mojang; _fabric = fabric; _modrinth = modrinth; }

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
        progress?.Report(new DownloadProgress(100, "Instalación completa"));
        return new InstalledGame
        {
            Classpath = files.Classpath.Concat(fabricLibs).ToList(),
            NativesDir = files.NativesDir,
            AssetsDir = files.AssetsDir,
            AssetIndexId = info.AssetIndexId
        };
    }
}
