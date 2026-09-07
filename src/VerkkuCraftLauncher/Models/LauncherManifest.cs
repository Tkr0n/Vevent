namespace VerkkuCraftLauncher.Models;

public class LauncherManifest
{
    public string LauncherVersion { get; set; } = string.Empty;
    public string LauncherDownloadUrl { get; set; } = string.Empty;
    public string ServerJarUrl { get; set; } = string.Empty;
    public string ServerVersion { get; set; } = string.Empty;
    public string PaperBuild { get; set; } = string.Empty;
    public List<PluginInfo> Plugins { get; set; } = new();
    public ServerConfig DefaultServerConfig { get; set; } = new();
}

public class PluginInfo
{
    public string Name { get; set; } = string.Empty;
    public string FileName { get; set; } = string.Empty;
    public string DownloadUrl { get; set; } = string.Empty;
    public string Version { get; set; } = string.Empty;
    public long FileSize { get; set; }
    public string Sha256 { get; set; } = string.Empty;
}
