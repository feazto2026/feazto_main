#!/usr/bin/env bash
# Local seed helper for Supabase (PostgreSQL + Auth + Storage).
# Idempotent by design: safe to re-run. Never destructively wipes data.
# Destructive resets must be explicit, manual, and never part of this script.
#
# Usage:
#   ./infra/local/seed.sh                 # apply supabase/seed/*.sql via psql
#   ./infra/local/seed.sh --check         # verify seed files exist, print plan only
#
# Requires: DATABASE_URL (from .env — see .env.example + infra/local/supabase-env.sh)
# Applies files in lexical order: supabase/seed/*.sql
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SEED_DIR="$REPO_ROOT/supabase/seed"
CHECK_ONLY=0

if [[ "${1:-}" == "--check" ]]; then
  CHECK_ONLY=1
fi

if [[ ! -d "$SEED_DIR" ]]; then
  echo "[seed] Seed dir not found: $SEED_DIR" >&2
  echo "[seed] The backend/database track will populate supabase/seed/. Nothing to apply yet — OK." >&2
  exit 0
fi

shopt -s nullglob
SEED_FILES=("$SEED_DIR"/*.sql)
shopt -u nullglob

if [[ "${#SEED_FILES[@]}" -eq 0 ]]; then
  echo "[seed] No .sql files in $SEED_DIR — nothing to apply. OK."
  exit 0
fi

echo "[seed] Found ${#SEED_FILES[@]} seed file(s):"
for f in "${SEED_FILES[@]}"; do
  echo "  - $(basename "$f")"
done

if [[ "$CHECK_ONLY" -eq 1 ]]; then
  echo "[seed] --check: plan only, no changes made."
  exit 0
fi

if [[ -f "$REPO_ROOT/.env" ]]; then
  set -a
  # shellcheck source=/dev/null
  source "$REPO_ROOT/.env"
  set +a
fi

if [[ -z "${DATABASE_URL:-}" ]]; then
  echo "[seed] DATABASE_URL is not set. Copy .env.example to .env and fill it in." >&2
  echo "[seed] See docs/operations/env.md and infra/local/supabase-env.sh." >&2
  exit 1
fi

if ! command -v psql >/dev/null 2>&1; then
  echo "[seed] psql not found. Install PostgreSQL client tools or run via Supabase SQL editor." >&2
  exit 1
fi

for f in "${SEED_FILES[@]}"; do
  echo "[seed] Applying $(basename "$f") ..."
  # ON_ERROR_STOP so a failing seed aborts instead of partially applying silently.
  psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f "$f"
done

echo "[seed] Done. Re-run is safe (seed files must be idempotent — use INSERT ... ON CONFLICT DO NOTHING)."
