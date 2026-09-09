using System.IO;
using System.Net.Http;
using System.Text.Json;
using System.Windows.Media.Imaging;
using VerkkuCraftLauncher.Helpers;

namespace VerkkuCraftLauncher.Services;

public class SkinService
{
    private readonly HttpDownloader _downloader;
    private static readonly HttpClient _http = new();

    public SkinService(HttpDownloader downloader)
    {
        _downloader = downloader;
    }

    /// <summary>
    /// Fetches rendered body skin from mc-heads.net for the launcher preview.
    /// </summary>
    public async Task<BitmapImage?> FetchSkinAsync(string username)
    {
        try
        {
            var url = $"https://mc-heads.net/body/{username}/220";
            var bytes = await _http.GetByteArrayAsync(url);

            var image = new BitmapImage();
            using var ms = new MemoryStream(bytes);
            image.BeginInit();
            image.CacheOption = BitmapCacheOption.OnLoad;
            image.StreamSource = ms;
            image.EndInit();
            image.Freeze();
            return image;
        }
        catch (Exception ex)
        {
            AppLogger.LogError("SkinService.FetchSkin", ex);
            return null;
        }
    }

    /// <summary>
    /// Downloads skin PNG and saves it to skinshuffle/presets if not already imported.
    /// </summary>
    public async Task AutoImportSkinAsync(string username, string gameDir)
    {
        try
        {
            var presetsDir = Path.Combine(gameDir, "skinshuffle", "presets");
            var prefix = $"mojang_{username.ToLowerInvariant()}_";

            if (Directory.Exists(presetsDir))
            {
                var existing = Directory.GetFiles(presetsDir, $"{prefix}*.png");
                if (existing.Length > 0)
                {
                    AppLogger.Log($"Skin already imported for {username}, skipping");
                    return;
                }
            }

            var uuid = await GetUuidAsync(username);
            if (uuid == null)
            {
                AppLogger.Log($"AutoImport: UUID not found for {username}");
                return;
            }

            var skinUrl = await GetSkinUrlAsync(uuid);
            if (skinUrl == null)
            {
                AppLogger.Log($"AutoImport: Skin URL not found for {username}");
                return;
            }

            var skinBytes = await _http.GetByteArrayAsync(skinUrl);
            Directory.CreateDirectory(presetsDir);

            var fileName = $"{prefix}{DateTime.Now:yyyyMMdd_HHmmss}.png";
            var destPath = Path.Combine(presetsDir, fileName);
            await File.WriteAllBytesAsync(destPath, skinBytes);

            AppLogger.Log($"Auto-imported skin to: {destPath}");
        }
        catch (Exception ex)
        {
            AppLogger.LogError("SkinService.AutoImportSkin", ex);
        }
    }

    private async Task<string?> GetUuidAsync(string username)
    {
        try
        {
            var json = await _downloader.DownloadStringAsync(
                $"https://api.mojang.com/users/profiles/minecraft/{username}");
            using var doc = JsonDocument.Parse(json);
            if (doc.RootElement.TryGetProperty("id", out var idProp))
                return idProp.GetString();
            return null;
        }
        catch
        {
            return null;
        }
    }

    private async Task<string?> GetSkinUrlAsync(string uuid)
    {
        try
        {
            var json = await _downloader.DownloadStringAsync(
                $"https://sessionserver.mojang.com/session/minecraft/profile/{uuid}");
            using var doc = JsonDocument.Parse(json);

            if (!doc.RootElement.TryGetProperty("properties", out var props))
                return null;

            foreach (var prop in props.EnumerateArray())
            {
                if (prop.TryGetProperty("name", out var name) && name.GetString() == "textures" &&
                    prop.TryGetProperty("value", out var value))
                {
                    var decoded = Convert.FromBase64String(value.GetString()!);
                    var textJson = JsonDocument.Parse(decoded);
                    if (textJson.RootElement.TryGetProperty("textures", out var textures) &&
                        textures.TryGetProperty("SKIN", out var skin) &&
                        skin.TryGetProperty("url", out var url))
                    {
                        return url.GetString();
                    }
                }
            }
            return null;
        }
        catch
        {
            return null;
        }
    }

    public async Task ImportSkinFileAsync(string sourcePath, string gameDir)
    {
        var presetsDir = Path.Combine(gameDir, "skinshuffle", "presets");
        Directory.CreateDirectory(presetsDir);

        var fileName = $"custom_{DateTime.Now:yyyyMMdd_HHmmss}.png";
        var destPath = Path.Combine(presetsDir, fileName);
        await Task.Run(() => File.Copy(sourcePath, destPath, true));
        AppLogger.Log($"Skin imported to: {destPath}");
    }
}
