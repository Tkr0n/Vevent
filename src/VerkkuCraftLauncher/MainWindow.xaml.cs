using System.Windows;
using System.Windows.Input;
using System.Linq;
using VerkkuCraftLauncher.Models;
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
