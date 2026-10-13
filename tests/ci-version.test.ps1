$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$tempRoot = Join-Path $project ('build/version-tests-' + [guid]::NewGuid())
New-Item -ItemType Directory -Path $tempRoot | Out-Null
New-Item -ItemType Directory -Path "$tempRoot/scripts", "$tempRoot/app" | Out-Null
Copy-Item "$project/scripts/common.ps1", "$project/scripts/ci-version.ps1" "$tempRoot/scripts/"
Copy-Item "$project/app/apktool.yml" "$tempRoot/app/"
$baseCode = [long]([regex]::Match((Get-Content "$project/app/apktool.yml" -Raw), 'versionCode:\s*(\d+)').Groups[1].Value)
foreach ($scenario in @('no-release', 'no-metadata', 'previous-release', 'limit')) {
    $envFile = Join-Path $tempRoot "$scenario.env"
    $wrapper = Join-Path $tempRoot "$scenario.ps1"
    @'
param($Project, $Scenario, $EnvFile)
$ErrorActionPreference = 'Stop'
$env:GITHUB_RUN_NUMBER = '1'
$env:GITHUB_REPOSITORY = 'test/depositly'
$env:GITHUB_ENV = $EnvFile
function gh {
    if ($args[1] -eq 'view') {
        $global:LASTEXITCODE = if ($Scenario -eq 'no-release') { 1 } else { 0 }
    } else {
        $global:LASTEXITCODE = if ($Scenario -eq 'no-metadata') { 1 } else { 0 }
        if ($global:LASTEXITCODE -eq 0) {
            $dir = $args[[Array]::IndexOf($args, '--dir') + 1]
            $code = if ($Scenario -eq 'limit') { 2100000000 } else { 1000000 }
            @{ versionCode = $code } | ConvertTo-Json | Set-Content "$dir/build-info.json"
        }
    }
}
& "$Project/scripts/ci-version.ps1"
# Match the exit behavior of GitHub Actions' PowerShell runner.
if (Test-Path variable:LASTEXITCODE) { exit $LASTEXITCODE }
'@ | Set-Content $wrapper
    $output = & (Join-Path $PSHOME 'pwsh.exe') -NoProfile -File $wrapper $tempRoot $scenario $envFile 2>&1
    $result = $LASTEXITCODE
    if ($scenario -eq 'limit') {
        if ($result -eq 0 -or (Test-Path $envFile)) { throw 'Version limit must fail before exporting a code.' }
    } else {
        if ($result -ne 0) { throw "Scenario $scenario failed: $output" }
        $expected = if ($scenario -eq 'previous-release') { 1000001 } else { $baseCode + 1 }
        if ((Get-Content $envFile -Raw).Trim() -ne "DEPOSITLY_BUILD_CODE=$expected") { throw "Incorrect version code for $scenario." }
    }
}
$global:LASTEXITCODE = 0
Write-Host 'CI version tests passed (first release, missing metadata, increasing code, limit).'
