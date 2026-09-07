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
