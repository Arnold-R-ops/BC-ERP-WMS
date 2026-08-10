param(
    [ValidateSet("Check", "Run")]
    [string]$Mode = "Check",

    [ValidateSet("All", "Backend", "Frontend")]
    [string]$Component = "All"
)

$ErrorActionPreference = "Stop"
$workspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$frontendRoot = Join-Path $workspaceRoot "frontend"
$artifactRoot = Join-Path $workspaceRoot "target\ci-verification"
$runBackend = $Component -in @("All", "Backend")
$runFrontend = $Component -in @("All", "Frontend")

function Resolve-Executable {
    param([string[]]$Names)

    foreach ($name in $Names) {
        $command = Get-Command $name -ErrorAction SilentlyContinue
        if ($null -ne $command) {
            return $command.Source
        }
    }
    return $null
}

function Require-Path {
    param([string]$Path, [string]$Description)

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "$Description is missing: $Path"
    }
    Write-Output "[PASS] $Description"
}

function Invoke-CheckedCommand {
    param(
        [string]$Executable,
        [string[]]$Arguments,
        [string]$Description
    )

    # Windows PowerShell converts native stderr into ErrorRecord objects.
    # Build tools legitimately write progress/warnings there, so the process
    # exit code—not the output stream—must determine success.
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & $Executable @Arguments
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousPreference
    }
    if ($exitCode -ne 0) {
        throw "$Description failed with exit code $exitCode."
    }
}

Require-Path -Path (Join-Path $workspaceRoot "pom.xml") -Description "Maven project"
Require-Path -Path (Join-Path $frontendRoot "package.json") -Description "frontend package manifest"
Require-Path -Path (Join-Path $frontendRoot "pnpm-lock.yaml") -Description "frozen pnpm lockfile"
Require-Path -Path (Join-Path $workspaceRoot "docs\frontend\openapi.json") -Description "OpenAPI snapshot"

$maven = Resolve-Executable -Names @("mvn.cmd", "mvn")
$pnpm = Resolve-Executable -Names @("pnpm.cmd", "pnpm")

if ($runBackend -and $null -eq $maven) {
    throw "Maven is required for backend CI verification. Install Maven 3.9+ or run this task in CI."
}
if ($runFrontend -and $null -eq $pnpm) {
    throw "pnpm is required for frontend CI verification. Enable Corepack and use pnpm 11.16.0."
}

if ($runBackend) {
    Write-Output "[PASS] Maven executable: $maven"
}
if ($runFrontend) {
    $pnpmVersion = (& $pnpm --version).Trim()
    if ($LASTEXITCODE -ne 0 -or $pnpmVersion -ne "11.16.0") {
        throw "pnpm 11.16.0 is required; found '$pnpmVersion'."
    }
    Write-Output "[PASS] pnpm version: $pnpmVersion"
}

if ($Mode -eq "Check") {
    Write-Output "CHECK_ONLY=true"
    exit 0
}

New-Item -ItemType Directory -Force -Path $artifactRoot | Out-Null

if ($runBackend) {
    Push-Location $workspaceRoot
    try {
        $env:SPRING_PROFILES_ACTIVE = "test"
        $env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/wms_db_test"
        Invoke-CheckedCommand -Executable $maven `
            -Arguments @("-B", "-ntp", "clean", "verify") `
            -Description "Backend Maven verification"
    }
    finally {
        Pop-Location
    }
}

if ($runFrontend) {
    Push-Location $frontendRoot
    try {
        Invoke-CheckedCommand -Executable $pnpm `
            -Arguments @("install", "--frozen-lockfile") `
            -Description "Frozen frontend dependency installation"

        Invoke-CheckedCommand -Executable $pnpm `
            -Arguments @("test") `
            -Description "Frontend tests"

        Invoke-CheckedCommand -Executable $pnpm `
            -Arguments @("build") `
            -Description "Frontend production build"

        $generatedSchema = Join-Path $artifactRoot "schema.d.ts"
        Invoke-CheckedCommand -Executable $pnpm `
            -Arguments @("exec", "openapi-typescript", "..\docs\frontend\openapi.json", "-o", $generatedSchema) `
            -Description "OpenAPI type generation"

        $checkedInSchema = Join-Path $frontendRoot "src\api\schema.d.ts"
        $difference = Compare-Object `
            (Get-Content -LiteralPath $checkedInSchema -Encoding UTF8) `
            (Get-Content -LiteralPath $generatedSchema -Encoding UTF8)
        if ($null -ne $difference) {
            throw "Generated TypeScript API contract differs from frontend/src/api/schema.d.ts."
        }
    }
    finally {
        Pop-Location
    }
}

Write-Output "CI_VERIFICATION=PASS"
