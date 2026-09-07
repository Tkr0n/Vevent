namespace VerkkuCraftLauncher.Models;

public class MinecraftInstance
{
    public string Id { get; set; } = Guid.NewGuid().ToString();
    public string Name { get; set; } = string.Empty;
    public string MinecraftVersion { get; set; } = string.Empty;
    public string JavaPath { get; set; } = string.Empty;
    public int MinMemoryMb { get; set; }
    public int MaxMemoryMb { get; set; }
    public string ServerAddress { get; set; } = string.Empty;
    public string ServerPort { get; set; } = string.Empty;
    public bool UseVPN { get; set; }
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    public DateTime LastPlayed { get; set; }
}
