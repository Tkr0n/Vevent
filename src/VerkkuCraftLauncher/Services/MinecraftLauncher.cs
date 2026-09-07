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
        MinecraftInstance instance,
        string gameDirectory,
        string username,
        IProgress<string>? statusProgress = null,
        CancellationToken cancellationToken = default)
    {
        var javaPath = _javaManager.GetJavaPath(instance.JavaPath);
        if (string.IsNullOrEmpty(javaPath))
        {
            statusProgress?.Report("Java no encontrado");
            return false;
        }

        var arguments = BuildJvmArguments(instance, gameDirectory, username);

        statusProgress?.Report("Iniciando Minecraft...");

        var process = Process.Start(new ProcessStartInfo
        {
            FileName = javaPath,
            Arguments = arguments,
            WorkingDirectory = gameDirectory,
            UseShellExecute = false
        });

        if (process == null)
        {
            statusProgress?.Report("Error al iniciar Minecraft");
            return false;
        }

        statusProgress?.Report("Minecraft iniciado");
        return true;
    }

    private string BuildJvmArguments(MinecraftInstance instance, string gameDirectory, string username)
    {
        var version = instance.MinecraftVersion;
        var clientJar = Path.Combine(gameDirectory, "versions", version, $"{version}.jar");
        var assetIndex = $"mojang-{version}";
        var uuid = GenerateOfflineUuid(username);

        var serverArg = string.IsNullOrEmpty(instance.ServerAddress)
            ? string.Empty
            : $"--server {instance.ServerAddress} --port {instance.ServerPort}";

        return $"-Xms{instance.MinMemoryMb}M -Xmx{instance.MaxMemoryMb}M " +
               $"-Djava.library.path=\"{gameDirectory}/natives\" " +
               $"-cp \"{clientJar}\" " +
               $"net.minecraft.client.main.Main " +
               $"--username {username} " +
               $"--version {version} " +
               $"--gameDir \"{gameDirectory}\" " +
               $"--assets \"{gameDirectory}/assets\" " +
               $"--assetIndex {assetIndex} " +
               $"--uuid {uuid} " +
               $"--accessToken 0 " +
               $"--userType mojang " +
               $"--width 854 --height 480 " +
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
