using System.Windows;
using System.Windows.Threading;
using VerkkuCraftLauncher.Helpers;
using VerkkuCraftLauncher.Services;
using VerkkuCraftLauncher.ViewModels;

namespace VerkkuCraftLauncher;

public partial class App : Application
{
    private MainViewModel? _mainViewModel;

    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);

        DispatcherUnhandledException += OnDispatcherUnhandledException;
        AppDomain.CurrentDomain.UnhandledException += (_, args) =>
        {
            if (args.ExceptionObject is Exception ex)
                AppLogger.LogError("AppDomain", ex);
        };
        TaskScheduler.UnobservedTaskException += (_, args) =>
        {
            AppLogger.LogError("UnobservedTask", args.Exception);
        };

        AppLogger.Log("Launcher starting...");

        try
        {
            var downloader = new HttpDownloader();
            var extractor = new FileExtractor();
            var manifestService = new ManifestService(downloader);
            var javaManager = new JavaManager();
            var serverManager = new ServerManager(downloader);
            var vpnManager = new VpnManager(downloader);
            var minecraftLauncher = new MinecraftLauncher(javaManager);
            var updateManager = new UpdateManager(downloader);
            var mojangMetaService = new MojangMetaService(downloader);
            var fabricService = new FabricService(downloader);
            var modrinthService = new ModrinthService(downloader);
            var gameInstaller = new GameInstallerService(mojangMetaService, fabricService, modrinthService);
            var accountService = new LauncherAccountService();

            _mainViewModel = new MainViewModel(
                manifestService, javaManager, serverManager,
                vpnManager, minecraftLauncher, updateManager, gameInstaller, accountService);

            var mainWindow = new MainWindow { DataContext = _mainViewModel };
            mainWindow.Show();

            AppLogger.Log("Services created, window shown. Starting InitializeAsync...");
            _ = InitializeWithErrorHandling();
        }
        catch (Exception ex)
        {
            AppLogger.LogError("OnStartup", ex);
        }
    }

    private async Task InitializeWithErrorHandling()
    {
        try
        {
            await _mainViewModel!.InitializeAsync();
            AppLogger.Log("InitializeAsync completed.");
        }
        catch (Exception ex)
        {
            AppLogger.LogError("InitializeAsync", ex);
        }
    }

    private void OnDispatcherUnhandledException(object sender, DispatcherUnhandledExceptionEventArgs e)
    {
        AppLogger.LogError("DispatcherUnhandled", e.Exception);
        e.Handled = true;
    }
}
