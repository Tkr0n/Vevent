using System.IO;
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
        var modsDir = Path.Combine(baseDir, "mods");
        foreach (var mod in mods)
            await _modrinth.DownloadModAsync(mod.ModrinthProjectId, mcVersion, "fabric", modsDir, ct);
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
