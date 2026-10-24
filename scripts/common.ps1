$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path $PSScriptRoot -Parent
function Assert-ToolExit { if ($LASTEXITCODE -ne 0) { throw "Tool failed with exit code $LASTEXITCODE." } }
function Get-AndroidSdkPath([string]$RequestedPath) {
    foreach ($candidate in @($RequestedPath, $env:ANDROID_SDK_ROOT, $env:ANDROID_HOME, 'C:/Android/Sdk')) {
        if ($candidate -and (Test-Path -LiteralPath $candidate)) { return (Resolve-Path -LiteralPath $candidate).Path }
    }
    throw 'Android SDK not found. Set ANDROID_SDK_ROOT or pass -SdkPath.'
}
function Get-AppVersion {
    $config = Get-Content (Join-Path $ProjectRoot 'app/apktool.yml') -Raw -Encoding UTF8
    $name = [regex]::Match($config,'(?m)^\s*versionName:\s*(.+)$').Groups[1].Value.Trim()
    $code = [regex]::Match($config,'(?m)^\s*versionCode:\s*(\d+)').Groups[1].Value
    if ($name -notmatch '^\d+(\.\d+)+( Build [1-9]\d*)?$' -or !$code) { throw 'Invalid app version configuration.' }
    return @{Name=$name;Code=[int]$code;Slug=($name -replace ' Build ','-build-')}
}

