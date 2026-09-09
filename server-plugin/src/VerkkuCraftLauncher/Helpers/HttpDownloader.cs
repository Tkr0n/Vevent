using System.IO;
using System.Net.Http;

namespace VerkkuCraftLauncher.Helpers;

public class HttpDownloader : IDisposable
{
    private readonly HttpClient _httpClient;

    public HttpDownloader()
    {
        _httpClient = new HttpClient();
        _httpClient.Timeout = TimeSpan.FromMinutes(30);
        _httpClient.DefaultRequestHeaders.UserAgent.ParseAdd("VerkkuCraftLauncher/1.0.0 (https://github.com/Tkr0n/Vevent)");
    }

    public async Task DownloadFileAsync(
        string url,
        string destinationPath,
        IProgress<int>? progress = null,
        CancellationToken cancellationToken = default)
    {
        await RetryAsync(async () =>
        {
            using var response = await _httpClient.GetAsync(url, HttpCompletionOption.ResponseHeadersRead, cancellationToken);
            response.EnsureSuccessStatusCode();

            var totalBytes = response.Content.Headers.ContentLength ?? -1L;
            var totalBytesRead = 0L;

            Directory.CreateDirectory(Path.GetDirectoryName(destinationPath)!);

            using var contentStream = await response.Content.ReadAsStreamAsync(cancellationToken);
            using var fileStream = new FileStream(destinationPath, FileMode.Create, FileAccess.Write, FileShare.None);

            var buffer = new byte[65536];
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
        }, cancellationToken: cancellationToken);
    }

    public async Task<string> DownloadStringAsync(string url, CancellationToken cancellationToken = default)
    {
        return await RetryAsync(async () =>
        {
            using var response = await _httpClient.GetAsync(url, cancellationToken);
            response.EnsureSuccessStatusCode();
            return await response.Content.ReadAsStringAsync(cancellationToken);
        }, cancellationToken: cancellationToken);
    }

    private async Task RetryAsync(Func<Task> action, int maxRetries = 3, CancellationToken cancellationToken = default)
    {
        for (int i = 0; i <= maxRetries; i++)
        {
            try
            {
                await action();
                return;
            }
            catch (Exception) when (i < maxRetries)
            {
                await Task.Delay(TimeSpan.FromSeconds(Math.Pow(2, i)), cancellationToken);
            }
        }
        throw new InvalidOperationException("Unreachable");
    }

    private async Task<T> RetryAsync<T>(Func<Task<T>> action, int maxRetries = 3, CancellationToken cancellationToken = default)
    {
        for (int i = 0; i <= maxRetries; i++)
        {
            try
            {
                return await action();
            }
            catch (Exception) when (i < maxRetries)
            {
                await Task.Delay(TimeSpan.FromSeconds(Math.Pow(2, i)), cancellationToken);
            }
        }
        throw new InvalidOperationException("Unreachable");
    }

    public void Dispose()
    {
        _httpClient?.Dispose();
    }
}
