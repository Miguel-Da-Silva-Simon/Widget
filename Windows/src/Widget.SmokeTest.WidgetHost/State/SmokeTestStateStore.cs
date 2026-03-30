using System.Text.Json;

namespace Widget.SmokeTest.WidgetHost.State;

internal sealed class SmokeTestStateStore
{
    private readonly string _directory;
    private readonly string _filePath;
    private readonly JsonSerializerOptions _serializerOptions = new()
    {
        WriteIndented = true
    };

    public SmokeTestStateStore()
    {
        _directory = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "Widget.SmokeTest");
        _filePath = Path.Combine(_directory, "smoke-state.json");
    }

    public async Task<SmokeTestState> GetStateAsync(CancellationToken cancellationToken = default)
    {
        if (!File.Exists(_filePath))
        {
            return SmokeTestState.Initial;
        }

        await using var stream = File.OpenRead(_filePath);
        return await JsonSerializer.DeserializeAsync<SmokeTestState>(stream, _serializerOptions, cancellationToken)
            ?? SmokeTestState.Initial;
    }

    public async Task SaveAsync(SmokeTestState state, CancellationToken cancellationToken = default)
    {
        Directory.CreateDirectory(_directory);

        await using var stream = File.Create(_filePath);
        await JsonSerializer.SerializeAsync(stream, state, _serializerOptions, cancellationToken);
        await stream.FlushAsync(cancellationToken);
    }
}
