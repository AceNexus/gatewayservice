# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build executable JAR (output: build/libs/gatewayservice.jar)
./gradlew bootJar

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.acenexus.tata.gatewayservice.BusRefreshSecurityTests"

# Full build (compile + test + jar)
./gradlew build

# Run locally
./gradlew bootRun
```

Tests run without Config Server, RabbitMQ, or Eureka — the test `application.yml` disables all three.

## Architecture

This is a **Spring Cloud Gateway** service — the unified entry point for the AceNexus microservice ecosystem. It runs on **Spring WebFlux** (reactive/non-blocking) on port 8080.

### Request Pipeline

Every request passes through two global filters in order, then is routed to a downstream service:

1. **`GatewayLoggerFilter`** (`filter/GatewayLoggerFilter.java`, `HIGHEST_PRECEDENCE`) — Logs request start (requestId, method, path, IP) and response end (status, route, target, duration). Masks sensitive headers (`authorization`, `cookie`, `jwt`, `api-key`). Logs WARN for 4xx or slow requests (>3s), ERROR for 5xx. Injects `X-Trace-Id` response header via Micrometer `Tracer.currentSpan()` (uses `doOnRequest` to ensure the OTel span is already active).

2. **`JwtAuthFilter`** (`filter/JwtAuthFilter.java`, `HIGHEST_PRECEDENCE + 1`) — Validates JWT Bearer token. On success, injects `X-User-ID` and `X-User-Name` headers into the forwarded request. Returns 401 JSON (`ApiResponse`) on failure.

Spring Security (`SecurityConfig.java`) runs at the WebFilter level, **before** the gateway filter chain. It protects only `/actuator/busrefresh` with HTTP Basic Auth (`SECURITY_USERNAME` / `SECURITY_PASSWORD`). All other paths use `permitAll()` — JWT validation is handled by `JwtAuthFilter`.

### JWT Excluded Paths

These paths bypass JWT validation in `JwtAuthFilter`:
- `/actuator/health`
- `/actuator/busrefresh` (protected by Spring Security HTTP Basic instead)
- `/api/linebot/webhook`
- `/api/linebot/actuator/health`
- `/api/linebot-test/webhook`
- `/api/linebot-test/actuator/health`

Exclusion uses `startsWith` matching. On excluded paths, `X-User-ID` / `X-User-Name` headers are still stripped to prevent client spoofing.

### Package Structure

```
com.acenexus.tata.gatewayservice
├── config/SecurityConfig.java         # HTTP Basic auth for /actuator/busrefresh only
├── filter/GatewayLoggerFilter.java    # Global request/response logging
├── filter/JwtAuthFilter.java          # Global JWT authentication
├── provider/JwtTokenProvider.java     # JWT validation only (HS256, extractAllClaims)
└── define/ApiResponse.java            # Unified response wrapper {status, message, data}
```

Gateway **does not issue tokens**. Login and token generation belong in downstream Account Service.

## Configuration Profiles

| Profile | Config Source | Eureka | Bus |
|---------|--------------|--------|-----|
| `local` | `application-local.yml` (hardcoded routes) | Disabled | Disabled |
| `prod`  | `application-prod.yml` → Config Server (`spring.config.import`) | Enabled | Enabled |

Set active profile via: `SPRING_PROFILES_ACTIVE=prod`

Prod routes and Eureka settings are served from **configservice** at `configs/gatewayservice-prod.yml`.

**Local route targets** (in `application-local.yml`):
- `/api/account/**` → `http://localhost:8081`

## Key Conventions

- **API responses** always use `ApiResponse<T>` wrapper: `status` (0=success, 1=error), `message`, `data`.
- **Filters** implement `GlobalFilter, Ordered` and return `Mono<Void>` for reactive chains.
- **Null safety in filters**: `GatewayLoggerFilter` attributes (`ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR`, `GATEWAY_REQUEST_URL_ATTR`, `getStatusCode()`) can be null when `JwtAuthFilter` short-circuits before routing — always guard with null checks.
- **Logging** uses SLF4J structured log format with `|`-separated fields.
- **Versioning**: `build.gradle.kts` derives JAR version from `git describe --tags --abbrev=0`. Falls back to `0.0.1-SNAPSHOT`.
- **JAR filename** is fixed to `gatewayservice.jar` (required by Dockerfile `COPY` instruction).
- **Commit format**: `[type] 中文描述` — types: `feat`, `fix`, `refactor`, `docs`, `test`, `config`.

## Deployment

Deployed to **Kubernetes** (namespace: `acenexus`) via CI/CD:

- **CI**: GitHub Actions (`.github/workflows/ci.yml`) triggers on push / PR to `main`
  - test job: `./gradlew build` (compile + test)
  - release job: `./gradlew bootJar` → `docker build` → Trivy scan → push `ghcr.io/acenexus/gatewayservice:<sha>` to GHCR → update `AceNexus/deploy` k8s/gatewayservice/deployment.yaml image tag
- **CD**: ArgoCD detects deploy repo change → `kubectl apply` → rolling update

K8s manifests and operation guide: `AceNexus/deploy` repo → `k8s/gatewayservice/` and `README.md`.
