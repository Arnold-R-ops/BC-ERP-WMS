@echo off
chcp 65001 >nul
echo ========================================
echo 启动 WMS 系统应用
echo ========================================
echo.

REM ---- P0 安全修复：启动前检查必需的环境变量 ----
set MISSING_ENV=
if "%DB_PASSWORD%"=="" set MISSING_ENV=%MISSING_ENV% DB_PASSWORD
if "%JWT_SECRET%"=="" set MISSING_ENV=%MISSING_ENV% JWT_SECRET
if "%BATCH_SALT%"=="" set MISSING_ENV=%MISSING_ENV% BATCH_SALT

if not "%MISSING_ENV%"=="" (
    echo ❌ 缺少必需的环境变量:%MISSING_ENV%
    echo.
    echo 请先按 docs\security\SECURITY_SETUP.md 完成一次性配置，例如:
    echo    setx DB_USERNAME "wms_app"
    echo    setx DB_PASSWORD "你的数据库密码"
    echo    setx JWT_SECRET  "Base64编码的强密钥"
    echo    setx BATCH_SALT  "自定义随机字符串"
    echo.
    echo 设置后请重新打开终端再运行本脚本。
    echo.
    pause >nul
    exit /b 1
)

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
