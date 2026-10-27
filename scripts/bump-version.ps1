param([string]$PreviousBuildInfoPath)
. (Join-Path $PSScriptRoot 'common.ps1')

$version = Get-AppVersion
$parts = [regex]::Match($version.Name, '^(?<base>\d+(?:\.\d+)+)(?: Build (?<build>[1-9]\d*))?$')
$buildNumber = 0L
if ($parts.Groups['build'].Success -and ![long]::TryParse($parts.Groups['build'].Value, [ref]$buildNumber)) {
    throw 'The visible build number is too large.'
}
if ($buildNumber -eq [long]::MaxValue) { throw 'The visible build number limit has been reached.' }
$nextName = "$($parts.Groups['base'].Value) Build $($buildNumber + 1)"
$nextSlug = $nextName -replace ' Build ', '-build-'
$highestCode = [long]$version.Code
$metadataPaths = @()
$localMetadata = Join-Path $ProjectRoot 'dist/build-info.json'
if (Test-Path -LiteralPath $localMetadata) { $metadataPaths += $localMetadata }
if ($PreviousBuildInfoPath) {
    if (!(Test-Path -LiteralPath $PreviousBuildInfoPath -PathType Leaf)) { throw 'The previous build information file does not exist.' }
    $metadataPaths += $PreviousBuildInfoPath
}
foreach ($metadataPath in $metadataPaths) {
    $previous = Get-Content -LiteralPath $metadataPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $previousCode = 0L
    if ([string]$previous.versionCode -notmatch '^\d+$' -or ![long]::TryParse([string]$previous.versionCode, [ref]$previousCode)) {
        throw "Invalid Android version code in $metadataPath."
    }
    $highestCode = [Math]::Max($highestCode, $previousCode)
}
if ($highestCode -ge 2100000000) { throw 'Android version code limit reached.' }
$nextCode = $highestCode + 1

# Prepare every replacement before writing, so invalid metadata never partially bumps a release.
$configPath = Join-Path $ProjectRoot 'app/apktool.yml'
$config = Get-Content -LiteralPath $configPath -Raw -Encoding UTF8
$replacements = [ordered]@{
    '(?m)^([ \t]*versionName:[ \t]*)[^\r\n]+' = $nextName
    '(?m)^([ \t]*versionCode:[ \t]*)[^\r\n]+' = [string]$nextCode
    '(?m)^([ \t]*apkFileName:[ \t]*)[^\r\n]+' = "Depositly-$nextSlug.apk"
}
foreach ($pattern in $replacements.Keys) {
    if ([regex]::Matches($config, $pattern).Count -ne 1) { throw 'App version configuration must have one version name, code and APK filename.' }
    $config = [regex]::Replace($config, $pattern, ('${1}' + $replacements[$pattern]))
}
$updates = [ordered]@{ $configPath = $config }
foreach ($relativePath in @('app/assets/index.html', 'README.md', 'docs/release-notes.md', 'docs/google-drive.md')) {
    $filePath = Join-Path $ProjectRoot $relativePath
    if (!(Test-Path -LiteralPath $filePath -PathType Leaf)) { continue }
    $content = Get-Content -LiteralPath $filePath -Raw -Encoding UTF8
    # Protect the filename while replacing a bare version such as 2.0 in prose.
    $filenameMarker = '__DEPOSITLY_APK_' + [guid]::NewGuid().ToString('N') + '__'
    $content = $content.Replace("Depositly-$($version.Slug).apk", $filenameMarker)
    $content = [regex]::Replace($content, ('(?<![\d.])' + [regex]::Escape($version.Name) + '(?!\d|\.\d)'), $nextName)
    $content = $content.Replace($filenameMarker, "Depositly-$nextSlug.apk")
    $updates[$filePath] = $content
}
$encoding = [Text.UTF8Encoding]::new($false)
foreach ($filePath in $updates.Keys) { [IO.File]::WriteAllText($filePath, $updates[$filePath], $encoding) }
Write-Host "App version updated to $nextName (Android version code $nextCode)."
