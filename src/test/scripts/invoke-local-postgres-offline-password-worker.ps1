[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$WorkerPayload,
    [Parameter(Mandatory = $true)]
    [string]$WorkerResult,
    [Parameter(Mandatory = $true)]
    [string]$PostgresExe,
    [Parameter(Mandatory = $true)]
    [string]$DataDirectory
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$protectedBytes = $null
$plainBytes = $null
$bundle = $null
$newPassword = $null
$sql = $null
$standardOutput = $null
$standardError = $null
$workerSucceeded = $false
$workerErrorCode = $null
$postgresExitCode = $null
$payloadPath = $null
$process = $null

function Write-WorkerResult {
    param(
        [Parameter(Mandatory = $true)][bool]$Succeeded,
        [string]$ErrorCode
    )

    $result = [ordered]@{
        completedAt = (Get-Date).ToString('o')
        workerSid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
        succeeded = $Succeeded
        errorCode = $ErrorCode
        postgresExitCode = $postgresExitCode
    }
    $temporaryResult = "$WorkerResult.tmp"
    [IO.File]::WriteAllText(
        $temporaryResult,
        ($result | ConvertTo-Json),
        (New-Object Text.UTF8Encoding($false))
    )
    [IO.File]::Move($temporaryResult, $WorkerResult)
}

try {
    $workerSid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    if ($workerSid -cne 'S-1-5-20') {
        throw 'WORKER_IDENTITY_NOT_NETWORK_SERVICE'
    }

    $payloadPath = (Resolve-Path -LiteralPath $WorkerPayload).Path
    $postgresPath = (Resolve-Path -LiteralPath $PostgresExe).Path
    $clusterPath = (Resolve-Path -LiteralPath $DataDirectory).Path
    $resultPath = [IO.Path]::GetFullPath($WorkerResult)
    if ([IO.Path]::GetDirectoryName($payloadPath) -cne [IO.Path]::GetDirectoryName($resultPath)) {
        throw 'WORKER_PATH_BOUNDARY_MISMATCH'
    }
    if ([IO.Path]::GetFileName($payloadPath) -cne 'offline-payload.dpapi-machine') {
        throw 'WORKER_PAYLOAD_NAME_INVALID'
    }
    if ([IO.Path]::GetFileName($resultPath) -cne 'offline-result.json') {
        throw 'WORKER_RESULT_NAME_INVALID'
    }
    if ([IO.Path]::GetFileName($postgresPath) -cne 'postgres.exe') {
        throw 'POSTGRES_EXECUTABLE_INVALID'
    }
    if (Test-Path -LiteralPath (Join-Path $clusterPath 'postmaster.pid')) {
        throw 'POSTGRES_CLUSTER_STILL_RUNNING'
    }

    Add-Type -AssemblyName System.Security
    $protectedBytes = [IO.File]::ReadAllBytes($payloadPath)
    $plainBytes = [Security.Cryptography.ProtectedData]::Unprotect(
        $protectedBytes,
        $null,
        [Security.Cryptography.DataProtectionScope]::LocalMachine
    )
    $bundle = ([Text.Encoding]::UTF8.GetString($plainBytes) | ConvertFrom-Json)
    if ([DateTimeOffset]::Parse([string]$bundle.expiresAt) -lt [DateTimeOffset]::Now) {
        throw 'WORKER_PAYLOAD_EXPIRED'
    }
    if ([IO.Path]::GetFullPath([string]$bundle.postgresExe) -cne $postgresPath) {
        throw 'WORKER_POSTGRES_PATH_MISMATCH'
    }
    if ([IO.Path]::GetFullPath([string]$bundle.dataDirectory) -cne $clusterPath) {
        throw 'WORKER_DATA_PATH_MISMATCH'
    }

    $newPassword = [string]$bundle.postgresPassword
    if ($newPassword -cnotmatch '^[A-Za-z0-9_-]{40,128}$') {
        throw 'WORKER_PASSWORD_FORMAT_INVALID'
    }
    $escapedPassword = $newPassword.Replace("'", "''")
    $sql = "ALTER ROLE postgres PASSWORD '$escapedPassword';"

    $startInfo = New-Object Diagnostics.ProcessStartInfo
    $startInfo.FileName = $postgresPath
    $startInfo.Arguments = @(
        '--single'
        '-D', ('"{0}"' -f $clusterPath)
        '-c', 'password_encryption=scram-sha-256'
        '-c', 'log_statement=none'
        '-c', 'log_duration=off'
        '-c', 'log_min_duration_statement=-1'
        '-c', 'log_min_error_statement=panic'
        '-c', 'logging_collector=off'
        'postgres'
    ) -join ' '
    $startInfo.WorkingDirectory = [IO.Path]::GetDirectoryName($postgresPath)
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.EnvironmentVariables.Remove('PGPASSWORD')

    $process = New-Object Diagnostics.Process
    $process.StartInfo = $startInfo
    if (-not $process.Start()) {
        throw 'OFFLINE_POSTGRES_START_FAILED'
    }
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    $process.StandardInput.WriteLine($sql)
    $process.StandardInput.Close()
    if (-not $process.WaitForExit(60000)) {
        try {
            $process.Kill()
            $process.WaitForExit(5000) | Out-Null
        }
        catch {
            # The parent task still treats this worker as failed and performs its own task cleanup.
        }
        throw 'OFFLINE_POSTGRES_TIMEOUT'
    }
    $standardOutput = $stdoutTask.GetAwaiter().GetResult()
    $standardError = $stderrTask.GetAwaiter().GetResult()
    $postgresExitCode = $process.ExitCode
    $process.Dispose()
    $process = $null
    if ($postgresExitCode -ne 0) {
        throw "OFFLINE_POSTGRES_EXIT_$postgresExitCode"
    }
    if ($standardOutput -match '(?im)\b(ERROR|FATAL|PANIC):' -or
        $standardError -match '(?im)\b(ERROR|FATAL|PANIC):') {
        throw 'OFFLINE_POSTGRES_REPORTED_ERROR'
    }

    $workerSucceeded = $true
}
catch {
    $workerErrorCode = [string]$_.Exception.Message
    if (-not [string]::IsNullOrEmpty($newPassword)) {
        $workerErrorCode = $workerErrorCode.Replace($newPassword, '<REDACTED>')
    }
    if ($workerErrorCode -notmatch '^[A-Z0-9_-]+$') {
        $workerErrorCode = 'OFFLINE_WORKER_FAILED'
    }
}
finally {
    if ($null -ne $process) {
        try {
            if (-not $process.HasExited) {
                $process.Kill()
                $process.WaitForExit(5000) | Out-Null
            }
            $process.Dispose()
        }
        catch {
            $workerSucceeded = $false
            $workerErrorCode = 'OFFLINE_PROCESS_CLEANUP_FAILED'
        }
        $process = $null
    }
    try {
        if (-not [string]::IsNullOrWhiteSpace($payloadPath) -and
            [IO.Path]::GetFileName($payloadPath) -ceq 'offline-payload.dpapi-machine' -and
            (Test-Path -LiteralPath $payloadPath -PathType Leaf)) {
            Remove-Item -LiteralPath $payloadPath -Force
        }
    }
    catch {
        $workerSucceeded = $false
        $workerErrorCode = 'WORKER_PAYLOAD_CLEANUP_FAILED'
    }
    if ($null -ne $protectedBytes) {
        [Array]::Clear($protectedBytes, 0, $protectedBytes.Length)
    }
    if ($null -ne $plainBytes) {
        [Array]::Clear($plainBytes, 0, $plainBytes.Length)
    }
    $bundle = $null
    $newPassword = $null
    $sql = $null
    $standardOutput = $null
    $standardError = $null
    try {
        Write-WorkerResult -Succeeded $workerSucceeded -ErrorCode $workerErrorCode
    }
    catch {
        $workerSucceeded = $false
    }
}

if (-not $workerSucceeded) {
    exit 1
}
