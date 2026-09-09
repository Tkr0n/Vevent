using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using VerkkuCraftLauncher.Services;

namespace VerkkuCraftLauncher.ViewModels;

public partial class SetupViewModel : ObservableObject
{
    private readonly VpnManager _vpnManager;

    [ObservableProperty] private string _authKey = string.Empty;
    [ObservableProperty] private string _statusMessage = string.Empty;
    [ObservableProperty] private bool _isConnecting;
    [ObservableProperty] private bool _isConnected;

    public SetupViewModel(VpnManager vpnManager)
    {
        _vpnManager = vpnManager;
    }

    [RelayCommand]
    private async Task ConnectAsync()
    {
        if (string.IsNullOrWhiteSpace(AuthKey))
        {
            StatusMessage = "Ingresa una clave de autenticación";
            return;
        }

        IsConnecting = true;
        StatusMessage = "Conectando a Tailscale...";

        try
        {
            await _vpnManager.ConnectWithAuthKeyAsync(AuthKey);
            IsConnected = _vpnManager.IsTailscaleConnected();
            StatusMessage = IsConnected ? "Conectado exitosamente" : "Error al conectar";
        }
        catch (Exception ex)
        {
            StatusMessage = $"Error: {ex.Message}";
        }
        finally
        {
            IsConnecting = false;
        }
    }
}
