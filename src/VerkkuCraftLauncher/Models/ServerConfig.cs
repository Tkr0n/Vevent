namespace VerkkuCraftLauncher.Models;

public class ServerConfig
{
    public string ServerName { get; set; } = string.Empty;
    public string ServerAddress { get; set; } = string.Empty;
    public string Motd { get; set; } = string.Empty;
    public int MaxPlayers { get; set; } = 20;
    public int ServerPort { get; set; } = 25565;
    public string GameMode { get; set; } = string.Empty;
    public bool OnlineMode { get; set; }
    public bool AllowFlight { get; set; }
    public bool SpawnProtection { get; set; }
    public int ViewDistance { get; set; } = 10;
    public string Difficulty { get; set; } = string.Empty;
    public bool Whitelist { get; set; }
    public string IpWhitelist { get; set; } = string.Empty;
}
