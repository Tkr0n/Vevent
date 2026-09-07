using System.IO;
using System.Net.Http;

namespace VerkkuCraftLauncher.Helpers;

public class HttpDownloader
{
    private readonly HttpClient _httpClient;

    public HttpDownloader()
    {
        _httpClient = new HttpClient();
        _httpClient.Timeout = TimeSpan.FromMinutes(30);
    }

    public async Task DownloadFileAsync(
        string url,
        string destinationPath,
        IProgress<int>? progress = null,
        CancellationToken cancellationToken = default)
    {
        using var response = await _httpClient.GetAsync(url, HttpCompletionOption.ResponseHeadersRead, cancellationToken);
        response.EnsureSuccessStatusCode();

        var totalBytes = response.Content.Headers.ContentLength ?? -1L;
        var totalBytesRead = 0L;

        using var contentStream = await response.Content.ReadAsStreamAsync(cancellationToken);
        using var fileStream = new FileStream(destinationPath, FileMode.Create, FileAccess.Write, FileShare.None);
        
        var buffer = new byte[8192];
        int bytesRead;

        while ((bytesRead = await contentStream.ReadAsync(buffer, cancellationToken)) > 0)
        {
            await fileStream.WriteAsync(buffer.AsMemory(0, bytesRead), cancellationToken);
            totalBytesRead += bytesRead;

            if (totalBytes > 0)
            {
                var percentage = (int)(totalBytesRead * 100 / totalBytes);
                progress?.Report(percentage);
            }
        }
    }

    public async Task<string> DownloadStringAsync(string url, CancellationToken cancellationToken = default)
    {
        var response = await _httpClient.GetAsync(url, cancellationToken);
        response.EnsureSuccessStatusCode();
        return await response.Content.ReadAsStringAsync(cancellationToken);
    }
}
