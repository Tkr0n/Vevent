# Soporte de Shaders - Plan de Implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add complete shader support to VerkkuCraft Launcher - detect shaders from other launchers, allow selective import, and auto-install Iris + Sodium.

**Architecture:** Three-layer approach: detection service scans external launchers, installer service copies shaders and installs required mods, integration in GameInstallerService orchestrates the flow with user interaction.

**Tech Stack:** C# (.NET/WPF), Modrinth API, System.IO

---

## File Structure

### Files to Create

| File | Responsibility |
|------|----------------|
| `src/VerkkuCraftLauncher/Models/DetectedShader.cs` | Data model for detected shaders |
| `src/VerkkuCraftLauncher/Services/ShaderDetectionService.cs` | Scan external launchers for shaders |
| `src/VerkkuCraftLauncher/Services/ShaderInstallerService.cs` | Copy shaders and install Iris/Sodium |

### Files to Modify

| File | Changes |
|------|---------|
| `manifest.json` | Add `shaderMods` section |
| `src/VerkkuCraftLauncher/Models/LauncherManifest.cs` | Add `ShaderModInfo` class and `ShaderMods` property |
| `src/VerkkuCraftLauncher/Services/GameInstallerService.cs` | Integrate shader detection and installation |

---

## Task 1: Update Data Models

**Files:**
- Modify: `src/VerkkuCraftLauncher/Models/LauncherManifest.cs`
- Create: `src/VerkkuCraftLauncher/Models/DetectedShader.cs`

- [ ] **Step 1: Add ShaderModInfo class to LauncherManifest.cs**

```csharp
// Add after ClientModInfo class (line 33)
public class ShaderModInfo
{
    public string Name { get; set; } = string.Empty;
    public string ModrinthProjectId { get; set; } = string.Empty;
    public string Version { get; set; } = string.Empty;
    public string FileName { get; set; } = string.Empty;
    public string DownloadUrl { get; set; } = string.Empty;
}
```

- [ ] **Step 2: Add ShaderMods property to LauncherManifest class**

```csharp
// Add after ClientMods property (line 12)
public List<ShaderModInfo> ShaderMods { get; set; } = new();
```

- [ ] **Step 3: Create DetectedShader.cs**

```csharp
namespace VerkkuCraftLauncher.Models;

public class DetectedShader
{
    public string Name { get; set; } = string.Empty;
    public string FileName { get; set; } = string.Empty;
    public string SourcePath { get; set; } = string.Empty;
    public long Size { get; set; }
    public string SourceLauncher { get; set; } = string.Empty;
}
```

- [ ] **Step 4: Update manifest.json**

```json
// Add after clientMods array (after line 195)
"shaderMods": [
  {
    "name": "Iris",
    "modrinthProjectId": "iris",
    "version": "",
    "fileName": "",
    "downloadUrl": ""
  },
  {
    "name": "Sodium",
    "modrinthProjectId": "sodium",
    "version": "",
    "fileName": "",
    "downloadUrl": ""
  }
]
```

- [ ] **Step 5: Verify build compiles**

Run: `dotnet build src/VerkkuCraftLauncher/VerkkuCraftLauncher.csproj`
Expected: Build succeeded

- [ ] **Step 6: Commit**

```bash
git add src/VerkkuCraftLauncher/Models/LauncherManifest.cs src/VerkkuCraftLauncher/Models/DetectedShader.cs manifest.json
git commit -m "feat: add shader data models (ShaderModInfo, DetectedShader)"
```

---

## Task 2: Implement Shader Detection Service

**Files:**
- Create: `src/VerkkuCraftLauncher/Services/ShaderDetectionService.cs`

- [ ] **Step 1: Create ShaderDetectionService.cs**

```csharp
using System.IO;
using VerkkuCraftLauncher.Models;

namespace VerkkuCraftLauncher.Services;

public class ShaderDetectionService
{
    private static readonly string[] LauncherPaths = new[]
    {
        // Minecraft Launcher oficial y SKLauncher (misma ubicación)
        @"%APPDATA%\.minecraft\shaderpacks"
    };

    public List<DetectedShader> DetectShaders()
    {
        var shaders = new List<DetectedShader>();

        foreach (var pathTemplate in LauncherPaths)
        {
            var path = Environment.ExpandEnvironmentVariables(pathTemplate);
            var launcherName = GetLauncherName(path);

            if (!Directory.Exists(path))
                continue;

            try
            {
                var files = Directory.GetFiles(path, "*.zip");
                foreach (var file in files)
                {
                    var fileInfo = new FileInfo(file);
                    shaders.Add(new DetectedShader
                    {
                        Name = Path.GetFileNameWithoutExtension(file),
                        FileName = Path.GetFileName(file),
                        SourcePath = file,
                        Size = fileInfo.Length,
                        SourceLauncher = launcherName
                    });
                }
            }
            catch (UnauthorizedAccessException)
            {
                // Log warning but continue
                continue;
            }
            catch (IOException)
            {
                continue;
            }
        }

        return shaders;
    }

    private static string GetLauncherName(string path)
    {
        if (path.Contains(".minecraft"))
            return "Minecraft Launcher";
        return "Otro launcher";
    }
}
```

- [ ] **Step 2: Verify build compiles**

Run: `dotnet build src/VerkkuCraftLauncher/VerkkuCraftLauncher.csproj`
Expected: Build succeeded

- [ ] **Step 3: Commit**

```bash
git add src/VerkkuCraftLauncher/Services/ShaderDetectionService.cs
git commit -m "feat: add ShaderDetectionService for scanning external launchers"
```

---

## Task 3: Implement Shader Installer Service

**Files:**
- Create: `src/VerkkuCraftLauncher/Services/ShaderInstallerService.cs`

- [ ] **Step 1: Create ShaderInstallerService.cs**

```csharp
using System.IO;
using VerkkuCraftLauncher.Models;

namespace VerkkuCraftLauncher.Services;

public class ShaderInstallerService
{
    private readonly ModrinthService _modrinth;
    private readonly string _baseDir;

    public ShaderInstallerService(M modrinth, string baseDir)
    {
        _modrinth = modrinth;
        _baseDir = baseDir;
    }

    public async Task<ShaderInstallResult> InstallShadersAsync(
        List<DetectedShader> selectedShaders,
        string mcVersion,
        List<ShaderModInfo> shaderMods,
        IProgress<DownloadProgress>? progress = null,
        CancellationToken ct = default)
    {
        var result = new ShaderInstallResult();

        // Create shaderpacks directory
        var shaderpacksDir = Path.Combine(_baseDir, "game", mcVersion, "shaderpacks");
        Directory.CreateDirectory(shaderpacksDir);

        // Copy selected shaders
        foreach (var shader in selectedShaders)
        {
            var destPath = Path.Combine(shaderpacksDir, shader.FileName);

            if (File.Exists(destPath))
            {
                result.Skipped.Add(shader.FileName);
                continue;
            }

            try
            {
                File.Copy(shader.SourcePath, destPath, false);
                result.Installed.Add(shader.FileName);
            }
            catch (Exception ex)
            {
                result.Errors.Add($"{shader.FileName}: {ex.Message}");
            }
        }

        // Check and install Iris/Sodium if needed
        var modsDir = Path.Combine(_baseDir, "game", mcVersion, "mods");
        Directory.CreateDirectory(modsDir);

        foreach (var mod in shaderMods)
        {
            if (string.IsNullOrEmpty(mod.ModrinthProjectId))
                continue;

            // Check if mod already exists
            var existingMod = Directory.GetFiles(modsDir, $"{mod.Name}*.jar");
            if (existingMod.Length > 0)
            {
                result.ModsAlreadyInstalled.Add(mod.Name);
                continue;
            }

            try
            {
                progress?.Report(new DownloadProgress(0, $"Descargando {mod.Name}..."));
                await _modrinth.DownloadModAsync(mod.ModrinthProjectId, mcVersion, "fabric", modsDir, ct);
                result.ModsInstalled.Add(mod.Name);
            }
            catch (Exception ex)
            {
                result.Errors.Add($"{mod.Name}: {ex.Message}");
            }
        }

        return result;
    }
}

public class ShaderInstallResult
{
    public List<string> Installed { get; set; } = new();
    public List<string> Skipped { get; set; } = new();
    public List<string> ModsInstalled { get; set; } = new();
    public List<string> ModsAlreadyInstalled { get; set; } = new();
    public List<string> Errors { get; set; } = new();
}
```

- [ ] **Step 2: Verify build compiles**

Run: `dotnet build src/VerkkuCraftLauncher/VerkkuCraftLauncher.csproj`
Expected: Build succeeded

- [ ] **Step 3: Commit**

```bash
git add src/VerkkuCraftLauncher/Services/ShaderInstallerService.cs
git commit -m "feat: add ShaderInstallerService with Iris/Sodium auto-install"
```

---

## Task 4: Integrate into GameInstallerService

**Files:**
- Modify: `src/VerkkuCraftLauncher/Services/GameInstallerService.cs`

- [ ] **Step 1: Add ShaderDetectionService field and constructor parameter**

```csharp
// Modify class fields (line 18-19)
private readonly MojangMetaService _mojang;
private readonly FabricService _fabric;
private readonly ModrinthService _modrinth;
private readonly ShaderDetectionService _shaderDetection;

// Modify constructor (line 22-23)
public GameInstallerService(MojangMetaService mojang, FabricService fabric, 
    ModrinthService modrinth, ShaderDetectionService shaderDetection)
{ 
    _mojang = mojang; 
    _fabric = fabric; 
    _modrinth = modrinth;
    _shaderDetection = shaderDetection;
}
```

- [ ] **Step 2: Add shader detection call in EnsureInstalledAsync**

```csharp
// Add after line 72 (after downloading mods)
progress?.Report(new DownloadProgress(99, "Detectando shaders en otros launchers..."));
var detectedShaders = _shaderDetection.DetectShaders();

// Store detected shaders for UI to access
LastDetectedShaders = detectedShaders;
```

- [ ] **Step 3: Add LastDetectedShaders property**

```csharp
// Add after constructor
public List<DetectedShader> LastDetectedShaders { get; private set; } = new();
```

- [ ] **Step 4: Add using directive**

```csharp
// Add at top of file
using VerkkuCraftLauncher.Models;
```

- [ ] **Step 5: Verify build compiles**

Run: `dotnet build src/VerkkuCraftLauncher/VerkkuCraftLauncher.csproj`
Expected: Build succeeded

- [ ] **Step 6: Commit**

```bash
git add src/VerkkuCraftLauncher/Services/GameInstallerService.cs
git commit -m "feat: integrate shader detection into GameInstallerService"
```

---

## Task 5: Update Dependency Injection

**Files:**
- Modify: `src/VerkkuCraftLauncher/App.xaml.cs`

- [ ] **Step 1: Register ShaderDetectionService**

```csharp
// Find where other services are registered and add:
services.AddSingleton<ShaderDetectionService>();
```

- [ ] **Step 2: Update GameInstallerService registration**

```csharp
// Find GameInstallerService registration and update constructor call if needed
// The DI container should automatically resolve the new dependency
```

- [ ] **Step 3: Verify build compiles**

Run: `dotnet build src/VerkkuCraftLauncher/VerkkuCraftLauncher.csproj`
Expected: Build succeeded

- [ ] **Step 4: Commit**

```bash
git add src/VerkkuCraftLauncher/App.xaml.cs
git commit -m "feat: register ShaderDetectionService in DI container"
```

---

## Task 6: Add Basic UI for Shader Detection

**Files:**
- Modify: `src/VerkkuCraftLauncher/Views/MainWindow.xaml`
- Modify: `src/VerkkuCraftLauncher/Views/MainWindow.xaml.cs`
- Modify: `src/VerkkuCraftLauncher/ViewModels/MainViewModel.cs`

- [ ] **Step 1: Add DetectedShaders property to MainViewModel**

```csharp
// Add property
public ObservableCollection<DetectedShader> DetectedShaders { get; } = new();
public bool HasDetectedShaders => DetectedShaders.Count > 0;
```

- [ ] **Step 2: Add method to populate detected shaders**

```csharp
// Add method
public void LoadDetectedShaders(List<DetectedShader> shaders)
{
    DetectedShaders.Clear();
    foreach (var shader in shaders)
    {
        DetectedShaders.Add(shader);
    }
    OnPropertyChanged(nameof(HasDetectedShaders));
}
```

- [ ] **Step 3: Add basic UI elements to MainWindow.xaml**

```xml
<!-- Add after existing content, before closing Window tag -->
<Grid x:Name="ShaderImportPanel" Visibility="Collapsed" Margin="0,10,0,0">
    <Grid.RowDefinitions>
        <RowDefinition Height="Auto"/>
        <RowDefinition Height="*"/>
        <RowDefinition Height="Auto"/>
    </Grid.RowDefinitions>
    
    <TextBlock Grid.Row="0" Text="Shaders detectados:" FontWeight="Bold" Margin="0,0,0,5"/>
    
    <ListBox Grid.Row="1" x:Name="ShaderListBox" 
             ItemsSource="{Binding DetectedShaders}"
             DisplayMemberPath="Name"
             SelectionMode="ExtendedSelection"
             MaxHeight="150"/>
    
    <StackPanel Grid.Row="2" Orientation="Horizontal" HorizontalAlignment="Right" Margin="0,5,0,0">
        <Button Content="Importar seleccionados" 
                Click="ImportShaders_Click"
                Margin="0,0,10,0"/>
        <Button Content="Omitir" 
                Click="SkipShaders_Click"/>
    </StackPanel>
</Grid>
```

- [ ] **Step 4: Add event handlers in MainWindow.xaml.cs**

```csharp
// Add methods
private async void ImportShaders_Click(object sender, RoutedEventArgs e)
{
    var selectedShaders = ShaderListBox.SelectedItems.Cast<DetectedShader>().ToList();
    if (selectedShaders.Count == 0)
    {
        MessageBox.Show("Selecciona al menos un shader para importar.", "Aviso", 
            MessageBoxButton.OK, MessageBoxImage.Information);
        return;
    }

    // TODO: Call ShaderInstallerService in next task
    MessageBox.Show($"{selectedShaders.Count} shader(s) seleccionado(s) para importar.", 
        "Importar Shaders", MessageBoxButton.OK, MessageBoxImage.Information);
    
    ShaderImportPanel.Visibility = Visibility.Collapsed;
}

private void SkipShaders_Click(object sender, RoutedEventArgs e)
{
    ShaderImportPanel.Visibility = Visibility.Collapsed;
}
```

- [ ] **Step 5: Verify build compiles**

Run: `dotnet build src/VerkkuCraftLauncher/VerkkuCraftLauncher.csproj`
Expected: Build succeeded

- [ ] **Step 6: Commit**

```bash
git add src/VerkkuCraftLauncher/Views/MainWindow.xaml src/VerkkuCraftLauncher/Views/MainWindow.xaml.cs src/VerkkuCraftLauncher/ViewModels/MainViewModel.cs
git commit -m "feat: add basic shader import UI"
```

---

## Task 7: Connect UI to Services

**Files:**
- Modify: `src/VerkkuCraftLauncher/Views/MainWindow.xaml.cs`
- Modify: `src/VerkkuCraftLauncher/ViewModels/MainViewModel.cs`

- [ ] **Step 1: Add ShaderInstallerService field to MainWindow**

```csharp
// Add field
private ShaderInstallerService? _shaderInstaller;
```

- [ ] **Step 2: Initialize installer in constructor or loaded event**

```csharp
// In constructor or Window_Loaded
_shaderInstaller = new ShaderInstallerService(
    App.ServiceProvider.GetRequiredService<ModrinthService>(),
    Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "VerkkuCraft"));
```

- [ ] **Step 3: Update ImportShaders_Click to call installer**

```csharp
private async void ImportShaders_Click(object sender, RoutedEventArgs e)
{
    var selectedShaders = ShaderListBox.SelectedItems.Cast<DetectedShader>().ToList();
    if (selectedShaders.Count == 0)
    {
        MessageBox.Show("Selecciona al menos un shader para importar.", "Aviso", 
            MessageBoxButton.OK, MessageBoxImage.Information);
        return;
    }

    if (_shaderInstaller == null)
    {
        MessageBox.Show("Error: Servicio de instalación no disponible.", "Error", 
            MessageBoxButton.OK, MessageBoxImage.Error);
        return;
    }

    // Get current MC version from config or use default
    var mcVersion = "1.21.1"; // TODO: Get from manifest
    
    // Get shader mods from manifest
    var manifest = App.ServiceProvider.GetRequiredService<ManifestService>().Manifest;
    var shaderMods = manifest?.ShaderMods ?? new List<ShaderModInfo>();

    try
    {
        var progress = new Progress<DownloadProgress>(p => 
        {
            StatusText.Text = p.Message;
        });

        var result = await _shaderInstaller.InstallShadersAsync(
            selectedShaders, mcVersion, shaderMods, progress);

        var message = $"Importación completada:\n" +
                      $"- Instalados: {result.Installed.Count}\n" +
                      $"- Omitidos (ya existen): {result.Skipped.Count}\n" +
                      $"- Mods instalados: {result.ModsInstalled.Count}";

        if (result.Errors.Count > 0)
        {
            message += $"\n\nErrores:\n{string.Join("\n", result.Errors)}";
        }

        MessageBox.Show(message, "Resultado", MessageBoxButton.OK, MessageBoxImage.Information);
    }
    catch (Exception ex)
    {
        MessageBox.Show($"Error al importar shaders: {ex.Message}", "Error", 
            MessageBoxButton.OK, MessageBoxImage.Error);
    }

    ShaderImportPanel.Visibility = Visibility.Collapsed;
}
```

- [ ] **Step 4: Verify build compiles**

Run: `dotnet build src/VerkkuCraftLauncher/VerkkuCraftLauncher.csproj`
Expected: Build succeeded

- [ ] **Step 5: Commit**

```bash
git add src/VerkkuCraftLauncher/Views/MainWindow.xaml.cs src/VerkkuCraftLauncher/ViewModels/MainViewModel.cs
git commit -m "feat: connect shader import UI to installer service"
```

---

## Task 8: Trigger Detection on Startup

**Files:**
- Modify: `src/VerkkuCraftLauncher/Views/MainWindow.xaml.cs`

- [ ] **Step 1: Add detection call in Window_Loaded or constructor**

```csharp
// Add after window loads
private async void Window_Loaded(object sender, RoutedEventArgs e)
{
    // Existing code...
    
    // Detect shaders from other launchers
    var detectionService = App.ServiceProvider.GetRequiredService<ShaderDetectionService>();
    var detectedShaders = detectionService.DetectShaders();
    
    if (detectedShaders.Count > 0)
    {
        var result = MessageBox.Show(
            $"Se encontraron {detectedShaders.Count} shader(s) en otro launcher.\n" +
            "¿Deseas importarlos?",
            "Shaders detectados",
            MessageBoxButton.YesNo,
            MessageBoxImage.Question);

        if (result == MessageBoxResult.Yes)
        {
            ViewModel.LoadDetectedShaders(detectedShaders);
            ShaderImportPanel.Visibility = Visibility.Visible;
        }
    }
}
```

- [ ] **Step 2: Verify build compiles**

Run: `dotnet build src/VerkkuCraftLauncher/VerkkuCraftLauncher.csproj`
Expected: Build succeeded

- [ ] **Step 3: Manual test**

1. Place some .zip files in `%appdata%\.minecraft\shaderpacks`
2. Run the launcher
3. Verify dialog appears asking to import shaders
4. Select shaders and verify they're copied to VerkkuCraft folder

- [ ] **Step 4: Commit**

```bash
git add src/VerkkuCraftLauncher/Views/MainWindow.xaml.cs
git commit -m "feat: trigger shader detection on launcher startup"
```

---

## Task 9: Final Integration Test

**Files:**
- None (testing only)

- [ ] **Step 1: Full build and test**

```bash
dotnet build src/VerkkuCraftLauncher/VerkkuCraftLauncher.csproj
```

- [ ] **Step 2: Manual integration test**

1. Ensure `%appdata%\.minecraft\shaderpacks` has at least one .zip file
2. Run launcher
3. Verify detection dialog appears
4. Select shaders and import
5. Verify shaders copied to `%appdata%\VerkkuCraft\game\1.21.1\shaderpacks`
6. Verify Iris and Sodium installed in `mods` folder
7. Launch Minecraft and verify shaders appear in video settings

- [ ] **Step 3: Final commit**

```bash
git add -A
git commit -m "feat: complete shader support implementation"
```

---

## Summary

| Task | Description | Files Changed |
|------|-------------|---------------|
| 1 | Data models | LauncherManifest.cs, DetectedShader.cs, manifest.json |
| 2 | Detection service | ShaderDetectionService.cs |
| 3 | Installer service | ShaderInstallerService.cs |
| 4 | Integration | GameInstallerService.cs |
| 5 | DI setup | App.xaml.cs |
| 6 | Basic UI | MainWindow.xaml, MainWindow.xaml.cs, MainViewModel.cs |
| 7 | Connect UI | MainWindow.xaml.cs, MainViewModel.cs |
| 8 | Startup trigger | MainWindow.xaml.cs |
| 9 | Final test | None |

**Total files created:** 3
**Total files modified:** 6
