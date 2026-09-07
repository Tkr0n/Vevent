using System.Text.Json;
using VerkkuCraftLauncher.Helpers;
using VerkkuCraftLauncher.Models;

namespace VerkkuCraftLauncher.Services;

public class ManifestService
{
    private readonly HttpDownloader _downloader;
    // TEMPORAL prueba local: apunta a la rama hasta el merge a main
    private const string ManifestUrl = "https://raw.githubusercontent.com/Tkr0n/Vevent/feat/standalone-fabric/manifest.json";

    public ManifestService(HttpDownloader downloader)
    {
        _downloader = downloader;
    }

    public async Task<LauncherManifest?> FetchManifestAsync(CancellationToken cancellationToken = default)
    {
        try
        {
            var json = await _downloader.DownloadStringAsync(ManifestUrl, cancellationToken);
            return JsonSerializer.Deserialize<LauncherManifest>(json, new JsonSerializerOptions
            {
                PropertyNameCaseInsensitive = true
            });
        }
        catch (Exception)
        {
            return null;
        }
    }
}
