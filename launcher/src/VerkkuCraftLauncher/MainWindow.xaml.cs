using System.IO;
using System.Windows;
using System.Windows.Input;
using System.Linq;
using VerkkuCraftLauncher.Helpers;
using VerkkuCraftLauncher.Models;
using VerkkuCraftLauncher.Services;
using VerkkuCraftLauncher.ViewModels;
using Wpf.Ui.Controls;

namespace VerkkuCraftLauncher;

/// <summary>
/// Interaction logic for MainWindow.xaml
/// </summary>
public partial class MainWindow : FluentWindow
{
    private ShaderInstallerService? _shaderInstaller;

    public MainWindow()
    {
        InitializeComponent();
    }

    private void Window_Loaded(object sender, RoutedEventArgs e)
    {
        if (DataContext is not MainViewModel viewModel) return;

        var baseDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "VerkkuCraft");
        var downloader = new HttpDownloader();
        var modrinthService = new ModrinthService(downloader);
        _shaderInstaller = new ShaderInstallerService(modrinthService, downloader, baseDir);

        var detectedShaders = viewModel.DetectShaders();
        if (detectedShaders.Count > 0)
        {
            var result = System.Windows.MessageBox.Show(
                $"Se encontraron {detectedShaders.Count} shader(s) en otro launcher.\n" +
                "¿Deseas importarlos?",
                "Shaders detectados",
                System.Windows.MessageBoxButton.YesNo,
                System.Windows.MessageBoxImage.Question);

            if (result == System.Windows.MessageBoxResult.Yes)
            {
                viewModel.LoadDetectedShaders(detectedShaders);
                ShaderImportPanel.Visibility = Visibility.Visible;
            }
        }
    }

    private void TitleBar_MouseLeftButtonDown(object sender, MouseButtonEventArgs e)
    {
        if (e.ChangedButton == MouseButton.Left)
        {
            DragMove();
        }
    }

    private void MinimizeButton_Click(object sender, RoutedEventArgs e)
    {
        WindowState = WindowState.Minimized;
    }

    private void CloseButton_Click(object sender, RoutedEventArgs e)
    {
        Close();
    }

    private async void ImportShaders_Click(object sender, RoutedEventArgs e)
    {
        var selectedShaders = ShaderListBox.SelectedItems.Cast<DetectedShader>().ToList();
        if (selectedShaders.Count == 0)
        {
            System.Windows.MessageBox.Show("Selecciona al menos un shader para importar.", "Aviso",
                System.Windows.MessageBoxButton.OK, System.Windows.MessageBoxImage.Information);
            return;
        }

        if (_shaderInstaller == null)
        {
            System.Windows.MessageBox.Show("Error: Servicio de instalación no disponible.", "Error",
                System.Windows.MessageBoxButton.OK, System.Windows.MessageBoxImage.Error);
            return;
        }

        if (DataContext is not MainViewModel viewModel) return;

        var mcVersion = viewModel.Manifest?.ServerVersion ?? "1.21.1";
        var shaderMods = viewModel.Manifest?.ShaderMods ?? new List<ShaderModInfo>();

        try
        {
            var progress = new Progress<DownloadProgress>(p =>
            {
                viewModel.StatusMessage = p.Message;
            });

            var result = await _shaderInstaller.InstallShadersAsync(
                selectedShaders, mcVersion, shaderMods, progress);

            var message = $"Importación completada:\n" +
                          $"- Shaders instalados: {result.Installed.Count}\n" +
                          $"- Shaders omitidos (ya existían): {result.Skipped.Count}\n" +
                          $"- Mods de shader instalados: {result.ModsInstalled.Count}\n" +
                          $"- Mods de shader ya instalados: {result.ModsAlreadyInstalled.Count}";

            if (result.Errors.Count > 0)
            {
                message += $"\n\nErrores:\n{string.Join("\n", result.Errors)}";
            }

            if (result.ModsInstalled.Count == 0 && result.ModsAlreadyInstalled.Count == 0 && result.Errors.Count == 0)
            {
                message += "\n\nNo se instalaron Iris/Sodium. Los shaders no funcionarán sin ellos.";
            }

            System.Windows.MessageBox.Show(message, "Resultado", System.Windows.MessageBoxButton.OK, System.Windows.MessageBoxImage.Information);
        }
        catch (Exception ex)
        {
            System.Windows.MessageBox.Show($"Error al importar shaders: {ex.Message}", "Error",
                System.Windows.MessageBoxButton.OK, System.Windows.MessageBoxImage.Error);
        }

        ShaderImportPanel.Visibility = Visibility.Collapsed;
    }

    private void SkipShaders_Click(object sender, RoutedEventArgs e)
    {
        ShaderImportPanel.Visibility = Visibility.Collapsed;
    }
}
