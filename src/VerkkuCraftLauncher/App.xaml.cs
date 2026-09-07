using System.Windows;
using VerkkuCraftLauncher.Helpers;
using VerkkuCraftLauncher.Services;
using VerkkuCraftLauncher.ViewModels;

namespace VerkkuCraftLauncher;

public partial class App : Application
{
    private MainViewModel? _mainViewModel;

    protected override async void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);

        // Create services
        var downloader = new HttpDownloader();
        var extractor = new FileExtractor();
        var manifestService = new ManifestService(downloader);
        var javaManager = new JavaManager();
        var serverManager = new ServerManager(downloader);
        var pluginManager = new PluginManager(downloader);
        var vpnManager = new VpnManager(downloader);
        var minecraftLauncher = new MinecraftLauncher(javaManager);
        var updateManager = new UpdateManager(downloader);
        var mojangMetaService = new MojangMetaService(downloader);
        var fabricService = new FabricService(downloader);
        var modrinthService = new ModrinthService(downloader);
        var gameInstaller = new GameInstallerService(mojangMetaService, fabricService, modrinthService);
        var accountService = new LauncherAccountService();

        // Create ViewModel
        _mainViewModel = new MainViewModel(
            manifestService,
            javaManager,
            serverManager,
            pluginManager,
            vpnManager,
            minecraftLauncher,
            updateManager,
            gameInstaller,
            accountService);

        // Set MainWindow DataContext and show
        var mainWindow = new MainWindow
        {
            DataContext = _mainViewModel
        };
        mainWindow.Show();

        // Initialize
        await _mainViewModel.InitializeAsync();
    }
}
