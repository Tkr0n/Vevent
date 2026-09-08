using System.IO;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Text.Json.Serialization;

namespace VerkkuCraftLauncher.Services;

public class LauncherAccountService
{
    public string? DetectUsername()
    {
        // 1. Launcher oficial de Minecraft
        var official = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
            ".minecraft", "usernamecache.json");
        var name = ReadOfficialUsername(official);
        if (!string.IsNullOrEmpty(name)) return name;

        // 2. HMCL (best-effort: hmcl.json guarda el personaje seleccionado)
        var hmcl = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
            ".minecraft", "hmcl.json");
        return ReadHmclUsername(hmcl);
    }

    private static string? ReadOfficialUsername(string path)
    {
        try
        {
            if (!File.Exists(path)) return null;
            var dictionary = JsonSerializer.Deserialize<Dictionary<string, string>>(File.ReadAllText(path));
            return dictionary?.FirstOrDefault().Value;
        }
        catch { return null; }
    }

    private static string? ReadHmclUsername(string path)
    {
        try
        {
            if (!File.Exists(path)) return null;
            using var doc = JsonDocument.Parse(File.ReadAllText(path));
            if (doc.RootElement.TryGetProperty("selectedCharacter", out var c))
                return c.GetString();
            return null;
        }
        catch { return null; }
    }
}
