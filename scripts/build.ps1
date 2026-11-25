param([switch]$SkipTests)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$workspaceRoot=Split-Path -Parent $projectRoot
$jdkBin='C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot\bin'
$mergedJar=Join-Path $workspaceRoot 'build\moddev\artifacts\neoforge-21.1.209-merged.jar'
if(-not (Test-Path -LiteralPath $mergedJar)){throw 'Run Gradle build first to resolve the NeoForge development artifacts.'}
$classes=Join-Path $projectRoot 'build\classes\java\main'
$tests=Join-Path $projectRoot 'build\classes\java\test'
$output=Join-Path $projectRoot 'build\libs'
New-Item -ItemType Directory -Path $classes,$tests,$output -Force | Out-Null
# Use the resolved loader libraries; the merged development jar supplies Minecraft mappings.
$launcherLibraries=Join-Path $env:APPDATA 'ATLauncher\libraries'
$libraries=@(Get-ChildItem -LiteralPath $launcherLibraries -Recurse -File -Filter '*.jar' | Where-Object { $_.Name -notmatch '(sources|installer|client-1\.21|server-1\.21)' } | Select-Object -ExpandProperty FullName)
$gradleLibraries=Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1'
$extra=@('org.jetbrains\annotations','com.google.code.gson\gson','net.neoforged\bus','net.neoforged.fancymodloader','org.joml\joml','org.lwjgl','com.mojang','com.google.guava\guava','org.slf4j\slf4j-api','it.unimi.dsi\fastutil')
foreach($entry in $extra){$candidate=Join-Path $gradleLibraries $entry;if(Test-Path -LiteralPath $candidate){$libraries+=Get-ChildItem -LiteralPath $candidate -Recurse -File -Filter '*.jar' | Where-Object {$_.Name -notmatch 'sources'} | Select-Object -ExpandProperty FullName}}
$classPath=(@($mergedJar)+$libraries | Select-Object -Unique)-join ';'
$sourceFiles=Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src\main\java') -Recurse -File -Filter '*.java' | Select-Object -ExpandProperty FullName
$compilerArgs=@('--release','21','-encoding','UTF-8','-proc:none','-classpath',('"'+$classPath.Replace('\','/')+'"'),'-d',('"'+$classes.Replace('\','/')+'"'))
$compilerArgs+=$sourceFiles | ForEach-Object {'"'+$_.Replace('\','/')+'"'}
$argsFile=Join-Path $projectRoot 'build\compile.args'
[IO.File]::WriteAllLines($argsFile,$compilerArgs,[Text.UTF8Encoding]::new($false))
& (Join-Path $jdkBin 'javac.exe') ('@'+$argsFile)
if($LASTEXITCODE -ne 0){throw 'Java compilation failed'}
if(-not $SkipTests){
    & (Join-Path $jdkBin 'javac.exe') --release 21 -encoding UTF-8 -classpath $classes -d $tests (Join-Path $projectRoot 'src\test\java\dev\codex\fireflysam\CombatRulesTest.java')
    if($LASTEXITCODE -ne 0){throw 'Test compilation failed'}
    & (Join-Path $jdkBin 'java.exe') -classpath ($tests+';'+$classes) dev.codex.fireflysam.CombatRulesTest
    if($LASTEXITCODE -ne 0){throw 'Combat scenario verification failed'}
}
$jar=Join-Path $output 'firefly-sam-2.0.0.jar'
& (Join-Path $jdkBin 'jar.exe') --create --file $jar -C $classes . -C (Join-Path $projectRoot 'src\main\resources') .
if($LASTEXITCODE -ne 0){throw 'Jar packaging failed'}
Get-FileHash -LiteralPath $jar -Algorithm SHA256 | Format-List
Write-Output ('Built '+$jar)
