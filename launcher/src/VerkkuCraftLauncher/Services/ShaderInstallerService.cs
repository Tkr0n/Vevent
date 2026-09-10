using System.IO;
using VerkkuCraftLauncher.Helpers;
using VerkkuCraftLauncher.Models;

namespace VerkkuCraftLauncher.Services;

public class ShaderInstallerService
{
    private readonly ModrinthService _modrinth;
    private readonly HttpDownloader _downloader;
    private readonly string _baseDir;

    public ShaderInstallerService(ModrinthService modrinth, HttpDownloader downloader, string baseDir)
    {
        _modrinth = modrinth;
        _downloader = downloader;
        _baseDir = baseDir;
    }

    public async Task<ShaderInstallResult> InstallShadersAsync(
        List<DetectedShader> selectedShaders,
        string mcVersion,
        List<ShaderModInfo> shaderMods,
        IProgress<DownloadProgress>? progress = null,
        CancellationToken ct = default)
    {
        var result = new ShaderInstallResult();

        // Create shaderpacks directory
        var shaderpacksDir = Path.Combine(_baseDir, "game", mcVersion, "shaderpacks");
        Directory.CreateDirectory(shaderpacksDir);

        // Copy selected shaders
        foreach (var shader in selectedShaders)
        {
            var destPath = Path.Combine(shaderpacksDir, shader.FileName);

            if (File.Exists(destPath))
            {
                result.Skipped.Add(shader.FileName);
                continue;
            }

            try
            {
                File.Copy(shader.SourcePath, destPath, false);
                result.Installed.Add(shader.FileName);
            }
            catch (Exception ex)
            {
                result.Errors.Add($"{shader.FileName}: {ex.Message}");
            }
        }

        // Check and install Iris/Sodium with dependency-aware resolution
        var modsDir = Path.Combine(_baseDir, "game", mcVersion, "mods");
        Directory.CreateDirectory(modsDir);

        // Separate already-installed mods from those needing download
        var toDownload = new List<ShaderModInfo>();
        foreach (var mod in shaderMods)
        {
            if (string.IsNullOrEmpty(mod.ModrinthProjectId))
            {
                progress?.Report(new DownloadProgress(0, $"{mod.Name}: sin ID de Modrinth, omitido"));
                continue;
            }

            var existingMod = Directory.GetFiles(modsDir, "*.jar")
                .FirstOrDefault(f => Path.GetFileName(f).Contains(mod.Name, StringComparison.OrdinalIgnoreCase));
            if (existingMod != null)
            {
                result.ModsAlreadyInstalled.Add(mod.Name);
                progress?.Report(new DownloadProgress(0, $"{mod.Name} ya instalado: {Path.GetFileName(existingMod)}"));
                continue;
            }

            toDownload.Add(mod);
        }

        if (toDownload.Count > 0)
        {
            progress?.Report(new DownloadProgress(0, "Resolviendo versiones compatibles..."));

            // Build mod key -> modrinth ID mapping for dependency resolution
            var modKeyMap = new Dictionary<string, string>();
            foreach (var mod in toDownload)
            {
                modKeyMap[mod.Name] = mod.ModrinthProjectId;
            }

            try
            {
                // Resolve all mods together with dependency checking
                var resolved = await _modrinth.ResolveCompatibleVersionsAsync(modKeyMap, mcVersion, "fabric", ct);

                foreach (var mod in toDownload)
                {
                    if (resolved.TryGetValue(mod.Name, out var info))
                    {
                        var destPath = Path.Combine(modsDir, info.FileName);
                        if (!File.Exists(destPath))
                        {
                            await _downloader.DownloadFileAsync(info.Url, destPath, null, ct);
                        }
                        result.ModsInstalled.Add(mod.Name);
                        progress?.Report(new DownloadProgress(0, $"{mod.Name} instalado: {info.FileName}"));
                    }
                    else
                    {
                        result.Errors.Add($"{mod.Name}: no se encontro version compatible");
                        progress?.Report(new DownloadProgress(0, $"Error {mod.Name}: version incompatible"));
                    }
                }
            }
            catch (Exception ex)
            {
                foreach (var mod in toDownload)
                {
                    result.Errors.Add($"{mod.Name}: {ex.Message}");
                }
                progress?.Report(new DownloadProgress(0, $"Error resolviendo versiones: {ex.Message}"));
            }
        }

        return result;
    }
}

public class ShaderInstallResult
{
    public List<string> Installed { get; set; } = new();
    public List<string> Skipped { get; set; } = new();
    public List<string> ModsInstalled { get; set; } = new();
    public List<string> ModsAlreadyInstalled { get; set; } = new();
    public List<string> Errors { get; set; } = new();
}
