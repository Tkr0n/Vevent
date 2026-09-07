using System.IO;
using System.Text.Json;

namespace VerkkuCraftLauncher.Services;

public class LauncherAccountService
{
    public string? DetectUsername()
    {
        // 1. Launcher oficial de Minecraft
        var official = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
            ".minecraft", "launcher_accounts.json");
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
            using var doc = JsonDocument.Parse(File.ReadAllText(path));
            var root = doc.RootElement;
            if (!root.TryGetProperty("activeAccount", out var active)) return null;
            var key = active.GetProperty("localId").GetString();
            if (key == null) return null;
            return root.GetProperty("accounts").GetProperty(key)
                .GetProperty("minecraftProfile").GetProperty("name").GetString();
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
