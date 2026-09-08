using System.Diagnostics;
using System.IO;
using System.Threading;
using System.Threading.Tasks;
using VerkkuCraftLauncher.Helpers;
using VerkkuCraftLauncher.Models;

namespace VerkkuCraftLauncher.Services;

public class VpnManager
{
    private readonly HttpDownloader _downloader;
    private const string TailscaleDownloadUrl = "https://pkgs.tailscale.com/stable/tailscale-setup-latest.exe";
    private static readonly string TailscaleCustomPath = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "VerkkuCraft", "Tailscale", "tailscale.exe");

    private static readonly string[] TailscaleSearchPaths =
    [
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "Tailscale", "tailscale.exe"),
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86), "Tailscale", "tailscale.exe"),
        TailscaleCustomPath
    ];

    private static readonly Lazy<string?> TailscalePath = new(() =>
    {
        foreach (var path in TailscaleSearchPaths)
        {
            if (File.Exists(path))
                return path;
        }

        // Check if tailscale is in PATH
        try
        {
            var process = Process.Start(new ProcessStartInfo
            {
                FileName = "tailscale",
                Arguments = "version",
                RedirectStandardOutput = true,
                UseShellExecute = false,
                CreateNoWindow = true
            });
            var output = process?.StandardOutput.ReadToEnd();
            process?.WaitForExit();
            if (process?.ExitCode == 0)
                return "tailscale"; // Use PATH
        }
        catch { }

        return null;
    });

    public VpnManager(HttpDownloader downloader)
    {
        _downloader = downloader;
    }

    public bool IsTailscaleInstalled() => TailscalePath.Value is not null;

    private string GetTailscalePath()
    {
        return TailscalePath.Value ?? TailscaleCustomPath;
    }

    public bool IsTailscaleConnected()
    {
        try
        {
            var process = Process.Start(new ProcessStartInfo
            {
                FileName = GetTailscalePath(),
                Arguments = "status",
                RedirectStandardOutput = true,
                UseShellExecute = false,
                CreateNoWindow = true
            });

            var output = process?.StandardOutput.ReadToEnd();
            process?.WaitForExit();

            if (process?.ExitCode != 0) return false;
            return output?.Contains("100.") == true;
        }
        catch
        {
            return false;
        }
    }

    public async Task OpenTailscaleLoginAsync(CancellationToken cancellationToken = default)
    {
        var process = Process.Start(new ProcessStartInfo
        {
                FileName = GetTailscalePath(),
                Arguments = "login",
                UseShellExecute = true
        });
        await process!.WaitForExitAsync(cancellationToken);
    }

    public async Task InstallTailscaleAsync(IProgress<DownloadProgress>? progress = null, CancellationToken cancellationToken = default)
    {
        var tempPath = Path.Combine(Path.GetTempPath(), "tailscale-setup.exe");
        progress?.Report(new DownloadProgress(0, "Descargando instalador de Tailscale..."));
        await _downloader.DownloadFileAsync(TailscaleDownloadUrl, tempPath,
            new Progress<int>(p => progress?.Report(new DownloadProgress(p, "Descargando instalador de Tailscale..."))),
            cancellationToken);

        progress?.Report(new DownloadProgress(100, "Instalando Tailscale..."));
        var process = Process.Start(new ProcessStartInfo
        {
            FileName = tempPath,
            Arguments = "/quiet /norestart",
            UseShellExecute = true,
            Verb = "runas"
        });

        await process!.WaitForExitAsync(cancellationToken);
        progress?.Report(new DownloadProgress(100, "Tailscale instalado"));
    }

    public async Task ConnectWithAuthKeyAsync(string authKey, CancellationToken cancellationToken = default)
    {
        var process = Process.Start(new ProcessStartInfo
        {
                FileName = GetTailscalePath(),
                Arguments = $"login --authkey={authKey}",
                RedirectStandardOutput = true,
                UseShellExecute = false,
                CreateNoWindow = true
        });

        await process!.WaitForExitAsync(cancellationToken);
    }

    public async Task<string> GetMachineIpAsync(CancellationToken cancellationToken = default)
    {
        try
        {
            var process = Process.Start(new ProcessStartInfo
            {
                FileName = GetTailscalePath(),
                Arguments = "ip -4",
                RedirectStandardOutput = true,
                UseShellExecute = false,
                CreateNoWindow = true
            });

            var output = await process!.StandardOutput.ReadToEndAsync(cancellationToken);
            await process.WaitForExitAsync(cancellationToken);

            return output.Trim();
        }
        catch
        {
            return string.Empty;
        }
    }
}
