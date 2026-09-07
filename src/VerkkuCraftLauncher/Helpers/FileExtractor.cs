using SharpCompress.Archives;
using SharpCompress.Common;

namespace VerkkuCraftLauncher.Helpers;

public class FileExtractor
{
    public async Task ExtractArchiveAsync(
        string archivePath,
        string destinationPath,
        IProgress<int>? progress = null,
        CancellationToken cancellationToken = default)
    {
        await Task.Run(() =>
        {
            using (var archive = ArchiveFactory.Open(archivePath))
            {
                var totalEntries = archive.Entries.Count();
                var extractedCount = 0;

                foreach (var entry in archive.Entries)
                {
                    cancellationToken.ThrowIfCancellationRequested();

                    if (!entry.IsDirectory)
                    {
                        entry.WriteToDirectory(destinationPath, new ExtractionOptions()
                        {
                            ExtractFullPath = true,
                            Overwrite = true
                        });
                    }

                    extractedCount++;
                    if (totalEntries > 0)
                    {
                        var percentage = (int)(extractedCount * 100 / totalEntries);
                        progress?.Report(percentage);
                    }
                }
            }
        }, cancellationToken);
    }
}
