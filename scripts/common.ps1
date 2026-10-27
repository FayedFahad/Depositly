$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path $PSScriptRoot -Parent
function Get-AppWorkDirectory {
    $base = if ($env:DEPOSITLY_WORK_DIRECTORY) { $env:DEPOSITLY_WORK_DIRECTORY }
        elseif ($env:GITHUB_ACTIONS -ne 'true' -and (Test-Path -LiteralPath 'Z:\WORK DIRECTORY')) { 'Z:\WORK DIRECTORY' }
        elseif ($env:RUNNER_TEMP) { $env:RUNNER_TEMP }
        else { [IO.Path]::GetTempPath() }
    $work = Join-Path ([IO.Path]::GetFullPath($base)) 'Depositly'
    New-Item -ItemType Directory -Force -Path $work | Out-Null
    return $work
}
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

function Assert-NewAppRelease([hashtable]$Candidate, [string]$PreviousBuildInfoPath) {
    if (!(Test-Path -LiteralPath $PreviousBuildInfoPath)) { return }
    $previous = Get-Content -LiteralPath $PreviousBuildInfoPath -Raw | ConvertFrom-Json
    if ($previous.versionName) {
        $pattern = '^(?<base>\d+(?:\.\d+)+)(?: Build (?<build>[1-9]\d*))?$'
        $currentName = [regex]::Match($Candidate.Name, $pattern)
        $previousName = [regex]::Match([string]$previous.versionName, $pattern)
        if (!$currentName.Success -or !$previousName.Success) { throw 'Invalid release version metadata.' }
        $baseOrder = ([version]$currentName.Groups['base'].Value).CompareTo([version]$previousName.Groups['base'].Value)
        $currentBuild = if ($currentName.Groups['build'].Success) { [long]$currentName.Groups['build'].Value } else { 0 }
        $previousBuild = if ($previousName.Groups['build'].Success) { [long]$previousName.Groups['build'].Value } else { 0 }
        if ($baseOrder -lt 0 -or ($baseOrder -eq 0 -and $currentBuild -le $previousBuild)) {
            throw "App updates need a new visible version. $($Candidate.Name) is not newer than $($previous.versionName). Run scripts/bump-version.ps1 before building the update."
        }
    }
    if ($previous.versionCode -and [long]$Candidate.Code -le [long]$previous.versionCode) {
        throw 'App updates need a higher Android version code. Run scripts/bump-version.ps1 with the previous build metadata.'
    }
}

