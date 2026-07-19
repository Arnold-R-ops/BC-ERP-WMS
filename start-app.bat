@echo off
setlocal
cd /d "%~dp0"

echo ========================================
echo Starting WMS backend
echo ========================================
echo.

rem Validate the secrets required by application.yml.
set "MISSING_ENV="
if not defined DB_PASSWORD set "MISSING_ENV=%MISSING_ENV% DB_PASSWORD"
if not defined JWT_SECRET set "MISSING_ENV=%MISSING_ENV% JWT_SECRET"
if not defined BATCH_SALT set "MISSING_ENV=%MISSING_ENV% BATCH_SALT"

if defined MISSING_ENV (
    echo ERROR: Missing required environment variables:%MISSING_ENV%
    echo See docs\security\SECURITY_SETUP.md for setup instructions.
    echo Reopen the terminal after using setx.
    exit /b 1
)

set "MVN_CMD="

if exist "%CD%\mvnw.cmd" set "MVN_CMD=%CD%\mvnw.cmd"

if not defined MVN_CMD if defined MAVEN_HOME if exist "%MAVEN_HOME%\bin\mvn.cmd" (
    set "MVN_CMD=%MAVEN_HOME%\bin\mvn.cmd"
)

if not defined MVN_CMD (
    for /f "delims=" %%I in ('where mvn.cmd 2^>nul') do if not defined MVN_CMD set "MVN_CMD=%%I"
)

rem IntelliJ bundles Maven even when it is not installed system-wide.
if not defined MVN_CMD if exist "D:\ITsoftware\Toolbox" (
    for /f "delims=" %%I in ('where /r "D:\ITsoftware\Toolbox" mvn.cmd 2^>nul') do if not defined MVN_CMD set "MVN_CMD=%%I"
)

if not defined MVN_CMD (
    echo ERROR: Maven was not found.
    echo Install Maven, set MAVEN_HOME, or add Maven to PATH.
    exit /b 1
)

echo Maven: %MVN_CMD%
call "%MVN_CMD%" spring-boot:run
set "APP_EXIT=%ERRORLEVEL%"

if "%APP_EXIT%"=="0" (
    echo WMS backend stopped normally.
) else (
    echo ERROR: WMS backend failed with exit code %APP_EXIT%.
)

exit /b %APP_EXIT%
