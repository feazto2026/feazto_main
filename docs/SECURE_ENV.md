# Secure Env Usage Guide — Feazto

> Status: `main` clean at `9d6d03b` (push `dd7ebce..9d6d03b` fixed GH013 secret push-protection).
> This file contains **placeholders only — no real secrets**.

## 1. Golden rule

- Real secrets live **only** in local, gitignored files (`.env`, `backend-start-8082.cmd`).
- Repo contains **only templates**: `.env.example`, `backend-start-8082.example.cmd`, `application-security.example.yml`.
- Never `git add -f` an ignored secret file. Never paste keys into docs/logs/issues.

## 2. Files

| File | Tracked? | Purpose |
|---|---|---|
| `.env` (36 keys, local-only) | ❌ gitignored | Local dev secrets + ports. Single source for `docker compose` + scripts |
| `.env.example` | ✅ template | Copy starter: `cp .env.example .env`, then fill real values |
| `backend-start-8082.cmd` | ❌ gitignored | Local Windows API starter with real `set "KEY=..."` values |
| `backend-start-8082.example.cmd` | ✅ template | Placeholder pattern (`set "KEY=%KEY%"`); copy to `.cmd` and fill from `.env` |
| `docker-compose.yml` | ✅ | Reads `.env` via `${VAR:-default}` indirection; no hardcoded secrets |
| `services/api/src/main/resources/application.yml` + `application-security.example.yml` | ✅ | Spring maps env vars via `@Value` / `${...}` placeholders |

Setup:

```cmd
copy .env.example .env
copy backend-start-8082.example.cmd backend-start-8082.cmd
REM edit .env + backend-start-8082.cmd with real values (never commit)
```

```bash
cp .env.example .env
# edit .env with real values (never commit)
docker compose up redis          # needs no secrets
docker compose up redis api      # needs .env
```

## 3. docker-compose `${VAR:-}` indirection

`docker-compose.yml` never hardcodes secrets. It forwards env with safe defaults:

```yaml
env_file:
  - path: ./.env
    required: false
environment:
  DATABASE_URL: "${DATABASE_URL:-}"
  SUPABASE_URL: "${SUPABASE_URL:-}"
  SUPABASE_ANON_KEY: "${SUPABASE_ANON_KEY:-}"
  SUPABASE_SERVICE_ROLE_KEY: "${SUPABASE_SERVICE_ROLE_KEY:-}"
  REDIS_URL: "${REDIS_URL:-redis://redis:6379}"
  PAYMENT_SECRET: "${PAYMENT_SECRET:-}"
  PAYMENT_WEBHOOK_SECRET: "${PAYMENT_WEBHOOK_SECRET:-}"
  OTP_SECRET: "${OTP_SECRET:-}"
  SERVER_PORT: "${SERVER_PORT:-8080}"
  SPRING_PROFILES_ACTIVE: "${SPRING_PROFILES_ACTIVE:-local}"
ports:
  - "${SERVER_PORT:-8080}:8080"
  - "${REDIS_PORT:-6379}:6379"
  - "${ADMIN_WEB_PORT:-5173}:80"
```

`required: false` lets `docker compose up redis` work with no `.env`.

## 4. Spring `@Value` mapping (no secrets in code)

```java
@Value("${OTP_SECRET:${app.otp.secret:}}") String otpSecret;
@Value("${payments.webhook.secret:${PAYMENTS_WEBHOOK_SECRET:}}") String secret;
@Value("${supabase.url:${SUPABASE_URL:}}") String supabaseUrl;
@Value("${supabase.anon-key:${SUPABASE_ANON_KEY:}}") String anonKey;
@Value("${security.jwt.jwks-url:${SUPABASE_URL:}/auth/v1/.well-known/jwks.json}") String jwksUrl;
@Value("${security.jwt.issuer:}") String issuer;
@Value("${security.cors.allowed-origins:}") String origins;
```

YAML side (`application.yml` / `application-security.example.yml`):

```yaml
spring.datasource.url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/food_platform}
supabase.url: ${SUPABASE_URL:}
supabase.anon-key: ${SUPABASE_ANON_KEY:}
security.jwt.issuer: ${SUPABASE_JWT_ISSUER:}
security.jwt.audience: ${SUPABASE_JWT_AUDIENCE:}
security.jwt.jwks-url: ${SUPABASE_JWKS_URL:${SUPABASE_URL:}/auth/v1/.well-known/jwks.json}
app.otp.secret: ${OTP_SECRET:}
payments.webhook.secret: ${PAYMENTS_WEBHOOK_SECRET:}
```

Missing secret = empty default → fail-closed at runtime, not in git.

## 5. Client-safe vs server-only (critical)

**Client-safe (public, baked into bundles):**

```bash
VITE_API_BASE_URL=http://localhost:8080/api/v1
EXPO_PUBLIC_API_BASE_URL=http://localhost:8080/api/v1
EXPO_PUBLIC_SUPABASE_URL=https://<project-ref>.supabase.co
EXPO_PUBLIC_SUPABASE_ANON_KEY=<anon-publishable-key-only>
API_BASE_URL=http://localhost:8080/api/v1
```

**Server-only (NEVER `VITE_*` / `EXPO_*` / client env):**

```bash
SUPABASE_SERVICE_ROLE_KEY=<server-only>
DATABASE_URL=postgresql://postgres:<db-password>@db.<project-ref>.supabase.co:5432/postgres
DB_PASSWORD=<server-only>
OTP_SECRET=<min-32-random-chars-server-only>
JWT_SECRET=<server-only>
PAYMENT_SECRET=<server-only>
PAYMENT_WEBHOOK_SECRET=<server-only>
PAYMENTS_WEBHOOK_SECRET=<server-only>
SUPABASE_JWT_ISSUER / SUPABASE_JWKS_URL # server verification config
```

Rules:

- `SUPABASE_ANON_KEY` may reach clients only via `EXPO_PUBLIC_SUPABASE_ANON_KEY` (RLS still enforced).
- `SERVICE_ROLE_KEY` bypasses RLS — API + seed scripts only.
- Vite `VITE_*` and Expo `EXPO_PUBLIC_*` are embedded in JS bundles — anything there is public.

## 6. `.gitignore` (already enforced)

```gitignore
.env
.env.local
.env.*.local
.env.production
.env.development
!.env.example
!.env.*.example
backend-start-*.cmd
!backend-start-*.example.cmd
*.pid
backend-*.log
backend-*.err.log
*.prev.log
.*token*.txt
.final_token.txt
.probe_token.txt
.ps_token.txt
probe_*
probe*.json
scripts/local/run-api-*.cmd
scripts/local/run-api-*.sh
```

Verify:

```cmd
git check-ignore -v .env backend-start-8082.cmd
git check-ignore -v .env.example backend-start-8082.example.cmd || echo "templates NOT ignored (correct)"
git status --porcelain --ignored | findstr ".env"
```

## 7. Pre-push secret scan (run every time)

```cmd
REM 1. What would push?
git status --short
git diff --cached --name-only

REM 2. Blocked patterns in staged/tracked files (must print nothing):
git grep -nE "sb_secret_|sb_publishable_|Feazto@2026|eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}" -- . ":!docs/SECURE_ENV.md" || echo "no staged secret patterns"

REM 3. Full-history check before push (must be empty):
git log --all -p -S "sb_secret_" --pickaxe-regex -- . | findstr "sb_secret_" && echo "HISTORY CONTAINS SECRET" || echo "history clean"

REM 4. Gitleaks (if installed):
gitleaks detect --source . --no-git -v
```

If GitHub blocks with `GH013`: unstage the file, replace real value with `<placeholder>`, amend, re-scan, push again. Never bypass with `-f`.

## 8. Rotation required (exposed credentials)

The following classes were present in a blocked push / local starter and **must be rolled** — they are compromised even though `main` is now clean:

- `sb_secret_*` (Supabase `SERVICE_ROLE_KEY`) — roll in Supabase Dashboard → Project → API keys → roll service-role / secret keys. Update local `.env` + `backend-start-8082.cmd` only.
- `DB_PASSWORD` matching `Feazto@2026` pattern — reset via Supabase Dashboard → Database → connection string / password. Update `DATABASE_URL` locally.
- Supabase JWTs (`eyJ...` / `sb_publishable_*`) seen in push attempt — roll via Supabase Dashboard → Auth / API keys.
- `OTP_SECRET` / `JWT_SECRET` / `PAYMENT_*` dev-dummy values — replace with fresh `openssl rand -hex 32` (OTP/JWT) and provider dashboard (payment/webhook) before any staging/prod use.

After rotation: re-run §7 scans, confirm `git log --all -p` has no `sb_secret_`, then push.

## 9. References

- Template: `.env.example` (full catalogue: `docs/operations/env.md` if present)
- Example starter: `backend-start-8082.example.cmd`
- Compose: `docker-compose.yml` (`api`, `admin-web`, `redis` services)
- Backend config: `services/api/src/main/resources/application.yml`, `application-security.example.yml`
