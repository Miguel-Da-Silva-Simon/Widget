using System;
using System.Runtime.InteropServices;
using Widget.TimeTracking.WidgetHost.Com;
using Widget.TimeTracking.WidgetHost.Composition;
using Widget.TimeTracking.WidgetHost.Providers;

namespace Widget.TimeTracking.WidgetHost;

internal static class Program
{
    private const uint ClsContextLocalServer = 0x4;
    private const uint RegistrationMultipleUse = 0x1;

    [DllImport("ole32.dll")]
    private static extern int CoRegisterClassObject(
        [MarshalAs(UnmanagedType.LPStruct)] Guid rclsid,
        [MarshalAs(UnmanagedType.IUnknown)] object pUnk,
        uint dwClsContext,
        uint flags,
        out uint lpdwRegister);

    [DllImport("ole32.dll")]
    private static extern int CoRevokeClassObject(uint dwRegister);

    [DllImport("kernel32.dll")]
    private static extern IntPtr GetConsoleWindow();

    [STAThread]
    private static int Main()
    {
        AppServices.Initialize();

        uint cookie;
        var registration = CoRegisterClassObject(
            WidgetHostConstants.WidgetProviderClsid,
            new WidgetProviderFactory<TimeTrackingWidgetProvider>(),
            ClsContextLocalServer,
            RegistrationMultipleUse,
            out cookie);

        Marshal.ThrowExceptionForHR(registration);

        try
        {
            if (GetConsoleWindow() != IntPtr.Zero)
            {
                Console.WriteLine("Registered successfully. Press ENTER to exit.");
                Console.ReadLine();
            }
            else
            {
                using var emptyWidgetListEvent = TimeTrackingWidgetProvider.GetEmptyWidgetListEvent();
                emptyWidgetListEvent.WaitOne();
            }

            return 0;
        }
        finally
        {
            CoRevokeClassObject(cookie);
        }
    }
}
