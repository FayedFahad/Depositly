param([switch]$Unsigned, [string]$SdkPath, [string]$KeystorePath, [string]$PasswordPath, [int]$VersionCodeOverride=0)
. (Join-Path $PSScriptRoot 'common.ps1')
$version = Get-AppVersion
if ($VersionCodeOverride) {
    if ($VersionCodeOverride -lt $version.Code -or $VersionCodeOverride -gt 2100000000) { throw 'Invalid Android version code override.' }
    $version.Code = $VersionCodeOverride
}
if (!$Unsigned) {
    Assert-NewAppRelease $version (Join-Path $ProjectRoot 'dist/build-info.json')
    if ($env:GITHUB_ACTIONS -eq 'true') {
        Assert-NewAppRelease $version (Join-Path $ProjectRoot 'build/previous-release/build-info.json')
    }
}
& node (Join-Path $ProjectRoot 'tests/display.test.cjs') | Out-Host
Assert-ToolExit
$sdkRoot = Get-AndroidSdkPath $SdkPath
& (Join-Path $PSScriptRoot 'bootstrap.ps1') -SdkPath $sdkRoot
$buildTools = Join-Path $sdkRoot 'build-tools/34.0.0'
$workRoot = Get-AppWorkDirectory
$buildRoot = Join-Path $workRoot 'build'
$releaseRoot = Join-Path $ProjectRoot 'dist'
New-Item -ItemType Directory -Force -Path $buildRoot, $releaseRoot | Out-Null
$env:DEPOSITLY_BUILD_CODE = [string]$version.Code
& (Join-Path $PSScriptRoot 'gradle.ps1') -SdkPath $sdkRoot -Tasks @('assembleRelease')
$unsignedPath = Join-Path $workRoot 'native-build/outputs/apk/release/Depositly-release-unsigned.apk'
if (!(Test-Path $unsignedPath)) { throw 'Gradle did not produce the unsigned release APK.' }
$alignedPath = Join-Path $buildRoot 'Depositly-aligned.apk'
& "$buildTools/zipalign.exe" -f 4 $unsignedPath $alignedPath | Out-Host
Assert-ToolExit
$suffix = if ($Unsigned) { '-unsigned' } else { '' }
$output = Join-Path $releaseRoot "Depositly-$($version.Slug)$suffix.apk"
$certificate = '655445e0b1a199ef9ae90efa6e795d0800d36ef0ca725f5f87835d396796224d'
if ($Unsigned) { Copy-Item -LiteralPath $alignedPath -Destination $output }
else {
    if (!$KeystorePath) { $KeystorePath = Join-Path $ProjectRoot 'private/signing/depositly.p12' }
    if (!$PasswordPath) { $PasswordPath = Join-Path $ProjectRoot 'private/signing/password.txt' }
    if (!(Test-Path -LiteralPath $KeystorePath) -or !(Test-Path -LiteralPath $PasswordPath)) { throw 'Signing files are missing; see docs/github.md.' }
    & "$buildTools/apksigner.bat" sign --ks $KeystorePath --ks-type PKCS12 --ks-key-alias depositly --ks-pass "file:$PasswordPath" --out $output $alignedPath | Out-Host
    Assert-ToolExit
    $verification = & "$buildTools/apksigner.bat" verify --verbose --print-certs $output
    Assert-ToolExit
    $digest = @($verification | Select-String '^Signer #1 certificate SHA-256 digest:')[0].Line
    if (!$digest.EndsWith($certificate)) { throw 'Signing certificate differs from the installed Depositly app.' }
}
& "$buildTools/zipalign.exe" -c 4 $output | Out-Host
Assert-ToolExit
$badging = & "$buildTools/aapt.exe" dump badging $output
Assert-ToolExit
if (!$badging[0].Contains("name='app.depositly.mobile' versionCode='$($version.Code)' versionName='$($version.Name)'")) { throw 'APK package/version verification failed.' }
$info = [ordered]@{package='app.depositly.mobile';versionName=$version.Name;versionCode=$version.Code;commit=$env:GITHUB_SHA;apkSha256=(Get-FileHash -LiteralPath $output -Algorithm SHA256).Hash.ToLowerInvariant();certificateSha256=if ($Unsigned) {$null} else {$certificate}}
$infoName = if ($Unsigned) { 'build-info-unsigned.json' } else { 'build-info.json' }
$info | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $releaseRoot $infoName) -Encoding UTF8
Write-Host "Built and verified $output (Android version code $($version.Code))."

