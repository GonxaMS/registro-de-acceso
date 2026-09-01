param([switch]$ResetApp)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$properties = Get-Content -LiteralPath (Join-Path $projectRoot "local.properties")
$sdkLine = $properties | Where-Object { $_ -match "^sdk\.dir=" } | Select-Object -First 1
if (!$sdkLine) { throw "Falta sdk.dir en local.properties" }
$sdkPath = ($sdkLine.Substring(8) -replace "\\:", ":") -replace "\\\\", "\"
$adb = Join-Path $sdkPath "platform-tools\adb.exe"
$emulator = Join-Path $sdkPath "emulator\emulator.exe"
$nodePath = "C:\Users\gonza\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin"
$pnpm = "C:\Users\gonza\.cache\codex-runtimes\codex-primary-runtime\dependencies\bin\fallback\pnpm.cmd"
$runtimeDir = Join-Path $projectRoot "build\local-test"
New-Item -ItemType Directory -Force -Path $runtimeDir | Out-Null

$connected = & $adb devices | Select-String "emulator-\d+\s+device"
if (!$connected) {
    Start-Process -FilePath $emulator -ArgumentList @("-avd", "RegistroAcceso_API35", "-no-snapshot", "-no-boot-anim", "-no-audio", "-gpu", "swiftshader_indirect") -WindowStyle Hidden
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        Start-Sleep -Seconds 2
        if ((& $adb devices) -match "emulator-\d+\s+device") { break }
    }
}
& $adb wait-for-device
for ($attempt = 0; $attempt -lt 60; $attempt++) {
    if ((& $adb shell getprop sys.boot_completed).Trim() -eq "1") { break }
    Start-Sleep -Seconds 2
}

$firebasePidFile = Join-Path $runtimeDir "firebase.pid"
$firebaseRunning = $false
if (Test-Path -LiteralPath $firebasePidFile) {
    $savedPid = [int](Get-Content -Raw -LiteralPath $firebasePidFile)
    $firebaseRunning = $null -ne (Get-Process -Id $savedPid -ErrorAction SilentlyContinue)
}
if (!$firebaseRunning) {
    $env:Path = $nodePath + ";" + $env:Path
    $stdout = Join-Path $runtimeDir "firebase.stdout.log"
    $stderr = Join-Path $runtimeDir "firebase.stderr.log"
    $process = Start-Process -FilePath $pnpm -ArgumentList @("dlx", "firebase-tools", "emulators:start", "--only", "auth,firestore", "--project", "registro-guardias-408cb") -WorkingDirectory $projectRoot -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
    Set-Content -LiteralPath $firebasePidFile -Value $process.Id
}

$ready = $false
for ($attempt = 0; $attempt -lt 60; $attempt++) {
    try {
        $client = [Net.Sockets.TcpClient]::new()
        $client.Connect("127.0.0.1", 8080)
        $client.Dispose()
        $ready = $true
        break
    } catch { Start-Sleep -Seconds 2 }
}
if (!$ready) { throw "Firebase local no inició. Revisa build/local-test/firebase.stderr.log" }

& (Join-Path $PSScriptRoot "seed-local-firebase.ps1")
& (Join-Path $projectRoot "gradlew.bat") --no-daemon -PfirebaseEmulator=true assembleDebug
$apk = Join-Path $projectRoot "app\build\outputs\apk\debug\app-debug.apk"
& $adb install -r $apk
if ($ResetApp) { & $adb shell pm clear com.ejemplo.registroguardias | Out-Null }
& $adb shell monkey -p com.ejemplo.registroguardias -c android.intent.category.LAUNCHER 1 | Out-Null
Write-Host "Entorno listo. Firebase UI: http://127.0.0.1:4000"