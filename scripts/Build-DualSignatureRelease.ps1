param(
    [string]$Toolchain = 'N:\codex-L\instant-camera\.toolchain',
    [string]$StageDirectory = (Join-Path $env:TEMP 'dayflow-apk-dual-signature-release')
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Security
$source = Split-Path $PSScriptRoot -Parent
$sourceFull = [IO.Path]::GetFullPath($source).TrimEnd('\')
$stageFull = [IO.Path]::GetFullPath($StageDirectory).TrimEnd('\')
if ($stageFull.Equals($sourceFull,[StringComparison]::OrdinalIgnoreCase) -or $stageFull.StartsWith($sourceFull + '\',[StringComparison]::OrdinalIgnoreCase)) { throw 'ASCII build staging must be outside the repository.' }
$secureRoot = Join-Path $env:LOCALAPPDATA 'Dayflow-Android'
$officialStore = Join-Path $secureRoot 'dayflow-release.jks'
$secretPath = Join-Path $secureRoot 'dayflow-release-password.dpapi'
$legacyStore = Join-Path $env:USERPROFILE '.android\debug.keystore'
$gradle = Join-Path $Toolchain 'gradle-8.9\bin\gradle.bat'
$apksigner = Join-Path $Toolchain 'sdk\build-tools\35.0.0\apksigner.bat'
if (!(Test-Path $gradle) -or !(Test-Path $apksigner)) { throw 'Dayflow Gradle/JDK17/Android SDK35 toolchain is required.' }
if (!(Test-Path $officialStore) -or !(Test-Path $secretPath)) { throw 'The existing official Android signing key/password is unavailable.' }
if (!(Test-Path $legacyStore)) { throw 'The existing legacy Android debug signing key is unavailable; refusing to generate a different compatibility signer.' }
$plainPassword = [Text.Encoding]::UTF8.GetString([Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($secretPath),$null,[Security.Cryptography.DataProtectionScope]::CurrentUser))
$version = [regex]::Match((Get-Content -LiteralPath (Join-Path $source 'app\build.gradle.kts') -Raw),'versionName\s*=\s*"([0-9.]+)"').Groups[1].Value
if (!$version) { throw 'Android versionName is missing.' }
if ($version -ne '1.0.18') { throw 'This release script is scoped to the current Android v1.0.18 release.' }
$null = New-Item -ItemType Directory -Path $StageDirectory -Force
& robocopy $source $StageDirectory /E /XD .git .gradle .kotlin build artifacts .superpowers /XF local.properties /NFL /NDL /NJH /NJS /NP
if ($LASTEXITCODE -ge 8) { throw 'ASCII release staging copy failed.' }
$env:JAVA_HOME = Join-Path $Toolchain 'jdk-17.0.20.1+1'
$env:ANDROID_HOME = Join-Path $Toolchain 'sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$output = Join-Path $source 'artifacts'
$null = New-Item -ItemType Directory -Path $output -Force
function Build-SignedVariant([string]$Store,[string]$Password,[string]$Alias,[string]$ExpectedCertificate,[string]$FileName) {
    $env:DAYFLOW_RELEASE_STORE_FILE = $Store
    $env:DAYFLOW_RELEASE_STORE_PASSWORD = $Password
    $env:DAYFLOW_RELEASE_KEY_ALIAS = $Alias
    $env:DAYFLOW_RELEASE_KEY_PASSWORD = $Password
    Push-Location $StageDirectory
    try {
        & $gradle clean :app:assembleRelease --no-daemon --console=plain
        if ($LASTEXITCODE -ne 0) { throw "Android $FileName build failed ($LASTEXITCODE)." }
    } finally { Pop-Location }
    $apk = Join-Path $StageDirectory 'app\build\outputs\apk\release\app-release.apk'
    $certificate = (& $apksigner verify --verbose --print-certs $apk 2>&1 | Out-String)
    $verifyExitCode = $LASTEXITCODE
    if ($verifyExitCode -ne 0 -or $certificate -notmatch [regex]::Escape($ExpectedCertificate)) { throw "Android $FileName signer does not match its established certificate." }
    Copy-Item -LiteralPath $apk -Destination (Join-Path $output $FileName) -Force
    Write-Output "Built and signature-checked $FileName"
}
try {
    Build-SignedVariant $officialStore $plainPassword 'dayflow-release' '83b1ef579ae005e90a10d247a984d1ceb83fdb81c3ff99ba9fc5b8c7f52abe0b' "Dayflow-$version.apk"
    Build-SignedVariant $legacyStore 'android' 'androiddebugkey' '89dbc4d6576a4bd5818e3ee9eefa499f962419621eb3a3ed8608f9961f844e7e' "Dayflow-$version-debug-compat.apk"
} finally {
    Remove-Item Env:\DAYFLOW_RELEASE_STORE_FILE -ErrorAction SilentlyContinue
    Remove-Item Env:\DAYFLOW_RELEASE_STORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:\DAYFLOW_RELEASE_KEY_ALIAS -ErrorAction SilentlyContinue
    Remove-Item Env:\DAYFLOW_RELEASE_KEY_PASSWORD -ErrorAction SilentlyContinue
    $plainPassword = $null
}
