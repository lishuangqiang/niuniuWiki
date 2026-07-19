# Docker 数据卷与品牌迁移

Compose 项目名不会再决定持久化卷名。`docker-compose.yml` 默认显式复用当前生产数据卷：

- `llmwiki_pgdata17`
- `llmwiki_redisdata`
- `llmwiki_miniodata`
- `llmwiki_natsdata`
- `llmwiki_qdrantdata`
- `llmwiki_raglitedata`

这些卷被声明为 `external`，因此名称错误时 Compose 会停止，而不会静默创建一套空数据库。

首次全新安装时，先执行：

```bash
./scripts/prepare-docker-volumes.sh
docker compose up -d --build
```

迁移已有安装时，先备份 PostgreSQL、MinIO、Qdrant 和 NATS，再通过环境变量覆盖卷名。未经备份不要删除旧卷，也不要同时运行两套绑定同一数据卷的数据库容器。

默认所有端口只绑定 `127.0.0.1`。生产环境应由经过 TLS、限流与访问控制的反向代理显式暴露应用端和管理端，不要直接开放数据库、消息队列或对象存储端口。
