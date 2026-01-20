@echo off
chcp 65001 >nul
echo ========================================
echo 运行 WMS 系统测试
echo ========================================
echo.

REM 检查是否存在 Maven wrapper
if exist "mvnw.cmd" (
    echo 使用 Maven Wrapper...
    call mvnw.cmd test
) else if exist "%MAVEN_HOME%\bin\mvn.cmd" (
    echo 使用 Maven 从 MAVEN_HOME...
    call "%MAVEN_HOME%\bin\mvn.cmd" test
) else (
    echo 使用系统 Maven...
    mvn test
)

if %ERRORLEVEL% equ 0 (
    echo.
    echo ========================================
    echo ✅ 所有测试通过！
    echo ========================================
) else (
    echo.
    echo ========================================
    echo ❌ 部分测试失败
    echo ========================================
)

echo.
echo 按任意键退出...
pause >nul
