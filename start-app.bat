@echo off
chcp 65001 >nul
echo ========================================
echo 启动 WMS 系统应用
echo ========================================
echo.

REM 检查是否存在 Maven wrapper
if exist "mvnw.cmd" (
    echo 使用 Maven Wrapper 启动应用...
    call mvnw.cmd spring-boot:run
) else if exist "%MAVEN_HOME%\bin\mvn.cmd" (
    echo 使用 Maven 从 MAVEN_HOME 启动应用...
    call "%MAVEN_HOME%\bin\mvn.cmd" spring-boot:run
) else (
    echo 使用系统 Maven 启动应用...
    mvn spring-boot:run
)

if %ERRORLEVEL% equ 0 (
    echo.
    echo ========================================
    echo ✅ 应用已成功启动！
    echo ========================================
) else (
    echo.
    echo ========================================
    echo ❌ 应用启动失败
    echo ========================================
)

echo.
echo 按任意键退出...
pause >nul
