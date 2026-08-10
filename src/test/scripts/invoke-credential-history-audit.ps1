[CmdletBinding()]
param(
    [switch]$FailOnBlocker
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path

function Invoke-GitLines {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    $output = & git @Arguments 2>$null
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        throw "Git command failed with exit code $exitCode."
    }
    return @($output)
}

function Get-EnvironmentValue {
    param([Parameter(Mandatory = $true)][string]$Name)

    $item = Get-Item -Path "Env:$Name" -ErrorAction SilentlyContinue
    if ($null -eq $item -or [string]::IsNullOrEmpty($item.Value)) {
        return $null
    }
    return [string]$item.Value
}

Push-Location $repositoryRoot
try {
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
        throw 'Git is required for the credential history audit.'
    }

    $commits = Invoke-GitLines -Arguments @('rev-list', '--all')
    $reachableObjects = Invoke-GitLines -Arguments @('rev-list', '--objects', '--all')

    $signatureRules = @(
        @{ Id = 'PRIVATE_KEY'; Pattern = '-----BEGIN [A-Z ]*PRIVATE KEY-----' },
        @{ Id = 'AWS_ACCESS_KEY'; Pattern = 'A(KIA|SIA)[A-Z0-9]{16}' },
        @{ Id = 'GITHUB_TOKEN'; Pattern = 'gh[pousr]_[A-Za-z0-9]{20,}' },
        @{ Id = 'GITLAB_TOKEN'; Pattern = 'glpat-[A-Za-z0-9_-]{20,}' },
        @{ Id = 'SLACK_TOKEN'; Pattern = 'xox[baprs]-[A-Za-z0-9-]{10,}' },
        @{ Id = 'STRIPE_LIVE_KEY'; Pattern = '(sk|rk)_live_[A-Za-z0-9]{16,}' },
        @{ Id = 'SHOPIFY_TOKEN'; Pattern = 'shp(at|ss|ca)_[A-Za-z0-9]{16,}' },
        @{ Id = 'NPM_TOKEN'; Pattern = 'npm_[A-Za-z0-9]{20,}' },
        @{ Id = 'JWT_COMPACT'; Pattern = 'eyJ[A-Za-z0-9_-]{8,}\.eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}' },
        @{ Id = 'URI_CREDENTIALS'; Pattern = '[A-Za-z][A-Za-z0-9+.-]*://[^/@:[:space:]]+:[^/@[:space:]]+@' }
    )

    $signatureHits = @()
    foreach ($commit in $commits) {
        foreach ($rule in $signatureRules) {
            $previousErrorActionPreference = $ErrorActionPreference
            $ErrorActionPreference = 'Continue'
            $rawMatches = & git grep -I -n -E -- $rule.Pattern $commit -- 2>$null
            $grepExitCode = $LASTEXITCODE
            $ErrorActionPreference = $previousErrorActionPreference
            if ($grepExitCode -gt 1) {
                throw "Git history scan failed for rule $($rule.Id)."
            }

            foreach ($rawMatch in @($rawMatches)) {
                if ($rawMatch -notmatch '^[^:]+:(.*?):([0-9]+):(.*)$') {
                    continue
                }
                $path = $Matches[1]
                $lineNumber = [int]$Matches[2]
                $content = $Matches[3]
                $classification = if (
                    $path -match '(^|/)(src/test|test|tests|docs/history)(/|$)' -or
                    $content -match '(?i)(example|placeholder|xxxxx|\.\.\.|your[_ -]|dummy|sample)'
                ) { 'TEST_OR_EXAMPLE' } else { 'REVIEW' }

                $signatureHits += [pscustomobject]@{
                    rule = $rule.Id
                    commit = $commit.Substring(0, 7)
                    path = $path
                    line = $lineNumber
                    classification = $classification
                }
            }
        }
    }

    $signatureLocations = @($signatureHits |
        Sort-Object rule, path, line, classification -Unique)
    $reviewLocations = @($signatureLocations |
        Where-Object { $_.classification -eq 'REVIEW' })
    $exampleLocations = @($signatureLocations |
        Where-Object { $_.classification -eq 'TEST_OR_EXAMPLE' })

    $productionLiteralRecords = @()
    foreach ($commit in $commits) {
        $previousErrorActionPreference = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        $configLines = & git show "$commit`:src/main/resources/application.yml" 2>$null
        $showExitCode = $LASTEXITCODE
        $ErrorActionPreference = $previousErrorActionPreference
        if ($showExitCode -ne 0) {
            continue
        }

        $lineNumber = 0
        foreach ($line in @($configLines)) {
            $lineNumber++
            if ($line -notmatch '(?i)^\s*(password|secret|salt):\s*(.+?)\s*$') {
                continue
            }

            $key = $Matches[1].ToLowerInvariant()
            $rawValue = $Matches[2].Trim().Trim('"').Trim("'")
            $value = ($rawValue -replace '\s+#.*$', '').Trim()
            if (
                [string]::IsNullOrEmpty($value) -or
                $value -match '^\$\{' -or
                $value -match '^(?i:changeme|example|placeholder|<.+>)$'
            ) {
                continue
            }

            $variable = switch ($key) {
                'password' { 'DB_PASSWORD' }
                'secret' { 'JWT_SECRET' }
                'salt' { 'BATCH_SALT' }
            }
            $productionLiteralRecords += [pscustomobject]@{
                variable = $variable
                value = $value
                commit = $commit.Substring(0, 7)
                path = 'src/main/resources/application.yml'
                line = $lineNumber
            }
        }
    }

    $environmentComparisons = @()
    foreach ($variable in @('DB_PASSWORD', 'JWT_SECRET', 'BATCH_SALT')) {
        $currentValue = Get-EnvironmentValue -Name $variable
        $historicalValues = @($productionLiteralRecords |
            Where-Object { $_.variable -eq $variable } |
            ForEach-Object { $_.value } |
            Sort-Object -Unique)
        $matchesHistory = $false
        if ($null -ne $currentValue) {
            $matchesHistory = @($historicalValues |
                Where-Object { $_ -ceq $currentValue }).Count -gt 0
        }
        $environmentComparisons += [pscustomobject]@{
            variable = $variable
            present = $null -ne $currentValue
            matchesHistoricalLiteral = $matchesHistory
            historicalLiteralIdentityCount = $historicalValues.Count
        }
    }

    $dbUsername = Get-EnvironmentValue -Name 'DB_USERNAME'
    $profile = Get-EnvironmentValue -Name 'SPRING_PROFILES_ACTIVE'
    $sensitiveHistoryPaths = @(Invoke-GitLines -Arguments @(
            'log', '--all', '--name-only', '--pretty=format:', '--',
            '*application-local*', '*settings.local*', '*.env', '*.pem',
            '*.key', '*credentials*', '*secrets*'
        ) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
        Sort-Object -Unique)

    $blockers = @()
    $dbPasswordComparison = $environmentComparisons |
        Where-Object { $_.variable -eq 'DB_PASSWORD' }
    $jwtComparison = $environmentComparisons |
        Where-Object { $_.variable -eq 'JWT_SECRET' }

    if (-not $dbPasswordComparison.present) {
        $blockers += 'DB_PASSWORD_NOT_AVAILABLE_FOR_LOCAL_ROTATION_CHECK'
    } elseif ($dbPasswordComparison.matchesHistoricalLiteral) {
        $blockers += 'DB_PASSWORD_MATCHES_GIT_HISTORY'
    }
    if ([string]::IsNullOrEmpty($dbUsername)) {
        $blockers += 'DB_USERNAME_NOT_EXPLICITLY_CONFIGURED'
    } elseif ($dbUsername -cne 'wms_app') {
        $blockers += 'DB_USERNAME_IS_NOT_WMS_APP'
    }
    if (-not $jwtComparison.present) {
        $blockers += 'JWT_SECRET_NOT_AVAILABLE_FOR_LOCAL_ROTATION_CHECK'
    } elseif ($jwtComparison.matchesHistoricalLiteral) {
        $blockers += 'JWT_SECRET_MATCHES_GIT_HISTORY'
    }
    if ($reviewLocations.Count -gt 0) {
        $blockers += 'HIGH_CONFIDENCE_SIGNATURE_REQUIRES_REVIEW'
    }

    $result = [pscustomobject]@{
        auditVersion = 1
        repositoryHead = (Invoke-GitLines -Arguments @('rev-parse', 'HEAD') |
            Select-Object -First 1)
        commitCount = $commits.Count
        reachableObjectPathCount = $reachableObjects.Count
        signatureRuleCount = $signatureRules.Count
        highConfidenceReviewLocationCount = $reviewLocations.Count
        testOrExampleSignatureLocationCount = $exampleLocations.Count
        sensitiveHistoryPaths = $sensitiveHistoryPaths
        environmentComparisons = $environmentComparisons
        dbUsername = [pscustomobject]@{
            present = -not [string]::IsNullOrEmpty($dbUsername)
            isExpectedLeastPrivilegeAccount = $dbUsername -ceq 'wms_app'
        }
        springProfile = [pscustomobject]@{
            present = -not [string]::IsNullOrEmpty($profile)
            isProduction = $profile -ceq 'prod'
        }
        releaseCredentialGate = if ($blockers.Count -eq 0) { 'PASS' } else { 'BLOCKED' }
        blockers = $blockers
    }

    $result | ConvertTo-Json -Depth 6
    if ($FailOnBlocker -and $blockers.Count -gt 0) {
        exit 2
    }
}
finally {
    Pop-Location
}
