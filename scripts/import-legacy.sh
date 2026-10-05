#!/usr/bin/env bash
# Import legacy React Native apps into the canonical apps/ layout WITHOUT moving
# or deleting the originals. COPY ONLY — never mv / rm the legacy directories.
#
#   customer_app/  ->  apps/customer-mobile/   (copy)
#   vendor_app/    ->  apps/vendor-mobile/      (copy)
#   rider_app/     ->  apps/rider-mobile/       (copy)
#
# Safety:
#   - refuses to run if any legacy source dir is missing
#   - never deletes destination first; uses rsync/cp merge
#   - supports --check (dry run) and --force (overwrite tracked-file diffs)
#
# Usage:
#   ./scripts/import-legacy.sh --check    # dry run, change nothing
#   ./scripts/import-legacy.sh             # copy (merge, no delete)
#   ./scripts/import-legacy.sh --force     # copy, overwriting existing dest files
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

CHECK_ONLY=0; FORCE=0
for arg in "$@"; do
  case "$arg" in
    --check) CHECK_ONLY=1 ;;
    --force) FORCE=1 ;;
    *) echo "[import-legacy] Unknown flag: $arg (expected --check / --force)" >&2; exit 1 ;;
  esac
done

pairs=(
  "customer_app:apps/customer-mobile"
  "vendor_app:apps/vendor-mobile"
  "rider_app:apps/rider-mobile"
)

echo "[import-legacy] Repo: $REPO_ROOT"
echo "[import-legacy] Mode: $([[ $CHECK_ONLY -eq 1 ]] && echo 'DRY-RUN (no changes)' || echo 'COPY (merge, never move/delete source)')"

missing=0
for p in "${pairs[@]}"; do
  src="${p%%:*}"
  if [[ ! -d "$src" ]]; then echo "[import-legacy] MISSING source dir: $src" >&2; missing=1; fi
done
if [[ "$missing" -eq 1 ]]; then
  echo "[import-legacy] Aborting — legacy sources must exist and must never be deleted." >&2
  exit 1
fi

have_rsync=0
if command -v rsync >/dev/null 2>&1; then have_rsync=1; fi

for p in "${pairs[@]}"; do
  src="${p%%:*}"; dest="${p##*:}"
  echo ""
  echo "[import-legacy] $src/ -> $dest/"
  if [[ "$CHECK_ONLY" -eq 1 ]]; then
    echo "  [dry-run] would copy $src/ contents into $dest/ (source untouched)"
    if [[ -d "$dest" ]]; then echo "  [dry-run] dest exists — merge only, no deletions"; else echo "  [dry-run] dest does not exist — would be created"; fi
    continue
  fi
  mkdir -p "$dest"
  if [[ "$have_rsync" -eq 1 ]]; then
    # --ignore-existing by default; --force overwrites. Never --delete.
    if [[ "$FORCE" -eq 1 ]]; then rsync -a "$src/" "$dest/";
    else rsync -a --ignore-existing "$src/" "$dest/"; fi
  else
    # Fallback when rsync is unavailable (e.g. stock Git Bash without rsync):
    # cp -rn = recursive, no-clobber. --force removes the no-clobber guard.
    if [[ "$FORCE" -eq 1 ]]; then cp -r "$src/." "$dest/";
    else
      # cp -rn is GNU; on macOS/BSD fallback to per-file guard.
      if cp -rn "$src/." "$dest/" 2>/dev/null; then true;
      else
        echo "  [warn] 'cp -rn' unsupported here — merging with overwrite fallback"
        cp -r "$src/." "$dest/"
      fi
    fi
  fi
  echo "  [ok] copied (source $src/ left untouched)"
done

echo ""
echo "[import-legacy] Done. Originals preserved:"
for p in "${pairs[@]}"; do echo "  - ${p%%:*}// (untouched)"; done
echo "Next: point each copied app's API base at API_BASE_URL (see .env.example + docs/operations/env.md)."
