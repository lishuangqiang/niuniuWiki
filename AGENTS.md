# AGENTS.md

This file is for coding agents working in `NiuniuWiki`. Communicate with the user in Chinese unless they explicitly ask otherwise.

## Repository Layout

- `backend/`: Java 21 / Spring Boot backend API, consumer, migrations and deployment files.
- `web/`: pnpm workspace.
- `web/admin/`: React + Vite admin console.
- `web/app/`: Next.js public wiki.
- `sdk/`: SDK-related code.

## Backend Architecture

- Use feature packages under `backend/src/main/java/com/chaitin/niuniuwiki/`.
- Controllers own HTTP binding only; services own business rules.
- Keep PostgreSQL access in service or adapter classes through `JdbcTemplate` and `JdbcMaps`.
- Keep external integrations behind adapters such as `RagClient`, `VectorTaskPublisher`, and `ObjectStorageService`.
- Preserve the existing JSON envelope, snake_case fields, SSE event shapes, and `/api/v1` and `/share/v1` routes.
- Add schema migrations under `backend/src/main/resources/db/migration/`; never edit a migration already released.

## Tooling

- Backend: Java 21, Maven Wrapper 3.9.11, Spring Boot 3.5.
- Frontend: pnpm only.

Run from the repository root unless noted otherwise.

### Backend

- Compile: `cd backend && ./mvnw compile`
- Test: `cd backend && ./mvnw test`
- Full verification: `cd backend && ./mvnw clean verify`
- Package: `cd backend && ./mvnw clean package`
- Run API: `cd backend && ./mvnw spring-boot:run`
- Run consumer: `cd backend && SPRING_PROFILES_ACTIVE=consumer SPRING_MAIN_WEB_APPLICATION_TYPE=none ./mvnw spring-boot:run`

### Frontend

- Install: `cd web && pnpm install`
- Build all apps: `cd web && pnpm build`
- Build admin: `cd web/admin && pnpm build`
- Lint public app: `cd web/app && pnpm lint`

## Java Style

- Use four spaces, Java records for compact request/response DTOs, and constructor injection.
- Prefer explicit types at API and persistence boundaries; small response payloads may use `Map<String, Object>` to preserve the legacy JSON contract.
- Throw `ApiException` for actionable domain failures and let `GlobalExceptionHandler` create the protocol envelope.
- Use `@Transactional` around multi-table writes.
- Parameterize SQL values. Dynamic identifiers must come only from a fixed internal allowlist.
- Never log secrets, API keys, passwords, JWTs, or full third-party responses containing credentials.
- For asynchronous work, publish only after the database transaction commits.

## Generated Contracts

- `backend/openapi/swagger.json` and `swagger.yaml` are the legacy API contract snapshots.
- `web/admin/src/request/*` is generated frontend request code and should generally not be hand-edited.
- When an HTTP contract changes, update all callers and regenerate frontend clients with `cd web && pnpm api`.

## Verification Checklist

- Backend change: run `cd backend && ./mvnw test` at minimum.
- Migration change: verify against PostgreSQL, not H2; `DatabaseMigrationTest` uses embedded PostgreSQL.
- API contract change: compare all routes and search frontend callers.
- Frontend change: run the relevant lint/build command.
- Before committing backend work: run `cd backend && ./mvnw clean verify`.
