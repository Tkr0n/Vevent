namespace VerkkuCraftLauncher.Models;

public class ServerConfig
{
    public string ServerName { get; set; } = string.Empty;
    public string Motd { get; set; } = string.Empty;
    public int MaxPlayers { get; set; }
    public int ServerPort { get; set; }
    public string GameMode { get; set; } = string.Empty;
    public bool OnlineMode { get; set; }
    public bool AllowFlight { get; set; }
    public bool SpawnProtection { get; set; }
    public string ViewDistance { get; set; } = string.Empty;
    public string Difficulty { get; set; } = string.Empty;
    public bool WhiteList { get; set; }
    public string IpWhitelist { get; set; } = string.Empty;
}
