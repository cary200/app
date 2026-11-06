$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
$jar = Join-Path $PSScriptRoot "gradle/wrapper/gradle-wrapper.jar"
$expected = "498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17"
if (!(Test-Path $jar)) {
    Invoke-WebRequest -UseBasicParsing -Uri "https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar" -OutFile "$jar.tmp"
    if ((Get-FileHash "$jar.tmp" -Algorithm SHA256).Hash -ne $expected) { throw "Gradle wrapper checksum mismatch" }
    Move-Item "$jar.tmp" $jar -Force
}
if ((Get-FileHash $jar -Algorithm SHA256).Hash -ne $expected) { throw "Gradle wrapper checksum mismatch" }
if (!$env:JAVA_HOME -and (Test-Path "$env:ProgramFiles/Android/Android Studio/jbr/bin/java.exe")) {
    $env:JAVA_HOME = "$env:ProgramFiles/Android/Android Studio/jbr"
}
if (!$env:ANDROID_HOME -and (Test-Path "$env:LOCALAPPDATA/Android/Sdk")) {
    $env:ANDROID_HOME = "$env:LOCALAPPDATA/Android/Sdk"
}
$targets = if ($args.Count) { $args } else { @(":app:assembleDebug", ":fixture:assembleDebug") }
& .\gradlew.bat @targets
exit $LASTEXITCODE
