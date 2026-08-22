@echo off
setlocal EnableExtensions
set "BASE_DIR=%~dp0"
set "DIST_DIR=%BASE_DIR%.mvn\wrapper\dists\apache-maven-3.9.16"
set "MAVEN_BIN=%DIST_DIR%\apache-maven-3.9.16\bin\mvn.cmd"
set "ARCHIVE=%DIST_DIR%\apache-maven-3.9.16-bin.zip"
set "PROPS=%BASE_DIR%.mvn\wrapper\maven-wrapper.properties"
set "URL="
set "EXPECTED="
for /f "usebackq tokens=1,* delims==" %%A in ("%PROPS%") do (
    if "%%A"=="distributionUrl" set "URL=%%B"
    if "%%A"=="distributionSha512Sum" set "EXPECTED=%%B"
)
if not defined URL (
    echo mvnw: distributionUrl is missing from %PROPS% 1>&2
    exit /b 1
)
if not defined EXPECTED (
    echo mvnw: distributionSha512Sum is missing from %PROPS% 1>&2
    exit /b 1
)

if exist "%MAVEN_BIN%" goto run

echo mvnw: bootstrapping Apache Maven 3.9.16 1>&2
if not exist "%DIST_DIR%" mkdir "%DIST_DIR%"
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference='Stop'; Invoke-WebRequest -UseBasicParsing -Uri '%URL%' -OutFile '%ARCHIVE%'; $h=(Get-FileHash -Algorithm SHA512 '%ARCHIVE%').Hash.ToLowerInvariant(); if ($h -ne '%EXPECTED%') { Remove-Item -Force '%ARCHIVE%'; throw 'Maven distribution SHA-512 mismatch' }; $extract='%DIST_DIR%\.extract'; Remove-Item -Recurse -Force $extract -ErrorAction SilentlyContinue; Expand-Archive -Path '%ARCHIVE%' -DestinationPath $extract -Force; Remove-Item -Recurse -Force '%DIST_DIR%\apache-maven-3.9.16' -ErrorAction SilentlyContinue; Move-Item ($extract + '\apache-maven-3.9.16') '%DIST_DIR%\apache-maven-3.9.16'; Remove-Item -Recurse -Force $extract; Remove-Item -Force '%ARCHIVE%'"
if errorlevel 1 exit /b 1

:run
call "%MAVEN_BIN%" %*
exit /b %ERRORLEVEL%
