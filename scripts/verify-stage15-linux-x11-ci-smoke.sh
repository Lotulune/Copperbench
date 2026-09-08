#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: verify-stage15-linux-x11-ci-smoke.sh <portable-root> <smoke-root>" >&2
  exit 2
fi

portable_root="$(cd -- "$1" && pwd)"
smoke_root="$(mkdir -p -- "$2" && cd -- "$2" && pwd)"
isolated_home="$smoke_root/home"
workspace_root="$isolated_home/MCreatorWorkspaces/graphical-smoke"
probe="$smoke_root/evidence/graphical-product-probe.json"
product_log="$smoke_root/evidence/product-shell.log"
bootstrap_json="$smoke_root/evidence/bootstrap-create.json"
bootstrap_log="$smoke_root/evidence/bootstrap-create.log"
bootstrap_pid=""
product_pid=""

cleanup() {
  if [[ -n "$bootstrap_pid" ]] && kill -0 "$bootstrap_pid" 2>/dev/null; then
    kill -TERM "$bootstrap_pid" 2>/dev/null || true
    wait "$bootstrap_pid" 2>/dev/null || true
  fi
  if [[ -n "$product_pid" ]] && kill -0 "$product_pid" 2>/dev/null; then
    kill -TERM "$product_pid" 2>/dev/null || true
    wait "$product_pid" 2>/dev/null || true
  fi
}
trap cleanup EXIT

test -n "${DISPLAY:-}"
test -x "$portable_root/copperbench.sh"
test -x "$portable_root/jdk/bin/java"
command -v xdotool >/dev/null 2>&1
rm -rf "$isolated_home" "$smoke_root/evidence"
mkdir -p "$isolated_home/runtime" "$smoke_root/evidence"
chmod 700 "$isolated_home/runtime"

export HOME="$isolated_home"
export XDG_DATA_HOME="$isolated_home/data"
export XDG_CONFIG_HOME="$isolated_home/config"
export XDG_CACHE_HOME="$isolated_home/cache"
export XDG_STATE_HOME="$isolated_home/state"
export XDG_RUNTIME_DIR="$isolated_home/runtime"
export JAVA_TOOL_OPTIONS="-Duser.home=$isolated_home"

"$portable_root/copperbench.sh" bootstrap create-workspace \
  --generator-id fabric-1.21.1 \
  --mod-name "Stage15 Graphical Smoke" \
  --mod-id stage15_graphical_smoke \
  --package-name dev.copperbench.stage15.graphical \
  --workspace-folder "$workspace_root" \
  --version 1.0.0 >"$bootstrap_json" 2>"$bootstrap_log" &
bootstrap_pid=$!

approval_window=""
for ((attempt = 0; attempt < 60; attempt++)); do
  approval_window="$(xdotool search --onlyvisible --name '^Copperbench workspace creation$' 2>/dev/null | head -n 1 || true)"
  if [[ -n "$approval_window" ]]; then
    break
  fi
  if ! kill -0 "$bootstrap_pid" 2>/dev/null; then
    break
  fi
  sleep 1
done
if [[ -z "$approval_window" ]]; then
  echo "Packaged bootstrap did not present the local workspace-creation approval dialog" >&2
  cat "$bootstrap_log" >&2 || true
  exit 1
fi
xdotool windowfocus "$approval_window"
xdotool key --window "$approval_window" Return
if ! wait "$bootstrap_pid"; then
  echo "Packaged bootstrap failed after local workspace-creation approval" >&2
  cat "$bootstrap_log" >&2 || true
  cat "$bootstrap_json" >&2 || true
  exit 1
fi
bootstrap_pid=""
grep -q '"status":"committed"' "$bootstrap_json"

workspace_file="$(find "$workspace_root" -maxdepth 1 -type f -name '*.mcreator' -print -quit)"
test -n "$workspace_file"
test -f "$workspace_file"

export COPPERBENCH_GRAPHICAL_PROBE_RESULT="$probe"
"$portable_root/copperbench.sh" "$workspace_file" >"$product_log" 2>&1 &
product_pid=$!

for ((attempt = 0; attempt < 120; attempt++)); do
  if [[ -f "$probe" ]]; then
    break
  fi
  first_run_window="$(xdotool search --onlyvisible --name '^Copperbench$' 2>/dev/null | head -n 1 || true)"
  if [[ -n "$first_run_window" ]]; then
    xdotool windowfocus "$first_run_window" 2>/dev/null || true
    xdotool key --window "$first_run_window" Return 2>/dev/null || true
  fi
  if ! kill -0 "$product_pid" 2>/dev/null; then
    echo "Packaged Copperbench exited before the graphical probe was written" >&2
    cat "$product_log" >&2 || true
    exit 1
  fi
  sleep 1
done

if [[ ! -f "$probe" ]]; then
  echo "Timed out waiting for the packaged JCEF graphical probe" >&2
  cat "$product_log" >&2 || true
  exit 1
fi

"$portable_root/jdk/bin/java" \
  -cp "$portable_root/lib/copperbench.jar:$portable_root/lib/*" \
  "$GITHUB_WORKSPACE/scripts/stage15/Stage15GraphicalProbeVerifier.java" \
  "$probe" "$workspace_root"

connection_file="$workspace_root/.copperbench/mcp-connection.json"
test -f "$connection_file"
test "$(stat -c '%a' "$connection_file")" = "600"
test "$(stat -c '%a' "$workspace_root/.copperbench")" = "700"
kill -0 "$product_pid"
sleep 3
kill -0 "$product_pid"

main_window="$(xdotool search --onlyvisible --name '^Stage15 Graphical Smoke.*Copperbench' 2>/dev/null | head -n 1 || true)"
if [[ -z "$main_window" ]]; then
  echo "The packaged product shell reported ready but no matching X11 workspace window is visible" >&2
  cat "$product_log" >&2 || true
  exit 1
fi

echo "Stage 15 packaged X11 JCEF/MCP smoke passed for PID $product_pid"
