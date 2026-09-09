using System.IO;
using VerkkuCraftLauncher.Models;

namespace VerkkuCraftLauncher.Services;

public class ShaderDetectionService
{
    private static readonly string[] LauncherPaths = new[]
    {
        // Minecraft Launcher oficial y SKLauncher (misma ubicación)
        @"%APPDATA%\.minecraft\shaderpacks"
    };

    public List<DetectedShader> DetectShaders()
    {
        var shaders = new List<DetectedShader>();

        foreach (var pathTemplate in LauncherPaths)
        {
            var path = Environment.ExpandEnvironmentVariables(pathTemplate);
            var launcherName = GetLauncherName(path);

            if (!Directory.Exists(path))
                continue;

            try
            {
                var files = Directory.GetFiles(path, "*.zip");
                foreach (var file in files)
                {
                    var fileInfo = new FileInfo(file);
                    shaders.Add(new DetectedShader
                    {
                        Name = Path.GetFileNameWithoutExtension(file),
                        FileName = Path.GetFileName(file),
                        SourcePath = file,
                        Size = fileInfo.Length,
                        SourceLauncher = launcherName
                    });
                }
            }
            catch (UnauthorizedAccessException)
            {
                // Log warning but continue
                continue;
            }
            catch (IOException)
            {
                continue;
            }
        }

        return shaders;
    }

    private static string GetLauncherName(string path)
    {
        if (path.Contains(".minecraft"))
            return "Minecraft Launcher";
        return "Otro launcher";
    }
}
