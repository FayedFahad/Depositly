$ErrorActionPreference = 'Stop'
$projectPath = Split-Path $PSScriptRoot -Parent
$sdkPath = 'C:/Android/Sdk'
$androidJar = "$sdkPath/platforms/android-34/android.jar"
$nativeBuild = Join-Path $projectPath ('build/native-' + (Get-Date -Format 'yyyyMMdd-HHmmssfff'))
New-Item -ItemType Directory -Force -Path "$nativeBuild/classes", "$nativeBuild/dex", "$nativeBuild/apk" | Out-Null
function Check { if ($LASTEXITCODE -ne 0) { throw "Native build tool failed: $LASTEXITCODE" } }
$sources = @(rg --files (Join-Path $PSScriptRoot 'src') -g '*.java')
Check
& javac --release 8 -encoding UTF-8 -classpath $androidJar -d "$nativeBuild/classes" @sources
Check
Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::CreateFromDirectory("$nativeBuild/classes", "$nativeBuild/helpers.jar")
& "$sdkPath/build-tools/34.0.0/d8.bat" --lib $androidJar --min-api 26 --output "$nativeBuild/dex" "$nativeBuild/helpers.jar"
Check
Copy-Item -LiteralPath "$nativeBuild/dex/classes.dex" -Destination "$nativeBuild/apk/classes.dex"
$template = [System.IO.Compression.ZipFile]::OpenRead((Join-Path $projectPath '../Depositly-1.0.apk'))
try { [System.IO.Compression.ZipFileExtensions]::ExtractToFile($template.GetEntry('AndroidManifest.xml'), "$nativeBuild/apk/AndroidManifest.xml") }
finally { $template.Dispose() }
[System.IO.Compression.ZipFile]::CreateFromDirectory("$nativeBuild/apk", "$nativeBuild/helpers.apk")
& java -jar "$projectPath/tools/apktool.jar" d -r --no-assets "$nativeBuild/helpers.apk" -o "$nativeBuild/decoded" -p "$projectPath/build/framework"
Check
Copy-Item -Path "$nativeBuild/decoded/smali/*" -Destination "$projectPath/app/smali" -Recurse -Force
Write-Output 'Compiled Android reminder helpers from their Java sources.'
