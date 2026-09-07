namespace VerkkuCraftLauncher.Models;

public class MinecraftInstance
{
    public string Id { get; set; } = Guid.NewGuid().ToString();
    public string Name { get; set; } = string.Empty;
    public string MinecraftVersion { get; set; } = string.Empty;
    public string JavaPath { get; set; } = string.Empty;
    public int MinMemoryMb { get; set; } = 512;
    public int MaxMemoryMb { get; set; } = 2048;
    public string ServerAddress { get; set; } = string.Empty;
    public int ServerPort { get; set; } = 25565;
    public bool UseVPN { get; set; }
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    public DateTime LastPlayed { get; set; }
}
