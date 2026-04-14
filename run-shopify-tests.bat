@echo off
REM V3.9 Shopify Integration - 测试运行脚本
REM 用于快速运行所有 Shopify 相关测试

echo ========================================
echo V3.9 Shopify Integration 测试套件
echo ========================================
echo.

REM 检查 Maven 是否可用
where mvn >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [错误] 未找到 Maven，请确保 Maven 已安装并添加到 PATH
    echo.
    pause
    exit /b 1
)

echo [信息] 开始运行测试...
echo.

REM 设置 Maven 选项
set MAVEN_OPTS=-Xmx1024m

REM 选择测试类型
echo 请选择要运行的测试:
echo 1. 运行所有 Shopify 相关测试 (推荐)
echo 2. 运行 Repository 测试
echo 3. 运行 API Client 测试
echo 4. 运行 Service 测试
echo 5. 运行 Scheduler 测试
echo 6. 运行 E2E 集成测试
echo 7. 运行所有测试并生成报告
echo.

set /p choice="请输入选项 (1-7): "

if "%choice%"=="1" (
    echo.
    echo [运行] 所有 Shopify 相关测试...
    mvn test -Dtest=IntegrationConfigRepositoryTest,ShopifyApiClientTest,ShopifyIntegrationServiceTest,IntegrationSchedulerTest,ShopifyIntegrationE2ETest
) else if "%choice%"=="2" (
    echo.
    echo [运行] Repository 测试...
    mvn test -Dtest=IntegrationConfigRepositoryTest
) else if "%choice%"=="3" (
    echo.
    echo [运行] API Client 测试...
    mvn test -Dtest=ShopifyApiClientTest
) else if "%choice%"=="4" (
    echo.
    echo [运行] Service 测试...
    mvn test -Dtest=ShopifyIntegrationServiceTest
) else if "%choice%"=="5" (
    echo.
    echo [运行] Scheduler 测试...
    mvn test -Dtest=IntegrationSchedulerTest
) else if "%choice%"=="6" (
    echo.
    echo [运行] E2E 集成测试...
    mvn test -Dtest=ShopifyIntegrationE2ETest
) else if "%choice%"=="7" (
    echo.
    echo [运行] 所有测试并生成报告...
    mvn test -Dtest=IntegrationConfigRepositoryTest,ShopifyApiClientTest,ShopifyIntegrationServiceTest,IntegrationSchedulerTest,ShopifyIntegrationE2ETest
    echo.
    echo [生成] 测试报告...
    mvn surefire-report:report
    echo.
    echo [完成] 测试报告已生成: target\site\surefire-report.html
    start target\site\surefire-report.html
) else (
    echo.
    echo [错误] 无效的选项
    pause
    exit /b 1
)

echo.
echo ========================================
echo 测试完成
echo ========================================
echo.

REM 检查测试结果
if %ERRORLEVEL% EQU 0 (
    echo [成功] 所有测试通过！
) else (
    echo [失败] 部分测试失败，请查看上方日志
)

echo.
pause
