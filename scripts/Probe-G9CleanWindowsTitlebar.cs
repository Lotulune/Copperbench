using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Runtime.Serialization;
using System.Runtime.Serialization.Json;
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
internal sealed class ProbeElement
{
    [DataMember(Name = "name")]
    public string Name = "";

    [DataMember(Name = "className")]
    public string ClassName = "";

    [DataMember(Name = "controlType")]
    public string ControlType = "";
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
            RunProbe(workspaceFile, installDir, result);
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

    private static void RunProbe(string workspaceFile, string installDir, ProbeResult result)
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
                        InspectProcess(process.Id, result);
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

    private static void InspectProcess(int processId, ProbeResult result)
    {
        var condition = new PropertyCondition(AutomationElement.ProcessIdProperty, processId);
        AutomationElementCollection elements = AutomationElement.RootElement.FindAll(TreeScope.Descendants, condition);
        var buttons = new List<ProbeButton>();
        result.VisibleElementCount = 0;
        result.ControlTypeCounts.Clear();
        result.Elements.Clear();
        foreach (AutomationElement element in elements)
        {
            try
            {
                AutomationElement.AutomationElementInformation current = element.Current;
                if (current.ClassName == "SunAwtFrame" && current.Name.Contains(" - Copperbench")
                    && current.NativeWindowHandle != 0 && !current.IsOffscreen)
                {
                    result.WorkspaceWindowObserved = true;
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
                            ControlType = type
                        });
                    }
                }
            }
            catch (ElementNotAvailableException)
            {
                // Chromium can replace accessibility nodes while rendering.
            }
        }
        if (buttons.Count > result.Buttons.Count) result.Buttons = buttons;
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
