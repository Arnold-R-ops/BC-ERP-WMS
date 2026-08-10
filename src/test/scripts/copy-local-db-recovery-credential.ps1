[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('postgres', 'wms_app')]
    [string]$Credential,
    [string]$RecoveryFile,
    [switch]$CheckOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
if ([string]::IsNullOrWhiteSpace($RecoveryFile)) {
    $latest = Get-ChildItem -Path (Join-Path $repositoryRoot 'backups\security') `
        -Filter 'local-db-credentials-*.dpapi' -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($null -eq $latest) {
        throw 'No local DPAPI database credential recovery package was found.'
    }
    $RecoveryFile = $latest.FullName
}

Add-Type -AssemblyName System.Security
$protectedBytes = [IO.File]::ReadAllBytes((Resolve-Path $RecoveryFile))
$plainBytes = $null
$json = $null
$secret = $null
try {
    $plainBytes = [Security.Cryptography.ProtectedData]::Unprotect(
        $protectedBytes,
        $null,
        [Security.Cryptography.DataProtectionScope]::CurrentUser
    )
    $json = [Text.Encoding]::UTF8.GetString($plainBytes)
    $bundle = $json | ConvertFrom-Json
    $secret = if ($Credential -eq 'postgres') {
        [string]$bundle.postgresPassword
    } else {
        [string]$bundle.wmsAppPassword
    }
    if ([string]::IsNullOrEmpty($secret)) {
        throw 'The selected credential is absent from the recovery package.'
    }
    if (-not $CheckOnly) {
        Set-Clipboard -Value $secret
    }
    [pscustomobject]@{
        credential = $Credential
        recoverable = $true
        copiedToClipboard = -not $CheckOnly
        recoveryFile = (Resolve-Path $RecoveryFile).Path
    } | ConvertTo-Json
}
finally {
    if ($null -ne $protectedBytes) {
        [Array]::Clear($protectedBytes, 0, $protectedBytes.Length)
    }
    if ($null -ne $plainBytes) {
        [Array]::Clear($plainBytes, 0, $plainBytes.Length)
    }
    $json = $null
    $secret = $null
    $bundle = $null
}
