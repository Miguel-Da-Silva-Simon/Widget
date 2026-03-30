using Microsoft.UI.Xaml;

namespace Widget.TimeTracking.App;

public partial class App : Application
{
    private Window? _window;

    public App()
    {
        InitializeComponent();
    }

    protected override void OnLaunched(LaunchActivatedEventArgs args)
    {
        Composition.AppServices.Initialize();

        _window = new MainWindow();
        _window.Activate();
    }
}
