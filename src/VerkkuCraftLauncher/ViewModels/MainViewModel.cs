using System.Collections.ObjectModel;
using System.IO;
using System.Windows;
using System.Windows.Media.Imaging;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Microsoft.Win32;
using VerkkuCraftLauncher.Helpers;
using VerkkuCraftLauncher.Models;
using VerkkuCraftLauncher.Services;

namespace VerkkuCraftLauncher.ViewModels;

public partial class MainViewModel : ObservableObject
{
    private readonly ManifestService _manifestService;
    private readonly JavaManager _javaManager;
    private readonly ServerManager _serverManager;
    private readonly VpnManager _vpnManager;
    private readonly MinecraftLauncher _minecraftLauncher;
    private readonly UpdateManager _updateManager;
    private readonly GameInstallerService _gameInstaller;
    private readonly LauncherAccountService _accountService;
    private readonly SkinService _skinService;
    private InstalledGame? _installedGame;
    private CancellationTokenSource? _skinCts;

    [ObservableProperty] private string _statusMessage = "Iniciando VerkkuCraft...";
    [ObservableProperty] private int _progressValue;
    [ObservableProperty] private bool _isProgressIndeterminate = true;
    [ObservableProperty] private bool _isJugarEnabled;
    [ObservableProperty] private bool _isUpdating;
    [ObservableProperty] private string _username = string.Empty;
    [ObservableProperty] private LauncherManifest? _manifest;
    [ObservableProperty] private string _downloadDetail = string.Empty;
    [ObservableProperty] private int _downloadPercent;
    [ObservableProperty] private BitmapImage? _skinPreview;

    public ObservableCollection<DetectedShader> DetectedShaders { get; } = new();
    public bool HasDetectedShaders => DetectedShaders.Count > 0;

    public void LoadDetectedShaders(List<DetectedShader> shaders)
    {
        DetectedShaders.Clear();
        foreach (var shader in shaders)
        {
            DetectedShaders.Add(shader);
        }
        OnPropertyChanged(nameof(HasDetectedShaders));
    }

    partial void OnUsernameChanged(string value)
    {
        _skinCts?.Cancel();
        _skinCts = new CancellationTokenSource();
        _ = LoadSkinDebounced(value, _skinCts.Token);
    }

    private async Task LoadSkinDebounced(string username, CancellationToken ct)
    {
        try
        {
            await Task.Delay(500, ct);
            if (string.IsNullOrWhiteSpace(username)) { SkinPreview = null; return; }
            SkinPreview = await _skinService.FetchSkinAsync(username);
        }
        catch (OperationCanceledException) { }
        catch (Exception ex) { AppLogger.LogError("SkinPreview", ex); }
    }

    public MainViewModel(
        ManifestService manifestService,
        JavaManager javaManager,
        ServerManager serverManager,
        VpnManager vpnManager,
        MinecraftLauncher minecraftLauncher,
        UpdateManager updateManager,
        GameInstallerService gameInstaller,
        LauncherAccountService accountService,
        SkinService skinService)
    {
        _manifestService = manifestService;
        _javaManager = javaManager;
        _serverManager = serverManager;
        _vpnManager = vpnManager;
        _minecraftLauncher = minecraftLauncher;
        _updateManager = updateManager;
        _gameInstaller = gameInstaller;
        _accountService = accountService;
        _skinService = skinService;
    }

    public async Task InitializeAsync()
    {
        try
        {
            AppLogger.Log("Step 0: Detecting username...");
            Username = _accountService.DetectUsername() ?? string.Empty;
            AppLogger.Log($"Username: '{Username}'");

            AppLogger.Log("Step 1: Fetching manifest...");
            StatusMessage = "Obteniendo manifiesto...";
            Manifest = await _manifestService.FetchManifestAsync();
            
            if (Manifest == null)
            {
                StatusMessage = "Error al obtener manifiesto";
                AppLogger.Log("ERROR: Manifest is null");
                return;
            }
            AppLogger.Log($"Manifest OK. ServerVersion={Manifest.ServerVersion}, Plugins={Manifest.Plugins.Count}, ClientMods={Manifest.ClientMods.Count}");

            IsProgressIndeterminate = false;

            if (await _updateManager.CheckForUpdateAsync(Manifest.LauncherVersion))
            {
                StatusMessage = "Actualización disponible";
                IsUpdating = true;
                await _updateManager.UpdateLauncherAsync(Manifest.LauncherDownloadUrl, CreateProgress());
                return;
            }

            AppLogger.Log("Step 2: Ensuring Java...");
            StatusMessage = "Verificando Java...";
            await _javaManager.EnsureJavaInstalledAsync();
            AppLogger.Log("Java OK");

            AppLogger.Log("Step 3: Installing game + Fabric + mods...");
            StatusMessage = "Instalando juego y mods...";
            _installedGame = await _gameInstaller.EnsureInstalledAsync(
                Manifest.ServerVersion, Manifest.ClientMods,
                CreateDownloadProgress(), CreateProgress());
            AppLogger.Log($"Game installed. Classpath entries={_installedGame.Classpath?.Count ?? 0}");

            AppLogger.Log("Step 4: Checking VPN...");
            StatusMessage = "Verificando VPN...";
            if (!_vpnManager.IsTailscaleInstalled())
            {
                AppLogger.Log("Tailscale not found, installing...");
                StatusMessage = "Instalando Tailscale...";
                await _vpnManager.InstallTailscaleAsync(CreateDownloadProgress());
                AppLogger.Log("Tailscale install complete");
            }
            else
            {
                AppLogger.Log("Tailscale already installed");
            }

            if (!_vpnManager.IsTailscaleConnected())
            {
                if (!string.IsNullOrEmpty(Manifest.TailscaleAuthKey))
                {
                    AppLogger.Log("Tailscale not connected, connecting with auth key...");
                    StatusMessage = "Conectando a Tailscale...";
                    await _vpnManager.ConnectWithAuthKeyAsync(Manifest.TailscaleAuthKey);
                    AppLogger.Log("Tailscale auth key login complete");
                }
                else
                {
                    AppLogger.Log("Tailscale not connected, opening login...");
                    StatusMessage = "Abriendo Tailscale para iniciar sesión...";
                    await _vpnManager.OpenTailscaleLoginAsync();
                    AppLogger.Log("Tailscale login window opened");
                }
            }

            StatusMessage = "Importando skin...";
            var gameDir = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
                "VerkkuCraft", "game", Manifest.ServerVersion);
            await _skinService.AutoImportSkinAsync(Username, gameDir);

            StatusMessage = "Todo listo. Presiona JUGAR para iniciar.";
            IsJugarEnabled = true;
            IsProgressIndeterminate = false;
            AppLogger.Log("Initialization complete!");
        }
        catch (Exception ex)
        {
            AppLogger.LogError("InitializeAsync", ex);
            StatusMessage = $"Error: {ex.Message}";
        }
    }

    [RelayCommand]
    private async Task JugarAsync()
    {
        try
        {
            AppLogger.Log("JugarAsync started");
            if (Manifest == null)
            {
                AppLogger.Log("Manifest is null, aborting");
                return;
            }

            IsJugarEnabled = false;
            StatusMessage = "Verificando conexión VPN...";

            var vpnOk = _vpnManager.IsTailscaleConnected();
            AppLogger.Log($"Tailscale connected: {vpnOk}");

            if (!vpnOk)
            {
                AppLogger.Log("Tailscale not connected, aborting");
                StatusMessage = "Tailscale no está conectado. Abre Tailscale e inicia sesión.";
                IsJugarEnabled = true;
                return;
            }

            StatusMessage = "Iniciando Minecraft...";
            AppLogger.Log("Ensuring game installed...");

            _installedGame ??= await _gameInstaller.EnsureInstalledAsync(
                Manifest.ServerVersion, Manifest.ClientMods,
                CreateDownloadProgress(), CreateProgress());

            AppLogger.Log($"Classpath: {_installedGame.Classpath?.Count ?? 0} entries");

            var gameDir = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
                "VerkkuCraft", "game", Manifest.ServerVersion);
            Directory.CreateDirectory(gameDir);
            AppLogger.Log($"gameDir: {gameDir}");
            AppLogger.Log($"Java: {_javaManager.GetJavaPath(null)}");

            var launched = await _minecraftLauncher.LaunchMinecraftAsync(
                _installedGame,
                gameDir,
                Username,
                Manifest.DefaultServerConfig.ServerAddress,
                Manifest.DefaultServerConfig.ServerPort,
                Manifest.ServerVersion,
                CreateStringProgress());

            AppLogger.Log($"LaunchAsync returned: {launched}");

            if (launched)
            {
                StatusMessage = "Listo para jugar";
                ProgressValue = 0;
                DownloadPercent = 0;
                DownloadDetail = string.Empty;
                IsProgressIndeterminate = false;
            }
            else
                StatusMessage = "Error al iniciar Minecraft";

            IsJugarEnabled = true;
        }
        catch (Exception ex)
        {
            AppLogger.LogError("JugarAsync", ex);
            StatusMessage = $"Error: {ex.Message}";
            IsJugarEnabled = true;
        }
    }

    private IProgress<int> CreateProgress() => new Progress<int>(percent =>
    {
        ProgressValue = percent;
        IsProgressIndeterminate = false;
    });

    private IProgress<DownloadProgress> CreateDownloadProgress() => new Progress<DownloadProgress>(dp =>
    {
        DownloadPercent = dp.Percent;
        DownloadDetail = dp.Message;
        ProgressValue = dp.Percent;
        IsProgressIndeterminate = false;
    });

    private IProgress<string> CreateStringProgress() => new Progress<string>(message =>
    {
        StatusMessage = message;
    });

    [RelayCommand]
    private async Task ImportSkinAsync()
    {
        try
        {
            var dialog = new OpenFileDialog
            {
                Filter = "Archivos PNG (*.png)|*.png",
                Title = "Selecciona tu skin"
            };

            if (dialog.ShowDialog() != true) return;

            var gameDir = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
                "VerkkuCraft", "game", Manifest?.ServerVersion ?? "1.21.1");

            StatusMessage = "Importando skin...";
            await _skinService.ImportSkinFileAsync(dialog.FileName, gameDir);
            StatusMessage = "Skin importada correctamente. Abre SkinShuffle en el juego para activarla.";

            // Show cropped face in preview
            var skinBytes = await File.ReadAllBytesAsync(dialog.FileName);
            SkinPreview = CropFaceForPreview(skinBytes);
        }
        catch (Exception ex)
        {
            AppLogger.LogError("ImportSkin", ex);
            StatusMessage = $"Error al importar skin: {ex.Message}";
        }
    }

    private static BitmapImage? CropFaceForPreview(byte[] skinBytes)
    {
        var skin = new BitmapImage();
        using (var ms = new MemoryStream(skinBytes))
        {
            skin.BeginInit();
            skin.CacheOption = BitmapCacheOption.OnLoad;
            skin.StreamSource = ms;
            skin.EndInit();
            skin.Freeze();
        }

        var face = new CroppedBitmap(skin, new Int32Rect(8, 8, 8, 8));

        using var output = new MemoryStream();
        var encoder = new PngBitmapEncoder();
        encoder.Frames.Add(BitmapFrame.Create(face));
        encoder.Save(output);
        output.Position = 0;

        var avatar = new BitmapImage();
        avatar.BeginInit();
        avatar.CacheOption = BitmapCacheOption.OnLoad;
        avatar.StreamSource = output;
        avatar.DecodePixelWidth = 64;
        avatar.EndInit();
        avatar.Freeze();
        return avatar;
    }
}
