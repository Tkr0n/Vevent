using System.IO;
using System.Text.Json;
using VerkkuCraftLauncher.Helpers;
using VerkkuCraftLauncher.Models;

namespace VerkkuCraftLauncher.Services;

public class MojangMetaService
{
    private const string VersionManifestUrl = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private readonly HttpDownloader _downloader;

    public MojangMetaService(HttpDownloader downloader) { _downloader = downloader; }

    public async Task<MojangVersionInfo> ResolveAsync(string mcVersion, CancellationToken ct = default)
    {
        var manifestJson = await _downloader.DownloadStringAsync(VersionManifestUrl, ct);
        using var manifest = JsonDocument.Parse(manifestJson);
        string? versionUrl = null;
        foreach (var v in manifest.RootElement.GetProperty("versions").EnumerateArray())
        {
            if (v.GetProperty("id").GetString() == mcVersion)
            { versionUrl = v.GetProperty("url").GetString(); break; }
        }
        if (versionUrl == null) throw new InvalidOperationException($"Versión {mcVersion} no encontrada en Mojang");
        var versionJson = await _downloader.DownloadStringAsync(versionUrl, ct);
        using var doc = JsonDocument.Parse(versionJson);
        var root = doc.RootElement;
        var info = new MojangVersionInfo
        {
            Id = mcVersion,
            ClientJarUrl = root.GetProperty("downloads").GetProperty("client").GetProperty("url").GetString()!,
            AssetIndexId = root.GetProperty("assetIndex").GetProperty("id").GetString()!,
            AssetIndexUrl = root.GetProperty("assetIndex").GetProperty("url").GetString()!,
        };
        foreach (var lib in root.GetProperty("libraries").EnumerateArray())
        {
            var model = new MojangLibrary { Name = lib.GetProperty("name").GetString()! };
            if (lib.TryGetProperty("rules", out var rules))
                model.Rules = rules.EnumerateArray().Select(r => new MojangRule
                {
                    Action = r.GetProperty("action").GetString()!,
                    Os = r.TryGetProperty("os", out var os)
                        ? os.EnumerateObject().ToDictionary(p => p.Name, p => p.Value.GetString()!)
                        : null
                }).ToList();
            if (lib.TryGetProperty("natives", out var natives))
                model.Natives = natives.EnumerateObject().ToDictionary(p => p.Name, p => p.Value.GetString()!);
            if (lib.TryGetProperty("downloads", out var dl))
            {
                if (dl.TryGetProperty("artifact", out var art))
                    model.Downloads_Artifact = ParseArtifact(art);
                if (dl.TryGetProperty("classifiers", out var cls))
                    model.Downloads_Classifiers = cls.EnumerateObject()
                        .ToDictionary(p => p.Name, p => ParseArtifact(p.Value));
            }
            info.Libraries.Add(model);
        }
        return info;
    }

    private static MojangArtifact ParseArtifact(JsonElement e) => new()
    {
        Path = e.GetProperty("path").GetString()!,
        Url = e.GetProperty("url").GetString()!,
        Sha1 = e.GetProperty("sha1").GetString()!,
        Size = e.GetProperty("size").GetInt64()
    };

    public async Task<GameFiles> DownloadGameAsync(
        MojangVersionInfo info, string baseDir,
        IProgress<int>? progress = null, CancellationToken ct = default)
    {
        var versionDir = Path.Combine(baseDir, "versions", info.Id);
        var libsDir = Path.Combine(baseDir, "libraries");
        var nativesDir = Path.Combine(versionDir, "natives");
        var assetsDir = Path.Combine(baseDir, "assets");
        Directory.CreateDirectory(versionDir);
        Directory.CreateDirectory(libsDir);
        Directory.CreateDirectory(nativesDir);

        var clientJar = Path.Combine(versionDir, $"{info.Id}.jar");
        if (!File.Exists(clientJar))
            await _downloader.DownloadFileAsync(info.ClientJarUrl, clientJar, null, ct);

        var classpath = new List<string> { clientJar };
        var allowed = info.Libraries.Where(IsAllowedOnWindows).ToList();
        int done = 0;
        foreach (var lib in allowed)
        {
            string? classifier = null;
            if (lib.Natives != null && lib.Natives.TryGetValue("windows", out var nat))
                classifier = nat.Replace("${arch}", "64");
            if (classifier != null && lib.Downloads_Classifiers != null &&
                lib.Downloads_Classifiers.TryGetValue(classifier, out var natArt))
            {
                var tmp = Path.Combine(Path.GetTempPath(), Path.GetFileName(natArt.Path));
                if (!File.Exists(tmp)) await _downloader.DownloadFileAsync(natArt.Url, tmp, null, ct);
                System.IO.Compression.ZipFile.ExtractToDirectory(tmp, nativesDir, overwriteFiles: true);
            }
            else if (lib.Downloads_Artifact != null)
            {
                var dest = Path.Combine(libsDir, lib.Downloads_Artifact.Path.Replace('/', Path.DirectorySeparatorChar));
                Directory.CreateDirectory(Path.GetDirectoryName(dest)!);
                if (!File.Exists(dest))
                    await _downloader.DownloadFileAsync(lib.Downloads_Artifact.Url, dest, null, ct);
                classpath.Add(dest);
            }
            done++;
            progress?.Report(done * 100 / allowed.Count);
        }

        // Assets: índice + objetos referenciados
        var indexPath = Path.Combine(assetsDir, "indexes", $"{info.AssetIndexId}.json");
        Directory.CreateDirectory(Path.GetDirectoryName(indexPath)!);
        if (!File.Exists(indexPath))
            await _downloader.DownloadFileAsync(info.AssetIndexUrl, indexPath, null, ct);
        using var index = JsonDocument.Parse(await File.ReadAllTextAsync(indexPath, ct));
        foreach (var obj in index.RootElement.GetProperty("objects").EnumerateObject())
        {
            var hash = obj.Value.GetProperty("hash").GetString()!;
            var dest = Path.Combine(assetsDir, "objects", hash[..2], hash);
            if (File.Exists(dest)) continue;
            Directory.CreateDirectory(Path.GetDirectoryName(dest)!);
            await _downloader.DownloadFileAsync(
                $"https://resources.download.minecraft.net/{hash[..2]}/{hash}", dest, null, ct);
        }

        return new GameFiles { Classpath = classpath, NativesDir = nativesDir, AssetsDir = assetsDir };
    }

    public static bool IsAllowedOnWindows(MojangLibrary lib)
    {
        if (lib.Rules == null || lib.Rules.Count == 0) return true;
        var allowed = false;
        foreach (var rule in lib.Rules)
        {
            var applies = rule.Os == null ||
                (rule.Os.TryGetValue("name", out var osName) && osName == "windows");
            if (!applies) continue;
            allowed = rule.Action == "allow";
        }
        return allowed;
    }
}

public class GameFiles
{
    public List<string> Classpath { get; set; } = new();
    public string NativesDir { get; set; } = string.Empty;
    public string AssetsDir { get; set; } = string.Empty;
}
