using System.IO;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
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
    private InstalledGame? _installedGame;

    [ObservableProperty] private string _statusMessage = "Iniciando VerkkuCraft...";
    [ObservableProperty] private int _progressValue;
    [ObservableProperty] private bool _isProgressIndeterminate = true;
    [ObservableProperty] private bool _isJugarEnabled;
    [ObservableProperty] private bool _isUpdating;
    [ObservableProperty] private string _username = string.Empty;
    [ObservableProperty] private LauncherManifest? _manifest;
    [ObservableProperty] private string _downloadDetail = string.Empty;
    [ObservableProperty] private int _downloadPercent;

    public MainViewModel(
        ManifestService manifestService,
        JavaManager javaManager,
        ServerManager serverManager,
        VpnManager vpnManager,
        MinecraftLauncher minecraftLauncher,
        UpdateManager updateManager,
        GameInstallerService gameInstaller,
        LauncherAccountService accountService)
    {
        _manifestService = manifestService;
        _javaManager = javaManager;
        _serverManager = serverManager;
        _vpnManager = vpnManager;
        _minecraftLauncher = minecraftLauncher;
        _updateManager = updateManager;
        _gameInstaller = gameInstaller;
        _accountService = accountService;
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
        if (Manifest == null) return;

        IsJugarEnabled = false;
        StatusMessage = "Verificando conexión VPN...";

        if (!_vpnManager.IsTailscaleConnected())
        {
            StatusMessage = "Tailscale no está conectado. Abre Tailscale e inicia sesión.";
            IsJugarEnabled = true;
            return;
        }

        StatusMessage = "Iniciando Minecraft...";

        _installedGame ??= await _gameInstaller.EnsureInstalledAsync(
            Manifest.ServerVersion, Manifest.ClientMods,
            CreateDownloadProgress(), CreateProgress());

        var gameDir = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
            "VerkkuCraft", "game", Manifest.ServerVersion);
        Directory.CreateDirectory(gameDir);

        var launched = await _minecraftLauncher.LaunchMinecraftAsync(
            _installedGame,
            gameDir,
            Username,
            Manifest.DefaultServerConfig.ServerAddress,
            Manifest.DefaultServerConfig.ServerPort,
            CreateStringProgress());

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
}
