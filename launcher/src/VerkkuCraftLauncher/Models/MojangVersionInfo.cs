namespace VerkkuCraftLauncher.Models;

public class MojangLibrary
{
    public string Name { get; set; } = string.Empty; // group:artifact:version
    public MojangArtifact? Downloads_Artifact { get; set; }
    public Dictionary<string, MojangArtifact>? Downloads_Classifiers { get; set; }
    public List<MojangRule>? Rules { get; set; }
    public Dictionary<string, string>? Natives { get; set; }
}

public class MojangArtifact
{
    public string Path { get; set; } = string.Empty;
    public string Url { get; set; } = string.Empty;
    public string Sha1 { get; set; } = string.Empty;
    public long Size { get; set; }
}

public class MojangRule
{
    public string Action { get; set; } = "allow";
    public Dictionary<string, string>? Os { get; set; }
}

public class MojangVersionInfo
{
    public string Id { get; set; } = string.Empty;
    public string ClientJarUrl { get; set; } = string.Empty;
    public string ClientJarSha1 { get; set; } = string.Empty;
    public long ClientJarSize { get; set; }
    public string AssetIndexId { get; set; } = string.Empty;
    public string AssetIndexUrl { get; set; } = string.Empty;
    public List<MojangLibrary> Libraries { get; set; } = new();
}
