param([Parameter(ValueFromRemainingArguments=$true)][string[]]$GradleArguments)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$socketDirectory=Join-Path $env:USERPROFILE '.cache\firefly-sam\sockets'
New-Item -ItemType Directory -Path $socketDirectory -Force | Out-Null
$previousOptions=$env:JAVA_TOOL_OPTIONS
try {
    # Current Windows JDKs cannot connect AF_UNIX selector pipes through this
    # machine's shortened ADMINI~1 TEMP path. Use an explicit full directory.
    $env:JAVA_TOOL_OPTIONS=($previousOptions+' "-Djdk.net.unixdomain.tmpdir='+$socketDirectory+'"').Trim()
    Push-Location $projectRoot
    try { & (Join-Path $projectRoot 'gradlew.bat') build --console=plain @GradleArguments; $buildExit=$LASTEXITCODE }
    finally { Pop-Location }
} finally { $env:JAVA_TOOL_OPTIONS=$previousOptions }
exit $buildExit
