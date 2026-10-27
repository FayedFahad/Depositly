. (Join-Path $PSScriptRoot 'common.ps1')
$version = Get-AppVersion
$next = [long]$version.Code + [long]$env:GITHUB_RUN_NUMBER
$metadataPath = Join-Path $ProjectRoot 'build/previous-release'
New-Item -ItemType Directory -Force -Path $metadataPath | Out-Null
$previousBuildInfoPath = $null
$releaseLookup = @(& gh release view latest --repo $env:GITHUB_REPOSITORY 2>&1)
if ($LASTEXITCODE -eq 0) {
    & gh release download latest --repo $env:GITHUB_REPOSITORY --pattern build-info.json --dir $metadataPath --clobber *> $null
    if ($LASTEXITCODE -eq 0) {
        $previous = Get-Content (Join-Path $metadataPath 'build-info.json') -Raw | ConvertFrom-Json
        $next = [Math]::Max($next, [long]$previous.versionCode + 1)
        $previousBuildInfoPath = Join-Path $metadataPath 'build-info.json'
    } else { throw 'The previous release exists, but its build-info.json could not be read. Restore that metadata before releasing another app update.' }
} elseif (($releaseLookup -join "`n") -notmatch '(?i)release not found|HTTP 404|Not Found') {
    throw 'Could not inspect the previous release. Retry after GitHub access is restored.'
}
if ($next -gt 2100000000) { throw 'Android version code limit reached.' }
if ($previousBuildInfoPath) { Assert-NewAppRelease @{Name=$version.Name;Code=$next} $previousBuildInfoPath }
"DEPOSITLY_BUILD_CODE=$next" | Out-File -FilePath $env:GITHUB_ENV -Encoding utf8 -Append
Write-Host "This build will use Android version code $next."
# A missing release is expected for the first publication. GitHub's
# PowerShell runner exits with LASTEXITCODE, so clear the optional lookup failure.
$global:LASTEXITCODE = 0

