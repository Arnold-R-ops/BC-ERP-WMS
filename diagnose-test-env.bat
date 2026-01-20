@echo off
chcp 65001 >nul
echo ========================================
echo PostgreSQL 测试环境诊断工具
echo ========================================
echo.

echo [1/4] 检查 PostgreSQL 服务状态...
sc query postgresql-x64-16 | findstr "STATE" >nul 2>&1
if %errorlevel% equ 0 (
    sc query postgresql-x64-16 | findstr "RUNNING" >nul 2>&1
    if %errorlevel% equ 0 (
        echo ✓ PostgreSQL 服务正在运行
    ) else (
        echo ✗ PostgreSQL 服务未运行
        echo.
        echo 正在尝试启动服务...
        net start postgresql-x64-16
        if %errorlevel% equ 0 (
            echo ✓ 服务启动成功
        ) else (
            echo ✗ 服务启动失败，请手动启动
            pause
            exit /b 1
        )
    )
) else (
    echo ✗ 找不到 PostgreSQL 服务（可能服务名不同）
    echo 请手动检查服务：services.msc
)

echo.
echo [2/4] 测试数据库连接...
psql -U postgres -h localhost -c "SELECT version();" >nul 2>&1
if %errorlevel% equ 0 (
    echo ✓ 可以连接到 PostgreSQL
) else (
    echo ✗ 无法连接到 PostgreSQL
    echo 可能原因：
    echo   1. 密码错误
    echo   2. PostgreSQL 未启动
    echo   3. 端口 5432 被占用
    pause
    exit /b 1
)

echo.
echo [3/4] 检查测试数据库...
psql -U postgres -h localhost -c "\l" | findstr "wms_db_test" >nul 2>&1
if %errorlevel% equ 0 (
    echo ✓ 测试数据库 wms_db_test 已存在
) else (
    echo ✗ 测试数据库 wms_db_test 不存在
    echo.
    echo 正在创建测试数据库...
    psql -U postgres -h localhost -c "CREATE DATABASE wms_db_test WITH OWNER = postgres ENCODING = 'UTF8';"
    if %errorlevel% equ 0 (
        echo ✓ 测试数据库创建成功
    ) else (
        echo ✗ 测试数据库创建失败
        pause
        exit /b 1
    )
)

echo.
echo [4/4] 验证测试配置文件...
if exist "src\test\resources\application-test.yml" (
    echo ✓ 测试配置文件存在
    findstr /C:"wms_db_test" "src\test\resources\application-test.yml" >nul 2>&1
    if %errorlevel% equ 0 (
        echo ✓ 配置文件指向测试数据库
    ) else (
        echo ✗ 配置文件未正确配置
    )
) else (
    echo ✗ 测试配置文件不存在
)

echo.
echo ========================================
echo 诊断完成！
echo ========================================
echo.
echo 如果所有检查都通过 ✓，您可以运行测试了
echo.
echo 下一步：
echo 1. 在 IntelliJ IDEA 中右键 AuthControllerIntegrationTest
echo 2. 选择 "Run 'AuthControllerIntegrationTest'"
echo 3. 查看测试结果
echo.
pause
