FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -DskipTests dependency:go-offline
COPY src src
ARG VERSION=latest
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -DskipTests -Drevision=${VERSION} package

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache ca-certificates tzdata \
    && addgroup -S niuniuwiki && adduser -S -G niuniuwiki niuniuwiki
WORKDIR /app
COPY --from=builder /src/target/niuniu-wiki-backend-*.jar /app/niuniu-wiki.jar
USER niuniuwiki
EXPOSE 8000
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/niuniu-wiki.jar"]
