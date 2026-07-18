PLATFORM=linux/amd64
TAG=main
REGISTRY=niuniu-wiki-app


# 构建前端代码
build:
	pnpm run build

# 构建并加载到本地Docker
image: build
	docker buildx build \
	  -f Dockerfile \
	  --platform ${PLATFORM} \
	  --tag ${REGISTRY}/frontend:${TAG} \
	  --load \
	  .

save: image
	docker save -o /tmp/niuniu-wiki-app_frontend.tar niuniu-wiki-app/frontend:main
		