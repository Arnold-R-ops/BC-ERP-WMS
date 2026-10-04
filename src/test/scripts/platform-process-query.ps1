# Shared read-only process proof. No raw command line, credentials, or arbitrary exception message in output.
function Failure($record) {
 $e=$record.Exception
 while($e.InnerException){$e=$e.InnerException}
 $status=if($record.CategoryInfo.Category.ToString() -eq 'PermissionDenied' -or $e -is [UnauthorizedAccessException] -or $record.FullyQualifiedErrorId -match '80041003|80070005'){'ACCESS_DENIED'}else{'QUERY_FAILED'}
 return @{status=$status;exception=$e.GetType().FullName;hresult=$e.HResult;category=$record.CategoryInfo.Category.ToString()}
}
function HashText([string]$value) {
 $hash=[Security.Cryptography.SHA256]::Create()
 try{return ([BitConverter]::ToString($hash.ComputeHash([Text.Encoding]::UTF8.GetBytes($value)))).Replace('-','').ToLowerInvariant()}finally{$hash.Dispose()}
}
function ProcessProof([int]$processNumber,[string]$stage='PROCESS', $observed=$null,
 [scriptblock]$query={param($n) Get-CimInstance Win32_Process -Filter "ProcessId=$n" -ErrorAction Stop},
 [scriptblock]$ownerQuery={param($p) Invoke-CimMethod -InputObject $p -MethodName GetOwnerSid -ErrorAction Stop}) {
 $missing=@();$p=$null;$part='ROW';$firstFailure=$null
 try{
  $rows=@(if($null -ne $observed){$observed}else{& $query $processNumber})
  if($rows.Count -eq 0){return @{status='ABSENT';pid=$processNumber;stage=$stage;category='CONFIRMED_ABSENT'}}
  if($rows.Count -ne 1){return @{status='INCOMPLETE';pid=$processNumber;stage=$stage;category='AMBIGUOUS_ROWS';missingFields=@('UniqueProcessRow')}}
  $p=$rows[0]
  $missing=@(@('ProcessId','ParentProcessId','CommandLine','ExecutablePath','CreationDate')|Where-Object {$null -eq $p.$_ -or ($p.$_ -is [string] -and [string]::IsNullOrWhiteSpace($p.$_))})
  if([int]$p.ProcessId -ne $processNumber){$missing+= 'MatchingProcessId'}
  if($missing.Count -eq 0){
   $part='OWNER';$o=& $ownerQuery $p
   if($o.ReturnValue -ne 0 -or -not $o.Sid){
    $missing=@('OwnerSid');$firstFailure=@{status=$(if($o.ReturnValue -in @(2,3)){'ACCESS_DENIED'}else{'INCOMPLETE'});category='OWNER_QUERY_FAILED';ownerReturnCode=$o.ReturnValue}
   }else{
    $match=[regex]::Match($p.CommandLine,'(?:^|\s)-D\s+(?:"([^"]+)"|(\S+))')
    $dir=if($match.Success){if($match.Groups[1].Success){$match.Groups[1].Value}else{$match.Groups[2].Value}}else{''}
    return @{status='AVAILABLE';stage=$stage;pid=[int]$p.ProcessId;parentPid=[int]$p.ParentProcessId;executable=$p.ExecutablePath;directory=$dir;commandLineReadable=$true;commandLineSha256=(HashText $p.CommandLine);creationEpoch=([DateTimeOffset]$p.CreationDate).ToUnixTimeSeconds();creationUtc=([DateTimeOffset]$p.CreationDate).ToUniversalTime().ToString('o');creationTicks=([DateTimeOffset]$p.CreationDate).UtcTicks.ToString();ownerSid=$o.Sid}
   }
  }
 }catch{$firstFailure=Failure $_;if($part -eq 'OWNER'){$missing=@('OwnerSid')}else{$missing=@('ProcessRow')}}
 # A second read diagnoses the first failure; it NEVER upgrades an incomplete result to a valid proof.
 $result=@{status=$(if($firstFailure){$firstFailure.status}else{'INCOMPLETE'});pid=$processNumber;stage=$stage;queryPart=$part;missingFields=$missing;category='UNKNOWN'}
 if($firstFailure){$result.firstQueryFailure=$firstFailure}
 try{
  $again=@(& $query $processNumber)
  if($p -and $again.Count -eq 0){$result.status='EXITED_DURING_QUERY';$result.category='PROCESS_EXITED_DURING_QUERY'}
  elseif($p -and $p.CreationDate -and $again.Count -eq 1 -and $again[0].CreationDate -and ([DateTimeOffset]$p.CreationDate).UtcTicks -ne ([DateTimeOffset]$again[0].CreationDate).UtcTicks){$result.status='PID_REUSED';$result.category='PID_REUSED_DURING_QUERY'}
  elseif($firstFailure.status -eq 'ACCESS_DENIED'){$result.category='ACCESS_DENIED'}
  else{$result.category='INCOMPLETE_UNKNOWN';$result.diagnosticRowCount=$again.Count}
 }catch{
  $result.diagnosticFailure=Failure $_
  if($result.diagnosticFailure.status -eq 'ACCESS_DENIED'){$result.status='ACCESS_DENIED';$result.category='ACCESS_DENIED'}else{$result.category='QUERY_FAILED_UNKNOWN'}
 }
 return $result
}
