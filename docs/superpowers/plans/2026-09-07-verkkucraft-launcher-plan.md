# VerkkuCraft Launcher Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Windows-only C# WPF launcher that auto-installs Java, downloads server files/plugins, configures Tailscale VPN, and launches Minecraft — all with zero user configuration.

**Architecture:** .NET 8 WPF app with MVVM pattern. Services handle each concern (Java, Server, Plugins, VPN, Updates, Minecraft). Manifest from GitHub Releases drives all versions. Single .exe distribution via self-contained publish.

**Tech Stack:** C# 12, .NET 8, WPF, WPF-UI (Fluent Design), SharpCompress, System.Text.Json

---

## File Structure

```
VerkkuCraftLauncher/
├── VerkkuCraftLauncher.sln
├── src/
│   └── VerkkuCraftLauncher/
│       ├── VerkkuCraftLauncher.csproj
│       ├── App.xaml
│       ├── App.xaml.cs
│       ├── MainWindow.xaml
│       ├── MainWindow.xaml.cs
│       ├── ViewModels/
│       │   ├── MainViewModel.cs
│       │   └── ProgressViewModel.cs
│       ├── Views/
│       │   ├── WelcomeView.xaml
│       │   ├── WelcomeView.xaml.cs
│       │   ├── InstallView.xaml
│       │   ├── InstallView.xaml.cs
│       │   ├── PlayView.xaml
│       │   └── PlayView.xaml.cs
│       ├── Services/
│       │   ├── ILauncherService.cs
│       │   ├── JavaManager.cs
│       │   ├── ServerManager.cs
│       │   ├── PluginManager.cs
│       │   ├── VpnManager.cs
│       │   ├── UpdateManager.cs
│       │   ├── MinecraftLauncher.cs
│       │   └── ManifestService.cs
│       ├── Models/
│       │   ├── LauncherManifest.cs
│       │   ├── PluginInfo.cs
│       │   ├── ConfigFile.cs
│       │   └── LauncherState.cs
│       ├── Helpers/
│       │   ├── HttpDownloader.cs
│       │   └── FileExtractor.cs
│       └── Assets/
│           └── logo.png
├── publish.bat
└── README.md
```

---

### Task 1: Create Project Structure

**Files:**
- Create: `VerkkuCraftLauncher/VerkkuCraftLauncher.sln`
- Create: `VerkkuCraftLauncher/src/VerkkuCraftLauncher/VerkkuCraftLauncher.csproj`
- Create: `VerkkuCraftLauncher/src/VerkkuCraftLauncher/App.xaml`
- Create: `VerkkuCraftLauncher/src/VerkkuCraftLauncher/App.xaml.cs`
- Create: `VerkkuCraftLauncher/src/VerkkuCraftLauncher/MainWindow.xaml`
- Create: `VerkkuCraftLauncher/src/VerkkuCraftLauncher/MainWindow.xaml.cs`

- [ ] **Step 1: Create solution file**

Run from `VerkkuCraftLauncher/`:
```bash
dotnet new sln -n VerkkuCraftLauncher
```

- [ ] **Step 2: Create WPF project**

```bash
cd VerkkuCraftLauncher
dotnet new wpf -n VerkkuCraftLauncher -o src/VerkkuCraftLauncher
dotnet sln add src/VerkkuCraftLauncher/VerkkuCraftLauncher.csproj
```

- [ ] **Step 3: Edit .csproj with dependencies**

Edit `src/VerkkuCraftLauncher/VerkkuCraftLauncher.csproj`:

```xml
<Project Sdk="Microsoft.NET.Sdk">

  <PropertyGroup>
    <OutputType>WinExe</OutputType>
    <TargetFramework>net8.0-windows</TargetFramework>
    <Nullable>enable</Nullable>
    <ImplicitUsings>enable</ImplicitUsings>
    <UseWPF>true</UseWPF>
    <ApplicationIcon>Assets\logo.ico</ApplicationIcon>
    <AssemblyName>VerkkuCraftLauncher</AssemblyName>
    <RootNamespace>VerkkuCraftLauncher</RootNamespace>
  </PropertyGroup>

  <ItemGroup>
    <PackageReference Include="WPF-UI" Version="3.0.5" />
    <PackageReference Include="SharpCompress" Version="0.38.0" />
    <PackageReference Include="CommunityToolkit.Mvvm" Version="8.4.0" />
  </ItemGroup>

</Project>
```

- [ ] **Step 4: Create folder structure**

```bash
cd src/VerkkuCraftLauncher
mkdir ViewModels Views Services Models Helpers Assets
```

- [ ] **Step 5: Verify build compiles**

```bash
dotnet build
```
Expected: Build succeeded

- [ ] **Step 6: Commit**

```bash
git add VerkkuCraftLauncher/
git commit -m "feat: initialize WPF project with dependencies"
```

---

### Task 2: Define Models

**Files:**
- Create: `src/VerkkuCraftLauncher/Models/LauncherManifest.cs`
- Create: `src/VerkkuCraftLauncher/Models/PluginInfo.cs`
- Create: `src/VerkkuCraftLauncher/Models/ConfigFile.cs`
- Create: `src/VerkkuCraftLauncher/Models/LauncherState.cs`

- [ ] **Step 1: Create LauncherManifest.cs**

```csharp
namespace VerkkuCraftLauncher.Models;

public class LauncherManifest
{
    public int ManifestVersion { get; set; } = 1;
    public string LauncherVersion { get; set; } = "1.0.0";
    public ServerInfo Server { get; set; } = new();
    public List<PluginInfo> Plugins { get; set; } = new();
    public List<ConfigFile> ConfigFiles { get; set; } = new();
    public VpnInfo Vpn { get; set; } = new();
    public MinecraftInfo Minecraft { get; set; } = new();
}

public class ServerInfo
{
    public string Name { get; set; } = "VerkkuCraft";
    public string MinecraftVersion { get; set; } = "1.20.4";
    public int PaperBuild { get; set; }
    public string PaperUrl { get; set; } = "";
}

public class VpnInfo
{
    public string TailscaleAuthKeyUrl { get; set; } = "";
    public string NetworkName { get; set; } = "VerkkuCraft";
}

public class MinecraftInfo
{
    public string HmclUrl { get; set; } = "";
    public string Version { get; set; } = "1.20.4";
}
```

- [ ] **Step 2: Create PluginInfo.cs**

```csharp
namespace VerkkuCraftLauncher.Models;

public class PluginInfo
{
    public string Name { get; set; } = "";
    public string Version { get; set; } = "";
    public string Filename { get; set; } = "";
    public string DownloadUrl { get; set; } = "";
}
```

- [ ] **Step 3: Create ConfigFile.cs**

```csharp
namespace VerkkuCraftLauncher.Models;

public class ConfigFile
{
    public string Name { get; set; } = "";
    public string DownloadUrl { get; set; } = "";
}
```

- [ ] **Step 4: Create LauncherState.cs**

```csharp
namespace VerkkuCraftLauncher.Models;

public enum LauncherState
{
    Checking,
    Installing,
    Updating,
    ConnectingVpn,
    Ready,
    Launching,
    Error
}
```

- [ ] **Step 5: Verify build**

```bash
dotnet build
```
Expected: Build succeeded

- [ ] **Step 6: Commit**

```bash
git add src/VerkkuCraftLauncher/Models/
git commit -m "feat: add data models for manifest and launcher state"
```

---

### Task 3: Create HttpDownloader Helper

**Files:**
- Create: `src/VerkkuCraftLauncher/Helpers/HttpDownloader.cs`

- [ ] **Step 1: Create HttpDownloader.cs**

```csharp
namespace VerkkuCraftLauncher.Helpers;

public class DownloadProgress : EventArgs
{
    public long BytesReceived { get; set; }
    public long TotalBytes { get; set; }
    public double Percentage => TotalBytes > 0 ? (double)BytesReceived / TotalBytes * 100 : 0;
    public string FileName { get; set; } = "";
}

public class HttpDownloader
{
    private readonly HttpClient _httpClient;

    public HttpDownloader()
    {
        _httpClient = new HttpClient();
        _httpClient.DefaultRequestHeaders.UserAgent.ParseAdd("VerkkuCraftLauncher/1.0");
    }

    public event EventHandler<DownloadProgress>? ProgressChanged;

    public async Task DownloadFileAsync(string url, string destinationPath, CancellationToken ct = default)
    {
        var directory = Path.GetDirectoryName(destinationPath);
        if (!string.IsNullOrEmpty(directory))
            Directory.CreateDirectory(directory);

        using var response = await _httpClient.GetAsync(url, HttpCompletionOption.ResponseHeadersRead, ct);
        response.EnsureSuccessStatusCode();

        var totalBytes = response.Content.Headers.ContentLength ?? -1;

        await using var contentStream = await response.Content.ReadAsStreamAsync(ct);
        await using var fileStream = new FileStream(destinationPath, FileMode.Create, FileAccess.Write, FileShare.None, 8192, true);

        var buffer = new byte[8192];
        long totalRead = 0;
        int bytesRead;

        while ((bytesRead = await contentStream.ReadAsync(buffer, ct)) > 0)
        {
            await fileStream.WriteAsync(buffer.AsMemory(0, bytesRead), ct);
            totalRead += bytesRead;

            ProgressChanged?.Invoke(this, new DownloadProgress
            {
                BytesReceived = totalRead,
                TotalBytes = totalBytes,
                FileName = Path.GetFileName(destinationPath)
            });
        }
    }

    public async Task<string> DownloadStringAsync(string url, CancellationToken ct = default)
    {
        return await _httpClient.GetStringAsync(url, ct);
    }

    public async Task<bool> CheckUrlExistsAsync(string url, CancellationToken ct = default)
    {
        try
        {
            using var request = new HttpRequestMessage(HttpMethod.Head, url);
            using var response = await _httpClient.SendAsync(request, ct);
            return response.IsSuccessStatusCode;
        }
        catch
        {
            return false;
        }
    }
}
```

- [ ] **Step 2: Verify build**

```bash
dotnet build
```
Expected: Build succeeded

- [ ] **Step 3: Commit**

```bash
git add src/VerkkuCraftLauncher/Helpers/HttpDownloader.cs
git commit -m "feat: add HTTP downloader with progress reporting"
```

---

### Task 4: Create FileExtractor Helper

**Files:**
- Create: `src/VerkkuCraftLauncher/Helpers/FileExtractor.cs`

- [ ] **Step 1: Create FileExtractor.cs**

```csharp
using SharpCompress.Archives;
using SharpCompress.Common;

namespace VerkkuCraftLauncher.Helpers;

public class FileExtractor
{
    public void ExtractArchive(string archivePath, string destinationPath, CancellationToken ct = default)
    {
        Directory.CreateDirectory(destinationPath);

        using var archive = ArchiveFactory.Open(archivePath);
        foreach (var entry in archive.Entries.Where(e => !e.IsDirectory))
        {
            ct.ThrowIfCancellationRequested();

            var destinationFileName = Path.Combine(destinationPath, entry.Key ?? entry.ToString());
            var destinationDir = Path.GetDirectoryName(destinationFileName);
            if (!string.IsNullOrEmpty(destinationDir))
                Directory.CreateDirectory(destinationDir);

            using var entryStream = entry.OpenEntryStream();
            using var fileStream = File.Create(destinationFileName);
            entryStream.CopyTo(fileStream);
        }
    }

    public async Task ExtractArchiveAsync(string archivePath, string destinationPath, CancellationToken ct = default)
    {
        await Task.Run(() => ExtractArchive(archivePath, destinationPath, ct), ct);
    }
}
```

- [ ] **Step 2: Verify build**

```bash
dotnet build
```
Expected: Build succeeded

- [ ] **Step 3: Commit**

```bash
git add src/VerkkuCraftLauncher/Helpers/FileExtractor.cs
git commit -m "feat: add archive extractor using SharpCompress"
```

---

### Task 5: Create ManifestService

**Files:**
- Create: `src/VerkkuCraftLauncher/Services/ManifestService.cs`

- [ ] **Step 1: Create ManifestService.cs**

```csharp
using System.Text.Json;
using VerkkuCraftLauncher.Helpers;
using VerkkuCraftLauncher.Models;

namespace VerkkuCraftLauncher.Services;

public class ManifestService
{
    private readonly HttpDownloader _downloader;
    private readonly string _localManifestPath;
    private const string ManifestUrl = "https://api.github.com/repos/{owner}/{repo}/releases/latest";

    public ManifestService(string localDataPath)
    {
        _downloader = new HttpDownloader();
        _localManifestPath = Path.Combine(localDataPath, "manifest.local.json");
    }

    public async Task<LauncherManifest> GetManifestAsync(string manifestUrl, CancellationToken ct = default)
    {
        var json = await _downloader.DownloadStringAsync(manifestUrl, ct);
        return JsonSerializer.Deserialize<LauncherManifest>(json, new JsonSerializerOptions
        {
            PropertyNameCaseInsensitive = true
        }) ?? new LauncherManifest();
    }

    public LauncherManifest? GetLocalManifest()
    {
        if (!File.Exists(_localManifestPath))
            return null;

        var json = File.ReadAllText(_localManifestPath);
        return JsonSerializer.Deserialize<LauncherManifest>(json, new JsonSerializerOptions
        {
            PropertyNameCaseInsensitive = true
        });
    }

    public void SaveLocalManifest(LauncherManifest manifest)
    {
        var directory = Path.GetDirectoryName(_localManifestPath);
        if (!string.IsNullOrEmpty(directory))
            Directory.CreateDirectory(directory);

        var json = JsonSerializer.Serialize(manifest, new JsonSerializerOptions { WriteIndented = true });
        File.WriteAllText(_localManifestPath, json);
    }

    public bool HasUpdates(LauncherManifest remote, LauncherManifest local)
    {
        if (remote.LauncherVersion != local.LauncherVersion)
            return true;

        if (remote.Server.PaperBuild != local.Server.PaperBuild)
            return true;

        foreach (var remotePlugin in remote.Plugins)
        {
            var localPlugin = local.Plugins.FirstOrDefault(p => p.Name == remotePlugin.Name);
            if (localPlugin == null || localPlugin.Version != remotePlugin.Version)
                return true;
        }

        return false;
    }
}
```

- [ ] **Step 2: Verify build**

```bash
dotnet build
```
Expected: Build succeeded

- [ ] **Step 3: Commit**

```bash
git add src/VerkkuCraftLauncher/Services/ManifestService.cs
git commit -m "feat: add manifest service for remote version checking"
```

---

### Task 6: Create JavaManager Service

**Files:**
- Create: `src/VerkkuCraftLauncher/Services/JavaManager.cs`

- [ ] **Step 1: Create JavaManager.cs**

```csharp
using System.Diagnostics;
using System.Runtime.InteropServices;
using VerkkuCraftLauncher.Helpers;
using VerkkuCraftLauncher.Models;

namespace VerkkuCraftLauncher.Services;

public class JavaManager
{
    private readonly string _javaPath;
    private readonly HttpDownloader _downloader;
    private const string AdoptiumUrl = "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse";

    public JavaManager(string localDataPath)
    {
        _javaPath = Path.Combine(localDataPath, "java");
        _downloader = new HttpDownloader();
    }

    public event EventHandler<DownloadProgress>? ProgressChanged
    {
        add => _downloader.ProgressChanged += value;
        remove => _downloader.ProgressChanged -= value;
    }

    public string? FindJavaInstallation()
    {
        // Check local installation first
        var localJava = FindJavaInDirectory(_javaPath);
        if (localJava != null) return localJava;

        // Check system PATH
        var pathJava = FindJavaInPath();
        if (pathJava != null) return pathJava;

        // Check common install locations
        var commonLocations = new[]
        {
            @"C:\Program Files\Eclipse Adoptium",
            @"C:\Program Files\Java",
            @"C:\Program Files (x86)\Java"
        };

        foreach (var location in commonLocations)
        {
            if (Directory.Exists(location))
            {
                var found = FindJavaInDirectory(location);
                if (found != null) return found;
            }
        }

        return null;
    }

    public async Task<string> InstallJavaAsync(CancellationToken ct = default)
    {
        var msiPath = Path.Combine(_javaPath, "adoptium.msi");
        var installDir = Path.Combine(_javaPath, "jdk");

        Directory.CreateDirectory(_javaPath);

        // Download Adoptium JDK
        await _downloader.DownloadFileAsync(AdoptiumUrl, msiPath, ct);

        // Install silently
        var process = Process.Start(new ProcessStartInfo
        {
            FileName = "msiexec",
            Arguments = $"/i \"{msiPath}\" ADDLOCAL=FeatureMain,FeatureJarFileRunWith,FeatureJavaHome INSTALLDIR=\"{installDir}\" /quiet /norestart",
            UseShellExecute = false,
            CreateNoWindow = true
        });

        if (process != null)
        {
            await process.WaitForExitAsync(ct);
        }

        // Find java.exe in installed directory
        var javaExe = FindJavaInDirectory(_javaPath);
        if (javaExe == null)
            throw new InvalidOperationException("Java installation completed but java.exe not found");

        // Cleanup MSI
        if (File.Exists(msiPath))
            File.Delete(msiPath);

        return javaExe;
    }

    public async Task<bool> VerifyJavaVersionAsync(string javaPath, CancellationToken ct = default)
    {
        try
        {
            var process = Process.Start(new ProcessStartInfo
            {
                FileName = javaPath,
                Arguments = "-version",
                RedirectStandardError = true,
                UseShellExecute = false,
                CreateNoWindow = true
            });

            if (process == null) return false;

            var output = await process.StandardError.ReadToEndAsync(ct);
            await process.WaitForExitAsync(ct);

            return output.Contains("17.") || output.Contains("18.") || output.Contains("19.") ||
                   output.Contains("20.") || output.Contains("21.") || output.Contains("22.");
        }
        catch
        {
            return false;
        }
    }

    private string? FindJavaInDirectory(string directory)
    {
        if (!Directory.Exists(directory)) return null;

        foreach (var dir in Directory.GetDirectories(directory, "*", SearchOption.AllDirectories))
        {
            var javaExe = Path.Combine(dir, "bin", "java.exe");
            if (File.Exists(javaExe)) return javaExe;
        }

        return null;
    }

    private string? FindJavaInPath()
    {
        var pathEnv = Environment.GetEnvironmentVariable("PATH") ?? "";
        foreach (var path in pathEnv.Split(Path.PathSeparator))
        {
            var javaExe = Path.Combine(path.Trim(), "java.exe");
            if (File.Exists(javaExe)) return javaExe;
        }
        return null;
    }
}
```

- [ ] **Step 2: Verify build**

```bash
dotnet build
```
Expected: Build succeeded

- [ ] **Step 3: Commit**

```bash
git add src/VerkkuCraftLauncher/Services/JavaManager.cs
git commit -m "feat: add Java manager with auto-install from Adoptium"
```

---

### Task 7: Create ServerManager Service

**Files:**
- Create: `src/VerkkuCraftLauncher/Services/ServerManager.cs`

- [ ] **Step 1: Create ServerManager.cs**

```csharp
using VerkkuCraftLauncher.Helpers;
using VerkkuCraftLauncher.Models;

namespace VerkkuCraftLauncher.Services;

public class ServerManager
{
    private readonly string _serverPath;
    private readonly HttpDownloader _downloader;

    public ServerManager(string localDataPath)
    {
        _serverPath = Path.Combine(localDataPath, "server");
        _downloader = new HttpDownloader();
    }

    public event EventHandler<DownloadProgress>? ProgressChanged
    {
        add => _downloader.ProgressChanged += value;
        remove => _downloader.ProgressChanged -= value;
    }

    public string ServerPath => _serverPath;
    public string PaperJarPath => Path.Combine(_serverPath, "paper.jar");

    public bool IsServerInstalled()
    {
        return File.Exists(PaperJarPath);
    }

    public async Task InstallServerAsync(ServerInfo serverInfo, CancellationToken ct = default)
    {
        Directory.CreateDirectory(_serverPath);

        // Download Paper
        await _downloader.DownloadFileAsync(serverInfo.PaperUrl, PaperJarPath, ct);
    }

    public async Task UpdateServerAsync(ServerInfo serverInfo, CancellationToken ct = default)
    {
        if (!IsServerInstalled())
        {
            await InstallServerAsync(serverInfo, ct);
            return;
        }

        // Download new version, replace old
        var tempPath = PaperJarPath + ".tmp";
        await _downloader.DownloadFileAsync(serverInfo.PaperUrl, tempPath, ct);

        File.Delete(PaperJarPath);
        File.Move(tempPath, PaperJarPath);
    }

    public async Task DownloadConfigFilesAsync(List<ConfigFile> configFiles, CancellationToken ct = default)
    {
        foreach (var configFile in configFiles)
        {
            var destinationPath = Path.Combine(_serverPath, configFile.Name);
            await _downloader.DownloadFileAsync(configFile.DownloadUrl, destinationPath, ct);
        }
    }

    public void CreateRunScript(string javaPath)
    {
        var runBat = Path.Combine(_serverPath, "run.bat");
        var content = $"\"{javaPath}\" -Xmx2G -Xms2G -jar paper.jar nogui";
        File.WriteAllText(runBat, content);
    }
}
```

- [ ] **Step 2: Verify build**

```bash
dotnet build
```
Expected: Build succeeded

- [ ] **Step 3: Commit**

```bash
git add src/VerkkuCraftLauncher/Services/ServerManager.cs
git commit -m "feat: add server manager for Paper download and config"
```

---

### Task 8: Create PluginManager Service

**Files:**
- Create: `src/VerkkuCraftLauncher/Services/PluginManager.cs`

- [ ] **Step 1: Create PluginManager.cs**

```csharp
using VerkkuCraftLauncher.Helpers;
using VerkkuCraftLauncher.Models;

namespace VerkkuCraftLauncher.Services;

public class PluginManager
{
    private readonly string _pluginsPath;
    private readonly HttpDownloader _downloader;

    public PluginManager(string localDataPath)
    {
        _pluginsPath = Path.Combine(localDataPath, "server", "plugins");
        _downloader = new HttpDownloader();
    }

    public event EventHandler<DownloadProgress>? ProgressChanged
    {
        add => _downloader.ProgressChanged += value;
        remove => _downloader.ProgressChanged -= value;
    }

    public bool ArePluginsInstalled(List<PluginInfo> plugins)
    {
        return plugins.All(p =>
        {
            var path = Path.Combine(_pluginsPath, p.Filename);
            return File.Exists(path);
        });
    }

    public async Task InstallPluginsAsync(List<PluginInfo> plugins, CancellationToken ct = default)
    {
        Directory.CreateDirectory(_pluginsPath);

        foreach (var plugin in plugins)
        {
            var destinationPath = Path.Combine(_pluginsPath, plugin.Filename);
            await _downloader.DownloadFileAsync(plugin.DownloadUrl, destinationPath, ct);
        }
    }

    public async Task UpdatePluginsAsync(List<PluginInfo> plugins, LauncherManifest? localManifest, CancellationToken ct = default)
    {
        Directory.CreateDirectory(_pluginsPath);

        foreach (var plugin in plugins)
        {
            var localPlugin = localManifest?.Plugins.FirstOrDefault(p => p.Name == plugin.Name);

            if (localPlugin == null || localPlugin.Version != plugin.Version)
            {
                var destinationPath = Path.Combine(_pluginsPath, plugin.Filename);

                // Delete old version if exists
                if (File.Exists(destinationPath))
                    File.Delete(destinationPath);

                await _downloader.DownloadFileAsync(plugin.DownloadUrl, destinationPath, ct);
            }
        }
    }
}
```

- [ ] **Step 2: Verify build**

```bash
dotnet build
```
Expected: Build succeeded

- [ ] **Step 3: Commit**

```bash
git add src/VerkkuCraftLauncher/Services/PluginManager.cs
git commit -m "feat: add plugin manager for download and updates"
```

---

### Task 9: Create VpnManager Service

**Files:**
- Create: `src/VerkkuCraftLauncher/Services/VpnManager.cs`

- [ ] **Step 1: Create VpnManager.cs**

```csharp
using System.Diagnostics;
using VerkkuCraftLauncher.Helpers;
using VerkkuCraftLauncher.Models;

namespace VerkkuCraftLauncher.Services;

public class VpnManager
{
    private readonly string _vpnPath;
    private readonly HttpDownloader _downloader;
    private const string TailscaleDownloadUrl = "https://pkgs.tailscale.com/stable/tailscale-setup-latest.exe";

    public VpnManager(string localDataPath)
    {
        _vpnPath = Path.Combine(localDataPath, "tailscale");
        _downloader = new HttpDownloader();
    }

    public event EventHandler<DownloadProgress>? ProgressChanged
    {
        add => _downloader.ProgressChanged += value;
        remove => _downloader.ProgressChanged -= value;
    }

    public string TailscalePath => Path.Combine(_vpnPath, "tailscale.exe");

    public bool IsTailscaleInstalled()
    {
        return File.Exists(TailscalePath) || IsTailscaleInPath();
    }

    public async Task InstallTailscaleAsync(CancellationToken ct = default)
    {
        Directory.CreateDirectory(_vpnPath);

        var installerPath = Path.Combine(_vpnPath, "tailscale-setup.exe");
        await _downloader.DownloadFileAsync(TailscaleDownloadUrl, installerPath, ct);

        // Install silently
        var process = Process.Start(new ProcessStartInfo
        {
            FileName = installerPath,
            Arguments = "/S",
            UseShellExecute = false,
            CreateNoWindow = true
        });

        if (process != null)
        {
            await process.WaitForExitAsync(ct);
        }

        // Cleanup installer
        if (File.Exists(installerPath))
            File.Delete(installerPath);
    }

    public async Task ConnectAsync(string authKey, CancellationToken ct = default)
    {
        var tailscale = GetTailscaleExecutable();
        if (tailscale == null)
            throw new InvalidOperationException("Tailscale not found");

        var process = Process.Start(new ProcessStartInfo
        {
            FileName = tailscale,
            Arguments = $"login --authkey={authKey}",
            UseShellExecute = false,
            CreateNoWindow = true,
            RedirectStandardOutput = true,
            RedirectStandardError = true
        });

        if (process != null)
        {
            await process.WaitForExitAsync(ct);
            if (process.ExitCode != 0)
            {
                var error = await process.StandardError.ReadToEndAsync(ct);
                throw new InvalidOperationException($"Tailscale login failed: {error}");
            }
        }
    }

    public bool IsConnected()
    {
        var tailscale = GetTailscaleExecutable();
        if (tailscale == null) return false;

        try
        {
            var process = Process.Start(new ProcessStartInfo
            {
                FileName = tailscale,
                Arguments = "status",
                UseShellExecute = false,
                CreateNoWindow = true,
                RedirectStandardOutput = true
            });

            if (process == null) return false;

            var output = process.StandardOutput.ReadToEnd();
            process.WaitForExit();

            return process.ExitCode == 0 && !output.Contains("Logged out");
        }
        catch
        {
            return false;
        }
    }

    public string GetLocalIp()
    {
        var tailscale = GetTailscaleExecutable();
        if (tailscale == null) return "";

        try
        {
            var process = Process.Start(new ProcessStartInfo
            {
                FileName = tailscale,
                Arguments = "ip -4",
                UseShellExecute = false,
                CreateNoWindow = true,
                RedirectStandardOutput = true
            });

            if (process == null) return "";

            var output = process.StandardOutput.ReadToEnd().Trim();
            process.WaitForExit();

            return output;
        }
        catch
        {
            return "";
        }
    }

    private string? GetTailscaleExecutable()
    {
        if (File.Exists(TailscalePath)) return TailscalePath;

        // Check system installation
        var systemPath = @"C:\Program Files\Tailscale\tailscale.exe";
        if (File.Exists(systemPath)) return systemPath;

        return null;
    }

    private bool IsTailscaleInPath()
    {
        var pathEnv = Environment.GetEnvironmentVariable("PATH") ?? "";
        return pathEnv.Split(Path.PathSeparator).Any(p =>
            File.Exists(Path.Combine(p.Trim(), "tailscale.exe")));
    }
}
```

- [ ] **Step 2: Verify build**

```bash
dotnet build
```
Expected: Build succeeded

- [ ] **Step 3: Commit**

```bash
git add src/VerkkuCraftLauncher/Services/VpnManager.cs
git commit -m "feat: add VPN manager for Tailscale install and connect"
```

---

### Task 10: Create MinecraftLauncher Service

**Files:**
- Create: `src/VerkkuCraftLauncher/Services/MinecraftLauncher.cs`

- [ ] **Step 1: Create MinecraftLauncher.cs**

```csharp
using System.Diagnostics;
using VerkkuCraftLauncher.Helpers;
using VerkkuCraftLauncher.Models;

namespace VerkkuCraftLauncher.Services;

public class MinecraftLauncher
{
    private readonly string _localDataPath;
    private readonly HttpDownloader _downloader;

    public MinecraftLauncher(string localDataPath)
    {
        _localDataPath = localDataPath;
        _downloader = new HttpDownloader();
    }

    public event EventHandler<DownloadProgress>? ProgressChanged
    {
        add => _downloader.ProgressChanged += value;
        remove => _downloader.ProgressChanged -= value;
    }

    public string? FindMinecraftLauncher()
    {
        // Check common Minecraft launcher locations
        var commonPaths = new[]
        {
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "Minecraft Launcher", "MinecraftLauncher.exe"),
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Programs", "Minecraft Launcher", "MinecraftLauncher.exe"),
            @"C:\Program Files (x86)\Minecraft Launcher\MinecraftLauncher.exe"
        };

        foreach (var path in commonPaths)
        {
            if (File.Exists(path)) return path;
        }

        // Check for HMCL
        var hmclPath = Path.Combine(_localDataPath, "minecraft", "hmcl.exe");
        if (File.Exists(hmclPath)) return hmclPath;

        return null;
    }

    public async Task<string> DownloadHmclAsync(CancellationToken ct = default)
    {
        var minecraftDir = Path.Combine(_localDataPath, "minecraft");
        Directory.CreateDirectory(minecraftDir);

        var hmclPath = Path.Combine(minecraftDir, "hmcl.exe");
        await _downloader.DownloadFileAsync(
            "https://github.com/huanghongxun/HMCL/releases/download/v3.5.8/HMCL-3.5.8.exe",
            hmclPath, ct);

        return hmclPath;
    }

    public async Task LaunchMinecraftAsync(string launcherPath, string serverIp, int serverPort, string serverName, CancellationToken ct = default)
    {
        var process = Process.Start(new ProcessStartInfo
        {
            FileName = launcherPath,
            UseShellExecute = true
        });

        // Note: HMCL and official launcher have different ways to auto-connect
        // HMCL: hmcl.exe --server <ip> --port <port>
        // Official: needs registry/config manipulation
        // For now, we launch and let the user click "Play"
    }

    public async Task ConfigureHmclAsync(string serverIp, int serverPort, string serverName, CancellationToken ct = default)
    {
        var hmclDir = Path.Combine(_localDataPath, "minecraft", "hmcl");
        Directory.CreateDirectory(hmclDir);

        // HMCL stores config in its own directory
        // We can create a game profile JSON that pre-configures the server
        var configPath = Path.Combine(hmclDir, "versions", $"{serverName}.json");

        // Basic HMCL version config
        var config = new
        {
            name = serverName,
            type = "release",
            InheritsFrom = "1.20.4"
        };

        var json = System.Text.Json.JsonSerializer.Serialize(config, new System.Text.Json.JsonSerializerOptions { WriteIndented = true });
        Directory.CreateDirectory(Path.GetDirectoryName(configPath)!);
        await File.WriteAllTextAsync(configPath, json, ct);
    }
}
```

- [ ] **Step 2: Verify build**

```bash
dotnet build
```
Expected: Build succeeded

- [ ] **Step 3: Commit**

```bash
git add src/VerkkuCraftLauncher/Services/MinecraftLauncher.cs
git commit -m "feat: add Minecraft launcher detection and HMCL download"
```

---

### Task 11: Create UpdateManager Service

**Files:**
- Create: `src/VerkkuCraftLauncher/Services/UpdateManager.cs`

- [ ] **Step 1: Create UpdateManager.cs**

```csharp
using VerkkuCraftLauncher.Helpers;
using VerkkuCraftLauncher.Models;

namespace VerkkuCraftLauncher.Services;

public class UpdateManager
{
    private readonly ManifestService _manifestService;
    private readonly ServerManager _serverManager;
    private readonly PluginManager _pluginManager;
    private readonly string _localDataPath;

    public UpdateManager(string localDataPath)
    {
        _localDataPath = localDataPath;
        _manifestService = new ManifestService(localDataPath);
        _serverManager = new ServerManager(localDataPath);
        _pluginManager = new PluginManager(localDataPath);
    }

    public event EventHandler<DownloadProgress>? ProgressChanged
    {
        add
        {
            _serverManager.ProgressChanged += value;
            _pluginManager.ProgressChanged += value;
        }
        remove
        {
            _serverManager.ProgressChanged -= value;
            _pluginManager.ProgressChanged -= value;
        }
    }

    public event EventHandler<string>? StatusChanged;

    public async Task<LauncherManifest> CheckAndUpdateAsync(string manifestUrl, CancellationToken ct = default)
    {
        StatusChanged?.Invoke(this, "Checking for updates...");

        var remoteManifest = await _manifestService.GetManifestAsync(manifestUrl, ct);
        var localManifest = _manifestService.GetLocalManifest();

        if (localManifest == null || _manifestService.HasUpdates(remoteManifest, localManifest))
        {
            StatusChanged?.Invoke(this, "Updates found, downloading...");

            // Update server
            StatusChanged?.Invoke(this, "Downloading server...");
            await _serverManager.UpdateServerAsync(remoteManifest.Server, ct);

            // Update config files
            StatusChanged?.Invoke(this, "Downloading config...");
            await _serverManager.DownloadConfigFilesAsync(remoteManifest.ConfigFiles, ct);

            // Update plugins
            StatusChanged?.Invoke(this, "Downloading plugins...");
            await _pluginManager.UpdatePluginsAsync(remoteManifest.Plugins, localManifest, ct);

            // Save local manifest
            _manifestService.SaveLocalManifest(remoteManifest);
            localManifest = remoteManifest;
        }
        else
        {
            StatusChanged?.Invoke(this, "Already up to date");
        }

        return localManifest;
    }
}
```

- [ ] **Step 2: Verify build**

```bash
dotnet build
```
Expected: Build succeeded

- [ ] **Step 3: Commit**

```bash
git add src/VerkkuCraftLauncher/Services/UpdateManager.cs
git commit -m "feat: add update manager for version checking and updates"
```

---

### Task 12: Create ViewModels

**Files:**
- Create: `src/VerkkuCraftLauncher/ViewModels/MainViewModel.cs`
- Create: `src/VerkkuCraftLauncher/ViewModels/ProgressViewModel.cs`

- [ ] **Step 1: Create MainViewModel.cs**

```csharp
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using VerkkuCraftLauncher.Models;
using VerkkuCraftLauncher.Services;

namespace VerkkuCraftLauncher.ViewModels;

public partial class MainViewModel : ObservableObject
{
    private readonly string _localDataPath;
    private readonly JavaManager _javaManager;
    private readonly UpdateManager _updateManager;
    private readonly VpnManager _vpnManager;
    private readonly MinecraftLauncher _minecraftLauncher;

    [ObservableProperty]
    private LauncherState _currentState = LauncherState.Checking;

    [ObservableProperty]
    private string _statusMessage = "Initializing...";

    [ObservableProperty]
    private double _progressValue;

    [ObservableProperty]
    private string _serverStatus = "Unknown";

    [ObservableProperty]
    private string _playerCount = "0/20";

    [ObservableProperty]
    private string _vpnIp = "";

    [ObservableProperty]
    private object? _currentView;

    public MainViewModel()
    {
        _localDataPath = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "VerkkuCraft");

        _javaManager = new JavaManager(_localDataPath);
        _updateManager = new UpdateManager(_localDataPath);
        _vpnManager = new VpnManager(_localDataPath);
        _minecraftLauncher = new MinecraftLauncher(_localDataPath);

        // Subscribe to progress events
        _updateManager.ProgressChanged += (s, e) =>
        {
            ProgressValue = e.Percentage;
            StatusMessage = $"Downloading {e.FileName}... ({e.BytesReceived / 1024}KB / {e.TotalBytes / 1024}KB)";
        };

        _updateManager.StatusChanged += (s, message) =>
        {
            StatusMessage = message;
        };
    }

    [RelayCommand]
    private async Task InitializeAsync()
    {
        try
        {
            CurrentState = LauncherState.Installing;
            StatusMessage = "Checking Java installation...";

            // Check/install Java
            var javaPath = _javaManager.FindJavaInstallation();
            if (javaPath == null)
            {
                StatusMessage = "Installing Java 17...";
                javaPath = await _javaManager.InstallJavaAsync();
            }

            StatusMessage = "Java ready";

            // Check/install Tailscale
            if (!_vpnManager.IsTailscaleInstalled())
            {
                StatusMessage = "Installing Tailscale...";
                await _vpnManager.InstallTailscaleAsync();
            }

            StatusMessage = "Checking for updates...";

            // TODO: Replace with actual manifest URL from GitHub
            var manifestUrl = "https://raw.githubusercontent.com/{owner}/{repo}/main/manifest.json";
            var manifest = await _updateManager.CheckAndUpdateAsync(manifestUrl);

            // Connect VPN
            CurrentState = LauncherState.ConnectingVpn;
            StatusMessage = "Connecting to VPN...";

            // TODO: Fetch auth key from remote
            var authKey = "tskey-auth-xxxxx";
            await _vpnManager.ConnectAsync(authKey);

            if (_vpnManager.IsConnected())
            {
                VpnIp = _vpnManager.GetLocalIp();
                ServerStatus = "Online";
            }

            // Ready
            CurrentState = LauncherState.Ready;
            StatusMessage = "Ready to play!";
        }
        catch (Exception ex)
        {
            CurrentState = LauncherState.Error;
            StatusMessage = $"Error: {ex.Message}";
        }
    }

    [RelayCommand]
    private async Task LaunchGameAsync()
    {
        try
        {
            CurrentState = LauncherState.Launching;
            StatusMessage = "Launching Minecraft...";

            var launcherPath = _minecraftLauncher.FindMinecraftLauncher();
            if (launcherPath == null)
            {
                StatusMessage = "Downloading HMCL...";
                launcherPath = await _minecraftLauncher.DownloadHmclAsync();
            }

            // TODO: Get server IP from Tailscale
            var serverIp = VpnIp;
            await _minecraftLauncher.LaunchMinecraftAsync(launcherPath, serverIp, 25565, "VerkkuCraft");
        }
        catch (Exception ex)
        {
            CurrentState = LauncherState.Error;
            StatusMessage = $"Error: {ex.Message}";
        }
    }
}
```

- [ ] **Step 2: Create ProgressViewModel.cs**

```csharp
using CommunityToolkit.Mvvm.ComponentModel;

namespace VerkkuCraftLauncher.ViewModels;

public partial class ProgressViewModel : ObservableObject
{
    [ObservableProperty]
    private string _currentStep = "";

    [ObservableProperty]
    private double _progress;

    [ObservableProperty]
    private string _detail = "";
}
```

- [ ] **Step 3: Verify build**

```bash
dotnet build
```
Expected: Build succeeded

- [ ] **Step 4: Commit**

```bash
git add src/VerkkuCraftLauncher/ViewModels/
git commit -m "feat: add ViewModels with initialization and launch commands"
```

---

### Task 13: Create Views (XAML)

**Files:**
- Create: `src/VerkkuCraftLauncher/Views/WelcomeView.xaml` + `.cs`
- Create: `src/VerkkuCraftLauncher/Views/InstallView.xaml` + `.cs`
- Create: `src/VerkkuCraftLauncher/Views/PlayView.xaml` + `.cs`

- [ ] **Step 1: Create WelcomeView.xaml**

```xml
<UserControl x:Class="VerkkuCraftLauncher.Views.WelcomeView"
             xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
             xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"
             xmlns:vm="clr-namespace:VerkkuCraftLauncher.ViewModels"
             d:DesignHeight="450" d:DesignWidth="600"
             Background="#1a1a2e">

    <Grid>
        <Grid.RowDefinitions>
            <RowDefinition Height="*"/>
            <RowDefinition Height="Auto"/>
            <RowDefinition Height="*"/>
            <RowDefinition Height="Auto"/>
        </Grid.RowDefinitions>

        <!-- Logo and Title -->
        <StackPanel Grid.Row="0" VerticalAlignment="Center" HorizontalAlignment="Center">
            <TextBlock Text="🎮" FontSize="80" HorizontalAlignment="Center" Foreground="#00d4ff"/>
            <TextBlock Text="VerkkuCraft" FontSize="36" FontWeight="Bold" 
                       HorizontalAlignment="Center" Foreground="White" Margin="0,10,0,0"/>
            <TextBlock Text="Tu server, un clic away" FontSize="16" 
                       HorizontalAlignment="Center" Foreground="#888" Margin="0,5,0,0"/>
        </StackPanel>

        <!-- Progress Section -->
        <StackPanel Grid.Row="2" HorizontalAlignment="Center" Margin="50,0">
            <TextBlock x:Name="StatusText" Text="{Binding StatusMessage}" 
                       FontSize="14" Foreground="#aaa" HorizontalAlignment="Center" Margin="0,0,0,15"/>
            
            <ProgressBar Value="{Binding ProgressValue}" Maximum="100" Height="8"
                        Foreground="#00d4ff" Background="#333" HorizontalAlignment="Center"
                        Width="400"/>
        </StackPanel>

        <!-- Bottom Info -->
        <TextBlock Grid.Row="3" Text="Configurando tu juego..." 
                   FontSize="12" Foreground="#666" HorizontalAlignment="Center" 
                   Margin="0,0,0,20"/>
    </Grid>
</UserControl>
```

- [ ] **Step 2: Create WelcomeView.xaml.cs**

```csharp
using System.Windows.Controls;

namespace VerkkuCraftLauncher.Views;

public partial class WelcomeView : UserControl
{
    public WelcomeView()
    {
        InitializeComponent();
    }
}
```

- [ ] **Step 3: Create PlayView.xaml**

```xml
<UserControl x:Class="VerkkuCraftLauncher.Views.PlayView"
             xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
             xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"
             d:DesignHeight="450" d:DesignWidth="600"
             Background="#1a1a2e">

    <Grid>
        <Grid.RowDefinitions>
            <RowDefinition Height="Auto"/>
            <RowDefinition Height="*"/>
            <RowDefinition Height="Auto"/>
        </Grid.RowDefinitions>

        <!-- Header -->
        <TextBlock Grid.Row="0" Text="VerkkuCraft" FontSize="24" FontWeight="Bold"
                   Foreground="White" HorizontalAlignment="Center" Margin="0,30,0,0"/>

        <!-- Server Status -->
        <StackPanel Grid.Row="1" VerticalAlignment="Center" HorizontalAlignment="Center">
            <Border Background="#16213e" CornerRadius="15" Padding="40,30" Margin="0,0,0,20">
                <StackPanel>
                    <TextBlock Text="Estado del Server" FontSize="14" Foreground="#888" 
                               HorizontalAlignment="Center"/>
                    <StackPanel Orientation="Horizontal" HorizontalAlignment="Center" Margin="0,10,0,0">
                        <Ellipse Width="12" Height="12" Fill="#00ff88" Margin="0,0,8,0"/>
                        <TextBlock Text="{Binding ServerStatus}" FontSize="18" FontWeight="Bold" 
                                   Foreground="White"/>
                    </StackPanel>
                    <TextBlock Text="{Binding PlayerCount}" FontSize="14" Foreground="#aaa" 
                               HorizontalAlignment="Center" Margin="0,5,0,0"/>
                </StackPanel>
            </Border>

            <Border Background="#16213e" CornerRadius="15" Padding="40,20">
                <StackPanel>
                    <TextBlock Text="Tu IP VPN" FontSize="14" Foreground="#888" 
                               HorizontalAlignment="Center"/>
                    <TextBlock Text="{Binding VpnIp}" FontSize="16" FontWeight="Bold"
                               Foreground="#00d4ff" HorizontalAlignment="Center" Margin="0,5,0,0"/>
                </StackPanel>
            </Border>
        </StackPanel>

        <!-- Play Button -->
        <StackPanel Grid.Row="2" HorizontalAlignment="Center" Margin="0,0,0,30">
            <Button Content="🎮  JUGAR" Command="{Binding LaunchGameCommand}"
                    FontSize="20" FontWeight="Bold" Padding="60,20"
                    Background="#00d4ff" Foreground="White" BorderThickness="0"
                    Cursor="Hand">
                <Button.Template>
                    <ControlTemplate TargetType="Button">
                        <Border Background="{TemplateBinding Background}" 
                                CornerRadius="25" Padding="{TemplateBinding Padding}">
                            <ContentPresenter HorizontalAlignment="Center" VerticalAlignment="Center"/>
                        </Border>
                    </ControlTemplate>
                </Button.Template>
            </Button>

            <TextBlock Text="⚙️ Configuración" FontSize="12" Foreground="#666" 
                       HorizontalAlignment="Center" Margin="0,15,0,0" Cursor="Hand"/>
        </StackPanel>
    </Grid>
</UserControl>
```

- [ ] **Step 4: Create PlayView.xaml.cs**

```csharp
using System.Windows.Controls;

namespace VerkkuCraftLauncher.Views;

public partial class PlayView : UserControl
{
    public PlayView()
    {
        InitializeComponent();
    }
}
```

- [ ] **Step 5: Update MainWindow.xaml**

Replace the existing MainWindow.xaml with:

```xml
<ui:FluentWindow x:Class="VerkkuCraftLauncher.MainWindow"
        xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
        xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"
        xmlns:ui="http://schemas.lepo.co/wpfui/2022/xaml"
        xmlns:views="clr-namespace:VerkkuCraftLauncher.Views"
        xmlns:vm="clr-namespace:VerkkuCraftLauncher.ViewModels"
        Title="VerkkuCraft Launcher" 
        Width="600" Height="450"
        WindowStartupLocation="CenterScreen"
        ResizeMode="NoResize"
        Background="#1a1a2e">

    <Window.DataContext>
        <vm:MainViewModel/>
    </Window.DataContext>

    <Grid>
        <views:WelcomeView x:Name="WelcomeView" DataContext="{Binding DataContext, RelativeSource={RelativeSource AncestorType=Window}}"/>
    </Grid>
</ui:FluentWindow>
```

- [ ] **Step 6: Update App.xaml**

```xml
<Application x:Class="VerkkuCraftLauncher.App"
             xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
             xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"
             xmlns:ui="http://schemas.lepo.co/wpfui/2022/xaml">
    <Application.Resources>
        <ResourceDictionary>
            <ResourceDictionary.MergedDictionaries>
                <ui:ThemesDictionary Theme="Dark"/>
                <ui:ControlsDictionary/>
            </ResourceDictionary.MergedDictionaries>
        </ResourceDictionary>
    </Application.Resources>
</Application>
```

- [ ] **Step 7: Verify build**

```bash
dotnet build
```
Expected: Build succeeded

- [ ] **Step 8: Commit**

```bash
git add src/VerkkuCraftLauncher/Views/ src/VerkkuCraftLauncher/MainWindow.xaml src/VerkkuCraftLauncher/App.xaml
git commit -m "feat: add WPF views with dark theme and Fluent Design"
```

---

### Task 14: Create Build Script

**Files:**
- Create: `publish.bat`

- [ ] **Step 1: Create publish.bat**

```batch
@echo off
echo Building VerkkuCraft Launcher...

dotnet publish src/VerkkuCraftLauncher/VerkkuCraftLauncher.csproj ^
    -c Release ^
    -r win-x64 ^
    --self-contained true ^
    -p:PublishSingleFile=true ^
    -p:IncludeNativeLibrariesForSelfExtract=true ^
    -o publish

echo.
echo Build complete! Output: publish\VerkkuCraftLauncher.exe
echo.
pause
```

- [ ] **Step 2: Test build**

```bash
.\publish.bat
```
Expected: Build succeeds, creates `publish\VerkkuCraftLauncher.exe`

- [ ] **Step 3: Commit**

```bash
git add publish.bat
git commit -m "feat: add build script for self-contained .exe"
```

---

### Task 15: Create GitHub Releases Manifest

**Files:**
- Create: `manifest.json` (template for GitHub Releases)

- [ ] **Step 1: Create manifest.json template**

```json
{
  "manifest_version": 1,
  "launcher_version": "1.0.0",
  "server": {
    "name": "VerkkuCraft",
    "minecraft_version": "1.20.4",
    "paper_build": 218,
    "paper_url": "https://api.papermc.io/v2/projects/paper/versions/1.20.4/builds/218/downloads/paper-1.20.4-218.jar"
  },
  "plugins": [
    {
      "name": "vEventDrops",
      "version": "1.0.0",
      "filename": "vEventDrops-1.0.0.jar",
      "download_url": "https://github.com/{owner}/{repo}/releases/download/v1.0.0/vEventDrops-1.0.0.jar"
    },
    {
      "name": "GravesX",
      "version": "2026.4.9.1",
      "filename": "GravesX-2026.4.9.1.jar",
      "download_url": "https://github.com/{owner}/{repo}/releases/download/v1.0.0/GravesX-2026.4.9.1.jar"
    },
    {
      "name": "spark",
      "version": "2.8.1",
      "filename": "spark-2.8.1.jar",
      "download_url": "https://github.com/{owner}/{repo}/releases/download/v1.0.0/spark-2.8.1.jar"
    }
  ],
  "config_files": [
    {
      "name": "server.properties",
      "download_url": "https://github.com/{owner}/{repo}/releases/download/v1.0.0/server.properties"
    },
    {
      "name": "bukkit.yml",
      "download_url": "https://github.com/{owner}/{repo}/releases/download/v1.0.0/bukkit.yml"
    },
    {
      "name": "spigot.yml",
      "download_url": "https://github.com/{owner}/{repo}/releases/download/v1.0.0/spigot.yml"
    }
  ],
  "vpn": {
    "tailscale_authkey_url": "https://raw.githubusercontent.com/{owner}/{repo}/main/auth/vpn-key.txt",
    "network_name": "VerkkuCraft"
  },
  "minecraft": {
    "hmcl_url": "https://github.com/huanghongxun/HMCL/releases/download/v3.5.8/HMCL-3.5.8.exe",
    "version": "1.20.4"
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add manifest.json
git commit -m "feat: add manifest template for GitHub Releases"
```

---

## Summary

| Task | Component | Status |
|------|-----------|--------|
| 1 | Project Structure | ⬜ |
| 2 | Models | ⬜ |
| 3 | HttpDownloader | ⬜ |
| 4 | FileExtractor | ⬜ |
| 5 | ManifestService | ⬜ |
| 6 | JavaManager | ⬜ |
| 7 | ServerManager | ⬜ |
| 8 | PluginManager | ⬜ |
| 9 | VpnManager | ⬜ |
| 10 | MinecraftLauncher | ⬜ |
| 11 | UpdateManager | ⬜ |
| 12 | ViewModels | ⬜ |
| 13 | Views (XAML) | ⬜ |
| 14 | Build Script | ⬜ |
| 15 | Manifest Template | ⬜ |

**Total Tasks:** 15
