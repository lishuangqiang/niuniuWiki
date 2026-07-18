TAG ?= $(shell git describe --tags 2>/dev/null || echo latest)
VERSION ?= $(TAG)
PLATFORM ?= linux/amd64
OUTPUT ?= type=docker

.PHONY: test package lint image push-prod-images

test:
	./mvnw clean test

package:
	./mvnw clean package

lint:
	./mvnw clean verify

image:
	docker buildx build --platform $(PLATFORM) --tag $(IMAGE_NAME) \
		--build-arg VERSION=$(VERSION) --output $(OUTPUT) --progress plain \
		--file $(DOCKERFILE) .

push-prod-images:
	$(MAKE) image PLATFORM=linux/amd64,linux/arm64 DOCKERFILE=Dockerfile.api \
		IMAGE_NAME=chaitin-registry.cn-hangzhou.cr.aliyuncs.com/chaitin/niuniu-wiki-api:$(TAG) OUTPUT=type=registry
	$(MAKE) image PLATFORM=linux/amd64,linux/arm64 DOCKERFILE=Dockerfile.consumer \
		IMAGE_NAME=chaitin-registry.cn-hangzhou.cr.aliyuncs.com/chaitin/niuniu-wiki-consumer:$(TAG) OUTPUT=type=registry
