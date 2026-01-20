@echo off
REM ===================================================================
REM 清空 PostgreSQL 测试数据库
REM 数据库: wms_db_test
REM 用途: 在运行 @DataJpaTest 测试前清空所有表和索引
REM ===================================================================

echo ========================================
echo 正在清空测试数据库 wms_db_test...
echo ========================================

REM 执行清理 SQL 脚本
psql -U postgres -d wms_db_test -f clean-test-database.sql

if %errorlevel% equ 0 (
    echo.
    echo ========================================
    echo ✅ 测试数据库已清空成功
    echo ========================================
    echo.
    echo 现在可以运行测试:
    echo   - 在 IntelliJ IDEA 中右键 UserRepositoryTest
    echo   - 选择 Run 'UserRepositoryTest'
    echo.
) else (
    echo.
    echo ========================================
    echo ❌ 清空失败，请检查:
    echo ========================================
    echo   1. PostgreSQL 服务是否已启动
    echo   2. 用户名和密码是否正确
    echo   3. 数据库 wms_db_test 是否存在
    echo.
)

pause
