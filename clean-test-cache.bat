@echo off
echo ========================================
echo 清理 Spring 测试缓存和 Maven 缓存
echo ========================================
echo.

echo [1/4] 删除 target 目录...
if exist target (
    rmdir /s /q target
    echo ✓ target 目录已删除
) else (
    echo - target 目录不存在
)

echo.
echo [2/4] 清理 IDE 缓存...
echo 请手动执行以下操作之一：
echo.
echo IntelliJ IDEA:
echo   File ^> Invalidate Caches ^> Invalidate and Restart
echo.
echo Eclipse:
echo   Project ^> Clean... ^> Clean all projects
echo.
pause

echo.
echo [3/4] 删除 H2 本地缓存（如果存在）...
if exist "%USERPROFILE%\.m2\repository\com\h2database" (
    rmdir /s /q "%USERPROFILE%\.m2\repository\com\h2database"
    echo ✓ H2 缓存已删除
) else (
    echo - H2 缓存不存在
)

echo.
echo [4/4] 清理完成！
echo.
echo 下一步：
echo 1. 在 IDE 中刷新 Maven 项目（Reload All Maven Projects）
echo 2. 重新构建项目（Build ^> Rebuild Project）
echo 3. 运行测试
echo.
pause
