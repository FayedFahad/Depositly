$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
. (Join-Path $project 'scripts/common.ps1')
$fixtureRoot = Join-Path (Get-AppWorkDirectory) ('version-bump-tests-' + [guid]::NewGuid())
New-Item -ItemType Directory -Path $fixtureRoot | Out-Null
function New-VersionFixture([string]$Name, [string]$VersionName = '1.2 Build 1', [int]$Code = 18) {
    $fixture = Join-Path $fixtureRoot $Name
    New-Item -ItemType Directory -Path "$fixture/scripts", "$fixture/app/assets", "$fixture/docs", "$fixture/dist" | Out-Null
    Copy-Item "$project/scripts/common.ps1", "$project/scripts/bump-version.ps1" "$fixture/scripts/"
    $slug = $VersionName -replace ' Build ', '-build-'
    "apkFileName: Depositly-$slug.apk`nversionInfo:`n  versionCode: $Code`n  versionName: $VersionName`n" | Set-Content "$fixture/app/apktool.yml" -Encoding UTF8
    "<b>v$VersionName</b><span>$VersionName</span>" | Set-Content "$fixture/app/assets/index.html" -Encoding UTF8
    "Current version: $VersionName. Download dist/Depositly-$slug.apk" | Set-Content "$fixture/README.md" -Encoding UTF8
    "Depositly $VersionName" | Set-Content "$fixture/docs/release-notes.md" -Encoding UTF8
    "Depositly $VersionName uses Google Drive." | Set-Content "$fixture/docs/google-drive.md" -Encoding UTF8
    return $fixture
}
function Assert-Version([string]$Fixture, [string]$ExpectedName, [int]$ExpectedCode) {
    $config = Get-Content "$Fixture/app/apktool.yml" -Raw
    if ($config -notmatch ('(?m)^  versionName: ' + [regex]::Escape($ExpectedName) + '\r?$')) { throw "Incorrect visible version: $config" }
    if ($config -notmatch "(?m)^  versionCode: $ExpectedCode\r?$") { throw "Incorrect internal version: $config" }
    $slug = $ExpectedName -replace ' Build ', '-build-'
    if (!$config.Contains("apkFileName: Depositly-$slug.apk")) { throw 'APK filename was not updated.' }
    foreach ($relativePath in @('app/assets/index.html', 'README.md', 'docs/release-notes.md', 'docs/google-drive.md')) {
        if (!(Get-Content (Join-Path $Fixture $relativePath) -Raw).Contains($ExpectedName)) { throw "Version label missing in $relativePath." }
    }
    if (!(Get-Content "$Fixture/README.md" -Raw).Contains("dist/Depositly-$slug.apk")) { throw 'README APK path was not updated.' }
}

$regular = New-VersionFixture 'regular'
@{versionCode=19;versionName='1.2 Build 1'} | ConvertTo-Json | Set-Content "$regular/dist/build-info.json"
& "$regular/scripts/bump-version.ps1"
Assert-Version $regular '1.2 Build 2' 20
& "$regular/scripts/bump-version.ps1"
Assert-Version $regular '1.2 Build 3' 21
if ((Get-Content "$regular/app/assets/index.html" -Raw).Contains('1.2 Build 1')) { throw 'Stale UI version labels remain.' }

$withoutBuild = New-VersionFixture 'without-build' '2.0' 7
& "$withoutBuild/scripts/bump-version.ps1"
Assert-Version $withoutBuild '2.0 Build 1' 8

$external = New-VersionFixture 'external'
@{versionCode=19} | ConvertTo-Json | Set-Content "$external/dist/build-info.json"
$previousPath = Join-Path $fixtureRoot 'previous-release.json'
@{versionCode=42} | ConvertTo-Json | Set-Content $previousPath
& "$external/scripts/bump-version.ps1" -PreviousBuildInfoPath $previousPath
Assert-Version $external '1.2 Build 2' 43

foreach ($scenario in @('limit', 'invalid-metadata', 'missing-explicit-metadata')) {
    $fixture = New-VersionFixture $scenario
    if ($scenario -eq 'limit') { @{versionCode=2100000000} | ConvertTo-Json | Set-Content "$fixture/dist/build-info.json" }
    if ($scenario -eq 'invalid-metadata') { @{versionCode='not-a-code'} | ConvertTo-Json | Set-Content "$fixture/dist/build-info.json" }
    $before = @{}
    foreach ($file in Get-ChildItem -LiteralPath $fixture -Recurse -File) { $before[$file.FullName] = [IO.File]::ReadAllBytes($file.FullName) }
    $failed = $false
    try {
        if ($scenario -eq 'missing-explicit-metadata') { & "$fixture/scripts/bump-version.ps1" -PreviousBuildInfoPath "$fixture/missing.json" }
        else { & "$fixture/scripts/bump-version.ps1" }
    } catch { $failed = $true }
    if (!$failed) { throw "Scenario $scenario must fail." }
    foreach ($filePath in $before.Keys) {
        if ([Convert]::ToBase64String($before[$filePath]) -ne [Convert]::ToBase64String([IO.File]::ReadAllBytes($filePath))) { throw "Scenario $scenario wrote files before failing." }
    }
}
$global:LASTEXITCODE = 0
Write-Host 'Version bump tests passed (visible labels, APK paths, local/explicit code baselines, repeated bumps, no Build suffix and failures before writes).'
