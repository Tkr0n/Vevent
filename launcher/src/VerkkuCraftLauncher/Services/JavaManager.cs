using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Text.RegularExpressions;
using VerkkuCraftLauncher.Helpers;
using VerkkuCraftLauncher.Models;

namespace VerkkuCraftLauncher.Services;

public class JavaManager
{
    private readonly HttpDownloader? _downloader;

    /// <summary>Minecraft 26.2 requires Java 25+.</summary>
    public const int RequiredJavaMajor = 25;

    /// <summary>
    /// JRE 25 download sources, tried in order. First is the GitHub-hosted
    /// Adoptium asset; second is the Adoptium API which always resolves to the newest GA build.
    /// </summary>
    private static readonly string[] Temurin25JreUrls =
    [
        "https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.4.1%2B1/OpenJDK25U-jre_x64_windows_hotspot_25.0.4.1_1.zip",
        "https://api.adoptium.net/v3/binary/latest/25/ga/windows/x64/jre/hotspot/normal/eclipse"
    ];

    private static readonly string ManagedJavaDir = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "VerkkuCraft", "java25");

    private static readonly string[] JavaSearchPaths =
    [
        ManagedJavaDir,
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "Java"),
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86), "Java"),
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "Eclipse Adoptium"),
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "Microsoft"),
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Programs", "Eclipse Adoptium")
    ];

    public JavaManager() { }

    public JavaManager(HttpDownloader downloader)
    {
        _downloader = downloader;
    }

    public string? GetJavaPath(string? configuredPath)
    {
        return SelectJava(configuredPath, RequiredJavaMajor);
    }

    public string? FindJavaInstallation()
    {
        return GetJavaPath(null);
    }

    /// <summary>
    /// Selects the best installed Java: the configured path if valid,
    /// otherwise prefers major == <paramref name="minMajor"/>, then the lowest major above it.
    /// </summary>
    public string? SelectJava(string? configuredPath, int minMajor = RequiredJavaMajor)
    {
        var candidates = new List<(string Javaw, int Major)>();

        void TryAdd(string? javawPath)
        {
            if (string.IsNullOrEmpty(javawPath) || !File.Exists(javawPath))
                return;
            if (candidates.Any(c => string.Equals(c.Javaw, javawPath, StringComparison.OrdinalIgnoreCase)))
                return;
            var major = GetMajorVersion(javawPath);
            if (major is null)
                return;
            candidates.Add((javawPath, major.Value));
        }

        if (!string.IsNullOrEmpty(configuredPath) && File.Exists(configuredPath))
            TryAdd(NormalizeToJavaw(configuredPath));

        foreach (var dir in JavaSearchPaths)
        {
            if (!Directory.Exists(dir))
                continue;
            string[] found;
            try { found = Directory.GetFiles(dir, "javaw.exe", SearchOption.AllDirectories); }
            catch { continue; }
            foreach (var f in found.OrderBy(f => f, StringComparer.OrdinalIgnoreCase))
                TryAdd(f);
        }

        TryAdd(FindOnPath("javaw.exe"));
        TryAdd(NormalizeToJavaw(FindOnPath("java.exe") ?? string.Empty));

        return candidates
            .Where(c => c.Major >= minMajor)
            .OrderBy(c => c.Major == minMajor ? 0 : 1)
            .ThenBy(c => c.Major)
            .Select(c => c.Javaw)
            .FirstOrDefault();
    }

    public async Task EnsureJavaInstalledAsync(
        IProgress<DownloadProgress>? progress = null,
        CancellationToken cancellationToken = default)
    {
        if (SelectJava(null) != null)
            return;

        if (_downloader == null)
            throw new InvalidOperationException(
                "Se requiere Java 25 o superior para Minecraft 26.2, pero no se encontró ninguna instalación. " +
                "Instala Eclipse Temurin 25 (https://adoptium.net/) y reinicia el launcher.");

        AppLogger.Log("No suitable Java found, downloading Temurin 25 JRE...");
        progress?.Report(new DownloadProgress(0, "Descargando Java 25..."));

        var zipPath = Path.Combine(Path.GetTempPath(), "temurin25-jre.zip");
        var downloaded = false;
        Exception? lastError = null;
        foreach (var url in Temurin25JreUrls)
        {
            try
            {
                AppLogger.Log($"Trying Java 25 source: {url}");
                await _downloader.DownloadFileAsync(
                    url, zipPath,
                    new Progress<int>(p => progress?.Report(new DownloadProgress(p, "Descargando Java 25..."))),
                    cancellationToken);
                downloaded = true;
                break;
            }
            catch (Exception ex)
            {
                lastError = ex;
                AppLogger.Log($"Java 25 source failed, trying next: {ex.Message}");
            }
        }

        if (!downloaded)
            throw new InvalidOperationException(
                "No se pudo descargar Java 25 automáticamente. " +
                "Descárgalo manualmente desde https://adoptium.net/ y reinicia el launcher.",
                lastError);

        progress?.Report(new DownloadProgress(100, "Instalando Java 25..."));
        await Task.Run(() =>
        {
            if (Directory.Exists(ManagedJavaDir))
                Directory.Delete(ManagedJavaDir, true);
            Directory.CreateDirectory(ManagedJavaDir);
            ZipFile.ExtractToDirectory(zipPath, ManagedJavaDir);
        }, cancellationToken);

        try { File.Delete(zipPath); } catch { }

        if (SelectJava(null) == null)
            throw new InvalidOperationException(
                "No se pudo instalar Java 25 automáticamente. " +
                "Descárgalo manualmente desde https://adoptium.net/ y reinicia el launcher.");

        AppLogger.Log("Temurin 25 JRE installed.");
        progress?.Report(new DownloadProgress(100, "Java 25 instalado"));
    }

    private static string NormalizeToJavaw(string path)
    {
        if (string.IsNullOrEmpty(path))
            return path;
        if (path.EndsWith("java.exe", StringComparison.OrdinalIgnoreCase))
        {
            var dir = Path.GetDirectoryName(path);
            if (dir != null)
            {
                var javaw = Path.Combine(dir, "javaw.exe");
                if (File.Exists(javaw))
                    return javaw;
            }
        }
        return path;
    }

    private static int? GetMajorVersion(string javawPath)
    {
        try
        {
            var dir = Path.GetDirectoryName(javawPath);
            var javaExe = dir != null ? Path.Combine(dir, "java.exe") : javawPath;
            if (!File.Exists(javaExe))
                javaExe = javawPath;

            using var process = Process.Start(new ProcessStartInfo
            {
                FileName = javaExe,
                Arguments = "-version",
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                UseShellExecute = false,
                CreateNoWindow = true
            });
            if (process == null)
                return null;

            var output = process.StandardError.ReadToEnd() + process.StandardOutput.ReadToEnd();
            process.WaitForExit(10000);

            // Matches: openjdk version "25.0.4" ..., java version "1.8.0_xxx"
            var m = Regex.Match(output, "version \"(\\d+)(?:\\.(\\d+))?");
            if (!m.Success)
                return null;

            var major = int.Parse(m.Groups[1].Value);
            if (major == 1 && m.Groups[2].Success && int.TryParse(m.Groups[2].Value, out var minor))
                return minor; // 1.8 -> 8
            return major;
        }
        catch
        {
            return null;
        }
    }

    private static string? FindOnPath(string fileName)
    {
        var pathEnv = Environment.GetEnvironmentVariable("PATH") ?? string.Empty;
        foreach (var dir in pathEnv.Split(Path.PathSeparator))
        {
            var trimmed = dir.Trim().Trim('"');
            if (string.IsNullOrEmpty(trimmed))
                continue;
            var fullPath = Path.Combine(trimmed, fileName);
            if (File.Exists(fullPath))
                return fullPath;
        }
        return null;
    }
}
