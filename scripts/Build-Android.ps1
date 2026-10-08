param(
    [string[]]$Tasks = @(':app:testDebugUnitTest', ':app:assembleDebug'),
    [string]$Toolchain = 'N:\codex-L\instant-camera\.toolchain',
    [string]$StageDirectory = 'C:\Users\cybereun\AppData\Local\Temp\dayflow-apk-native-build'
)
$ErrorActionPreference = 'Stop'
$source = Split-Path $PSScriptRoot -Parent
if (!(Test-Path -LiteralPath (Join-Path $source 'app\build.gradle.kts'))) { throw 'Invalid source directory.' }
if (!(Test-Path -LiteralPath (Join-Path $Toolchain 'gradle-8.9\bin\gradle.bat'))) { throw 'Configure Toolchain with Gradle8.9, JDK17 and Android SDK35.' }
New-Item -ItemType Directory -Path $StageDirectory -Force | Out-Null
& robocopy $source $StageDirectory /E /XD .git .gradle .kotlin build artifacts .superpowers /XF local.properties /NFL /NDL /NJH /NJS /NP
if ($LASTEXITCODE -ge 8) { throw 'Source staging failed.' }
$env:JAVA_HOME = Join-Path $Toolchain 'jdk-17.0.20.1+1'
$env:ANDROID_HOME = Join-Path $Toolchain 'sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
Push-Location $StageDirectory
try {
    & (Join-Path $Toolchain 'gradle-8.9\bin\gradle.bat') @Tasks --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed ($LASTEXITCODE). Stage: $StageDirectory" }
    $apk = Join-Path $StageDirectory 'app\build\outputs\apk\debug\app-debug.apk'
    if (Test-Path -LiteralPath $apk) {
        $output = Join-Path $source 'artifacts'
        New-Item -ItemType Directory -Path $output -Force | Out-Null
        Copy-Item -LiteralPath $apk -Destination (Join-Path $output 'Dayflow-1.0.15-debug.apk') -Force
    }
} finally { Pop-Location }
