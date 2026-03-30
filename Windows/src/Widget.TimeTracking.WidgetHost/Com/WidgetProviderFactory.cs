using System.Runtime.InteropServices;
using Microsoft.Windows.Widgets.Providers;
using WinRT;

namespace Widget.TimeTracking.WidgetHost.Com;

internal static class ComGuids
{
    public const string IClassFactory = "00000001-0000-0000-C000-000000000046";
    public const string IUnknown = "00000000-0000-0000-C000-000000000046";
}

[ComImport]
[ComVisible(false)]
[InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
[Guid(ComGuids.IClassFactory)]
internal interface IClassFactory
{
    [PreserveSig]
    int CreateInstance(IntPtr pUnkOuter, ref Guid riid, out IntPtr ppvObject);

    [PreserveSig]
    int LockServer(bool fLock);
}

[ComVisible(true)]
internal sealed class WidgetProviderFactory<TProvider> : IClassFactory
    where TProvider : IWidgetProvider, new()
{
    private const int ClassErrorNoAggregation = unchecked((int)0x80040110);
    private const int ErrorNoInterface = unchecked((int)0x80004002);

    public int CreateInstance(IntPtr pUnkOuter, ref Guid riid, out IntPtr ppvObject)
    {
        ppvObject = IntPtr.Zero;

        if (pUnkOuter != IntPtr.Zero)
        {
            Marshal.ThrowExceptionForHR(ClassErrorNoAggregation);
        }

        var iUnknown = Guid.Parse(ComGuids.IUnknown);

        if (riid == typeof(TProvider).GUID || riid == iUnknown)
        {
            ppvObject = MarshalInspectable<IWidgetProvider>.FromManaged(new TProvider());
            return 0;
        }

        Marshal.ThrowExceptionForHR(ErrorNoInterface);
        return ErrorNoInterface;
    }

    public int LockServer(bool fLock) => 0;
}
