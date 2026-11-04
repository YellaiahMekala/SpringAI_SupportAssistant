# Web UI

Angular 20 SPA for Enterprise Spring AI Support Assistant. Keycloak sign-in; API calls stay same-origin.

## Screens

| Screen | Role |
|--------|------|
| Ask support | Streaming answers, citations, clear error states |
| Orders | Customer-owned orders; agents can look up any reference |
| Usage | Daily token usage |
| Documents | Agent-only upload and document list |

## Run

Requires the backend and Keycloak (see root README).

```bash
npm ci
npm start    # http://localhost:4200 → proxies /api to :8080
```

Dev users: `alice`, `bob`, `agent` (password in root README). Redirect URI must be `http://localhost:4200`.

## Test / build

```bash
npm test -- --watch=false --browsers=ChromeHeadlessNoSandbox
npm run build
```

## Notes

- Access tokens stay in memory (PKCE verifier/nonce use session storage for the redirect).
- Streaming uses `fetch` + SSE parsing (`EventSource` cannot send Authorization or a body).
- Compose serves the nginx image on http://localhost:8081.
