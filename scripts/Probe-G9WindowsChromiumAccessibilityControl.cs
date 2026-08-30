using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Runtime.InteropServices;
using System.Runtime.Serialization;
using System.Runtime.Serialization.Json;
using System.Text;
using System.Threading;
using System.Windows.Automation;
using Accessibility;

[DataContract]
internal sealed class ControlElement
{
    [DataMember(Name = "name")]
    public string Name = "";

    [DataMember(Name = "roleValue")]
    public int RoleValue;

    [DataMember(Name = "controlType")]
    public string ControlType = "";
}

[DataContract]
internal sealed class ControlResult
{
    [DataMember(Name = "passed")]
    public bool Passed;

    [DataMember(Name = "edgeVersion")]
    public string EdgeVersion = "";

    [DataMember(Name = "narratorStarted")]
    public bool NarratorStarted;

    [DataMember(Name = "narratorRunningDuringProbe")]
    public bool NarratorRunningDuringProbe;

    [DataMember(Name = "windowObserved")]
    public bool WindowObserved;

    [DataMember(Name = "uiaElementCount")]
    public int UiaElementCount;

    [DataMember(Name = "uiaButtonCount")]
    public int UiaButtonCount;

    [DataMember(Name = "uiaButtons")]
    public List<string> UiaButtons = new List<string>();

    [DataMember(Name = "uiaExpectedButtonCount")]
    public int UiaExpectedButtonCount;

    [DataMember(Name = "uiaExpectedButtons")]
    public List<string> UiaExpectedButtons = new List<string>();

    [DataMember(Name = "rendererHandle")]
    public long RendererHandle;

    [DataMember(Name = "rendererUiaElementCount")]
    public int RendererUiaElementCount;

    [DataMember(Name = "rendererUiaButtonCount")]
    public int RendererUiaButtonCount;

    [DataMember(Name = "rendererUiaExpectedButtonCount")]
    public int RendererUiaExpectedButtonCount;

    [DataMember(Name = "rendererUiaExpectedButtons")]
    public List<string> RendererUiaExpectedButtons = new List<string>();

    [DataMember(Name = "msaaHresult")]
    public int MsaaHresult;

    [DataMember(Name = "msaaRootChildCount")]
    public int MsaaRootChildCount;

    [DataMember(Name = "msaaElementCount")]
    public int MsaaElementCount;

    [DataMember(Name = "msaaButtonCount")]
    public int MsaaButtonCount;

    [DataMember(Name = "msaaExpectedButtonCount")]
    public int MsaaExpectedButtonCount;

    [DataMember(Name = "msaaExpectedButtons")]
    public List<string> MsaaExpectedButtons = new List<string>();

    [DataMember(Name = "msaaElements")]
    public List<ControlElement> MsaaElements = new List<ControlElement>();

    [DataMember(Name = "error", EmitDefaultValue = false)]
    public string Error;

    [DataMember(Name = "completedAt")]
    public string CompletedAt = "";
}

internal static class Program
{
    private delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);

    [DllImport("user32.dll")]
    private static extern bool EnumWindows(EnumWindowsProc callback, IntPtr lParam);

    [DllImport("user32.dll")]
    private static extern bool EnumChildWindows(IntPtr hWndParent, EnumWindowsProc callback, IntPtr lParam);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern int GetClassName(IntPtr hWnd, StringBuilder className, int maxCount);

    [DllImport("user32.dll")]
    private static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint processId);

    [DllImport("user32.dll")]
    private static extern bool SetForegroundWindow(IntPtr hWnd);

    [DllImport("oleacc.dll")]
    private static extern int AccessibleObjectFromWindow(IntPtr hWnd, uint dwObjectId, ref Guid riid,
        [MarshalAs(UnmanagedType.Interface)] out IAccessible accessibleObject);

    [DllImport("oleacc.dll")]
    private static extern int AccessibleChildren(IAccessible paccContainer, int iChildStart, int cChildren,
        [Out, MarshalAs(UnmanagedType.LPArray, SizeParamIndex = 2)] object[] rgvarChildren, out int pcObtained);

    private const uint OBJID_CLIENT = 0xFFFFFFFC;
    private const int ROLE_SYSTEM_PUSHBUTTON = 0x2B;
    private static readonly string[] ExpectedButtons = { "Control One", "Control Two", "Control Three" };

    private static int Main(string[] args)
    {
        var result = new ControlResult();
        string resultPath = null;
        try
        {
            var options = ParseArgs(args);
            string edgePath = Required(options, "edge");
            string htmlPath = Required(options, "html");
            resultPath = Required(options, "result");
            RunProbe(edgePath, htmlPath, result);
        }
        catch (Exception error)
        {
            result.Error = error.GetType().Name + ": " + error.Message;
        }
        finally
        {
            result.CompletedAt = DateTimeOffset.Now.ToString("o");
            if (!string.IsNullOrWhiteSpace(resultPath))
                WriteResult(resultPath, result);
        }
        return result.Passed ? 0 : 1;
    }

    private static void CaptureRendererUia(IntPtr renderer, ControlResult result)
    {
        try
        {
            AutomationElement root = AutomationElement.FromHandle(renderer);
            if (root == null)
                return;

            int elementCount = 0;
            int buttonCount = 0;
            var matched = new HashSet<string>(StringComparer.Ordinal);
            var walker = TreeWalker.RawViewWalker;
            var stack = new Stack<AutomationElement>();
            stack.Push(root);
            while (stack.Count > 0 && elementCount < 1000)
            {
                AutomationElement element = stack.Pop();
                try
                {
                    AutomationElement.AutomationElementInformation current = element.Current;
                    elementCount++;
                    if (current.ControlType == ControlType.Button)
                    {
                        buttonCount++;
                        string name = current.Name ?? "";
                        foreach (string expected in ExpectedButtons)
                            if (string.Equals(name, expected, StringComparison.Ordinal))
                                matched.Add(expected);
                    }

                    var children = new List<AutomationElement>();
                    AutomationElement child = walker.GetFirstChild(element);
                    while (child != null && children.Count < 500)
                    {
                        children.Add(child);
                        child = walker.GetNextSibling(child);
                    }
                    for (int index = children.Count - 1; index >= 0; index--)
                        stack.Push(children[index]);
                }
                catch (ElementNotAvailableException)
                {
                }
                catch (InvalidOperationException)
                {
                }
            }

            result.RendererUiaElementCount = Math.Max(result.RendererUiaElementCount, elementCount);
            result.RendererUiaButtonCount = Math.Max(result.RendererUiaButtonCount, buttonCount);
            if (matched.Count > result.RendererUiaExpectedButtonCount)
            {
                result.RendererUiaExpectedButtonCount = matched.Count;
                result.RendererUiaExpectedButtons = matched.OrderBy(value => value, StringComparer.Ordinal).ToList();
            }
        }
        catch (ElementNotAvailableException)
        {
        }
        catch (InvalidOperationException)
        {
        }
        catch (ArgumentException)
        {
        }
    }

    private static void RunProbe(string edgePath, string htmlPath, ControlResult result)
    {
        if (!File.Exists(edgePath))
            throw new FileNotFoundException("Edge executable was not found: " + edgePath, edgePath);
        if (!File.Exists(htmlPath))
            throw new FileNotFoundException("Control page was not found: " + htmlPath, htmlPath);

        result.EdgeVersion = FileVersionInfo.GetVersionInfo(edgePath).ProductVersion ?? "";
        int sessionId = Process.GetCurrentProcess().SessionId;
        StopSessionProcesses("msedge", sessionId);
        StopSessionProcesses("Narrator", sessionId);
        Thread.Sleep(1000);

        string narrator = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Windows), "System32", "Narrator.exe");
        if (File.Exists(narrator))
        {
            Process.Start(new ProcessStartInfo { FileName = narrator, UseShellExecute = true });
            result.NarratorStarted = true;
            Thread.Sleep(4000);
            result.NarratorRunningDuringProbe = HasSessionProcess("Narrator", sessionId);
        }

        string profileDir = Path.Combine(Path.GetTempPath(), "Copperbench-Edge-A11y-Control");
        Directory.CreateDirectory(profileDir);
        string fileUrl = new Uri(htmlPath).AbsoluteUri;
        Process.Start(new ProcessStartInfo
        {
            FileName = edgePath,
            Arguments = "--user-data-dir=\"" + profileDir + "\" --no-first-run --no-default-browser-check "
                + "--disable-sync --force-renderer-accessibility=complete "
                + "--enable-features=UiaProvider "
                + "--disable-features=msEdgeFirstRunExperience,SelectiveUIAEnablement "
                + "--new-window \"" + fileUrl + "\"",
            UseShellExecute = false
        });

        try
        {
            DateTime deadline = DateTime.UtcNow.AddSeconds(75);
            while (DateTime.UtcNow < deadline)
            {
                Thread.Sleep(500);
                AutomationElement frame = FindControlWindow();
                if (frame == null)
                    continue;

                result.WindowObserved = true;
                try
                {
                    int frameHandle = frame.Current.NativeWindowHandle;
                    if (frameHandle != 0)
                        SetForegroundWindow(new IntPtr(frameHandle));
                    frame.SetFocus();
                    Thread.Sleep(500);
                }
                catch (ElementNotAvailableException)
                {
                    continue;
                }
                catch (InvalidOperationException)
                {
                }
                CaptureUia(frame, result);
                CaptureRendererMsaa(frame.Current.ProcessId, result);
                if (result.UiaExpectedButtonCount >= 3)
                {
                    result.Passed = true;
                    return;
                }
            }
        }
        finally
        {
            StopSessionProcesses("Narrator", sessionId);
            StopSessionProcesses("msedge", sessionId);
        }
    }

    private static AutomationElement FindControlWindow()
    {
        AutomationElementCollection windows = AutomationElement.RootElement.FindAll(TreeScope.Children, Condition.TrueCondition);
        foreach (AutomationElement window in windows)
        {
            try
            {
                AutomationElement.AutomationElementInformation current = window.Current;
                if (current.Name != null && current.Name.IndexOf("Copperbench Accessibility Control", StringComparison.Ordinal) >= 0)
                    return window;
            }
            catch (ElementNotAvailableException)
            {
            }
        }
        return null;
    }

    private static void CaptureUia(AutomationElement frame, ControlResult result)
    {
        int elementCount = 0;
        int buttonCount = 0;
        var buttonNames = new List<string>();
        var matched = new HashSet<string>(StringComparer.Ordinal);
        AutomationElementCollection elements = frame.FindAll(TreeScope.Subtree, Condition.TrueCondition);
        foreach (AutomationElement element in elements)
        {
            try
            {
                AutomationElement.AutomationElementInformation current = element.Current;
                elementCount++;
                if (current.ControlType != ControlType.Button)
                    continue;
                buttonCount++;
                string name = current.Name ?? "";
                if (buttonNames.Count < 100)
                    buttonNames.Add(name);
                foreach (string expected in ExpectedButtons)
                    if (string.Equals(name, expected, StringComparison.Ordinal))
                        matched.Add(expected);
            }
            catch (ElementNotAvailableException)
            {
            }
        }

        result.UiaElementCount = Math.Max(result.UiaElementCount, elementCount);
        if (buttonCount > result.UiaButtonCount)
        {
            result.UiaButtonCount = buttonCount;
            result.UiaButtons = buttonNames;
        }
        if (matched.Count > result.UiaExpectedButtonCount)
        {
            result.UiaExpectedButtonCount = matched.Count;
            result.UiaExpectedButtons = matched.OrderBy(value => value, StringComparer.Ordinal).ToList();
        }
    }

    private static void CaptureRendererMsaa(int browserProcessId, ControlResult result)
    {
        IntPtr renderer = FindRendererWindow(browserProcessId);
        if (renderer == IntPtr.Zero)
            return;

        result.RendererHandle = renderer.ToInt64();
        CaptureRendererUia(renderer, result);
        Guid iid = typeof(IAccessible).GUID;
        IAccessible root;
        int hresult = AccessibleObjectFromWindow(renderer, OBJID_CLIENT, ref iid, out root);
        result.MsaaHresult = hresult;
        if (hresult < 0 || root == null)
            return;

        try
        {
            result.MsaaRootChildCount = root.accChildCount;
        }
        catch (COMException)
        {
            result.MsaaRootChildCount = -1;
        }

        int elementCount = 0;
        int buttonCount = 0;
        var matched = new HashSet<string>(StringComparer.Ordinal);
        var snapshot = new List<ControlElement>();
        WalkMsaa(root, 0, 0, snapshot, ref elementCount, ref buttonCount, matched);
        if (elementCount > result.MsaaElementCount)
        {
            result.MsaaElementCount = elementCount;
            result.MsaaButtonCount = buttonCount;
            result.MsaaElements = snapshot;
        }
        if (matched.Count > result.MsaaExpectedButtonCount)
        {
            result.MsaaExpectedButtonCount = matched.Count;
            result.MsaaExpectedButtons = matched.OrderBy(value => value, StringComparer.Ordinal).ToList();
        }
    }

    private static IntPtr FindRendererWindow(int browserProcessId)
    {
        IntPtr renderer = IntPtr.Zero;
        EnumWindows(delegate(IntPtr topLevel, IntPtr ignored)
        {
            if (TryRenderer(topLevel, browserProcessId))
            {
                renderer = topLevel;
                return false;
            }
            EnumChildWindows(topLevel, delegate(IntPtr child, IntPtr childIgnored)
            {
                if (!TryRenderer(child, browserProcessId))
                    return true;
                renderer = child;
                return false;
            }, IntPtr.Zero);
            return renderer == IntPtr.Zero;
        }, IntPtr.Zero);
        return renderer;
    }

    private static bool TryRenderer(IntPtr handle, int browserProcessId)
    {
        var className = new StringBuilder(256);
        GetClassName(handle, className, className.Capacity);
        if (!string.Equals(className.ToString(), "Chrome_RenderWidgetHostHWND", StringComparison.Ordinal))
            return false;
        uint processId;
        GetWindowThreadProcessId(handle, out processId);
        return unchecked((int)processId) == browserProcessId;
    }

    private static void WalkMsaa(IAccessible accessible, int childId, int depth, List<ControlElement> snapshot,
        ref int elementCount, ref int buttonCount, HashSet<string> matched)
    {
        const int maxNodes = 1000;
        if (accessible == null || elementCount >= maxNodes || depth > 80)
            return;

        object variantChild = childId;
        string name = SafeMsaaString(() => accessible.get_accName(variantChild));
        object role = SafeMsaaObject(() => accessible.get_accRole(variantChild));
        int roleValue = MsaaInt(role);
        elementCount++;
        if (roleValue == ROLE_SYSTEM_PUSHBUTTON)
        {
            buttonCount++;
            foreach (string expected in ExpectedButtons)
                if (string.Equals(name, expected, StringComparison.Ordinal))
                    matched.Add(expected);
        }
        if (snapshot.Count < 200)
            snapshot.Add(new ControlElement { Name = name, RoleValue = roleValue, ControlType = "MSAA" });

        if (childId != 0)
            return;

        int childCount;
        try
        {
            childCount = accessible.accChildCount;
        }
        catch (COMException)
        {
            return;
        }
        int capped = Math.Min(childCount, 500);
        if (capped <= 0)
            return;

        var children = new object[capped];
        int obtained;
        if (AccessibleChildren(accessible, 0, capped, children, out obtained) < 0)
            return;
        int limit = Math.Min(obtained, children.Length);
        for (int index = 0; index < limit && elementCount < maxNodes; index++)
        {
            IAccessible childAccessible = children[index] as IAccessible;
            if (childAccessible != null)
                WalkMsaa(childAccessible, 0, depth + 1, snapshot, ref elementCount, ref buttonCount, matched);
            else
            {
                int enumeratedChildId = MsaaInt(children[index]);
                if (enumeratedChildId > 0)
                    WalkMsaa(accessible, enumeratedChildId, depth + 1, snapshot, ref elementCount, ref buttonCount, matched);
            }
        }
    }

    private static string SafeMsaaString(Func<string> getter)
    {
        try { return getter() ?? ""; }
        catch (COMException) { return ""; }
        catch (ArgumentException) { return ""; }
    }

    private static object SafeMsaaObject(Func<object> getter)
    {
        try { return getter(); }
        catch (COMException) { return null; }
        catch (ArgumentException) { return null; }
    }

    private static int MsaaInt(object value)
    {
        if (value == null) return 0;
        try { return Convert.ToInt32(value); }
        catch (Exception error)
        {
            if (error is FormatException || error is InvalidCastException || error is OverflowException)
                return 0;
            throw;
        }
    }

    private static bool HasSessionProcess(string processName, int sessionId)
    {
        foreach (Process process in Process.GetProcessesByName(processName))
        {
            try { if (process.SessionId == sessionId) return true; }
            catch (InvalidOperationException) { }
            finally { process.Dispose(); }
        }
        return false;
    }

    private static void StopSessionProcesses(string processName, int sessionId)
    {
        foreach (Process process in Process.GetProcessesByName(processName))
        {
            try
            {
                if (process.SessionId == sessionId && !process.HasExited)
                    process.Kill();
            }
            catch (InvalidOperationException) { }
            catch (System.ComponentModel.Win32Exception) { }
            finally { process.Dispose(); }
        }
    }

    private static Dictionary<string, string> ParseArgs(string[] args)
    {
        var values = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        for (int index = 0; index < args.Length; index += 2)
        {
            if (index + 1 >= args.Length || !args[index].StartsWith("--", StringComparison.Ordinal))
                throw new ArgumentException("Expected --name value pairs");
            values[args[index].Substring(2)] = args[index + 1];
        }
        return values;
    }

    private static string Required(Dictionary<string, string> options, string name)
    {
        string value;
        if (!options.TryGetValue(name, out value) || string.IsNullOrWhiteSpace(value))
            throw new ArgumentException("Missing --" + name);
        return value;
    }

    private static void WriteResult(string path, ControlResult result)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(path));
        var serializer = new DataContractJsonSerializer(typeof(ControlResult));
        using (var stream = File.Create(path))
            serializer.WriteObject(stream, result);
    }
}
