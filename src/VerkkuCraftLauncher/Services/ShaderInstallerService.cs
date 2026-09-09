using System.IO;
using VerkkuCraftLauncher.Models;

namespace VerkkuCraftLauncher.Services;

public class ShaderInstallerService
{
    private readonly ModrinthService _modrinth;
    private readonly string _baseDir;

    public ShaderInstallerService(ModrinthService modrinth, string baseDir)
    {
        _modrinth = modrinth;
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

        // Check and install Iris/Sodium if needed
        var modsDir = Path.Combine(_baseDir, "game", mcVersion, "mods");
        Directory.CreateDirectory(modsDir);

        foreach (var mod in shaderMods)
        {
            progress?.Report(new DownloadProgress(0, $"Procesando {mod.Name}..."));

            if (string.IsNullOrEmpty(mod.ModrinthProjectId))
            {
                progress?.Report(new DownloadProgress(0, $"{mod.Name}: sin ID de Modrinth, omitido"));
                continue;
            }

            // Check if mod already exists (case-insensitive)
            var existingMod = Directory.GetFiles(modsDir, "*.jar")
                .FirstOrDefault(f => Path.GetFileName(f).Contains(mod.Name, StringComparison.OrdinalIgnoreCase));
            if (existingMod != null)
            {
                result.ModsAlreadyInstalled.Add(mod.Name);
                progress?.Report(new DownloadProgress(0, $"{mod.Name} ya instalado: {Path.GetFileName(existingMod)}"));
                continue;
            }

            try
            {
                progress?.Report(new DownloadProgress(0, $"Descargando {mod.Name}..."));
                var path = await _modrinth.DownloadModAsync(mod.ModrinthProjectId, mcVersion, "fabric", modsDir, ct);
                result.ModsInstalled.Add(mod.Name);
                progress?.Report(new DownloadProgress(0, $"{mod.Name} instalado: {Path.GetFileName(path)}"));
            }
            catch (Exception ex)
            {
                result.Errors.Add($"{mod.Name}: {ex.Message}");
                progress?.Report(new DownloadProgress(0, $"Error {mod.Name}: {ex.Message}"));
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
