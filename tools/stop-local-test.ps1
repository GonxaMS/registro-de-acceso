param([switch]$StopAndroidEmulator)

$projectRoot = Split-Path -Parent $PSScriptRoot
$pidFile = Join-Path $projectRoot "build\local-test\firebase.pid"

function Stop-ProcessTree([int]$TargetProcessId) {
    $children = Get-CimInstance Win32_Process -Filter "ParentProcessId=$TargetProcessId" -ErrorAction SilentlyContinue
    foreach ($child in $children) { Stop-ProcessTree -TargetProcessId $child.ProcessId }
    if (Get-Process -Id $TargetProcessId -ErrorAction SilentlyContinue) {
        Stop-Process -Id $TargetProcessId -Force
    }
}

if (Test-Path -LiteralPath $pidFile) {
    $savedPid = [int](Get-Content -Raw -LiteralPath $pidFile)
    Stop-ProcessTree -TargetProcessId $savedPid
    Remove-Item -LiteralPath $pidFile -ErrorAction SilentlyContinue
}
if ($StopAndroidEmulator) {
    $properties = Get-Content -LiteralPath (Join-Path $projectRoot "local.properties")
    $sdkLine = $properties | Where-Object { $_ -match "^sdk\.dir=" } | Select-Object -First 1
    $sdkPath = ($sdkLine.Substring(8) -replace "\\:", ":") -replace "\\\\", "\"
    & (Join-Path $sdkPath "platform-tools\adb.exe") emu kill | Out-Null
}
Write-Host "Entorno local detenido."
