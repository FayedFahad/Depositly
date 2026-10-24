. (Join-Path $PSScriptRoot 'common.ps1')
$version = Get-AppVersion
$repo = $env:GITHUB_REPOSITORY
$commit = $env:GITHUB_SHA
if (!$repo -or !$commit) { throw 'Publishing requires the GitHub Actions repository and commit context.' }
$notes = Join-Path $ProjectRoot 'build/release-notes.md'
$info = Get-Content (Join-Path $ProjectRoot 'dist/build-info.json') -Raw | ConvertFrom-Json
$changesPath = Join-Path $ProjectRoot 'docs/release-notes.md'
$changes = if (Test-Path $changesPath) { Get-Content $changesPath -Raw } else { '' }
@"
Signed APK built automatically from main.

Version: $($version.Name), Android version code: $($info.versionCode).
Source commit: $commit.

$changes

Install the APK over your existing Depositly app to retain entries. This release is refreshed after each successful main-branch app update.
"@ | Set-Content -LiteralPath $notes -Encoding UTF8
& gh api "repos/$repo/git/ref/tags/latest" *> $null
if ($LASTEXITCODE -eq 0) {
    & gh api --method PATCH "repos/$repo/git/refs/tags/latest" -f "sha=$commit" -F force=true *> $null
} else {
    & gh api --method POST "repos/$repo/git/refs" -f ref=refs/tags/latest -f "sha=$commit" *> $null
}
Assert-ToolExit
& gh release view latest --repo $repo *> $null
if ($LASTEXITCODE -eq 0) {
    & gh release edit latest --repo $repo --title "Depositly $($version.Name)" --notes-file $notes --latest | Out-Host
} else {
    & gh release create latest --repo $repo --verify-tag --title "Depositly $($version.Name)" --notes-file $notes --latest | Out-Host
}
Assert-ToolExit
# Remove obsolete APK names when the semantic version changes.
$assets = & gh api "repos/$repo/releases/tags/latest" --jq '.assets[] | select(.name | endswith(".apk")) | [.id,.name] | @tsv'
Assert-ToolExit
foreach ($asset in $assets) {
    $parts = $asset -split "`t", 2
    if ($parts[1] -ne "Depositly-$($version.Slug).apk") { & gh api --method DELETE "repos/$repo/releases/assets/$($parts[0])"; Assert-ToolExit }
}
& gh release upload latest (Join-Path $ProjectRoot "dist/Depositly-$($version.Slug).apk") (Join-Path $ProjectRoot 'dist/build-info.json') --repo $repo --clobber | Out-Host
Assert-ToolExit
