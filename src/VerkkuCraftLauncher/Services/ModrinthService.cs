using System.IO;
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
