param([string]$SdkPath)
. (Join-Path $PSScriptRoot 'common.ps1')
$sdkRoot = Get-AndroidSdkPath $SdkPath
$androidJar = Join-Path $sdkRoot 'platforms/android-34/android.jar'
$nativeBuild = Join-Path $ProjectRoot ('build/native-' + (Get-Date -Format 'yyyyMMdd-HHmmssfff'))
New-Item -ItemType Directory -Force -Path "$nativeBuild/classes", "$nativeBuild/dex" | Out-Null
$sources = @(Get-ChildItem -LiteralPath (Join-Path $ProjectRoot 'native/src') -Filter '*.java' -Recurse -File | ForEach-Object FullName)
if (!$sources.Count) { throw 'Native Java sources are missing.' }
& javac --release 8 -encoding UTF-8 -classpath $androidJar -d "$nativeBuild/classes" @sources | Out-Host
Assert-ToolExit
Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::CreateFromDirectory("$nativeBuild/classes", "$nativeBuild/helpers.jar")
& (Join-Path $sdkRoot 'build-tools/34.0.0/d8.bat') --lib $androidJar --min-api 26 --output "$nativeBuild/dex" "$nativeBuild/helpers.jar" | Out-Host
Assert-ToolExit
# Android 8+ loads the helper DEX natively; no original APK/decompiler template is needed.
Write-Output "$nativeBuild/dex/classes.dex"

