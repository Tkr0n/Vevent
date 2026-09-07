using System.Diagnostics;
using System.IO;
using System.Reflection;
using VerkkuCraftLauncher.Helpers;
using VerkkuCraftLauncher.Models;

namespace VerkkuCraftLauncher.Services;

public class UpdateManager
{
    private readonly HttpDownloader _downloader;
    private const string CurrentVersion = "1.0.0";

    public UpdateManager(HttpDownloader downloader)
    {
        _downloader = downloader;
    }

    public string GetCurrentVersion() => CurrentVersion;

    public async Task<bool> CheckForUpdateAsync(string manifestVersion, CancellationToken cancellationToken = default)
    {
        return await Task.Run(() =>
        {
            var current = new Version(CurrentVersion);
            var latest = new Version(manifestVersion);
            return latest > current;
        }, cancellationToken);
    }

    public async Task<bool> UpdateLauncherAsync(string downloadUrl, IProgress<int>? progress = null, CancellationToken cancellationToken = default)
    {
        try
        {
            var currentPath = Assembly.GetExecutingAssembly().Location;
            var tempPath = Path.Combine(Path.GetTempPath(), "VerkkuCraftLauncher-new.exe");
            var backupPath = currentPath + ".bak";

            await _downloader.DownloadFileAsync(downloadUrl, tempPath, progress, cancellationToken);

            File.Copy(currentPath, backupPath, true);
            
            Process.Start(new ProcessStartInfo
            {
                FileName = tempPath,
                Arguments = $"--update {currentPath}",
                UseShellExecute = true,
                Verb = "runas"
            });

            Environment.Exit(0);
            return true;
        }
        catch (Exception)
        {
            return false;
        }
    }
}
