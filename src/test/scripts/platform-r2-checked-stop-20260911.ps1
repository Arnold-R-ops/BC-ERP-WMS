$ErrorActionPreference='Stop'
$taskRoot='D:\ERP_WMS\BC warehouse\2G'
$taskDirectory=Join-Path $taskRoot 'target\platform-db-acceptance-20260911\r2\pgdata'
if((Get-Location).Path -ne $taskRoot -or (Resolve-Path -LiteralPath $taskDirectory).Path -ne $taskDirectory){throw 'Wrong directory; STOP'}
$taskLines=Get-Content -LiteralPath (Join-Path $taskDirectory 'postmaster.pid')
# Exact identities observed during this failed startup batch, never a broad service/name kill.
if([int]$taskLines[0] -ne 50900 -or [long]$taskLines[2] -ne 1789113767 -or [int]$taskLines[3] -ne 55449 -or $taskLines[5] -ne '127.0.0.1' -or $taskLines[1].Replace('/','\') -ne $taskDirectory){throw 'PID file identity mismatch'}
$taskProcess=Get-CimInstance Win32_Process -Filter 'ProcessId=50900' -ErrorAction Stop
if($taskProcess.ExecutablePath -ne 'D:\ITsoftware\PostgreSQL\bin\postgres.exe' -or -not $taskProcess.CommandLine.Replace('/','\').Contains($taskDirectory)){throw 'Executable/command identity mismatch'}
$taskListeners=@(Get-NetTCPConnection -State Listen -LocalPort 55449 -ErrorAction Stop)
if($taskListeners.Count -ne 1 -or $taskListeners[0].LocalAddress -ne '127.0.0.1' -or $taskListeners[0].OwningProcess -ne 50900){throw 'Listener identity mismatch'}
$taskBefore=[ordered]@{pid=50900;startedEpoch=1789113767;directory=$taskDirectory;executable=$taskProcess.ExecutablePath;processCreated=$taskProcess.CreationDate;address='127.0.0.1';port=55449;observedAt=[DateTimeOffset]::Now.ToString('o')}
& 'D:\ITsoftware\PostgreSQL\bin\pg_ctl.exe' stop -D $taskDirectory -m fast -w -t 30
$taskExit=$LASTEXITCODE
$taskRemaining=@(Get-NetTCPConnection -State Listen -ErrorAction Stop | Where-Object LocalPort -eq 55449)
$taskEvidence=[ordered]@{before=$taskBefore;exitCode=$taskExit;pidFileRemoved=!(Test-Path -LiteralPath (Join-Path $taskDirectory 'postmaster.pid'));processRemoved=!(Get-Process -Id 50900 -ErrorAction SilentlyContinue);listenersRemaining=$taskRemaining.Count;completedAt=[DateTimeOffset]::Now.ToString('o');databaseDirectoryRetained=$true;stopMode='fast';databaseConnectionMadeByStopScript=$false}
$taskDestination=Join-Path $taskRoot 'docs\releases\platform-db-acceptance-20260911\checked-stop.json'
$taskStream=[IO.File]::Open($taskDestination,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write)
try{$taskBytes=[Text.Encoding]::UTF8.GetBytes(($taskEvidence|ConvertTo-Json -Depth 6)+"`n");$taskStream.Write($taskBytes,0,$taskBytes.Length)}finally{$taskStream.Dispose()}
$taskEvidence|ConvertTo-Json -Depth 6
if($taskExit -ne 0 -or !$taskEvidence.pidFileRemoved -or !$taskEvidence.processRemoved -or $taskRemaining.Count){throw 'Shutdown incomplete; report, no further action'}
