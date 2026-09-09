using System.Windows;
using System.Windows.Input;
using System.Linq;
using VerkkuCraftLauncher.Models;
using VerkkuCraftLauncher.ViewModels;
using Wpf.Ui.Controls;

namespace VerkkuCraftLauncher;

/// <summary>
/// Interaction logic for MainWindow.xaml
/// </summary>
public partial class MainWindow : FluentWindow
{
    public MainWindow()
    {
        InitializeComponent();
    }

    private void Window_Loaded(object sender, RoutedEventArgs e)
    {
        if (DataContext is not MainViewModel viewModel) return;

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

    private void ImportShaders_Click(object sender, RoutedEventArgs e)
    {
        var selectedShaders = ShaderListBox.SelectedItems.Cast<DetectedShader>().ToList();
        if (selectedShaders.Count == 0)
        {
            System.Windows.MessageBox.Show("Selecciona al menos un shader para importar.", "Aviso",
                System.Windows.MessageBoxButton.OK, System.Windows.MessageBoxImage.Information);
            return;
        }

        // TODO: Call ShaderInstallerService in next task
        System.Windows.MessageBox.Show($"{selectedShaders.Count} shader(s) seleccionado(s) para importar.",
            "Importar Shaders", System.Windows.MessageBoxButton.OK, System.Windows.MessageBoxImage.Information);

        ShaderImportPanel.Visibility = Visibility.Collapsed;
    }

    private void SkipShaders_Click(object sender, RoutedEventArgs e)
    {
        ShaderImportPanel.Visibility = Visibility.Collapsed;
    }
}
