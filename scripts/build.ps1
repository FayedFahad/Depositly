param([switch]$Unsigned, [string]$SdkPath, [string]$KeystorePath, [string]$PasswordPath, [int]$VersionCodeOverride=0)
. (Join-Path $PSScriptRoot 'common.ps1')
$sdkRoot = Get-AndroidSdkPath $SdkPath
& (Join-Path $PSScriptRoot 'bootstrap.ps1') -SdkPath $sdkRoot
$buildTools = Join-Path $sdkRoot 'build-tools/34.0.0'
$version = Get-AppVersion
if ($VersionCodeOverride) {
    if ($VersionCodeOverride -lt $version.Code -or $VersionCodeOverride -gt 2100000000) { throw 'Invalid Android version code override.' }
    $version.Code = $VersionCodeOverride
}
$buildRoot = Join-Path $ProjectRoot 'build'
$releaseRoot = Join-Path $ProjectRoot 'dist'
New-Item -ItemType Directory -Force -Path $buildRoot, "$buildRoot/framework", $releaseRoot | Out-Null
$stage = Join-Path $buildRoot ('app-' + (Get-Date -Format 'yyyyMMdd-HHmmssfff'))
New-Item -ItemType Directory -Force -Path $stage | Out-Null
foreach ($entry in @('assets','res','AndroidManifest.xml','apktool.yml')) {
    Copy-Item -LiteralPath (Join-Path $ProjectRoot "app/$entry") -Destination $stage -Recurse
}
# Reminder bytecode is compiled from native/src, rather than checked-in generated smali.
$smaliRoot = Join-Path $ProjectRoot 'app/smali'
foreach ($source in (Get-ChildItem -LiteralPath $smaliRoot -Filter '*.smali' -Recurse -File)) {
    $relative = $source.FullName.Substring($smaliRoot.Length + 1)
    if ($relative.Replace('\','/') -like 'app/depositly/mobile/reminders/*') { continue }
    $destination = Join-Path $stage "smali/$relative"
    New-Item -ItemType Directory -Force -Path (Split-Path $destination -Parent) | Out-Null
    Copy-Item -LiteralPath $source.FullName -Destination $destination
}
$configPath = Join-Path $stage 'apktool.yml'
$config = Get-Content $configPath -Raw
$config = [regex]::Replace($config,'(?m)(versionCode:\s*)\d+',('${1}' + $version.Code))
[IO.File]::WriteAllText($configPath,$config,[Text.UTF8Encoding]::new($false))
$nativeDex = & (Join-Path $PSScriptRoot 'build-native.ps1') -SdkPath $sdkRoot
$unsignedPath = Join-Path $buildRoot 'Depositly-unsigned.apk'
$alignedPath = Join-Path $buildRoot 'Depositly-aligned.apk'
& java -jar (Join-Path $ProjectRoot 'tools/apktool.jar') b -f $stage --aapt "$buildTools/aapt2.exe" -p "$buildRoot/framework" -o $unsignedPath | Out-Host
Assert-ToolExit
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::Open($unsignedPath,[IO.Compression.ZipArchiveMode]::Update)
try {
    if ($archive.GetEntry('classes2.dex')) { throw 'Unexpected duplicate helper DEX.' }
    [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,$nativeDex,'classes2.dex') | Out-Null
} finally { $archive.Dispose() }
& "$buildTools/zipalign.exe" -f 4 $unsignedPath $alignedPath | Out-Host
Assert-ToolExit
$suffix = if ($Unsigned) { '-unsigned' } else { '' }
$output = Join-Path $releaseRoot "Depositly-$($version.Name)$suffix.apk"
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
$info | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $releaseRoot 'build-info.json') -Encoding UTF8
Write-Host "Built and verified $output (Android version code $($version.Code))."

