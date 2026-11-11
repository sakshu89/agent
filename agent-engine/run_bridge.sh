#!/usr/bin/env bash
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
HERMES="${HERMES_SOURCE_DIR:-$HERE/vendor/hermes-agent-0.23.1}"
if [[ ! -f "$HERMES/pyproject.toml" ]]; then
  echo "Hermes not extracted. Run: $HERE/setup_hermes.sh" >&2
  exit 1
fi
if [[ -z "${ITRA_HERMES_TOKEN:-}" ]]; then
  echo "Set ITRA_HERMES_TOKEN to a random secret (at least 16 characters)." >&2
  exit 1
fi
export HERMES_SOURCE_DIR="$HERMES"
exec uv run --project "$HERMES" python "$HERE/itra_hermes_bridge.py"
