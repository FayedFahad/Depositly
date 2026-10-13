param([string]$SdkPath, [switch]$InstallSdk)
. (Join-Path $PSScriptRoot 'common.ps1')
$sdkRoot = Get-AndroidSdkPath $SdkPath
$toolsRoot = Join-Path $ProjectRoot 'tools'
$metadata = Get-Content (Join-Path $toolsRoot 'apktool-source.json') -Raw | ConvertFrom-Json
$jarPath = Join-Path $toolsRoot 'apktool.jar'
if (!(Test-Path -LiteralPath $jarPath)) {
    Invoke-WebRequest -Uri $metadata.url -OutFile $jarPath
}
if ((Get-FileHash -LiteralPath $jarPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $metadata.sha256) { throw 'Apktool checksum mismatch.' }
$androidJar = Join-Path $sdkRoot 'platforms/android-34/android.jar'
$buildTools = Join-Path $sdkRoot 'build-tools/34.0.0'
if (!(Test-Path -LiteralPath $androidJar) -or !(Test-Path -LiteralPath (Join-Path $buildTools 'aapt2.exe'))) {
    if (!$InstallSdk) { throw 'Install Android platform 34 and build-tools 34.0.0, or run bootstrap.ps1 -InstallSdk.' }
    $sdkManager = Join-Path $sdkRoot 'cmdline-tools/latest/bin/sdkmanager.bat'
    if (!(Test-Path -LiteralPath $sdkManager)) { throw 'Android command-line tools are missing.' }
    ('y' + "`n") * 100 | & $sdkManager --licenses | Out-Host
    Assert-ToolExit
    & $sdkManager '--install' 'platforms;android-34' 'build-tools;34.0.0' | Out-Host
    Assert-ToolExit
}
Write-Host 'Android SDK and verified Apktool are ready.'

