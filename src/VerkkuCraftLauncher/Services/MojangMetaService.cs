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
