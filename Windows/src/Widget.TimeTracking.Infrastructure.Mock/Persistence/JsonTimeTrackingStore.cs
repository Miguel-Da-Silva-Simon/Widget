using System.Text.Json;
using System.Text.Json.Serialization;
using Widget.TimeTracking.Infrastructure.Mock.Configuration;

namespace Widget.TimeTracking.Infrastructure.Mock.Persistence;

public sealed class JsonTimeTrackingStore
{
    private readonly LocalStateOptions _options;
    private readonly JsonSerializerOptions _serializerOptions;

    public JsonTimeTrackingStore(LocalStateOptions options)
    {
        _options = options;
        _serializerOptions = new JsonSerializerOptions
        {
            WriteIndented = true
        };
        _serializerOptions.Converters.Add(new JsonStringEnumConverter());
    }

    public async Task<TimeTrackingStateDocument> LoadAsync(CancellationToken cancellationToken = default)
    {
        if (!File.Exists(_options.FullPath))
        {
            return TimeTrackingStateDocument.CreateDefault();
        }

        try
        {
            await using var stream = File.OpenRead(_options.FullPath);
            var document = await JsonSerializer.DeserializeAsync<TimeTrackingStateDocument>(
                stream,
                _serializerOptions,
                cancellationToken);

            return document ?? TimeTrackingStateDocument.CreateDefault();
        }
        catch (Exception exception) when (exception is IOException or UnauthorizedAccessException or JsonException)
        {
            return TimeTrackingStateDocument.CreateDefault();
        }
    }

    public async Task SaveAsync(TimeTrackingStateDocument document, CancellationToken cancellationToken = default)
    {
        Directory.CreateDirectory(_options.StorageDirectory);

        var temporaryPath = $"{_options.FullPath}.tmp";

        await using (var stream = File.Create(temporaryPath))
        {
            await JsonSerializer.SerializeAsync(stream, document, _serializerOptions, cancellationToken);
            await stream.FlushAsync(cancellationToken);
        }

        File.Move(temporaryPath, _options.FullPath, true);
    }
}
