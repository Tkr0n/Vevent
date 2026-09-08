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
    private static readonly string TailscaleInstallPath = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "VerkkuCraft", "Tailscale", "tailscale.exe");

    public VpnManager(HttpDownloader downloader)
    {
        _downloader = downloader;
    }

    public bool IsTailscaleInstalled() => File.Exists(TailscaleInstallPath);

    public bool IsTailscaleConnected()
    {
        try
        {
            var process = Process.Start(new ProcessStartInfo
            {
                FileName = TailscaleInstallPath,
                Arguments = "status",
                RedirectStandardOutput = true,
                UseShellExecute = false,
                CreateNoWindow = true
            });

            var output = process?.StandardOutput.ReadToEnd();
            process?.WaitForExit();

            return output?.Contains("Connected") == true ||
                   output?.Contains("Running") == true;
        }
        catch
        {
            return false;
        }
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
            FileName = TailscaleInstallPath,
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
                FileName = TailscaleInstallPath,
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
