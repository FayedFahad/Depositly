# Shared by the signed build and publication checks. Unsigned APKs never receive an update feed.
function Get-AppUpdateNotes([string]$Path) {
    $notes = if (Test-Path -LiteralPath $Path) { Get-Content -LiteralPath $Path -Raw -Encoding UTF8 } else { '' }
    $notes = ($notes -split '(?m)^## Earlier updates\s*\r?$', 2)[0].Trim()
    if ($notes.Length -gt 12000) { throw 'Current release notes exceed the 12000-character update feed limit.' }
    return $notes
}

function New-AppUpdateManifest($BuildInfo, [string]$ApkPath, [string]$Repository, [string]$ReleaseNotes, [string]$PublishedAt = '') {
    $certificate = '655445e0b1a199ef9ae90efa6e795d0800d36ef0ca725f5f87835d396796224d'
    if ($Repository -ne 'FayedFahad/Depositly') { throw 'Update feeds must point to the official public Depositly repository.' }
    if ($BuildInfo.package -ne 'app.depositly.mobile' -or $BuildInfo.versionName -notmatch '^\d+(?:\.\d+)+ Build [1-9]\d*$') { throw 'Invalid update package or visible version.' }
    $code = 0L
    if (![long]::TryParse([string]$BuildInfo.versionCode, [ref]$code) -or $code -lt 1 -or $code -gt 2100000000) { throw 'Invalid update Android version code.' }
    if ($BuildInfo.certificateSha256 -ne $certificate) { throw 'Only APKs signed with the original Depositly certificate can enter the update feed.' }
    if ($BuildInfo.apkSha256 -notmatch '^[a-f0-9]{64}$') { throw 'Invalid APK checksum.' }
    if ($ReleaseNotes.Length -gt 12000) { throw 'Release notes exceed the update feed limit.' }
    $slug = $BuildInfo.versionName -replace ' Build ', '-build-'
    if ([IO.Path]::GetFileName($ApkPath) -ne "Depositly-$slug.apk") { throw 'APK filename does not match the visible update version.' }
    $apk = Get-Item -LiteralPath $ApkPath
    if (!$apk.PSIsContainer -and $apk.Length -gt 0 -and $apk.Length -le 268435456) {
        if ((Get-FileHash -LiteralPath $ApkPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $BuildInfo.apkSha256) { throw 'The APK checksum does not match build-info.json.' }
    } else { throw 'Invalid update APK size.' }
    if (!$PublishedAt) { $PublishedAt = [DateTimeOffset]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ') }
    $timestamp = [DateTimeOffset]::MinValue
    if (![DateTimeOffset]::TryParse($PublishedAt, [ref]$timestamp)) { throw 'Invalid update publication time.' }
    return [ordered]@{
        schemaVersion = 1
        package = $BuildInfo.package
        versionName = $BuildInfo.versionName
        versionCode = $code
        apkUrl = "https://github.com/$Repository/releases/download/v$slug/Depositly-$slug.apk"
        apkSha256 = $BuildInfo.apkSha256
        certificateSha256 = $certificate
        apkSize = $apk.Length
        releaseNotes = $ReleaseNotes
        publishedAt = $PublishedAt
    }
}
