#!/usr/bin/env bash
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ARCHIVE="$HERE/hermes-agent-0.23.1.zip"
VENDOR="$HERE/vendor"
HERMES="$VENDOR/hermes-agent-0.23.1"
command -v unzip >/dev/null || { echo "Install unzip first." >&2; exit 1; }
command -v uv >/dev/null || { echo "Install uv first (https://docs.astral.sh/uv/)." >&2; exit 1; }
mkdir -p "$VENDOR"
if [[ ! -f "$HERMES/pyproject.toml" ]]; then
  unzip -q "$ARCHIVE" -d "$VENDOR"
fi
cd "$HERMES"
uv sync --all-extras
echo "Hermes source and Python environment prepared at: $HERMES"
echo "Next: set ITRA_HERMES_TOKEN and ITRA_HERMES_MODEL/ITRA_HERMES_BASE_URL, then run $HERE/run_bridge.sh"
