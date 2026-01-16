# WMS 仓库管理系统 (Warehouse Management System)

[![Java Version](https://img.shields.io/badge/Java-17-orange)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.11-green)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue)](https://www.postgresql.org/)

## 📋 项目简介

这是一个基于 **Java 17** + **Spring Boot 3.2.x** 开发的现代化 WMS（仓库管理系统），未来可扩展为完整的 ERP 系统。

### 核心功能（规划中）
- 📦 仓库管理：多仓库、多货架、库位管理
- 📥 入库管理：采购入库、退货入库、调拨入库
- 📤 出库管理：销售出库、生产领料、盘亏出库
- 📊 库存管理：实时库存查询、库存预警、库存盘点
- 🔍 批次追溯：全流程批次号跟踪
- 📈 报表分析：入出库报表、库存周转率分析

---

## 🛠️ 技术栈

| 类别            | 技术选型                      | 版本号  | 说明                          |
|---------------|---------------------------|---------|-----------------------------|
| **开发语言**     | Java (LTS)                | 17      | 长期支持版本，性能与新特性兼顾          |
| **核心框架**     | Spring Boot               | 3.2.11  | 企业级应用开发框架                  |
| **数据库**      | PostgreSQL                | 16      | 开源关系型数据库，稳定性高              |
| **ORM 框架**   | Spring Data JPA (Hibernate) | 6.4.x   | 自动管理对象关系，减少手写 SQL          |
| **构建工具**     | Maven                     | 3.9+    | 依赖管理与项目构建                  |
| **代码简化**     | Lombok                    | 1.18.30 | 自动生成 Getter/Setter/Builder |
| **参数校验**     | Spring Validation          | 自动集成   | 统一的参数校验框架                  |
| **连接池**      | HikariCP                  | 5.1.x   | 高性能数据库连接池（Spring Boot 默认）  |

---

## 📂 项目结构

```
wms-system/
├── src/main/java/com/wms/system/
│   ├── controller/         # REST API 控制器层
│   ├── service/            # 业务逻辑层
│   ├── repository/         # 数据访问层 (JPA Repository)
│   ├── entity/             # 实体类 (数据库表映射)
│   ├── dto/                # 数据传输对象 (请求/响应 DTO)
│   ├── exception/          # 自定义异常处理
│   ├── config/             # 配置类
│   └── WmsSystemApplication.java  # 主启动类
├── src/main/resources/
│   ├── application.yml     # 核心配置文件
│   └── logback-spring.xml  # 日志配置（可选）
├── src/test/java/          # 单元测试与集成测试
├── pom.xml                 # Maven 依赖配置
└── README.md               # 项目说明文档
```

---

## 🚀 快速开始

### 1. 环境要求

确保你的开发环境已安装以下工具：

| 工具           | 最低版本  | 检查命令                       |
|--------------|-------|----------------------------|
| JDK          | 17    | `java -version`            |
| Maven        | 3.6+  | `mvn -version`             |
| PostgreSQL   | 16    | `psql --version`           |
| IDE（推荐）     | IDEA  | 确保已安装 Lombok 插件           |

### 2. 数据库初始化

1. **启动 PostgreSQL 服务**
   ```bash
   # Windows（以管理员身份运行 CMD）
   net start postgresql-x64-16
   ```

2. **创建数据库**
   ```sql
   -- 登录 PostgreSQL（默认用户名：postgres）
   psql -U postgres

   -- 创建数据库
   CREATE DATABASE wms_db
       WITH ENCODING='UTF8'
       LC_COLLATE='zh_CN.UTF-8'
       LC_CTYPE='zh_CN.UTF-8'
       TEMPLATE=template0;

   -- 验证数据库
   \l
   ```

### 3. 配置数据库连接

编辑 `src/main/resources/application.yml`，修改以下三项：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/wms_db
    username: postgres           # 你的数据库用户名
    password: your_password_here # ⚠️ 替换为你的密码
```

### 4. 启动项目

```bash
# 方式 1：Maven 命令启动
mvn spring-boot:run

# 方式 2：IDEA 运行
# 右键点击 WmsSystemApplication.java → Run 'WmsSystemApplication'
```

### 5. 验证启动

项目启动成功后，访问以下地址：

```
🔗 健康检查：http://localhost:8080/api/health/check
```

**预期响应：**
```json
{
  "status": "UP",
  "message": "WMS System is running successfully!",
  "timestamp": "2025-01-09T10:30:00",
  "java_version": "17.0.xx",
  "spring_boot_version": "3.2.11"
}
```

---

## 📖 开发指南

### Maven 常用命令

```bash
# 清理编译结果
mvn clean

# 编译项目
mvn compile

# 运行测试
mvn test

# 打包（生成 JAR 文件）
mvn package

# 跳过测试打包
mvn package -DskipTests

# 安装到本地仓库
mvn install
```

### 数据库表自动创建

项目使用 **JPA 自动建表**（`hibernate.ddl-auto=update`），首次启动时会根据实体类自动创建表结构。

**DDL 模式说明：**
| 模式            | 说明                                | 适用场景     |
|---------------|-----------------------------------|----------|
| `none`        | 不执行任何操作                           | 生产环境     |
| `validate`    | 仅验证表结构，不修改                        | 生产环境部署前  |
| `update`      | 自动更新表结构（新增字段，不删除旧字段）              | **开发阶段**（推荐） |
| `create`      | 每次启动都删除并重建表（⚠️ **会丢失数据**）         | 测试环境     |
| `create-drop` | 启动时创建表，关闭时删除表（⚠️ **会丢失数据**）       | 单元测试     |

### Lombok 使用示例

```java
import lombok.*;

@Data                    // 自动生成 Getter/Setter/toString/equals/hashCode
@NoArgsConstructor       // 无参构造函数
@AllArgsConstructor      // 全参构造函数
@Builder                 // 建造者模式
@Entity
public class Warehouse {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String address;
}
```

---

## 📝 开发规范

### 代码分层原则

| 层级             | 职责                           | 示例类名                     |
|----------------|------------------------------|--------------------------|
| **Controller** | 处理 HTTP 请求，参数校验              | `WarehouseController`    |
| **Service**    | 业务逻辑处理                       | `WarehouseService`       |
| **Repository** | 数据库操作（JPA 接口）                | `WarehouseRepository`    |
| **Entity**     | 数据库表映射（JPA 实体）               | `Warehouse`              |
| **DTO**        | 数据传输对象（请求/响应参数）              | `WarehouseCreateRequest` |
| **Exception**  | 自定义异常类                       | `WarehouseNotFoundException` |

### RESTful API 规范

| 操作      | HTTP 方法  | URL 示例                    | 说明       |
|---------|----------|---------------------------|----------|
| 查询列表    | `GET`    | `/api/warehouses`         | 分页查询     |
| 查询详情    | `GET`    | `/api/warehouses/{id}`    | 根据 ID 查询 |
| 创建资源    | `POST`   | `/api/warehouses`         | 新增仓库     |
| 更新资源    | `PUT`    | `/api/warehouses/{id}`    | 全量更新     |
| 部分更新    | `PATCH`  | `/api/warehouses/{id}`    | 部分字段更新   |
| 删除资源    | `DELETE` | `/api/warehouses/{id}`    | 逻辑删除/物理删除 |

---

## 🧪 测试

```bash
# 运行所有测试
mvn test

# 运行指定测试类
mvn test -Dtest=WarehouseServiceTest

# 生成测试覆盖率报告
mvn jacoco:report
```

---

## 📦 部署

### 打包为可执行 JAR

```bash
mvn clean package -DskipTests
```

生成的 JAR 文件位于：`target/wms-system-0.0.1-SNAPSHOT.jar`

### 运行 JAR 文件

```bash
java -jar target/wms-system-0.0.1-SNAPSHOT.jar
```

### 生产环境配置

创建 `application-prod.yml` 文件，覆盖开发环境配置：

```yaml
spring:
  jpa:
    show-sql: false              # 关闭 SQL 日志
    hibernate:
      ddl-auto: validate         # 仅验证表结构
logging:
  level:
    root: WARN
    com.wms: INFO
```

启动时指定配置文件：
```bash
java -jar wms-system.jar --spring.profiles.active=prod
```

---

## 🤝 贡献指南

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交代码 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到远程分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

---

## 📄 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件

---

## 📧 联系方式

如有问题或建议，请提交 Issue 或 Pull Request。

**开发团队：** WMS Team
**创建日期：** 2025-01-09
**最后更新：** 2025-01-09
