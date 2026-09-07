using System.IO;
using System.Security.Cryptography;
using VerkkuCraftLauncher.Helpers;
using VerkkuCraftLauncher.Models;

namespace VerkkuCraftLauncher.Services;

public class PluginManager
{
    private readonly HttpDownloader _downloader;
    private const string PluginsDirectory = "plugins";

    public PluginManager(HttpDownloader downloader)
    {
        _downloader = downloader;
    }

    public async Task<bool> DownloadPluginAsync(PluginInfo plugin, string version, IProgress<int>? progress = null, CancellationToken cancellationToken = default)
    {
        var pluginsDir = Path.Combine(PluginsDirectory, version);
        Directory.CreateDirectory(pluginsDir);

        var pluginPath = Path.Combine(pluginsDir, plugin.FileName);
        
        if (File.Exists(pluginPath))
        {
            var existingHash = await ComputeSha256Async(pluginPath, cancellationToken);
            if (existingHash.Equals(plugin.Sha256, StringComparison.OrdinalIgnoreCase))
                return true;
        }

        await _downloader.DownloadFileAsync(plugin.DownloadUrl, pluginPath, progress, cancellationToken);
        return File.Exists(pluginPath);
    }

    public async Task<bool> VerifyPluginAsync(PluginInfo plugin, string version, CancellationToken cancellationToken = default)
    {
        var pluginPath = Path.Combine(PluginsDirectory, version, plugin.FileName);
        if (!File.Exists(pluginPath)) return false;

        var hash = await ComputeSha256Async(pluginPath, cancellationToken);
        return hash.Equals(plugin.Sha256, StringComparison.OrdinalIgnoreCase);
    }

    public async Task<List<PluginInfo>> GetInstalledPluginsAsync(string version, CancellationToken cancellationToken = default)
    {
        var pluginsDir = Path.Combine(PluginsDirectory, version);
        if (!Directory.Exists(pluginsDir)) return new List<PluginInfo>();

        var plugins = new List<PluginInfo>();
        foreach (var file in Directory.GetFiles(pluginsDir, "*.jar"))
        {
            var hash = await ComputeSha256Async(file, cancellationToken);
            plugins.Add(new PluginInfo
            {
                FileName = Path.GetFileName(file),
                Sha256 = hash,
                Name = Path.GetFileNameWithoutExtension(file)
            });
        }
        return plugins;
    }

    private async Task<string> ComputeSha256Async(string filePath, CancellationToken cancellationToken)
    {
        using var sha256 = SHA256.Create();
        using var stream = File.OpenRead(filePath);
        var hash = await sha256.ComputeHashAsync(stream, cancellationToken);
        return BitConverter.ToString(hash).Replace("-", string.Empty);
    }
}
