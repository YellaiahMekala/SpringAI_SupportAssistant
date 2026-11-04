# Enterprise Spring AI Support Assistant

Spring Boot 3 / Java 21 support assistant built with Spring AI. Every model call goes through a single governance layer (sensitive-data policy, input size check, per-account daily token budget, usage recording). Keycloak JWT secures the API; customers only see their own orders. Grounded answers use pgvector retrieval with visibility filters, citation validation, and score-based abstention.

[![Java 21](https://img.shields.io/badge/Java-21-437291?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.16-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.1.8-6DB33F?logo=spring&logoColor=white)](https://spring.io/projects/spring-ai)
[![Angular](https://img.shields.io/badge/Angular-20-DD0031?logo=angular&logoColor=white)](https://angular.dev/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![CI](https://github.com/YellaiahMekala/SpringAI_SupportAssistant/actions/workflows/ci.yml/badge.svg)](https://github.com/YellaiahMekala/SpringAI_SupportAssistant/actions/workflows/ci.yml)

## Stack

| Layer | Choice |
|-------|--------|
| Runtime | Java 21, Spring Boot 3.5, Spring AI 1.1 |
| Auth | Keycloak OIDC JWT (`customer`, `support_agent`) |
| Data | PostgreSQL 17 + pgvector, Flyway, JPA |
| Models | Ollama (default) or OpenAI-compatible profile |
| UI | Angular 20, Keycloak login, SSE streaming |
| Ops | Docker Compose, Micrometer/Prometheus, Resilience4j, GitHub Actions |

## Architecture

```
HTTP → JWT / roles → controller validation
     → GovernedCalls
          admit  → sensitive data → size → daily budget reserve
          call   → ChatClient / RAG / advisors → model
          settle → usage row + budget correction (or release on failure)
     → problem+json errors with request id
```

## Main APIs

| Endpoint | Description |
|----------|-------------|
| `POST /api/v1/support/ask` | Grounded support answer (JSON) |
| `POST /api/v1/support/ask/stream` | Same, SSE (`token` then `done`) |
| `GET /api/v1/orders`, `/api/v1/orders/{orderRef}` | Customer-scoped orders (agents see all) |
| `GET /api/v1/usage/today` | Daily token usage |
| `GET /api/v1/me` | Subject, roles, display name |
| `GET /actuator/health` | Public health |

All other endpoints require a bearer token. Model-calling routes are governed.

Legacy demo routes (`/api/chat/*`, `/api/order-support`, `/api/trips/*`, `/multi-model/*`) remain for prompt/template samples and are also governed.

## Governance

| Check | Result |
|-------|--------|
| Credential/secret with a value | `422 SENSITIVE_DATA` |
| Card number (Luhn) | Redacted to last four digits |
| Input over token budget (+ margin) | `422 INPUT_TOO_LONG` |
| Daily budget exhausted | `429 DAILY_BUDGET_EXHAUSTED` |
| Model circuit open | `503 MODEL_UNAVAILABLE` |

Usage is stored without prompt/answer text. Cost status is `NOT_CONFIGURED`, `LOCAL_MODEL`, or `ESTIMATED` when prices are configured.

## Prerequisites

- Java 21, Maven wrapper (`./mvnw`)
- Docker (Postgres/pgvector, Keycloak; Testcontainers for tests)
- Optional: [Ollama](https://ollama.com) with `llama3.2:3b` and `nomic-embed-text`

## Run locally

```bash
cp .env.example .env   # set SUPPORT_DB_PASSWORD and KEYCLOAK_ADMIN_PASSWORD
docker compose up -d db keycloak
export SUPPORT_DB_PASSWORD=<same value>
SPRING_PROFILES_ACTIVE=demo ./mvnw spring-boot:run
```

Demo users (dev only): `alice` / `bob` (`customer`), `agent` (`support_agent`), password `Demo-pass-1`.

```bash
TOKEN=$(curl -s -X POST http://localhost:8180/realms/support/protocol/openid-connect/token \
  -d client_id=support-dev -d grant_type=password -d username=alice -d password=Demo-pass-1 \
  | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')

curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/orders
curl -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"question":"Why is my order late?","orderRef":"ORD-1001"}' \
  http://localhost:8080/api/v1/support/ask
```

OpenAI-compatible endpoint:

```bash
SPRING_PROFILES_ACTIVE=openai,demo OPENAI_API_KEY=<key> ./mvnw spring-boot:run
```

### Web UI

```bash
cd frontend && npm ci && npm start   # http://localhost:4200
```

Or full stack: `docker compose up --build` (UI on http://localhost:8081). See `frontend/README.md`.

## Testing

```bash
./mvnw verify
cd frontend && npm ci && npm test -- --watch=false --browsers=ChromeHeadlessNoSandbox
```

Backend: **185** tests (Testcontainers + mocked models). Frontend: **112** tests (incl. axe). Evaluation gate: `evaluation/` (66 questions, baseline in CI).

## Project layout

```
src/main/java/com/springai/
  advisor/ governance/ budget/ knowledge/ order/ security/ support/ ...
frontend/          Angular UI
knowledge/         Sample support corpus
evaluation/        Regression questions + baseline
.github/workflows/ CI (Maven verify, Docker, frontend)
```

## License

Apache License 2.0. See [LICENSE](LICENSE) and [SECURITY.md](SECURITY.md).
