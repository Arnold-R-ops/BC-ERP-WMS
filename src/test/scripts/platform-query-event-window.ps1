# Fixed, read-only historical event window. No messages, queries, credentials or task names exported.
$ErrorActionPreference='Stop'
[Console]::OutputEncoding=New-Object System.Text.UTF8Encoding($false)
$start=[DateTimeOffset]::Parse('2026-09-18T15:05:00Z').LocalDateTime
$end=[DateTimeOffset]::Parse('2026-09-18T15:17:00Z').LocalDateTime
$plans=@(
 @{log='System';providers=@('Microsoft-Windows-Kernel-Power','Microsoft-Windows-Power-Troubleshooter','Microsoft-Windows-Kernel-General','Microsoft-Windows-Kernel-Processor-Power','Microsoft-Windows-Eventlog')},
 @{log='Microsoft-Windows-WMI-Activity/Operational'},
 @{log='Microsoft-Windows-TaskScheduler/Operational'},
 @{log='Microsoft-Windows-Diagnostics-Performance/Operational'},
 @{log='Microsoft-Windows-Kernel-EventTracing/Admin'}
)
function SafeFailure($record) {
 $e=$record.Exception
 while($e.InnerException){$e=$e.InnerException}
 return @{type=$e.GetType().FullName;hresult=$e.HResult;category=$record.CategoryInfo.Category.ToString()}
}
$result=@{windowUtc=@{start='2026-09-18T15:05:00Z';end='2026-09-18T15:17:00Z'};queriedAt=(Get-Date).ToUniversalTime().ToString('o');user=[Security.Principal.WindowsIdentity]::GetCurrent().Name;logs=@();databaseConnections=0}
foreach($plan in $plans){
 $row=@{log=$plan.log;events=@()};$result.logs+=,$row
 try{
  $meta=Get-WinEvent -ListLog $plan.log -ErrorAction Stop
  $row.metadata=@{enabled=$meta.IsEnabled;recordCount=$meta.RecordCount;logMode=$meta.LogMode.ToString();lastWriteTimeUtc=if($meta.LastWriteTime){$meta.LastWriteTime.ToUniversalTime().ToString('o')}else{$null}}
  $filter=@{LogName=$plan.log;StartTime=$start;EndTime=$end}
  if($plan.providers){$filter.ProviderName=$plan.providers}
  try{$events=@(Get-WinEvent -FilterHashtable $filter -ErrorAction Stop)}
  catch{
   if($_.FullyQualifiedErrorId -like 'NoMatchingEventsFound*'){$row.status='NO_MATCHING_EVENTS';continue}
   throw
  }
  foreach($event in $events){
   $x=[xml]$event.ToXml();$fields=@{}
   foreach($d in $x.SelectNodes('//*[not(*)]')){
    # No Operation, Query, User, ClientMachine, TaskName or message text.
    $fieldName=if($d.LocalName -eq 'Data'){$d.GetAttribute('Name')}else{$d.LocalName}
    if($fieldName -in @('SleepTime','WakeTime','SleepDuration','WakeDuration','TargetState','EffectiveState','Reason','ClientProcessId','ProcessId','ResultCode','NewTime','OldTime','SessionId','ScenarioInstanceId','PreviousSessionType','NextSessionType')){
     $v=$d.InnerText
     if($v.Length -le 80 -and $v -match '^[0-9a-fA-FxX: .+TZ/-]*$'){$fields[$fieldName]=$v}
    }
   }
   $entry=@{atUtc=$event.TimeCreated.ToUniversalTime().ToString('o');recordId=$event.RecordId;id=$event.Id;provider=$event.ProviderName;level=$event.Level;processId=$event.ProcessId;threadId=$event.ThreadId;fields=$fields}
   # Only these power-transition descriptions: never WMI or scheduling messages.
   if($event.ProviderName -eq 'Microsoft-Windows-Kernel-Power' -and $event.Id -in @(506,507,566)){$entry.powerTransitionDescription=$event.FormatDescription()}
   $row.events+=,$entry
  }
  $row.status='READ'
 }catch{$row.status='QUERY_FAILED';$row.failure=SafeFailure $_}
}
$result|ConvertTo-Json -Depth 10 -Compress
