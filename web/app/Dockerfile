FROM node:22-alpine AS builder

ENV PNPM_HOME=/pnpm
ENV PATH=$PNPM_HOME:$PATH
ENV NODE_OPTIONS=--max-old-space-size=4096

RUN corepack enable && corepack prepare pnpm@10.12.1 --activate

WORKDIR /src
COPY . .
RUN pnpm install --frozen-lockfile

ARG TARGET=http://niuniu-wiki-api:8000
ARG STATIC_FILE_TARGET=http://niuniu-wiki-minio:9000
ARG DEV_KB_ID=00000000-0000-0000-0000-000000000001
ENV TARGET=$TARGET
ENV STATIC_FILE_TARGET=$STATIC_FILE_TARGET
ENV DEV_KB_ID=$DEV_KB_ID

RUN pnpm --filter niuniu-wiki-app build

FROM node:22-alpine

ENV NODE_ENV=production
ENV PORT=3010
ENV HOSTNAME=0.0.0.0

RUN addgroup -g 1001 -S nodejs && adduser -S nextjs -u 1001

WORKDIR /app
COPY --from=builder --chown=nextjs:nodejs /src/app/public ./app/public
COPY --from=builder --chown=nextjs:nodejs /src/app/dist/standalone ./
COPY --from=builder --chown=nextjs:nodejs /src/app/dist/static ./app/dist/static

USER nextjs
EXPOSE 3010
CMD ["node", "app/server.js"]
