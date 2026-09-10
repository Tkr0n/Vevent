using System.IO;
using System.Text.Json;
using VerkkuCraftLauncher.Helpers;
using VerkkuCraftLauncher.Models;

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

    public async Task<List<ModrinthVersionInfo>> GetVersionsAsync(
        string projectId, string mcVersion, string loader, CancellationToken ct = default)
    {
        var url = $"https://api.modrinth.com/v2/project/{projectId}/version" +
                  $"?game_versions=%5B%22{mcVersion}%22%5D&loaders=%5B%22{loader}%22%5D";
        var json = await _downloader.DownloadStringAsync(url, ct);
        var options = new JsonSerializerOptions { PropertyNameCaseInsensitive = true };
        return JsonSerializer.Deserialize<List<ModrinthVersionInfo>>(json, options) ?? new();
    }

    /// <summary>
    /// Resolves compatible versions for multiple mods, checking required dependencies.
    /// Resolves leaf mods first, then mods that depend on them.
    /// </summary>
    public async Task<Dictionary<string, (string Url, string FileName, string Sha512)>> ResolveCompatibleVersionsAsync(
        Dictionary<string, string> modKeyToModrinthId,
        string mcVersion,
        string loader = "fabric",
        CancellationToken ct = default)
    {
        var result = new Dictionary<string, (string Url, string FileName, string Sha512)>();
        var resolvedVersions = new Dictionary<string, ModrinthVersionInfo>();

        var versionLists = new Dictionary<string, List<ModrinthVersionInfo>>();
        foreach (var kvp in modKeyToModrinthId)
        {
            versionLists[kvp.Value] = await GetVersionsAsync(kvp.Value, mcVersion, loader, ct);
        }

        var resolved = new HashSet<string>();
        var unresolved = new List<string>(modKeyToModrinthId.Keys);

        for (int pass = 0; pass < 5 && unresolved.Count > 0; pass++)
        {
            var stillUnresolved = new List<string>();

            foreach (var modKey in unresolved)
            {
                var modId = modKeyToModrinthId[modKey];
                var versions = versionLists.GetValueOrDefault(modId, new());

                if (versions.Count == 0) continue;

                var requiredDeps = versions.First()
                    .Dependencies
                    .Where(d => d.DependencyType == "required" && d.ProjectId != null)
                    .ToList();

                var unmetDeps = requiredDeps
                    .Where(d => !resolved.Contains(d.ProjectId!))
                    .ToList();

                if (unmetDeps.Count > 0)
                {
                    stillUnresolved.Add(modKey);
                    continue;
                }

                ModrinthVersionInfo? chosen = null;

                foreach (var ver in versions)
                {
                    var deps = ver.Dependencies.Where(d => d.DependencyType == "required").ToList();
                    bool compatible = true;

                    foreach (var dep in deps)
                    {
                        if (dep.ProjectId == null) continue;

                        var depVersion = resolvedVersions.GetValueOrDefault(dep.ProjectId);
                        if (depVersion == null) { compatible = false; break; }

                        if (dep.VersionId != null && depVersion.Id != dep.VersionId)
                        {
                            compatible = false;
                            break;
                        }
                    }

                    if (compatible)
                    {
                        chosen = ver;
                        break;
                    }
                }

                if (chosen == null) continue;

                resolvedVersions[modId] = chosen;
                var primaryFile = chosen.PrimaryFile;
                if (primaryFile == null) continue;

                var sha512 = primaryFile.Hashes.TryGetValue("sha512", out var sha) ? sha : "";

                result[modKey] = (primaryFile.Url, primaryFile.FileName, sha512);
                resolved.Add(modId);
            }

            unresolved = stillUnresolved;
        }

        return result;
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
