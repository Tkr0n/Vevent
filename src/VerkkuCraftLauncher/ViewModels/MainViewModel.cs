using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using VerkkuCraftLauncher.Models;
using VerkkuCraftLauncher.Services;

namespace VerkkuCraftLauncher.ViewModels;

public partial class MainViewModel : ObservableObject
{
    private readonly ManifestService _manifestService;
    private readonly JavaManager _javaManager;
    private readonly ServerManager _serverManager;
    private readonly PluginManager _pluginManager;
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

    public MainViewModel(
        ManifestService manifestService,
        JavaManager javaManager,
        ServerManager serverManager,
        PluginManager pluginManager,
        VpnManager vpnManager,
        MinecraftLauncher minecraftLauncher,
        UpdateManager updateManager,
        GameInstallerService gameInstaller,
        LauncherAccountService accountService)
    {
        _manifestService = manifestService;
        _javaManager = javaManager;
        _serverManager = serverManager;
        _pluginManager = pluginManager;
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
            Username = _accountService.DetectUsername() ?? string.Empty;

            StatusMessage = "Obteniendo manifiesto...";
            Manifest = await _manifestService.FetchManifestAsync();
            
            if (Manifest == null)
            {
                StatusMessage = "Error al obtener manifiesto";
                return;
            }

            IsProgressIndeterminate = false;

            // Check for launcher updates
            if (await _updateManager.CheckForUpdateAsync(Manifest.LauncherVersion))
            {
                StatusMessage = "Actualización disponible";
                IsUpdating = true;
                await _updateManager.UpdateLauncherAsync(Manifest.LauncherDownloadUrl, CreateProgress());
                return;
            }

            // Ensure Java is installed
            StatusMessage = "Verificando Java...";
            await _javaManager.EnsureJavaInstalledAsync();

            // Install game client + Fabric + mods
            StatusMessage = "Instalando juego y mods...";
            _installedGame = await _gameInstaller.EnsureInstalledAsync(Manifest.ServerVersion, Manifest.ClientMods, CreateStringProgress(), CreateProgress());

            // Download plugins
            StatusMessage = "Descargando plugins...";
            foreach (var plugin in Manifest.Plugins)
            {
                await _pluginManager.DownloadPluginAsync(plugin, Manifest.ServerVersion, CreateProgress());
            }

            // Check VPN
            StatusMessage = "Verificando VPN...";
            if (!_vpnManager.IsTailscaleInstalled())
            {
                StatusMessage = "Instalando Tailscale...";
                await _vpnManager.InstallTailscaleAsync(CreateProgress());
            }

            StatusMessage = "Todo listo. Presiona JUGAR para iniciar.";
            IsJugarEnabled = true;
            IsProgressIndeterminate = false;
        }
        catch (Exception ex)
        {
            StatusMessage = $"Error: {ex.Message}";
        }
    }

    [RelayCommand]
    private async Task JugarAsync()
    {
        if (Manifest == null) return;

        IsJugarEnabled = false;
        StatusMessage = "Iniciando Minecraft...";

        _installedGame ??= await _gameInstaller.EnsureInstalledAsync(
            Manifest.ServerVersion, Manifest.ClientMods, CreateStringProgress(), CreateProgress());

        var launched = await _minecraftLauncher.LaunchMinecraftAsync(
            _installedGame,
            _serverManager.GetServerDirectory(Manifest.ServerVersion),
            Username,
            Manifest.DefaultServerConfig.ServerAddress,
            Manifest.DefaultServerConfig.ServerPort,
            CreateStringProgress());

        if (launched)
            StatusMessage = "Minecraft iniciado. ¡Diviértete!";
        else
            StatusMessage = "Error al iniciar Minecraft";

        IsJugarEnabled = true;
    }

    private IProgress<int> CreateProgress() => new Progress<int>(percent =>
    {
        ProgressValue = percent;
        IsProgressIndeterminate = false;
    });

    private IProgress<string> CreateStringProgress() => new Progress<string>(message =>
    {
        StatusMessage = message;
    });
}
