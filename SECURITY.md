# Security

Report vulnerabilities privately (do not open a public issue with exploit details). Prefer GitHub private vulnerability reporting when enabled.

## Scope

This is a portfolio / demonstration application. It is not production-hardened as published.

## Development defaults

- Keycloak realm `support` includes a password-grant client (`support-dev`) and demo users — local use only.
- Set credentials via `.env` (git-ignored). Do not commit secrets or real customer data.
- `OPENAI_API_KEY` and database passwords must come from the environment or a secrets manager.

## Known limits

- Sensitive-data detection is pattern-based (not a classifier).
- JWT audience (`aud`) is not validated.
- Prometheus scrape is off unless explicitly enabled.
