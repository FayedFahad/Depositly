. (Join-Path $PSScriptRoot 'common.ps1')
$version = Get-AppVersion
$next = [long]$version.Code + [long]$env:GITHUB_RUN_NUMBER
$metadataPath = Join-Path $ProjectRoot 'build/previous-release'
New-Item -ItemType Directory -Force -Path $metadataPath | Out-Null
& gh release view latest --repo $env:GITHUB_REPOSITORY *> $null
if ($LASTEXITCODE -eq 0) {
    & gh release download latest --repo $env:GITHUB_REPOSITORY --pattern build-info.json --dir $metadataPath --clobber *> $null
    if ($LASTEXITCODE -eq 0) {
        $previous = Get-Content (Join-Path $metadataPath 'build-info.json') -Raw | ConvertFrom-Json
        $next = [Math]::Max($next, [long]$previous.versionCode + 1)
    }
}
if ($next -gt 2100000000) { throw 'Android version code limit reached.' }
"DEPOSITLY_BUILD_CODE=$next" | Out-File -FilePath $env:GITHUB_ENV -Encoding utf8 -Append
Write-Host "This build will use Android version code $next."
# Missing release/metadata is expected for the first publication. GitHub's
# PowerShell runner exits with LASTEXITCODE, so clear the optional lookup failure.
$global:LASTEXITCODE = 0

