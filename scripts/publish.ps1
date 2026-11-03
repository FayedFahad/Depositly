. (Join-Path $PSScriptRoot 'common.ps1')
. (Join-Path $PSScriptRoot 'release-manifest.ps1')
$version = Get-AppVersion
$repo = $env:GITHUB_REPOSITORY
$commit = $env:GITHUB_SHA
if (!$repo -or !$commit) { throw 'Publishing requires the GitHub Actions repository and commit context.' }
$work = Get-AppWorkDirectory
$notes = Join-Path $work 'release-notes.md'
$infoPath = Join-Path $ProjectRoot 'dist/build-info.json'
$manifestPath = Join-Path $ProjectRoot 'dist/update.json'
$apkPath = Join-Path $ProjectRoot "dist/Depositly-$($version.Slug).apk"
$info = Get-Content -LiteralPath $infoPath -Raw | ConvertFrom-Json
if ($info.versionName -ne $version.Name -or [long]$info.versionCode -lt $version.Code -or $info.commit -ne $commit) { throw 'APK version/source metadata does not match this release. Rebuild before publishing.' }
Assert-NewAppRelease @{Name=$info.versionName;Code=$info.versionCode} (Join-Path $ProjectRoot 'build/previous-release/build-info.json')
$changes = Get-AppUpdateNotes (Join-Path $ProjectRoot 'docs/release-notes.md')
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
$expected = New-AppUpdateManifest $info $apkPath $repo $changes $manifest.publishedAt
foreach ($key in $expected.Keys) {
    if ([string]$manifest.$key -cne [string]$expected[$key]) { throw "Update manifest differs from the verified release ($key). Rebuild before publishing." }
}
@"
Signed APK built automatically from main.

Version: $($version.Name), Android version code: $($info.versionCode).
Source commit: $commit.

$changes

Install the APK over your existing Depositly app to retain entries. Settings and the website use update.json to offer this verified release.
"@ | Set-Content -LiteralPath $notes -Encoding UTF8

function Get-Release([string]$Tag) {
    $lookup = @(& gh release view $Tag --repo $repo --json isDraft,targetCommitish 2>&1)
    if ($LASTEXITCODE -eq 0) { return ($lookup -join "`n" | ConvertFrom-Json) }
    if (($lookup -join "`n") -match '(?i)release not found|HTTP 404|Not Found') { $global:LASTEXITCODE = 0; return $null }
    throw "Could not inspect release $Tag. Retry after GitHub access is restored."
}

# A feed always names a version-specific APK. Publishing another update never
# deletes or overwrites an APK that an older manifest may still reference.
$tag = "v$($version.Slug)"
$tagLookup = @(& gh api "repos/$repo/git/ref/tags/$tag" 2>&1)
if ($LASTEXITCODE -eq 0) {
    $sourceTag = $tagLookup -join "`n" | ConvertFrom-Json
    if ($sourceTag.object.type -ne 'commit' -or $sourceTag.object.sha -ne $commit) { throw "Version tag $tag does not identify this source commit. Increase the visible Build." }
} elseif (($tagLookup -join "`n") -match '(?i)HTTP 404|Not Found') {
    & gh api --method POST "repos/$repo/git/refs" -f "ref=refs/tags/$tag" -f "sha=$commit" *> $null
    Assert-ToolExit
} else { throw "Could not inspect version tag $tag. Retry after GitHub access is restored." }
$release = Get-Release $tag
if ($release -and !$release.isDraft) {
    # A publishing retry can resume the compatibility alias, but public version
    # assets are immutable. A rebuilt APK with different bytes needs a new Build.
    $priorPath = Join-Path $work ('published-' + $version.Slug)
    New-Item -ItemType Directory -Force -Path $priorPath | Out-Null
    & gh release download $tag --repo $repo --pattern build-info.json --dir $priorPath --clobber | Out-Host
    Assert-ToolExit
    $prior = Get-Content -LiteralPath (Join-Path $priorPath 'build-info.json') -Raw | ConvertFrom-Json
    foreach ($key in @('package','versionName','versionCode','commit','apkSha256','certificateSha256')) {
        if ([string]$prior.$key -cne [string]$info.$key) { throw "Public release $tag already contains another APK. Increase the visible Build before publishing again." }
    }
} else {
    if ($release -and $release.targetCommitish -ne $commit) { throw "Draft release $tag belongs to another source commit. Increase the visible Build." }
    if (!$release) {
        & gh release create $tag --repo $repo --verify-tag --target $commit --title "Depositly $($version.Name)" --notes-file $notes --draft --latest=false | Out-Host
        Assert-ToolExit
    }
    & gh release upload $tag $apkPath $infoPath $manifestPath --repo $repo --clobber | Out-Host
    Assert-ToolExit
    # Until all three uploads succeed, only a private draft exists.
    & gh release edit $tag --repo $repo --draft=false --latest=false | Out-Host
    Assert-ToolExit
}

# Keep the established /releases/download/latest/* URLs for existing clients.
# Upload the new APK first, metadata next, and the public update feed last.
# Old rolling APK names remain available if a later upload fails.
$rolling = Get-Release 'latest'
if (!$rolling) {
    & gh release create latest --repo $repo --target $commit --title "Depositly $($version.Name)" --notes-file $notes --draft --latest=false | Out-Host
    Assert-ToolExit
}
& gh release upload latest $apkPath $infoPath --repo $repo --clobber | Out-Host
Assert-ToolExit
& gh release upload latest $manifestPath --repo $repo --clobber | Out-Host
Assert-ToolExit
& gh api "repos/$repo/git/ref/tags/latest" *> $null
if ($LASTEXITCODE -eq 0) {
    & gh api --method PATCH "repos/$repo/git/refs/tags/latest" -f "sha=$commit" -F force=true *> $null
} else {
    & gh api --method POST "repos/$repo/git/refs" -f ref=refs/tags/latest -f "sha=$commit" *> $null
}
Assert-ToolExit
& gh release edit latest --repo $repo --title "Depositly $($version.Name)" --notes-file $notes --draft=false --latest | Out-Host
Assert-ToolExit

# Cleanup is deliberately last. Version-specific releases stay available.
$assets = & gh api "repos/$repo/releases/tags/latest" --jq '.assets[] | select(.name | endswith(".apk")) | [.id,.name] | @tsv'
Assert-ToolExit
foreach ($asset in $assets) {
    $parts = $asset -split "`t", 2
    if ($parts[1] -ne "Depositly-$($version.Slug).apk") { & gh api --method DELETE "repos/$repo/releases/assets/$($parts[0])"; Assert-ToolExit }
}
