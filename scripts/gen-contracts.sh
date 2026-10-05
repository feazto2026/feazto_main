#!/usr/bin/env bash
# Generate / refresh shared API contracts for all clients.
# Contract-first: Spring Boot (services/api) publishes OpenAPI, clients consume types.
#
# Canonical flow:
#   services/api  (OpenAPI source, e.g. docs/openapi.yaml or /v3/api-docs export)
#        |
#        v
#   packages/api-contracts  (generated TS types + validation helpers shared by
#                            apps/admin-web and, later, the three mobile apps)
#
# Usage:
#   ./scripts/gen-contracts.sh
#   ./scripts/gen-contracts.sh --check   # verify tools + print plan, change nothing
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHECK_ONLY=0
if [[ "${1:-}" == "--check" ]]; then CHECK_ONLY=1; fi

# Candidate OpenAPI source locations (first existing file wins).
CANDIDATES=(
  "$REPO_ROOT/services/api/docs/openapi.yaml"
  "$REPO_ROOT/services/api/docs/openapi.json"
  "$REPO_ROOT/services/api/src/main/resources/static/openapi.yaml"
  "$REPO_ROOT/docs/api/openapi.yaml"
)
SPEC=""
for c in "${CANDIDATES[@]}"; do
  if [[ -f "$c" ]]; then SPEC="$c"; break; fi
done

OUT_DIR="$REPO_ROOT/packages/api-contracts"
OUT_FILE="$OUT_DIR/src/generated.ts"

echo "[gen-contracts] Repo: $REPO_ROOT"
if [[ -z "$SPEC" ]]; then
  echo "[gen-contracts] No OpenAPI spec found yet. Checked:"
  for c in "${CANDIDATES[@]}"; do echo "  - $c"; done
  echo "[gen-contracts] The backend track will publish the spec; re-run after it lands. OK (nothing to do)."
  exit 0
fi

echo "[gen-contracts] Spec: $SPEC"
echo "[gen-contracts] Out:  $OUT_FILE"

if [[ "$CHECK_ONLY" -eq 1 ]]; then
  echo "[gen-contracts] --check: plan only, no changes made."
  exit 0
fi

mkdir -p "$(dirname "$OUT_FILE")"

if command -v npx >/dev/null 2>&1; then
  echo "[gen-contracts] Generating TypeScript types via openapi-typescript ..."
  npx -y openapi-typescript "$SPEC" -o "$OUT_FILE"
  echo "[gen-contracts] Done: $OUT_FILE"
  echo "[gen-contracts] Consumers: import from packages/api-contracts in apps/admin-web (and later mobile apps)."
else
  echo "[gen-contracts] npx not found — install Node.js 20+ to generate contracts." >&2
  exit 1
fi
