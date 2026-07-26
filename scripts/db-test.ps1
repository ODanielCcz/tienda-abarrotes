. (Join-Path $PSScriptRoot '_db-common.ps1')
Invoke-Compose -ComposeArguments @('--profile', 'test', 'run', '--rm', 'db-test')

Invoke-Compose -ComposeArguments @('exec', '-T', 'database', 'psql', '-U', $DatabaseUser, '-d', $DatabaseName, '-v', 'ON_ERROR_STOP=1', '-f', '/workspace/tests/concurrency/setup.sql')

$root = $ProjectRoot
$dbUser = $DatabaseUser
$dbName = $DatabaseName
$jobA = Start-Job -ScriptBlock {
    param($projectRoot, $user, $databaseName)
    & docker compose --project-directory $projectRoot -f (Join-Path $projectRoot 'compose.yaml') exec -T database psql -U $user -d $databaseName -v ON_ERROR_STOP=1 -f /workspace/tests/concurrency/session_a.sql
    if ($LASTEXITCODE -ne 0) { throw 'Concurrency session A failed.' }
} -ArgumentList $root, $dbUser, $dbName

Start-Sleep -Milliseconds 250

$jobB = Start-Job -ScriptBlock {
    param($projectRoot, $user, $databaseName)
    & docker compose --project-directory $projectRoot -f (Join-Path $projectRoot 'compose.yaml') exec -T database psql -U $user -d $databaseName -v ON_ERROR_STOP=1 -f /workspace/tests/concurrency/session_b.sql
    if ($LASTEXITCODE -ne 0) { throw 'Concurrency session B failed.' }
} -ArgumentList $root, $dbUser, $dbName

Wait-Job -Job $jobA, $jobB | Out-Null
if (($jobA.State -ne 'Completed') -or ($jobB.State -ne 'Completed')) {
    Receive-Job -Job $jobA, $jobB
    throw 'Una sesión de concurrencia no terminó correctamente.'
}
Receive-Job -Job $jobA, $jobB
Remove-Job -Job $jobA, $jobB

Invoke-Compose -ComposeArguments @('exec', '-T', 'database', 'psql', '-U', $DatabaseUser, '-d', $DatabaseName, '-v', 'ON_ERROR_STOP=1', '-f', '/workspace/tests/concurrency/verify.sql')
Invoke-Compose -ComposeArguments @('exec', '-T', 'database', 'psql', '-U', $DatabaseUser, '-d', $DatabaseName, '-v', 'ON_ERROR_STOP=1', '-c', 'DROP SCHEMA IF EXISTS test_support CASCADE;')

