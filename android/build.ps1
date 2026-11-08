# 一键构建（本机没有 Android Studio 时用）
# 用法：.\build.ps1            构建 debug
#      .\build.ps1 -Task testDebugUnitTest
param(
    [string]$Task = "assembleDebug",
    [string]$Proxy = "127.0.0.1:7890"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)   # workspace 根（_tools 所在）
$tools = Join-Path $root "_tools"

$env:JAVA_HOME = Join-Path $tools "jdk21\jdk-21.0.13+9"
$env:ANDROID_HOME = Join-Path $tools "android-sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_USER_HOME = Join-Path $tools "android-home"
$env:GRADLE_USER_HOME = Join-Path $tools "gradle-home"
# 工程路径含中文，Kotlin 独立守护进程会把路径编码成 u5B66u4E60... 导致找不到源文件，
# 强制 in-process 编译（在 Gradle 进程内跑 kotlinc）绕开这个坑。
$env:TEMP = Join-Path $tools "tmp"
$env:TMP = $env:TEMP

$gradle = Join-Path $tools "gradle-8.11.1\bin\gradle.bat"
if (-not (Test-Path $gradle)) { throw "找不到 Gradle：$gradle" }
if (-not (Test-Path $env:JAVA_HOME)) { throw "找不到 JDK：$env:JAVA_HOME" }
if (-not (Test-Path $env:TEMP)) { New-Item -ItemType Directory -Path $env:TEMP | Out-Null }

$proxyArgs = @()
if ($Proxy) {
    $parts = $Proxy.Split(":")
    $proxyArgs = @(
        "-Dhttps.proxyHost=$($parts[0])", "-Dhttps.proxyPort=$($parts[1])",
        "-Dhttp.proxyHost=$($parts[0])", "-Dhttp.proxyPort=$($parts[1])"
    )
}

Push-Location $PSScriptRoot
try {
    & $gradle ":app:$Task" --no-daemon "-Pkotlin.compiler.execution.strategy=in-process" @proxyArgs
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
