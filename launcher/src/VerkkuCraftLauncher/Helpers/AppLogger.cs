using System.IO;

namespace VerkkuCraftLauncher.Helpers;

public static class AppLogger
{
    private static readonly string LogPath = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "VerkkuCraft", "launcher.log");

    private static readonly object _lock = new();

    static AppLogger()
    {
        var dir = Path.GetDirectoryName(LogPath)!;
        Directory.CreateDirectory(dir);
        File.WriteAllText(LogPath, $"=== VerkkuCraft Launcher Log {DateTime.Now} ===\n");
    }

    public static void Log(string message)
    {
        var line = $"[{DateTime.Now:HH:mm:ss.fff}] {message}\n";
        lock (_lock)
        {
            File.AppendAllText(LogPath, line);
        }
    }

    public static void LogError(string context, Exception ex)
    {
        Log($"ERROR [{context}]: {ex.GetType().Name}: {ex.Message}");
        if (ex.InnerException != null)
            Log($"  Inner: {ex.InnerException.GetType().Name}: {ex.InnerException.Message}");
        Log($"  StackTrace: {ex.StackTrace}");
    }
}
