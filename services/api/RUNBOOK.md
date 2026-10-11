# API Runbook — NOW RUNNING http://localhost:8082

## 1. Prerequisites
- Java 17.0.11 (`java -version`), Maven 3.9.11 (`mvn -version`), Docker 29.8.0, Supabase project `tznqnfqrighkjocpprir`
- Repo: `D:\feazto_main`, API: `services\api`, jar: `food-platform-api-0.1.0-SNAPSHOT.jar` (71,330,532 B ≈ 68 MB)

## 2. Why 8082
- `8080` taken: `0.0.0.0:8080 LISTENING PID 5424 (httpd.exe)`. `8081` reserved for Expo (currently no listener, do not use). `8082` free → `SERVER_PORT=8082`, PID `21060 (java.exe)`.

## 3. Redis fix (done, verified `PONG`)
```powershell
docker compose up -d redis
docker exec feazto-redis redis-cli ping            # PONG
docker exec feazto-redis redis-cli CONFIG GET protected-mode  # no
docker ps --filter name=redis   # feazto-redis Up (healthy) 0.0.0.0:6379->6379/tcp
```
Config: `redis:7-alpine`, `infra/redis/redis.conf`, publish `${REDIS_PORT:-6379}:6379`.

## 4. Env vars (PowerShell, current boot)
```powershell
$env:SERVER_PORT="8082"; $env:SPRING_PROFILES_ACTIVE="local"
$env:DATABASE_URL="jdbc:postgresql://aws-1-ap-south-1.pooler.supabase.com:6543/postgres?sslmode=require"
$env:DB_USER="postgres.tznqnfqrighkjocpprir"; $env:DB_PASSWORD="<supabase-db-password>"
$env:REDIS_HOST="127.0.0.1"; $env:REDIS_PORT="6379"
$env:SUPABASE_URL="https://tznqnfqrighkjocpprir.supabase.co"
$env:SUPABASE_JWKS_URL="$env:SUPABASE_URL/auth/v1/.well-known/jwks.json"
$env:SUPABASE_ISSUER="$env:SUPABASE_URL/auth/v1"; $env:SUPABASE_AUDIENCE="authenticated"
$env:OTP_SECRET="dev-dummy-otp-secret-min-32-chars-000000"; $env:JWT_SECRET="dev-dummy-jwt-secret-min-32-chars-000000"
$env:PAYMENTS_WEBHOOK_SECRET="dev-dummy"
$env:SPRING_FLYWAY_BASELINE_ON_MIGRATE="true"; $env:BASELINE_VERSION="0"
```

## 5. Build + Run
```powershell
cd D:\feazto_main\services\api
mvn -B -DskipTests clean package   # produces target\food-platform-api-0.1.0-SNAPSHOT.jar
java -jar target\food-platform-api-0.1.0-SNAPSHOT.jar
```

## 6. Verify (all passing 2026-10-08)
```powershell
curl.exe -s http://localhost:8082/actuator/health        # 200 {"status":"UP"}
curl.exe -s -o NUL -w '%{http_code}\n' http://localhost:8082/v3/api-docs            # 200
curl.exe -s -o NUL -w '%{http_code}\n' http://localhost:8082/swagger-ui/index.html  # 200 (note: /swagger-ui.html itself is 302 redirect here)
curl.exe -s -X POST http://localhost:8082/api/v1/auth/otp/request -H 'Content-Type: application/json' -d '{"phone":"+919999999999"}'  # 200 {"success":true,...}
```

## 7. Fixes applied this boot
- `WebConfig` bean renames (duplicate `corsConfigurer`/`objectMapper` eliminated)
- `$` in enum names escaped (5x compile errors fixed)
- `CustomerAddress.IsActive` → `Active` (property-path fix)
- `@Param` hardening on JPA queries, stale `V2__` placeholder migration cleaned, Flyway `baseline-on-migrate=true, baseline-version=0`

## 8. Known gaps (do not treat as prod-ready)
- Malformed JSON → `500` (should be `400`); serviceability endpoint returns `401` vs README `public`; payments webhook returns `500` on dummy secret; `CORS *`; placeholder `OTP/JWT/WEBHOOK` secrets; `/api/*` filter pattern may over-match; `REDIS_URL` env ignored (uses `REDIS_HOST/PORT`).

## 9. Stop / Restart
```powershell
# Stop: Ctrl+C in java window, or:
taskkill /PID 21060 /F            # java on 8082
docker compose stop redis; netstat -ano | findstr "8082 6379"
# Restart:
docker compose up -d redis; java -jar target\food-platform-api-0.1.0-SNAPSHOT.jar
```
