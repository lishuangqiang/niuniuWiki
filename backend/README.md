# NiuniuWiki Java 后端

这是 NiuniuWiki 后端的 Java 21 / Spring Boot 3.5 实现，保持现有前端使用的 HTTP、SSE、数据库和基础设施协议不变。

## 技术栈

- Java 21、Spring Boot 3.5、Spring MVC、JDBC
- PostgreSQL 14+、Flyway、Redis
- NATS JetStream、RAGLite、MinIO/S3
- Maven Wrapper 3.9.11

## 模块

- `user`、`security`：登录、JWT、用户和知识库权限
- `knowledgebase`、`nav`、`node`：知识库、导航、文档、发布快照和权限组
- `chat`、`conversation`、`creation`：RAG 检索、模型调用、对话和内容创作
- `share`、`comment`、`stat`：公开站点、SSE、评论、反馈和统计
- `rag`、`maintenance`：NATS 向量任务 consumer、RAGLite 适配和定时维护
- `migration`：兼容原 Go 版本的 5 个数据迁移

## 本地运行

需要 JDK 21，以及可访问的 PostgreSQL、Redis、MinIO、NATS、RAGLite。

```bash
./mvnw clean test
./mvnw spring-boot:run
```

默认配置与现有 `deploy` 环境变量兼容。常用变量：

| 变量 | 默认值 | 用途 |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://niuniu-wiki-postgres:5432/niuniu-wiki` | PostgreSQL |
| `PG_DSN` | 空 | 兼容 Go 版的 PostgreSQL DSN；未设置 JDBC URL 时自动转换 |
| `POSTGRES_PASSWORD` | `niuniu-wiki-secret` | 数据库密码 |
| `JWT_SECRET` | `change-me-before-production` | 管理端 JWT，生产环境必须修改 |
| `RAG_CT_RAG_BASE_URL` | `http://169.254.15.18:5050` | RAGLite |
| `MQ_NATS_SERVER` | `nats://169.254.15.13:4222` | NATS |
| `REDIS_ADDR` | `niuniu-wiki-redis:6379` | 兼容 Go 版 Redis 地址 |
| `S3_ENDPOINT` | `http://niuniu-wiki-minio:9000` | MinIO/S3 |

API 进程使用默认 profile。consumer 使用同一 JAR：

```bash
SPRING_PROFILES_ACTIVE=consumer \
SPRING_MAIN_WEB_APPLICATION_TYPE=none \
java -jar target/niuniu-wiki-backend-1.0.0-SNAPSHOT.jar
```

## 数据升级

Flyway 直接复用原项目的 38 个 PostgreSQL SQL 迁移。对于已有 Go 版本数据库，`baseline-on-migrate` 会在 V38 建立 Flyway 基线；`migrations` 表中的 5 个 Go 数据迁移由 `LegacyDataMigrationRunner` 幂等接管。升级前仍应备份 PostgreSQL、MinIO 和 RAG 数据。

## 镜像

```bash
docker build -f Dockerfile.api -t niuniu-wiki-api-java .
docker build -f Dockerfile.consumer -t niuniu-wiki-consumer-java .
```

健康检查为 `/actuator/health`。旧 Swagger 合同快照保存在 `openapi/`，用于核对前端兼容性。
