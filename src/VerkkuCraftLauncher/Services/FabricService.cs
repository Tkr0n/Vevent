using System.IO;
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
