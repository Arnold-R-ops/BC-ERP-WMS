# Read-only OS inspection. No PostgreSQL command, SQL, process termination or configuration change.
param(
 [Parameter(Mandatory=$true)][string]$Directory,
 [Parameter(Mandatory=$true)][ValidateRange(1,65535)][int]$Port,
 [Parameter(Mandatory=$true)][int]$OwnerPid,
 [Parameter(Mandatory=$true)][string]$OwnerExecutable
)
$ErrorActionPreference='Stop'
# PROCESS_QUERY_HELPERS
# ProcessProof uses GetOwnerSid and exact creation time; incomplete reads stay fail-closed.
function PathProof([string]$file,[bool]$readPid) {
 try{
  $attributes=[IO.File]::GetAttributes($file)
  if(-not $readPid){return @{status='PRESENT';directory=[bool]($attributes -band [IO.FileAttributes]::Directory)}}
  $rows=[IO.File]::ReadAllLines($file)
  if($rows.Count -lt 6){return @{status='INCOMPLETE'}}
  return @{status='PRESENT';pid=$rows[0];directory=$rows[1];startedEpoch=$rows[2];port=$rows[3];address=$rows[5]}
 }catch{
  $e=$_.Exception
  while($e.InnerException){$e=$e.InnerException}
  if($e -is [IO.FileNotFoundException] -or $e -is [IO.DirectoryNotFoundException]){return @{status='ABSENT'}}
  if($e -is [UnauthorizedAccessException]){return @{status='ACCESS_DENIED';exception=$e.GetType().FullName;hresult=$e.HResult}}
  return Failure $_
 }
}
$result=@{schema=1;checkedAt=(Get-Date).ToUniversalTime().ToString('o');target=@{directory=$Directory;port=$Port};ownerPid=$OwnerPid;ownerExecutable=$OwnerExecutable}
try{
 Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class PlatformReadOnlyToken {
 [DllImport("advapi32.dll", SetLastError=true)] [return:MarshalAs(UnmanagedType.Bool)] public static extern bool IsTokenRestricted(IntPtr token);
 [DllImport("advapi32.dll", SetLastError=true)] [return:MarshalAs(UnmanagedType.Bool)] public static extern bool GetTokenInformation(IntPtr token,int kind,out int value,int length,out int returned);
}
'@
 $identity=[Security.Principal.WindowsIdentity]::GetCurrent()
 $principal=New-Object Security.Principal.WindowsPrincipal($identity)
 $elevation=0;$elevationType=0;$returned=0
 if(-not [PlatformReadOnlyToken]::GetTokenInformation($identity.Token,20,[ref]$elevation,4,[ref]$returned)){throw 'Token elevation unavailable'}
 if(-not [PlatformReadOnlyToken]::GetTokenInformation($identity.Token,18,[ref]$elevationType,4,[ref]$returned)){throw 'Token type unavailable'}
 $result.context=@{status='AVAILABLE';user=$identity.Name;sid=$identity.User.Value;authenticationType=$identity.AuthenticationType;administratorEnabled=$principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator);restrictedToken=[PlatformReadOnlyToken]::IsTokenRestricted($identity.Token);elevated=[bool]$elevation;elevationType=$elevationType;powerShellPid=$PID;powerShellPath=(Get-Process -Id $PID).Path;powerShellVersion=$PSVersionTable.PSVersion.ToString()}
}catch{$result.context=Failure $_}
$result.self=ProcessProof $PID 'SELF'
$result.owner=ProcessProof $OwnerPid 'EXECUTOR_OWNER'
try{
 $all=@(Get-CimInstance Win32_Process -Filter "Name='postgres.exe'" -ErrorAction Stop)
 $issues=@();$matches=@()
 foreach($p in $all){
  $missing=@(@('ProcessId','ParentProcessId','CommandLine','ExecutablePath','CreationDate')|Where-Object {$null -eq $p.$_ -or ($p.$_ -is [string] -and [string]::IsNullOrWhiteSpace($p.$_))})
  if($missing.Count){$issues+=ProcessProof ([int]$p.ProcessId) 'POSTGRES_INVENTORY' $p;continue}
  $match=[regex]::Match($p.CommandLine,'(?:^|\s)-D\s+(?:"([^"]+)"|(\S+))')
  $dir=if($match.Success){if($match.Groups[1].Success){$match.Groups[1].Value}else{$match.Groups[2].Value}}else{''}
  # Exact data directory, not a prefix that could accidentally identify another cluster.
  if($dir.Replace('\','/').TrimEnd('/') -ieq $Directory.Replace('\','/').TrimEnd('/')){
   $matches+=@{pid=[int]$p.ProcessId;parentPid=[int]$p.ParentProcessId;executable=$p.ExecutablePath;directory=$dir;creationEpoch=([DateTimeOffset]$p.CreationDate).ToUnixTimeSeconds();creationTicks=([DateTimeOffset]$p.CreationDate).UtcTicks.ToString();commandLineSha256=(HashText $p.CommandLine)}
  }
 }
 $result.postgres=@{status=$(if($issues.Count){'INCOMPLETE'}else{'AVAILABLE'});count=$all.Count;incomplete=$issues.Count;issues=$issues;targetMatches=$matches;processIds=@($all|ForEach-Object {[int]$_.ProcessId});parentIds=@($all|ForEach-Object {[int]$_.ParentProcessId})}
}catch{$result.postgres=Failure $_}
try{
 $raw=@(& 'C:/Windows/System32/netstat.exe' -ano -p TCP 2>&1)
 if($LASTEXITCODE -ne 0){throw 'netstat execution failed'}
 $rows=@();$malformed=0
 foreach($line in $raw){
  $parts=$line.ToString().Trim() -split '\s+'
  if($parts[0] -ne 'TCP'){continue}
  if($parts.Count -ne 5 -or $parts[4] -notmatch '^\d+$'){$malformed++;continue}
  if($parts[3] -ne 'LISTENING'){continue}
  $endpoint=[regex]::Match($parts[1],'^(.+):(\d+)$')
  if(-not $endpoint.Success){$malformed++;continue}
  $rows+=@{address=$endpoint.Groups[1].Value;port=[int]$endpoint.Groups[2].Value;pid=[int]$parts[4]}
 }
 $selected=@($rows|Where-Object {$_.port -in @($Port,5432,8080,5173,55439,15173,18091)})
 $proofs=@($selected|ForEach-Object {ProcessProof $_.pid 'LISTENER_OWNER'})
 $result.listeners=@{status=$(if($malformed){'INCOMPLETE'}else{'AVAILABLE'});total=$rows.Count;malformed=$malformed;selected=$selected;pidProofs=$proofs}
}catch{$result.listeners=Failure $_}
$result.directory=PathProof $Directory $false
$result.pidFile=PathProof ($Directory.TrimEnd('/','\')+'/postmaster.pid') $true
$result|ConvertTo-Json -Depth 8 -Compress
