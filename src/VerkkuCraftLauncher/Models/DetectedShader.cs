namespace VerkkuCraftLauncher.Models;

public class DetectedShader
{
    public string Name { get; set; } = string.Empty;
    public string FileName { get; set; } = string.Empty;
    public string SourcePath { get; set; } = string.Empty;
    public long Size { get; set; }
    public string SourceLauncher { get; set; } = string.Empty;
}
