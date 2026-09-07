using System.IO;

namespace VerkkuCraftLauncher.Services;

public class JavaManager
{
    private static readonly string[] JavaSearchPaths =
    [
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "Java"),
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86), "Java"),
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "Eclipse Adoptium"),
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "Microsoft"),
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Programs", "Eclipse Adoptium")
    ];

    public string? GetJavaPath(string? configuredPath)
    {
        if (!string.IsNullOrEmpty(configuredPath) && File.Exists(configuredPath))
            return configuredPath;

        foreach (var searchPath in JavaSearchPaths)
        {
            if (!Directory.Exists(searchPath))
                continue;

            var javas = Directory.GetFiles(searchPath, "javaw.exe", SearchOption.AllDirectories);
            if (javas.Length > 0)
                return javas[0];
        }

        var systemJava = FindOnPath("javaw.exe");
        if (systemJava != null)
            return systemJava;

        return FindOnPath("java.exe");
    }

    public string? FindJavaInstallation()
    {
        return GetJavaPath(null);
    }

    public async Task EnsureJavaInstalledAsync()
    {
        var javaPath = FindJavaInstallation();
        if (javaPath != null)
            return;

        await Task.Run(() =>
        {
            System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo
            {
                FileName = "https://adoptium.net/",
                UseShellExecute = true
            });
        });

        throw new InvalidOperationException(
            "Java is required to run Minecraft but was not found on your system. " +
            "A browser window has been opened to download Java. " +
            "Please install Java and restart the launcher.");
    }

    private static string? FindOnPath(string fileName)
    {
        var pathEnv = Environment.GetEnvironmentVariable("PATH") ?? string.Empty;
        foreach (var dir in pathEnv.Split(Path.PathSeparator))
        {
            var fullPath = Path.Combine(dir.Trim(), fileName);
            if (File.Exists(fullPath))
                return fullPath;
        }
        return null;
    }
}
