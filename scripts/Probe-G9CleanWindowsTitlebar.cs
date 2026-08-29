using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Runtime.InteropServices;
using System.Runtime.Serialization;
using System.Runtime.Serialization.Json;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Windows.Automation;

[DataContract]
internal sealed class ProbeButton
{
    [DataMember(Name = "name")]
    public string Name = "";

    [DataMember(Name = "className")]
    public string ClassName = "";

    [DataMember(Name = "automationId")]
    public string AutomationId = "";

    [DataMember(Name = "enabled")]
    public bool Enabled;

    [DataMember(Name = "offscreen")]
    public bool Offscreen;
}

[DataContract]
internal sealed class ProbeNativeWindow
{
    [DataMember(Name = "handle")]
    public long Handle;

    [DataMember(Name = "className")]
    public string ClassName = "";

    [DataMember(Name = "processId")]
    public int ProcessId;

    [DataMember(Name = "visible")]
    public bool Visible;

    [DataMember(Name = "win32InputEventTarget")]
    public long Win32InputEventTarget;
}

[DataContract]
internal sealed class ProbeElement
{
    [DataMember(Name = "name")]
    public string Name = "";

    [DataMember(Name = "className")]
    public string ClassName = "";

    [DataMember(Name = "controlType")]
    public string ControlType = "";

    [DataMember(Name = "processId")]
    public int ProcessId;

    [DataMember(Name = "nativeWindowHandle")]
    public int NativeWindowHandle;
}

[DataContract]
internal sealed class ProbeResult
{
    [DataMember(Name = "passed")]
    public bool Passed;

    [DataMember(Name = "workspaceWindowObserved")]
    public bool WorkspaceWindowObserved;

    [DataMember(Name = "buttonCount")]
    public int ButtonCount;

    [DataMember(Name = "matched")]
    public List<ProbeButton> Matched = new List<ProbeButton>();

    [DataMember(Name = "buttons")]
    public List<ProbeButton> Buttons = new List<ProbeButton>();

    [DataMember(Name = "visibleElementCount")]
    public int VisibleElementCount;

    [DataMember(Name = "controlTypeCounts")]
    public Dictionary<string, int> ControlTypeCounts = new Dictionary<string, int>();

    [DataMember(Name = "elements")]
    public List<ProbeElement> Elements = new List<ProbeElement>();

    [DataMember(Name = "nativeWindows")]
    public List<ProbeNativeWindow> NativeWindows = new List<ProbeNativeWindow>();

    [DataMember(Name = "sessionChromiumWindows")]
    public List<ProbeNativeWindow> SessionChromiumWindows = new List<ProbeNativeWindow>();

    [DataMember(Name = "wmGetObjectAttempted")]
    public bool WmGetObjectAttempted;

    [DataMember(Name = "wmGetObjectMode")]
    public string WmGetObjectMode = "none";

    [DataMember(Name = "wmGetObjectTargetHandle")]
    public long WmGetObjectTargetHandle;

    [DataMember(Name = "wmGetObjectDelivered")]
    public bool WmGetObjectDelivered;

    [DataMember(Name = "wmGetObjectResult")]
    public long WmGetObjectResult;

    [DataMember(Name = "rawButtonCount")]
    public int RawButtonCount;

    [DataMember(Name = "rawElementCount")]
    public int RawElementCount;

    [DataMember(Name = "rawElements")]
    public List<ProbeElement> RawElements = new List<ProbeElement>();

    [DataMember(Name = "narratorStarted")]
    public bool NarratorStarted;

    [DataMember(Name = "narratorRunningDuringProbe")]
    public bool NarratorRunningDuringProbe;

    [DataMember(Name = "error", EmitDefaultValue = false)]
    public string Error;

    [DataMember(Name = "completedAt")]
    public string CompletedAt = "";
}

internal static class Program
{
    private static readonly Regex RequiredButton = new Regex("生成|构建|测试客户端", RegexOptions.Compiled);
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
    private static extern bool IsWindowVisible(IntPtr hWnd);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern IntPtr GetProp(IntPtr hWnd, string name);

    [DllImport("user32.dll", SetLastError = true)]
    private static extern IntPtr SendMessageTimeout(IntPtr hWnd, uint message, IntPtr wParam, IntPtr lParam,
        uint flags, uint timeout, out IntPtr result);

    private const uint WM_GETOBJECT = 0x003D;
    private const uint SMTO_ABORTIFHUNG = 0x0002;

    private static int Main(string[] args)
    {
        var result = new ProbeResult();
        string resultPath = null;
        try
        {
            var options = ParseArgs(args);
            string workspaceFile = Required(options, "workspace");
            string installDir = Required(options, "install-dir");
            resultPath = Required(options, "result");
            string wmGetObjectMode = Optional(options, "wm-getobject-mode", "none");
            if (wmGetObjectMode != "none" && wmGetObjectMode != "chromium-test" && wmGetObjectMode != "uia-v2")
                throw new ArgumentException("Unsupported --wm-getobject-mode: " + wmGetObjectMode);
            result.WmGetObjectMode = wmGetObjectMode;
            RunProbe(workspaceFile, installDir, wmGetObjectMode, result);
        }
        catch (Exception error)
        {
            result.Error = error.GetType().Name + ": " + error.Message;
        }
        finally
        {
            result.CompletedAt = DateTimeOffset.Now.ToString("o");
            if (!string.IsNullOrWhiteSpace(resultPath)) WriteResult(resultPath, result);
        }
        return result.Passed ? 0 : 1;
    }

    private static void ActivateChromiumAccessibility(int browserProcessId, string mode, ProbeResult result)
    {
        if (mode == "none" || result.WmGetObjectAttempted)
            return;

        ProbeNativeWindow renderer = result.SessionChromiumWindows.FirstOrDefault(window =>
            window.ProcessId == browserProcessId
            && string.Equals(window.ClassName, "Chrome_RenderWidgetHostHWND", StringComparison.Ordinal));
        if (renderer == null)
            return;

        result.WmGetObjectAttempted = true;
        result.WmGetObjectTargetHandle = renderer.Handle;

        IntPtr wParam = mode == "chromium-test" ? new IntPtr(-4) : IntPtr.Zero;
        IntPtr messageResult;
        IntPtr delivered = SendMessageTimeout(new IntPtr(renderer.Handle), WM_GETOBJECT, wParam,
            new IntPtr(1), SMTO_ABORTIFHUNG, 2000, out messageResult);
        result.WmGetObjectDelivered = delivered != IntPtr.Zero;
        result.WmGetObjectResult = messageResult.ToInt64();
        Thread.Sleep(500);
    }

    private static void CaptureSessionChromiumWindows(ProbeResult result)
    {
        int sessionId = Process.GetCurrentProcess().SessionId;
        var windows = new List<ProbeNativeWindow>();
        var seen = new HashSet<long>();

        EnumWindows(delegate(IntPtr topLevel, IntPtr ignored)
        {
            CaptureRelevantWindow(topLevel, sessionId, windows, seen);
            EnumChildWindows(topLevel, delegate(IntPtr child, IntPtr childIgnored)
            {
                CaptureRelevantWindow(child, sessionId, windows, seen);
                return windows.Count < 500;
            }, IntPtr.Zero);
            return windows.Count < 500;
        }, IntPtr.Zero);

        if (windows.Count > result.SessionChromiumWindows.Count)
            result.SessionChromiumWindows = windows;
    }

    private static void CaptureRelevantWindow(IntPtr handle, int sessionId, List<ProbeNativeWindow> windows,
        HashSet<long> seen)
    {
        long rawHandle = handle.ToInt64();
        if (rawHandle == 0 || !seen.Add(rawHandle))
            return;

        var className = new StringBuilder(256);
        GetClassName(handle, className, className.Capacity);
        string name = className.ToString();
        if (name.IndexOf("Chrome", StringComparison.OrdinalIgnoreCase) < 0
            && name.IndexOf("Cef", StringComparison.OrdinalIgnoreCase) < 0
            && name.IndexOf("D3D", StringComparison.OrdinalIgnoreCase) < 0)
            return;

        uint processId;
        GetWindowThreadProcessId(handle, out processId);
        try
        {
            using (Process process = Process.GetProcessById(unchecked((int)processId)))
            {
                if (process.SessionId != sessionId)
                    return;
            }
        }
        catch (ArgumentException)
        {
            return;
        }
        catch (InvalidOperationException)
        {
            return;
        }

        windows.Add(new ProbeNativeWindow
        {
            Handle = rawHandle,
            ClassName = name,
            ProcessId = unchecked((int)processId),
            Visible = IsWindowVisible(handle),
            Win32InputEventTarget = GetProp(handle, "Win32_InputEventTarget").ToInt64()
        });
    }

    private static void RunProbe(string workspaceFile, string installDir, string wmGetObjectMode, ProbeResult result)
    {
        string launcher = Path.Combine(installDir, "copperbench.exe");
        if (!File.Exists(launcher)) throw new FileNotFoundException("Copperbench launcher was not found.", launcher);
        if (!File.Exists(workspaceFile)) throw new FileNotFoundException("Workspace was not found.", workspaceFile);

        int sessionId = Process.GetCurrentProcess().SessionId;
        StopSessionProcesses("copperbench", sessionId);
        StopSessionProcesses("javaw", sessionId);
        StopSessionProcesses("Narrator", sessionId);
        Thread.Sleep(TimeSpan.FromSeconds(2));

        string narrator = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Windows), "System32", "Narrator.exe");
        if (File.Exists(narrator))
        {
            Process.Start(new ProcessStartInfo { FileName = narrator, UseShellExecute = true });
            result.NarratorStarted = true;
            Thread.Sleep(TimeSpan.FromSeconds(5));
            result.NarratorRunningDuringProbe = HasSessionProcess("Narrator", sessionId);
        }

        Process.Start(new ProcessStartInfo
        {
            FileName = launcher,
            Arguments = "-workspace \"" + workspaceFile.Replace("\"", "\\\"") + "\"",
            UseShellExecute = false
        });

        try
        {
            DateTime deadline = DateTime.UtcNow.AddMinutes(2);
            while (DateTime.UtcNow < deadline)
            {
                Thread.Sleep(500);
                foreach (Process process in Process.GetProcessesByName("javaw"))
                {
                    try
                    {
                        if (process.SessionId != sessionId) continue;
                        InspectProcess(process.Id, wmGetObjectMode, result);
                    }
                    catch (InvalidOperationException)
                    {
                        // The process can exit while the UI Automation tree is being read.
                    }
                    finally
                    {
                        process.Dispose();
                    }
                }
                result.Matched = result.Buttons.Where(button => RequiredButton.IsMatch(button.Name)).ToList();
                result.ButtonCount = result.Buttons.Count;
                if (result.WorkspaceWindowObserved && result.Matched.Count >= 3)
                {
                    result.Passed = true;
                    return;
                }
            }
        }
        finally
        {
            StopSessionProcesses("Narrator", sessionId);
        }
    }

    private static void InspectProcess(int processId, string wmGetObjectMode, ProbeResult result)
    {
        var processCondition = new PropertyCondition(AutomationElement.ProcessIdProperty, processId);
        var frameCondition = new PropertyCondition(AutomationElement.ClassNameProperty, "SunAwtFrame");
        var topLevelCondition = new AndCondition(processCondition, frameCondition);
        AutomationElementCollection frames = AutomationElement.RootElement.FindAll(TreeScope.Children, topLevelCondition);
        var buttons = new List<ProbeButton>();
        result.VisibleElementCount = 0;
        result.ControlTypeCounts.Clear();
        result.Elements.Clear();
        result.RawButtonCount = 0;
        result.RawElementCount = 0;
        result.RawElements.Clear();
        foreach (AutomationElement frame in frames)
        {
            try
            {
                AutomationElement.AutomationElementInformation frameCurrent = frame.Current;
                if (!frameCurrent.Name.Contains(" - Copperbench") || frameCurrent.NativeWindowHandle == 0
                    || frameCurrent.IsOffscreen)
                    continue;

                result.WorkspaceWindowObserved = true;
                CaptureNativeWindows(new IntPtr(frameCurrent.NativeWindowHandle), result);
                CaptureSessionChromiumWindows(result);
                ActivateChromiumAccessibility(frameCurrent.ProcessId, wmGetObjectMode, result);

                var rawButtons = new List<ProbeButton>();
                WalkRawTree(frame, rawButtons, result);
                result.RawButtonCount = Math.Max(result.RawButtonCount, rawButtons.Count);
                if (rawButtons.Count > buttons.Count)
                    buttons = rawButtons;

                var controlButtons = new List<ProbeButton>();
                AutomationElementCollection elements = frame.FindAll(TreeScope.Subtree, Condition.TrueCondition);
                foreach (AutomationElement element in elements)
                {
                    try
                    {
                        AutomationElement.AutomationElementInformation current = element.Current;
                        if (current.ControlType == ControlType.Button && controlButtons.Count < 100)
                        {
                            controlButtons.Add(new ProbeButton
                            {
                                Name = current.Name ?? "",
                                ClassName = current.ClassName ?? "",
                                AutomationId = current.AutomationId ?? "",
                                Enabled = current.IsEnabled,
                                Offscreen = current.IsOffscreen
                            });
                        }
                        if (!current.IsOffscreen)
                        {
                            result.VisibleElementCount++;
                            string type = current.ControlType.ProgrammaticName ?? "unknown";
                            int count;
                            result.ControlTypeCounts.TryGetValue(type, out count);
                            result.ControlTypeCounts[type] = count + 1;
                            if (result.Elements.Count < 200)
                            {
                                result.Elements.Add(new ProbeElement
                                {
                                    Name = current.Name ?? "",
                                    ClassName = current.ClassName ?? "",
                                    ControlType = type,
                                    ProcessId = current.ProcessId,
                                    NativeWindowHandle = current.NativeWindowHandle
                                });
                            }
                        }
                    }
                    catch (ElementNotAvailableException)
                    {
                        // Chromium can replace accessibility nodes while rendering.
                    }
                }
                if (controlButtons.Count > buttons.Count)
                    buttons = controlButtons;
            }
            catch (ElementNotAvailableException)
            {
                // The top-level frame can disappear while the tree is being read.
            }
        }
        if (buttons.Count > result.Buttons.Count) result.Buttons = buttons;
    }

    private static void WalkRawTree(AutomationElement root, List<ProbeButton> buttons, ProbeResult result)
    {
        const int maxNodes = 1000;
        var walker = TreeWalker.RawViewWalker;
        var stack = new Stack<AutomationElement>();
        stack.Push(root);

        while (stack.Count > 0 && result.RawElementCount < maxNodes)
        {
            AutomationElement element = stack.Pop();
            try
            {
                AutomationElement.AutomationElementInformation current = element.Current;
                result.RawElementCount++;
                if (result.RawElements.Count < 300)
                {
                    result.RawElements.Add(new ProbeElement
                    {
                        Name = current.Name ?? "",
                        ClassName = current.ClassName ?? "",
                        ControlType = current.ControlType.ProgrammaticName ?? "unknown",
                        ProcessId = current.ProcessId,
                        NativeWindowHandle = current.NativeWindowHandle
                    });
                }
                if (current.ControlType == ControlType.Button && buttons.Count < 100)
                {
                    buttons.Add(new ProbeButton
                    {
                        Name = current.Name ?? "",
                        ClassName = current.ClassName ?? "",
                        AutomationId = current.AutomationId ?? "",
                        Enabled = current.IsEnabled,
                        Offscreen = current.IsOffscreen
                    });
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
                // Chromium can replace accessibility nodes while rendering.
            }
            catch (InvalidOperationException)
            {
                // A provider can disappear while RawViewWalker is traversing it.
            }
        }
    }

    private static void CaptureNativeWindows(IntPtr frameHandle, ProbeResult result)
    {
        var windows = new List<ProbeNativeWindow>();
        AddNativeWindow(frameHandle, windows);
        EnumChildWindows(frameHandle, delegate(IntPtr child, IntPtr ignored)
        {
            if (windows.Count < 200)
                AddNativeWindow(child, windows);
            return true;
        }, IntPtr.Zero);

        if (windows.Count > result.NativeWindows.Count)
            result.NativeWindows = windows;
    }

    private static void AddNativeWindow(IntPtr handle, List<ProbeNativeWindow> windows)
    {
        var className = new StringBuilder(256);
        GetClassName(handle, className, className.Capacity);
        uint processId;
        GetWindowThreadProcessId(handle, out processId);
        windows.Add(new ProbeNativeWindow
        {
            Handle = handle.ToInt64(),
            ClassName = className.ToString(),
            ProcessId = unchecked((int)processId),
            Visible = IsWindowVisible(handle),
            Win32InputEventTarget = GetProp(handle, "Win32_InputEventTarget").ToInt64()
        });
    }

    private static void StopSessionProcesses(string name, int sessionId)
    {
        foreach (Process process in Process.GetProcessesByName(name))
        {
            try
            {
                if (process.SessionId == sessionId) process.Kill();
            }
            catch (InvalidOperationException)
            {
            }
            finally
            {
                process.Dispose();
            }
        }
    }

    private static bool HasSessionProcess(string name, int sessionId)
    {
        bool found = false;
        foreach (Process process in Process.GetProcessesByName(name))
        {
            try
            {
                if (process.SessionId == sessionId) found = true;
            }
            catch (InvalidOperationException)
            {
            }
            finally
            {
                process.Dispose();
            }
        }
        return found;
    }

    private static Dictionary<string, string> ParseArgs(string[] args)
    {
        var result = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        for (int index = 0; index < args.Length; index += 2)
        {
            if (index + 1 >= args.Length || !args[index].StartsWith("--", StringComparison.Ordinal))
                throw new ArgumentException("Arguments must be --name value pairs.");
            result[args[index].Substring(2)] = args[index + 1];
        }
        return result;
    }

    private static string Required(Dictionary<string, string> options, string key)
    {
        string value;
        if (!options.TryGetValue(key, out value) || string.IsNullOrWhiteSpace(value))
            throw new ArgumentException("Missing required argument --" + key + ".");
        return value;
    }

    private static string Optional(Dictionary<string, string> options, string key, string fallback)
    {
        string value;
        return options.TryGetValue(key, out value) && !string.IsNullOrWhiteSpace(value) ? value : fallback;
    }

    private static void WriteResult(string path, ProbeResult result)
    {
        string directory = Path.GetDirectoryName(path);
        if (!string.IsNullOrWhiteSpace(directory)) Directory.CreateDirectory(directory);
        using (var stream = File.Create(path))
        {
            new DataContractJsonSerializer(typeof(ProbeResult)).WriteObject(stream, result);
        }
    }
}
