param([Parameter(Mandatory=$true)][ValidateSet('--authorized-20260919-r11-arnold-elevated-platform-backend-only')][string]$Authorization)
$ErrorActionPreference = 'Stop'
# Test-only, one batch. No power policy, registry, service, scheduled task or input simulation.
Add-Type -TypeDefinition @'
using System;
using System.Diagnostics;
using System.IO;
using System.Runtime.InteropServices;
using System.Security.Principal;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
public static class PlatformR11Awake {
    const string Root = @"D:\ERP_WMS\BC warehouse\2G";
    const string Source = Root + @"\target\platform-db-acceptance-20260919\r11\snapshot\source";
    const string Journal = Root + @"\docs\releases\platform-db-acceptance-20260919\r11\awake-journal.jsonl";
    const string ChildScript = Source + @"\src\test\scripts\platform-r11-database-batch.mjs";
    const string Auth = "--authorized-20260919-r11-arnold-elevated-platform-backend-only";
    const int ObservationBudgetMs = 18000000;
    static string InputId;
    [DllImport("kernel32.dll", SetLastError=true)] static extern uint SetThreadExecutionState(uint flags);
    [DllImport("kernel32.dll")] static extern uint GetCurrentThreadId();
    [DllImport("advapi32.dll", SetLastError=true)] [return: MarshalAs(UnmanagedType.Bool)] static extern bool GetTokenInformation(IntPtr token, int informationClass, out int value, int length, out int needed);
    [DllImport("advapi32.dll")] [return: MarshalAs(UnmanagedType.Bool)] static extern bool IsTokenRestricted(IntPtr token);
    static string Text(string value) { return "\"" + value.Replace("\\", "\\\\").Replace("\"", "\\\"") + "\""; }
    static void Event(StreamWriter log, Stopwatch clock, string kind, string fields) {
        log.WriteLine("{\"event\":" + Text(kind) + ",\"inputId\":" + Text(InputId) + ",\"at\":" + Text(DateTime.UtcNow.ToString("o")) + ",\"elapsedMs\":" + clock.ElapsedMilliseconds + ",\"wrapperPid\":" + Process.GetCurrentProcess().Id + ",\"osThreadId\":" + GetCurrentThreadId() + fields + "}");
        log.Flush();
    }
    public static int Run(string authorization) {
        if (authorization != Auth) return 120;
        InputId = Environment.GetEnvironmentVariable("P3_R11_AWAKE_INPUT_ID");
        if (InputId == null || !Regex.IsMatch(InputId, "^[a-f0-9]{64}$")) return 120;
        var clock = Stopwatch.StartNew();
        using (var file = new FileStream(Journal, FileMode.CreateNew, FileAccess.Write, FileShare.Read))
        using (var log = new StreamWriter(file, new UTF8Encoding(false))) {
            bool acquired = false, releaseConfirmed = false;
            int result = 121;
            uint holderThread = 0;
            Thread.BeginThreadAffinity();
            try {
                holderThread = GetCurrentThreadId();
                Event(log, clock, "WRAPPER_STARTED", ",\"observationBudgetMs\":" + ObservationBudgetMs);
                using (var identity = WindowsIdentity.GetCurrent()) {
                    int elevated, elevationType, needed;
                    if (!GetTokenInformation(identity.Token, 20, out elevated, 4, out needed) || !GetTokenInformation(identity.Token, 18, out elevationType, 4, out needed)) throw new InvalidOperationException("EXECUTION_CONTEXT_QUERY_FAILED");
                    bool admin = new WindowsPrincipal(identity).IsInRole(WindowsBuiltInRole.Administrator);
                    bool restricted = IsTokenRestricted(identity.Token);
                    string sid = identity.User.Value;
                    Event(log, clock, "CONTEXT_CHECKED", ",\"user\":" + Text(identity.Name) + ",\"sid\":" + Text(sid) + ",\"administratorEnabled\":" + admin.ToString().ToLowerInvariant() + ",\"elevated\":" + (elevated != 0).ToString().ToLowerInvariant() + ",\"restrictedToken\":" + restricted.ToString().ToLowerInvariant() + ",\"elevationType\":" + elevationType);
                    if (!String.Equals(identity.Name, @"ARNOLD\Arnold", StringComparison.OrdinalIgnoreCase) || sid != "S-1-5-21-3770167052-703495069-1631994977-1001" || !admin || elevated == 0 || restricted) throw new InvalidOperationException("EXECUTION_CONTEXT_REJECTED");
                }
                uint previous = SetThreadExecutionState(0x80000003u);
                acquired = previous != 0;
                Event(log, clock, "REQUEST_ACQUIRED", ",\"flags\":2147483651,\"result\":" + previous + ",\"confirmed\":" + acquired.ToString().ToLowerInvariant());
                if (!acquired) throw new InvalidOperationException("AWAKE_ACQUIRE_FAILED");
                var info = new ProcessStartInfo(@"D:\JAVA\NODE\node.exe", "\"" + ChildScript + "\" combined " + Auth);
                info.WorkingDirectory = Root;
                info.UseShellExecute = false;
                info.CreateNoWindow = true;
                info.WindowStyle = ProcessWindowStyle.Hidden;
                info.EnvironmentVariables["P3_R11_AWAKE_INPUT_ID"] = InputId;
                info.EnvironmentVariables["P3_R11_AWAKE_WRAPPER_PID"] = Process.GetCurrentProcess().Id.ToString();
                info.EnvironmentVariables["P3_R11_AWAKE_THREAD_ID"] = holderThread.ToString();
                using (var child = new Process()) {
                    child.StartInfo = info;
                    if (!child.Start()) throw new InvalidOperationException("BATCH_CHILD_START_FAILED");
                    Event(log, clock, "CHILD_STARTED", ",\"childPid\":" + child.Id);
                    // This observation ceiling does not extend any frozen per-operation budget.
                    // Host suspension can delay scheduled code; it is not a guaranteed wall-clock deadline.
                    bool exited = child.WaitForExit(ObservationBudgetMs);
                    if (!exited) {
                        Event(log, clock, "OBSERVATION_TIMEOUT", ",\"childPid\":" + child.Id + ",\"childExitConfirmed\":false,\"databaseClosedConfirmed\":false");
                        result = 124; // Never kill a process tree or infer database ownership here.
                    } else {
                        result = child.ExitCode;
                        Event(log, clock, "CHILD_EXITED", ",\"childPid\":" + child.Id + ",\"exitCode\":" + result + ",\"confirmed\":true");
                    }
                }
            } catch (Exception error) {
                // Only exception type and known local code; never persist arbitrary child output/authentication data.
                string code = Regex.IsMatch(error.Message, "^(EXECUTION_CONTEXT_QUERY_FAILED|EXECUTION_CONTEXT_REJECTED|AWAKE_ACQUIRE_FAILED|BATCH_CHILD_START_FAILED)$") ? error.Message : "AWAKE_WRAPPER_ERROR";
                Event(log, clock, "WRAPPER_ERROR", ",\"type\":" + Text(error.GetType().Name) + ",\"code\":" + Text(code));
                result = 125;
            } finally {
                if (acquired) {
                    bool sameThread = GetCurrentThreadId() == holderThread;
                    uint released = sameThread ? SetThreadExecutionState(0x80000000u) : 0;
                    releaseConfirmed = sameThread && released != 0;
                    Event(log, clock, "REQUEST_RELEASED", ",\"flags\":2147483648,\"result\":" + released + ",\"sameThread\":" + sameThread.ToString().ToLowerInvariant() + ",\"confirmed\":" + releaseConfirmed.ToString().ToLowerInvariant());
                    if (!releaseConfirmed && result == 0) result = 126;
                }
                Event(log, clock, "WRAPPER_FINISHED", ",\"exitCode\":" + result + ",\"acquired\":" + acquired.ToString().ToLowerInvariant() + ",\"releaseConfirmed\":" + releaseConfirmed.ToString().ToLowerInvariant());
                Thread.EndThreadAffinity();
            }
            return result;
        }
    }
}
'@
exit [PlatformR11Awake]::Run($Authorization)
