param(
    [string]$Toolchain = 'N:\codex-L\instant-camera\.toolchain',
    [string]$StageDirectory = 'C:\Users\cybereun\AppData\Local\Temp\dayflow-apk-native-build-release'
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Security
$source = Split-Path $PSScriptRoot -Parent
$secureRoot = Join-Path $env:LOCALAPPDATA 'Dayflow-Android'
$keyPath = Join-Path $secureRoot 'dayflow-release.jks'
$secretPath = Join-Path $secureRoot 'dayflow-release-password.dpapi'
$keytool = Join-Path $Toolchain 'jdk-17.0.20.1+1\bin\keytool.exe'
$gradle = Join-Path $Toolchain 'gradle-8.9\bin\gradle.bat'
if (!(Test-Path -LiteralPath $gradle) -or !(Test-Path -LiteralPath $keytool)) { throw 'Configure the Dayflow JDK17 and Gradle8.9 toolchain first.' }
New-Item -ItemType Directory -Path $secureRoot -Force | Out-Null
if (!(Test-Path -LiteralPath $keyPath)) {
    $bytes = New-Object byte[] 32; $rng = New-Object Security.Cryptography.RNGCryptoServiceProvider; try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    $password = [Convert]::ToBase64String($bytes).Replace('+','A').Replace('/','B').Replace('=','')
    & $keytool -genkeypair -noprompt -storetype PKCS12 -keystore $keyPath -storepass $password -keypass $password -alias dayflow-release -keyalg RSA -keysize 4096 -validity 10000 -dname 'CN=Dayflow, OU=Dayflow Android, O=cybereun, L=Seoul, C=KR'
    if ($LASTEXITCODE -ne 0) { throw 'Release signing key creation failed.' }
    $plain = [Text.Encoding]::UTF8.GetBytes($password)
    [IO.File]::WriteAllBytes($secretPath,[Security.Cryptography.ProtectedData]::Protect($plain,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser))
}
if (!(Test-Path -LiteralPath $secretPath)) { throw "The local Dayflow release key exists but its protected password is unavailable: $keyPath" }
$password = [Text.Encoding]::UTF8.GetString([Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($secretPath),$null,[Security.Cryptography.DataProtectionScope]::CurrentUser))
$env:DAYFLOW_RELEASE_STORE_FILE = $keyPath
$env:DAYFLOW_RELEASE_STORE_PASSWORD = $password
$env:DAYFLOW_RELEASE_KEY_ALIAS = 'dayflow-release'
$env:DAYFLOW_RELEASE_KEY_PASSWORD = $password
New-Item -ItemType Directory -Path $StageDirectory -Force | Out-Null
& robocopy $source $StageDirectory /E /XD .git .gradle .kotlin build artifacts .superpowers /XF local.properties /NFL /NDL /NJH /NJS /NP
if ($LASTEXITCODE -ge 8) { throw 'Source staging failed.' }
$env:JAVA_HOME = Join-Path $Toolchain 'jdk-17.0.20.1+1'
$env:ANDROID_HOME = Join-Path $Toolchain 'sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
Push-Location $StageDirectory
try {
    & $gradle ':app:testDebugUnitTest' ':app:assembleRelease' --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle release build failed ($LASTEXITCODE)." }
    $apk = Join-Path $StageDirectory 'app\build\outputs\apk\release\app-release.apk'
    if (!(Test-Path -LiteralPath $apk)) { throw 'The signed release APK was not created.' }
    & (Join-Path $env:ANDROID_HOME 'build-tools\35.0.0\apksigner.bat') verify --verbose --print-certs $apk
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
    $output = Join-Path $source 'artifacts'
    New-Item -ItemType Directory -Path $output -Force | Out-Null
    Copy-Item -LiteralPath $apk -Destination (Join-Path $output 'Dayflow-1.0.14.apk') -Force
} finally { Pop-Location }
