#!/usr/bin/env sh
set -eu

# 只有显式执行本脚本才允许创建空卷，避免 Compose 项目改名后静默启动到空库。
volumes="
${POSTGRES_VOLUME_NAME:-llmwiki_pgdata17}
${REDIS_VOLUME_NAME:-llmwiki_redisdata}
${MINIO_VOLUME_NAME:-llmwiki_miniodata}
${NATS_VOLUME_NAME:-llmwiki_natsdata}
${QDRANT_VOLUME_NAME:-llmwiki_qdrantdata}
${RAGLITE_VOLUME_NAME:-llmwiki_raglitedata}
"

for volume in $volumes; do
  if docker volume inspect "$volume" >/dev/null 2>&1; then
    echo "复用数据卷: $volume"
  else
    docker volume create "$volume" >/dev/null
    echo "已显式创建空数据卷: $volume"
  fi
done
