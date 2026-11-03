$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
. (Join-Path $project 'scripts/common.ps1')
. (Join-Path $project 'scripts/release-manifest.ps1')
$testRoot = Join-Path (Get-AppWorkDirectory) ('release-feed-tests-' + [guid]::NewGuid())
New-Item -ItemType Directory -Path "$testRoot/scripts", "$testRoot/app", "$testRoot/docs", "$testRoot/dist" | Out-Null
Copy-Item "$project/scripts/common.ps1", "$project/scripts/release-manifest.ps1", "$project/scripts/publish.ps1" "$testRoot/scripts/"
$apkPath = Join-Path $testRoot 'dist/Depositly-1.3.1-build-7.apk'
[IO.File]::WriteAllBytes($apkPath, [byte[]](1..128))
$info = [ordered]@{package='app.depositly.mobile';versionName='1.3.1 Build 7';versionCode=215;commit=('1' * 40);apkSha256=(Get-FileHash $apkPath -Algorithm SHA256).Hash.ToLowerInvariant();certificateSha256='655445e0b1a199ef9ae90efa6e795d0800d36ef0ca725f5f87835d396796224d'}
$notes = "Depositly 1.3.1 Build 7`n`n- Update from Settings."
$notes + "`n`n## Earlier updates`n`n- Old change." | Set-Content "$testRoot/docs/release-notes.md" -Encoding UTF8
if ((Get-AppUpdateNotes "$testRoot/docs/release-notes.md") -cne $notes) { throw 'The feed must contain current notes without accumulated history.' }
$manifest = New-AppUpdateManifest $info $apkPath 'FayedFahad/Depositly' $notes '2026-11-03T12:00:00Z'
if ($manifest.apkUrl -ne 'https://github.com/FayedFahad/Depositly/releases/download/v1.3.1-build-7/Depositly-1.3.1-build-7.apk' -or $manifest.apkSize -ne 128 -or $manifest.schemaVersion -ne 1 -or $manifest.versionCode -ne 215) { throw 'Incorrect immutable manifest fields.' }
foreach ($scenario in @('wrong-package','unsigned','wrong-hash','bad-code','bad-version','oversize-notes','wrong-repo','wrong-filename','bad-time')) {
    $candidate = [ordered]@{}
    foreach ($key in $info.Keys) { $candidate[$key] = $info[$key] }
    $repo = 'FayedFahad/Depositly'; $candidatePath = $apkPath; $changes = $notes; $timestamp = '2026-11-03T12:00:00Z'
    switch ($scenario) {
        'wrong-package' { $candidate.package = 'other.app' }
        'unsigned' { $candidate.certificateSha256 = $null }
        'wrong-hash' { $candidate.apkSha256 = '0' * 64 }
        'bad-code' { $candidate.versionCode = '215.5' }
        'bad-version' { $candidate.versionName = '1.3.1' }
        'oversize-notes' { $changes = 'x' * 12001 }
        'wrong-repo' { $repo = 'other/depositly' }
        'wrong-filename' { $candidatePath = "$testRoot/dist/other.apk"; Copy-Item $apkPath $candidatePath }
        'bad-time' { $timestamp = 'not-a-time' }
    }
    $failed = $false
    try { New-AppUpdateManifest $candidate $candidatePath $repo $changes $timestamp | Out-Null } catch { $failed = $true }
    if (!$failed) { throw "Invalid manifest scenario $scenario was accepted." }
}
"versionInfo:`n  versionCode: 215`n  versionName: 1.3.1 Build 7" | Set-Content "$testRoot/app/apktool.yml"
$info | ConvertTo-Json | Set-Content "$testRoot/dist/build-info.json"
$manifest | ConvertTo-Json | Set-Content "$testRoot/dist/update.json"
$wrapper = Join-Path $testRoot 'publish-test.ps1'
@'
param($Project, $Scenario, $Log)
$ErrorActionPreference = 'Stop'
$env:GITHUB_REPOSITORY = 'FayedFahad/Depositly'
$env:GITHUB_SHA = '1' * 40
function gh {
    [IO.File]::AppendAllText($Log, (ConvertTo-Json -InputObject @($args) -Compress) + "`n")
    $global:LASTEXITCODE = 0
    if ($args[0] -eq 'api' -and $args[1] -match '/git/ref/tags/v') {
        if ($Scenario -eq 'first-release') { $global:LASTEXITCODE = 1; return 'HTTP 404: Not Found' }
        $source = if ($Scenario -eq 'tag-different') { '2' * 40 } else { '1' * 40 }
        return ('{"object":{"type":"commit","sha":"' + $source + '"}}')
    }
    if ($args[0] -eq 'release' -and $args[1] -eq 'view') {
        if ($Scenario -eq 'lookup-failed') { $global:LASTEXITCODE = 1; return 'connection timed out' }
        if ($args[2] -eq 'latest' -and $Scenario -ne 'first-release') { return '{"isDraft":false,"targetCommitish":"main"}' }
        if ($Scenario -in @('existing-matching','existing-different')) { return '{"isDraft":false,"targetCommitish":"main"}' }
        if ($Scenario -eq 'draft-matching') { return ('{"isDraft":true,"targetCommitish":"' + ('1' * 40) + '"}') }
        if ($Scenario -eq 'draft-different') { return ('{"isDraft":true,"targetCommitish":"' + ('2' * 40) + '"}') }
        $global:LASTEXITCODE = 1; return 'release not found'
    }
    if ($args[0] -eq 'release' -and $args[1] -eq 'download') {
        $dir = $args[[Array]::IndexOf($args, '--dir') + 1]
        $prior = Get-Content "$Project/dist/build-info.json" -Raw | ConvertFrom-Json
        if ($Scenario -eq 'existing-different') { $prior.apkSha256 = '0' * 64 }
        $prior | ConvertTo-Json | Set-Content "$dir/build-info.json"
    }
    if ($args[0] -eq 'release' -and $args[1] -eq 'upload') {
        if ($Scenario -eq 'version-upload-failed' -and $args[2] -ne 'latest') { $global:LASTEXITCODE = 1 }
        if ($Scenario -eq 'latest-apk-failed' -and $args[2] -eq 'latest' -and $args[3].EndsWith('.apk')) { $global:LASTEXITCODE = 1 }
        if ($Scenario -eq 'latest-feed-failed' -and $args[2] -eq 'latest' -and $args[3].EndsWith('update.json')) { $global:LASTEXITCODE = 1 }
    }
    if ($Scenario -eq 'promotion-failed' -and $args[0] -eq 'release' -and $args[1] -eq 'edit' -and $args[2] -ne 'latest') { $global:LASTEXITCODE = 1 }
    if ($args[0] -eq 'api' -and $args -contains '--jq') { return "123`tDepositly-old.apk", "124`tDepositly-1.3.1-build-7.apk" }
}
& "$Project/scripts/publish.ps1"
if (Test-Path variable:LASTEXITCODE) { exit $LASTEXITCODE }
'@ | Set-Content $wrapper
foreach ($scenario in @('success','first-release','lookup-failed','version-upload-failed','promotion-failed','latest-apk-failed','latest-feed-failed','existing-matching','existing-different','draft-matching','draft-different','tag-different','manifest-tampered')) {
    $log = Join-Path $testRoot "$scenario.jsonl"
    $manifest | ConvertTo-Json | Set-Content "$testRoot/dist/update.json"
    if ($scenario -eq 'manifest-tampered') {
        $tampered = Get-Content "$testRoot/dist/update.json" -Raw | ConvertFrom-Json
        $tampered.apkUrl = 'https://example.com/other.apk'
        $tampered | ConvertTo-Json | Set-Content "$testRoot/dist/update.json"
    }
    $output = & (Join-Path $PSHOME 'pwsh.exe') -NoProfile -File $wrapper $testRoot $scenario $log 2>&1
    $result = $LASTEXITCODE
    $calls = if (Test-Path $log) { @(Get-Content $log | ForEach-Object { ,(ConvertFrom-Json $_) }) } else { @() }
    $success = $scenario -in @('success','first-release','existing-matching','draft-matching')
    if (($result -eq 0) -ne $success) { throw "Unexpected publication result for ${scenario}: $output" }
    $text = $calls | ForEach-Object { $_ -join ' ' }
    if (!$success -and ($text -match 'api --method DELETE' -or $text -match 'release edit latest')) { throw "Failed publication $scenario altered the advertised latest release or deleted prior APKs." }
    if ($scenario -eq 'version-upload-failed' -and ($text -match 'release edit v' -or $text -match 'release upload latest')) { throw 'An incomplete version must remain a draft without touching the rolling release.' }
    if ($scenario -eq 'promotion-failed' -and $text -match 'release upload latest') { throw 'Failed draft promotion must not touch the rolling release.' }
    if ($scenario -eq 'draft-matching' -and (($text -join "`n") -match 'release create v' -or ($text -join "`n") -notmatch 'release upload v')) { throw 'A same-source draft must be reused and finished.' }
    if ($scenario -eq 'draft-different' -and ($text -match 'release upload' -or $text -match 'release edit')) { throw 'A different-source draft must not be modified.' }
    if ($scenario -eq 'tag-different' -and ($text -match 'release create' -or $text -match 'release upload' -or $text -match 'release edit')) { throw 'A conflicting version tag must fail before changing releases.' }
    if ($scenario -in @('existing-matching','existing-different') -and $text -match 'release upload v') { throw 'Public version assets must never be overwritten.' }
    if ($scenario -eq 'manifest-tampered' -and $calls.Count) { throw 'A tampered manifest reached GitHub.' }
    if ($success) {
        $sequence = $text -join "`n"
        if ($sequence -notmatch 'release upload latest [^\r\n]+\.apk[^\r\n]+build-info\.json[\s\S]+release upload latest [^\r\n]+update\.json[\s\S]+release edit latest[\s\S]+api --method DELETE') { throw 'Latest feed must be uploaded after the APK/metadata and before cleanup.' }
        if ($scenario -in @('success','first-release')) {
            $created = @($calls | Where-Object { $_[0] -eq 'release' -and $_[1] -eq 'create' })
            foreach ($call in $created) {
                if ($call[[Array]::IndexOf($call, '--target') + 1] -ne ('1' * 40) -or $call -notcontains '--draft') { throw 'Draft releases must target the exact built source commit.' }
                if ($call[2] -ne 'latest' -and $call -notcontains '--verify-tag') { throw 'Version drafts must use the verified source tag.' }
            }
        }
    }
}
$global:LASTEXITCODE = 0
Write-Host 'Release feed tests passed (verified signed metadata, immutable downloads, history limit, draft-first publication, upload failures, retry and tampering rejection).'
