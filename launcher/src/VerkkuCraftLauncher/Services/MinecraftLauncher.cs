using System.Diagnostics;
using System.IO;
using System.Security.Cryptography;
using System.Text;
using VerkkuCraftLauncher.Models;

namespace VerkkuCraftLauncher.Services;

public class MinecraftLauncher
{
    private readonly JavaManager _javaManager;

    public MinecraftLauncher(JavaManager javaManager)
    {
        _javaManager = javaManager;
    }

    public async Task<bool> LaunchMinecraftAsync(
        InstalledGame game,
        string gameDirectory,
        string username,
        string serverAddress,
        int serverPort,
        string mcVersion,
        IProgress<string>? statusProgress = null,
        CancellationToken cancellationToken = default)
    {
        var javaPath = _javaManager.GetJavaPath(null);
        if (string.IsNullOrEmpty(javaPath))
        {
            statusProgress?.Report("Java no encontrado");
            return false;
        }

        var arguments = BuildJvmArguments(game, gameDirectory, username, serverAddress, serverPort, mcVersion);

        statusProgress?.Report("Iniciando Minecraft...");

        var process = Process.Start(new ProcessStartInfo
        {
            FileName = javaPath,
            Arguments = arguments,
            WorkingDirectory = gameDirectory,
            UseShellExecute = true
        });

        if (process == null)
        {
            statusProgress?.Report("Error al iniciar Minecraft");
            return false;
        }

        statusProgress?.Report("Minecraft iniciado");
        return true;
    }

    private string BuildJvmArguments(InstalledGame game, string gameDir, string username, string server, int port, string mcVersion)
    {
        var uuid = GenerateOfflineUuid(username);
        var cp = string.Join(Path.PathSeparator, game.Classpath);
        var serverArg = string.IsNullOrEmpty(server) ? string.Empty : $"--server {server} --port {port}";
        return $"-Xms512M -Xmx2048M " +
               $"-Djava.library.path=\"{game.NativesDir}\" " +
               $"-cp \"{cp}\" " +
               $"{game.MainClass} " +
                $"--username {username} " +
               $"--version {mcVersion} " +
               $"--gameDir \"{gameDir}\" " +
               $"--assetsDir \"{game.AssetsDir}\" " +
               $"--assetIndex {game.AssetIndexId} " +
               $"--uuid {uuid} " +
               $"--accessToken 0 " +
               $"--userType legacy " +
               serverArg;
    }

    private static string GenerateOfflineUuid(string username)
    {
        var bytes = MD5.HashData(Encoding.UTF8.GetBytes($"OfflinePlayer:{username}"));
        bytes[6] = (byte)((bytes[6] & 0x0F) | 0x30);
        bytes[8] = (byte)((bytes[8] & 0x3F) | 0x80);
        return BitConverter.ToString(bytes).Replace("-", "").ToLowerInvariant();
    }

    public async Task<List<string>> DetectExistingLaunchersAsync()
    {
        var launchers = new List<string>();

        var officialPath = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
            ".minecraft", "launcher", "MinecraftLauncher.exe");
        if (File.Exists(officialPath))
            launchers.Add(officialPath);

        var hmclPath = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.Desktop),
            "HMCL", "HMCL.exe");
        if (File.Exists(hmclPath))
            launchers.Add(hmclPath);

        return launchers;
    }
}
