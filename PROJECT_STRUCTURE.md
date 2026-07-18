# NiuniuWiki 项目结构文档

NiuniuWiki 是一个由 AI 大模型驱动的开源知识库系统，采用前后端分离架构。

## 根目录

```text
NiuniuWiki/
├── .github/              # GitHub Actions 与项目配置
├── backend/              # Java 21 / Spring Boot 后端
├── images/               # 文档图片
├── sdk/                  # SDK
├── web/                  # pnpm 前端工作区
├── AGENTS.md             # 开发约定
├── LICENSE               # AGPL-3.0
└── README.md             # 项目说明
```

## 后端 `backend/`

```text
backend/
├── .mvn/                 # Maven Wrapper 配置
├── openapi/              # 原 API 合同快照
├── src/main/java/com/chaitin/niuniuwiki/
│   ├── app/              # 应用/机器人配置
│   ├── auth/             # 公开站点认证配置
│   ├── captcha/          # CAP 验证协议
│   ├── chat/             # RAG 问答与模型调用
│   ├── comment/          # 评论
│   ├── common/           # 响应、异常、JDBC 映射
│   ├── config/           # Spring 配置
│   ├── conversation/     # 对话与反馈
│   ├── crawler/          # 文档抓取服务适配
│   ├── creation/         # AI 内容创作
│   ├── file/             # MinIO/S3 文件服务
│   ├── knowledgebase/    # 知识库与发布快照
│   ├── maintenance/      # consumer 定时任务
│   ├── migration/        # 原 Go 数据迁移兼容层
│   ├── model/            # 大模型配置
│   ├── nav/              # 导航页签
│   ├── node/             # 文档树与权限
│   ├── rag/              # RAGLite 与 NATS JetStream
│   ├── security/         # JWT、API Token、RBAC
│   ├── share/            # 公开 Wiki API、OAuth、SSE
│   ├── stat/             # 访问统计
│   └── user/             # 后台用户
├── src/main/resources/
│   ├── application.yml   # 环境变量兼容配置
│   └── db/migration/     # Flyway V1-V38
├── src/test/             # 单元测试与嵌入式 PostgreSQL 迁移测试
├── Dockerfile.api        # API 镜像
├── Dockerfile.consumer   # consumer 镜像
├── Makefile
├── mvnw
└── pom.xml
```

API 和 consumer 使用同一 JAR；consumer 通过 `consumer` Spring profile 启动 NATS 消费与维护任务。

## 前端 `web/`

```text
web/
├── admin/                # React + Vite 管理后台
├── app/                  # Next.js 公开 Wiki
├── packages/             # 共享组件和工具
├── package.json
├── pnpm-lock.yaml
└── pnpm-workspace.yaml
```

## SDK `sdk/`

`sdk/rag/` 提供 RAG 相关 SDK 与协议资产。
