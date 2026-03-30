using System.Runtime.InteropServices;
using Widget.SmokeTest.WidgetHost.Com;
using Widget.SmokeTest.WidgetHost.Providers;

namespace Widget.SmokeTest.WidgetHost;

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

    [STAThread]
    private static int Main()
    {
        uint cookie = 0;
        try
        {
            var registration = CoRegisterClassObject(
                WidgetHostConstants.WidgetProviderClsid,
                new WidgetProviderFactory<SmokeTestWidgetProvider>(),
                ClsContextLocalServer,
                RegistrationMultipleUse,
                out cookie);

            Marshal.ThrowExceptionForHR(registration);

            SmokeTestWidgetProvider.GetEmptyWidgetListEvent().WaitOne();
            return 0;
        }
        finally
        {
            if (cookie != 0)
            {
                CoRevokeClassObject(cookie);
            }
        }
    }
}
