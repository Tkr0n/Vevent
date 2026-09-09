using System.IO;
using VerkkuCraftLauncher.Helpers;
using VerkkuCraftLauncher.Models;

namespace VerkkuCraftLauncher.Services;

public class ServerManager
{
    private readonly HttpDownloader _downloader;
    private const string ServerDirectory = "server";

    public ServerManager(HttpDownloader downloader)
    {
        _downloader = downloader;
    }

    public async Task<bool> DownloadServerJarAsync(string paperUrl, string version, IProgress<int>? progress = null, CancellationToken cancellationToken = default)
    {
        var serverDir = Path.Combine(ServerDirectory, version);
        Directory.CreateDirectory(serverDir);

        var jarPath = Path.Combine(serverDir, "paper.jar");
        await _downloader.DownloadFileAsync(paperUrl, jarPath, progress, cancellationToken);
        return File.Exists(jarPath);
    }

    public async Task ConfigureServerAsync(ServerConfig config, string version, CancellationToken cancellationToken = default)
    {
        var serverDir = Path.Combine(ServerDirectory, version);
        var propertiesPath = Path.Combine(serverDir, "server.properties");

        var properties = $"""
            server-name={config.ServerName}
            motd={config.Motd}
            max-players={config.MaxPlayers}
            server-port={config.ServerPort}
            gamemode={config.GameMode}
            online-mode={config.OnlineMode.ToString().ToLower()}
            allow-flight={config.AllowFlight.ToString().ToLower()}
            spawn-protection={config.SpawnProtection}
            view-distance={config.ViewDistance}
            difficulty={config.Difficulty}
            white-list={config.Whitelist.ToString().ToLower()}
            ip-whitelist={config.IpWhitelist}
            """;

        await File.WriteAllTextAsync(propertiesPath, properties, cancellationToken);
    }

    public async Task AcceptEulaAsync(string version, CancellationToken cancellationToken = default)
    {
        var eulaPath = Path.Combine(ServerDirectory, version, "eula.txt");
        await File.WriteAllTextAsync(eulaPath, "eula=true", cancellationToken);
    }

    public string GetServerPath(string version) =>
        Path.Combine(ServerDirectory, version, "paper.jar");

    public string GetServerDirectory(string version) =>
        Path.Combine(ServerDirectory, version);

    public bool ServerExists(string version) =>
        File.Exists(GetServerPath(version));
}
