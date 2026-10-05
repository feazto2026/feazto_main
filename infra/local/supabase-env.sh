#!/usr/bin/env bash
# Local Supabase environment helper — validates and exports required variables.
# Source it, do NOT execute it directly:
#   set -a; source ./infra/local/supabase-env.sh; set +a
#   # or: source ./infra/local/supabase-env.sh
#
# It never contains secrets. Values come from your local `.env`
# (copied from `.env.example`). See docs/operations/env.md.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

if [[ -f "$REPO_ROOT/.env" ]]; then
  # shellcheck disable=SC1091
  set -a
  # shellcheck source=/dev/null
  source "$REPO_ROOT/.env"
  set +a
else
  echo "[supabase-env] No .env found at $REPO_ROOT/.env — copy .env.example to .env first." >&2
fi

missing=()
for var in SUPABASE_URL SUPABASE_ANON_KEY SUPABASE_SERVICE_ROLE_KEY DATABASE_URL; do
  if [[ -z "${!var:-}" ]]; then
    missing+=("$var")
  fi
done

echo "[supabase-env] SUPABASE_URL=${SUPABASE_URL:-<missing>}"
echo "[supabase-env] DATABASE_URL present: $([[ -n "${DATABASE_URL:-}" ]] && echo yes || echo no) (password hidden)"
echo "[supabase-env] ANON_KEY present: $([[ -n "${SUPABASE_ANON_KEY:-}" ]] && echo yes || echo no)"
echo "[supabase-env] SERVICE_ROLE_KEY present: $([[ -n "${SUPABASE_SERVICE_ROLE_KEY:-}" ]] && echo yes || echo no) (server-only, never expose to clients)"

if [[ "${#missing[@]}" -gt 0 ]]; then
  echo "[supabase-env] MISSING: ${missing[*]}" >&2
  echo "[supabase-env] See .env.example and docs/operations/env.md." >&2
  return 1 2>/dev/null || exit 1
fi

echo "[supabase-env] OK — Supabase env looks present. Service-role key stays server-side only."
