param([string]$SdkPath, [string[]]$Tasks=@('assembleRelease'), [switch]$WriteLocks)
. (Join-Path $PSScriptRoot 'common.ps1')
$env:ANDROID_HOME = Get-AndroidSdkPath $SdkPath
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$version = Get-AppVersion
$code = if ($env:DEPOSITLY_BUILD_CODE) { [int]$env:DEPOSITLY_BUILD_CODE } else { $version.Code }
$metadata = Get-Content (Join-Path $ProjectRoot 'tools/gradle-source.json') -Raw | ConvertFrom-Json
$workRoot = Get-AppWorkDirectory
$toolsRoot = Join-Path $workRoot 'tools'
New-Item -ItemType Directory -Force -Path $toolsRoot | Out-Null
$gradle = Join-Path $toolsRoot "gradle-$($metadata.version)/bin/gradle.bat"
if (!(Test-Path $gradle)) {
    $zip = Join-Path $toolsRoot 'gradle.zip'
    if (!(Test-Path $zip)) { Invoke-WebRequest $metadata.url -OutFile $zip }
    if ((Get-FileHash $zip -Algorithm SHA256).Hash.ToLowerInvariant() -ne $metadata.sha256) { throw 'Gradle checksum mismatch.' }
    Expand-Archive -LiteralPath $zip -DestinationPath $toolsRoot -Force
}
$cache = if ($env:DEPOSITLY_GRADLE_CACHE) { $env:DEPOSITLY_GRADLE_CACHE } else { Join-Path $workRoot 'gradle-home' }
$arguments = @('--no-daemon','--console=plain','--max-workers=2','-g',$cache,'--project-cache-dir',(Join-Path $workRoot 'project-cache'),'-p',(Join-Path $ProjectRoot 'native'),"-PdepositlyBuildDirectory=$(Join-Path $workRoot 'native-build')","-PdepositlyVersionCode=$code","-PdepositlyVersionName=$($version.Name)")
if ($WriteLocks) { $arguments += '--write-locks' }
& $gradle @arguments @Tasks | Out-Host
Assert-ToolExit
