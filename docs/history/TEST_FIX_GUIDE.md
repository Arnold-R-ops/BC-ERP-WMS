# 测试修复指南

## 问题描述
`AuthControllerIntegrationTest` 无法启动，错误：`Cannot load driver class: org.h2.Driver`

## 根本原因
H2 数据库驱动未被正确加载到测试 classpath 中。

---

## 解决方案

### 步骤 1: 强制刷新 Maven 依赖

#### IntelliJ IDEA
1. 打开 Maven 面板（右侧栏或 View → Tool Windows → Maven）
2. 点击刷新按钮 🔄 "Reload All Maven Projects"
3. 等待依赖下载完成

**或者使用命令行（如果 Maven 可用）**：
```bash
# 强制更新依赖
mvn clean install -U

# -U 参数强制更新所有快照依赖
```

#### Eclipse
1. 右键点击项目
2. Maven → Update Project
3. 勾选 "Force Update of Snapshots/Releases"
4. 点击 OK

---

### 步骤 2: 清理项目

#### IntelliJ IDEA
```
Build → Clean Project
Build → Rebuild Project
```

#### 命令行
```bash
# 删除编译输出
rmdir /s /q target

# 或使用 Maven
mvn clean
```

---

### 步骤 3: 验证 H2 依赖

检查 `pom.xml` 中 H2 配置：

```xml
<!-- H2 Database (内存数据库用于测试) -->
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <version>2.2.224</version>
    <!-- 临时移除 test scope 以确保测试运行时可用 -->
</dependency>
```

**重要**: 已临时移除 `<scope>test</scope>`，确保 H2 在测试运行时可用。

---

### 步骤 4: 验证 H2 JAR 是否存在

检查 Maven 本地仓库是否包含 H2：

```bash
# Windows 默认位置
dir "%USERPROFILE%\.m2\repository\com\h2database\h2\2.2.224"

# 应该看到 h2-2.2.224.jar 文件
```

**如果文件不存在**：
```bash
# 手动下载 H2 依赖
mvn dependency:get -Dartifact=com.h2database:h2:2.2.224
```

---

### 步骤 5: 重新运行测试

#### IntelliJ IDEA
1. 右键点击 `AuthControllerIntegrationTest`
2. 选择 "Run 'AuthControllerIntegrationTest'"
3. 或运行所有测试：右键 `src/test/java` → Run 'All Tests'

#### 命令行
```bash
# 运行所有测试
mvn test

# 运行单个测试类
mvn test -Dtest=AuthControllerIntegrationTest

# 跳过其他测试
mvn test -Dtest=AuthControllerIntegrationTest#login_Success
```

---

## 预期结果

✅ **成功的测试输出**：
```
[INFO] Tests run: 15, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] Results:
[INFO]
[INFO] Tests run: 58, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] BUILD SUCCESS
```

---

## 备选方案：手动添加 H2 JAR

如果 Maven 无法下载依赖，可以手动添加：

### 1. 下载 H2 JAR
从官方网站下载：https://repo1.maven.org/maven2/com/h2database/h2/2.2.224/h2-2.2.224.jar

### 2. 添加到项目

#### IntelliJ IDEA
1. File → Project Structure → Modules
2. Dependencies → + → JARs or directories
3. 选择下载的 `h2-2.2.224.jar`
4. Scope 设置为 "Test"

#### Eclipse
1. 右键项目 → Build Path → Add External Archives
2. 选择 `h2-2.2.224.jar`

---

## 常见问题

### Q1: Maven 刷新后仍然报错？
**解决**：
```bash
# 清除 Maven 缓存
del /s /q "%USERPROFILE%\.m2\repository\com\h2database"

# 重新下载
mvn clean install -U
```

### Q2: IDE 无法识别 H2 类？
**解决**：
1. File → Invalidate Caches / Restart
2. 选择 "Invalidate and Restart"

### Q3: 测试在命令行可以运行，但 IDE 中失败？
**解决**：
1. IntelliJ IDEA: File → Settings → Build, Execution, Deployment → Build Tools → Maven
2. 确保 "Use Maven output directory" 已勾选
3. 重新构建项目

### Q4: 使用代理或企业网络？
**解决**：
在 `%USERPROFILE%\.m2\settings.xml` 中配置代理：
```xml
<settings>
  <proxies>
    <proxy>
      <active>true</active>
      <protocol>http</protocol>
      <host>proxy.company.com</host>
      <port>8080</port>
    </proxy>
  </proxies>
</settings>
```

---

## 验证修复

运行以下命令验证所有测试：

```bash
# 1. 验证 H2 依赖
mvn dependency:tree | findstr h2

# 预期输出：
# [INFO] +- com.h2database:h2:jar:2.2.224:compile

# 2. 运行所有测试
mvn clean test

# 3. 查看测试报告
start target\surefire-reports\index.html
```

---

## 联系方式

如果问题仍然存在，请提供以下信息：
1. Java 版本：`java -version`
2. Maven 版本：`mvn -version`
3. IDE 版本
4. 完整的错误堆栈跟踪

**修复完成日期**: 2026-01-18
**适用版本**: Spring Boot 3.2.11 + H2 2.2.224
