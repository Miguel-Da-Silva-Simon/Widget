namespace Widget.TimeTracking.Infrastructure.Mock.Configuration;

public sealed class LocalStateOptions
{
    public string StorageDirectory { get; init; } =
        Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "Widget.TimeTracking");

    public string FileName { get; init; } = "time-tracking-state.json";

    public string SessionFileName { get; init; } = "user-session.json";

    public int MaxHistoryEntries { get; init; } = 20;

    public string FullPath => Path.Combine(StorageDirectory, FileName);

    public string SessionFullPath => Path.Combine(StorageDirectory, SessionFileName);
}
