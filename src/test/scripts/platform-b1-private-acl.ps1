param([Parameter(Mandatory=$true)][string]$Directory,[Parameter(Mandatory=$true)][string]$OwnerSid)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
$OutputEncoding = [Console]::OutputEncoding
$expectedHome = 'C:\Windows\System32\WindowsPowerShell\v1.0'
$manifest = $expectedHome + '\Modules\Microsoft.PowerShell.Security\Microsoft.PowerShell.Security.psd1'
$targetRoot = 'D:\ERP_WMS\BC warehouse\2G\target\'
$p = [IO.Path]::GetFullPath($Directory)
if ($PSHOME -ne $expectedHome -or $PSVersionTable.PSEdition -ne 'Desktop' -or -not $p.StartsWith($targetRoot,[StringComparison]::OrdinalIgnoreCase)) { throw 'BROWSER_ACL_CONTEXT' }
if ($OwnerSid -ne [Security.Principal.WindowsIdentity]::GetCurrent().User.Value -or $OwnerSid -ne 'S-1-5-21-3770167052-703495069-1631994977-1001') { throw 'BROWSER_ACL_OWNER_CONTEXT' }
if (-not [IO.Directory]::Exists($p) -or ([IO.File]::GetAttributes($p) -band [IO.FileAttributes]::ReparsePoint) -ne 0 -or @([IO.Directory]::EnumerateFileSystemEntries($p)).Count -ne 0) { throw 'BROWSER_ACL_NOT_NEW_EMPTY_DIRECTORY' }
# Absolute, trusted Desktop module only. Do not change PSModulePath or execution policy.
$module = @(Import-Module -Name $manifest -ErrorAction Stop -PassThru)
if ($module.Count -ne 1 -or $module[0].Path -ne $manifest -or $module[0].Version.ToString() -ne '3.0.0.0') { throw 'BROWSER_ACL_MODULE_IDENTITY' }
$set = Get-Command 'Microsoft.PowerShell.Security\Set-Acl' -ErrorAction Stop
$get = Get-Command 'Microsoft.PowerShell.Security\Get-Acl' -ErrorAction Stop
if ($set.Module.Path -ne $manifest -or $get.Module.Path -ne $manifest) { throw 'BROWSER_ACL_COMMAND_SOURCE' }
$sid = [Security.Principal.SecurityIdentifier]::new($OwnerSid)
$acl = [Security.AccessControl.DirectorySecurity]::new()
$acl.SetOwner($sid)
$acl.SetAccessRuleProtection($true,$false)
foreach ($id in @($OwnerSid,'S-1-5-18')) {
    $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new([Security.Principal.SecurityIdentifier]::new($id),'FullControl','ContainerInherit,ObjectInherit','None','Allow'))
}
& $set -LiteralPath $p -AclObject $acl
$actual = & $get -LiteralPath $p
$rules = @($actual.GetAccessRules($true,$true,[Security.Principal.SecurityIdentifier]) | ForEach-Object {
    [pscustomobject]@{sid=$_.IdentityReference.Value; rights=[int]$_.FileSystemRights; type=[string]$_.AccessControlType; inherited=$_.IsInherited; inheritanceFlags=[int]$_.InheritanceFlags; propagationFlags=[int]$_.PropagationFlags}
})
$owner = $actual.GetOwner([Security.Principal.SecurityIdentifier]).Value
if ($owner -ne $OwnerSid -or -not $actual.AreAccessRulesProtected -or -not $actual.AreAccessRulesCanonical -or $rules.Count -ne 2) { throw 'BROWSER_ACL_READBACK_STRUCTURE' }
foreach ($id in @($OwnerSid,'S-1-5-18')) {
    $selected = @($rules | Where-Object { $_.sid -eq $id })
    if ($selected.Count -ne 1 -or $selected[0].rights -ne [int][Security.AccessControl.FileSystemRights]::FullControl -or $selected[0].type -ne 'Allow' -or $selected[0].inherited -or $selected[0].inheritanceFlags -ne 3 -or $selected[0].propagationFlags -ne 0) { throw 'BROWSER_ACL_READBACK_RULE' }
}
function FileHash([string]$name) { $sha=[Security.Cryptography.SHA256]::Create();try { return ([BitConverter]::ToString($sha.ComputeHash([IO.File]::ReadAllBytes($name)))).Replace('-','').ToLowerInvariant() } finally { $sha.Dispose() } }
$assembly = $set.ImplementingType.Assembly.Location
[pscustomobject]@{
    status='VERIFIED'; directory=$p; ownerSid=$owner; protected=$actual.AreAccessRulesProtected; canonical=$actual.AreAccessRulesCanonical; rules=$rules
    module=[pscustomobject]@{path=$module[0].Path;version=$module[0].Version.ToString();manifestSha256=(FileHash $manifest);assembly=$assembly;assemblySha256=(FileHash $assembly)}
} | ConvertTo-Json -Depth 8 -Compress
