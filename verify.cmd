@echo off
setlocal
if not "%~2"=="" goto usage
if not "%~1"=="" set "RSCACHE_DIR=%~1"
if "%RSCACHE_DIR%"=="" goto usage
call "%~dp0mvnw.cmd" clean verify -Drscache="%RSCACHE_DIR%"
exit /b %ERRORLEVEL%

:usage
echo Usage: verify.cmd ^<revision-377-rscache-directory^> 1>&2
echo    or: set RSCACHE_DIR=C:\path\to\rscache ^&^& verify.cmd 1>&2
exit /b 2
