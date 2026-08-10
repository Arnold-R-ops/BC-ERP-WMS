param(
    [ValidateSet("Preflight", "Execute")]
    [string]$Mode = "Preflight",

    [ValidateSet("All", "v45-hardening", "v452-chaotic-storage")]
    [string]$Suite = "All",

    [string]$HostUrl = "http://127.0.0.1:8080",

    [switch]$AllowBusinessWrites,

    [string]$CleanupPlanPath,

    [switch]$AllowRemoteHost,

    [string]$ReportDirectory
)

$ErrorActionPreference = "Stop"

$workspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$manifestPath = Join-Path $PSScriptRoot "http-acceptance-suites.json"
if ([string]::IsNullOrWhiteSpace($ReportDirectory)) {
    $ReportDirectory = Join-Path $workspaceRoot "target\http-acceptance-preflight"
}

$script:checks = New-Object System.Collections.Generic.List[object]
$script:report = [ordered]@{
    generatedAt = [DateTimeOffset]::Now.ToString("o")
    mode = $Mode
    suite = $Suite
    hostUrl = $HostUrl
    businessWritesExecuted = $false
    checks = $script:checks
    suites = @()
}

function Add-Check {
    param(
        [string]$Name,
        [bool]$Passed,
        [string]$Detail
    )

    $script:checks.Add([ordered]@{
        name = $Name
        passed = $Passed
        detail = $Detail
    })
    $status = if ($Passed) { "PASS" } else { "FAIL" }
    Write-Output "[$status] $Name - $Detail"
}

function Require-Check {
    param(
        [string]$Name,
        [bool]$Condition,
        [string]$Detail
    )

    Add-Check -Name $Name -Passed $Condition -Detail $Detail
    if (-not $Condition) {
        throw "Preflight failed: $Name - $Detail"
    }
}

function Get-MethodCounts {
    param([string]$Path, [string]$Pattern)

    $counts = [ordered]@{ GET = 0; POST = 0; PUT = 0; PATCH = 0; DELETE = 0 }
    foreach ($match in [regex]::Matches((Get-Content -Raw -LiteralPath $Path), $Pattern)) {
        $method = $match.Groups[1].Value.ToUpperInvariant()
        if ($counts.Contains($method)) {
            $counts[$method]++
        }
    }
    return $counts
}

function Test-BackendHealth {
    param([uri]$BaseUri)

    $uri = [uri]::new($BaseUri, "/health/check")
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Method GET -Uri $uri -TimeoutSec 10
        return [ordered]@{ passed = ([int]$response.StatusCode -eq 200); detail = "HTTP $([int]$response.StatusCode)" }
    } catch {
        $status = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
        return [ordered]@{ passed = $false; detail = "HTTP $status; $($_.Exception.Message)" }
    }
}

function Write-Report {
    New-Item -ItemType Directory -Path $ReportDirectory -Force | Out-Null
    $fileName = "http-acceptance-$($Mode.ToLowerInvariant())-$([DateTimeOffset]::Now.ToUnixTimeMilliseconds()).json"
    $path = Join-Path $ReportDirectory $fileName
    [System.IO.File]::WriteAllText(
        $path,
        ($script:report | ConvertTo-Json -Depth 12),
        (New-Object System.Text.UTF8Encoding($false))
    )
    Write-Output "REPORT=$path"
}

try {
    Require-Check "manifest exists" (Test-Path -LiteralPath $manifestPath -PathType Leaf) $manifestPath
    $baseUri = [uri]$HostUrl
    Require-Check "host uses HTTP(S)" ($baseUri.Scheme -in @("http", "https")) $baseUri.AbsoluteUri

    $selectedSuites = @((Get-Content -Raw -LiteralPath $manifestPath | ConvertFrom-Json).suites |
        Where-Object { $Suite -eq "All" -or $_.id -eq $Suite })
    Require-Check "requested suite exists" ($selectedSuites.Count -gt 0) $Suite

    foreach ($suiteDefinition in $selectedSuites) {
        $scriptPath = Join-Path $PSScriptRoot $suiteDefinition.script
        $httpPath = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot $suiteDefinition.httpFile))
        $scriptExists = Test-Path -LiteralPath $scriptPath -PathType Leaf
        $httpExists = Test-Path -LiteralPath $httpPath -PathType Leaf
        Require-Check "suite $($suiteDefinition.id) script exists" $scriptExists $scriptPath
        Require-Check "suite $($suiteDefinition.id) HTTP file exists" $httpExists $httpPath

        $scriptText = Get-Content -Raw -LiteralPath $scriptPath
        $hasHostParameter = $scriptText -match '\[string\]\$HostUrl'
        Require-Check "suite $($suiteDefinition.id) accepts HostUrl" $hasHostParameter "HostUrl parameter required for reproducible routing"

        $httpCounts = Get-MethodCounts -Path $httpPath -Pattern '(?m)^(?:\s*)(GET|POST|PUT|PATCH|DELETE)\s+'
        $scriptCounts = Get-MethodCounts -Path $scriptPath -Pattern '(?m)Invoke-Api\s+(GET|POST|PUT|PATCH|DELETE)\s+'
        $script:report.suites += [ordered]@{
            id = $suiteDefinition.id
            displayName = $suiteDefinition.displayName
            script = $scriptPath
            httpFile = $httpPath
            scriptSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $scriptPath).Hash
            httpSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $httpPath).Hash
            httpMethodCounts = $httpCounts
            scriptMethodCounts = $scriptCounts
            impact = $suiteDefinition.impact
            cleanupStrategy = $suiteDefinition.cleanupStrategy
            safeToExecute = [bool]$suiteDefinition.safeToExecute
        }
        Add-Check "suite $($suiteDefinition.id) business-write inventory" $true "HTTP write methods: POST=$($httpCounts.POST), PUT=$($httpCounts.PUT), PATCH=$($httpCounts.PATCH), DELETE=$($httpCounts.DELETE)"
    }

    $health = Test-BackendHealth -BaseUri $baseUri
    Require-Check "backend health read-only probe" $health.passed $health.detail

    if ($Mode -eq "Preflight") {
        Add-Check "business writes not executed" $true "Preflight performs only local file inspection and GET /health/check"
        Write-Report
        exit 0
    }

    Require-Check "explicit business-write authorization" $AllowBusinessWrites.IsPresent "Use -AllowBusinessWrites only after user approval"
    Require-Check "cleanup plan supplied" (-not [string]::IsNullOrWhiteSpace($CleanupPlanPath) -and (Test-Path -LiteralPath $CleanupPlanPath -PathType Leaf)) "A reviewed cleanup plan file is mandatory"
    $isLoopback = $baseUri.Host -in @("127.0.0.1", "localhost", "::1")
    Require-Check "remote host explicitly authorized" ($isLoopback -or $AllowRemoteHost.IsPresent) "Non-loopback execution requires -AllowRemoteHost"

    foreach ($suiteDefinition in $selectedSuites) {
        Require-Check "suite $($suiteDefinition.id) has an automated cleanup strategy" ([bool]$suiteDefinition.safeToExecute) "Current manifest marks this suite NOT_AUTOMATED; execution remains blocked"
    }

    # This line is reached only after every future suite declares a reviewed,
    # automated cleanup strategy. It is intentionally unreachable for today's
    # business-write suites.
    foreach ($suiteDefinition in $selectedSuites) {
        $script:report.businessWritesExecuted = $true
        & (Join-Path $PSScriptRoot $suiteDefinition.script) -HostUrl $HostUrl
        if ($LASTEXITCODE -ne 0) {
            throw "Suite failed: $($suiteDefinition.id)"
        }
    }
    Write-Report
} catch {
    $script:report.error = $_.Exception.Message
    Write-Report
    throw
}
