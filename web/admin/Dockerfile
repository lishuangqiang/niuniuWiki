FROM node:22-alpine AS builder

ENV PNPM_HOME=/pnpm
ENV PATH=$PNPM_HOME:$PATH
ENV NODE_OPTIONS=--max-old-space-size=4096

RUN corepack enable && corepack prepare pnpm@10.12.1 --activate

WORKDIR /src
COPY . .
RUN pnpm install --frozen-lockfile
RUN pnpm --filter niuniu-wiki-admin build

FROM nginx:alpine
COPY --from=builder /src/admin/dist /opt/frontend/dist
COPY admin/server.local.conf /etc/nginx/conf.d/default.conf
EXPOSE 8080
CMD ["nginx", "-g", "daemon off;"]
