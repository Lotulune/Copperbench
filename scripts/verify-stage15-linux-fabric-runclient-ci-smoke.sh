#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: verify-stage15-linux-fabric-runclient-ci-smoke.sh <portable-root> <smoke-root>" >&2
  exit 2
fi

portable_root="$(cd -- "$1" && pwd)"
smoke_root="$(mkdir -p -- "$2" && cd -- "$2" && pwd)"
isolated_home="$smoke_root/home"
workspace_root="$isolated_home/MCreatorWorkspaces/fabric-runclient-smoke"
evidence_root="$smoke_root/evidence"
fixture_classes="$smoke_root/fixture-classes"
bootstrap_json="$evidence_root/bootstrap-create.json"
bootstrap_log="$evidence_root/bootstrap-create.log"
build_json="$evidence_root/headless-build.json"
build_log="$evidence_root/headless-build.log"
runclient_stdout="$evidence_root/headless-run-client.stdout"
runclient_stderr="$evidence_root/headless-run-client.stderr"
client_log_copy="$evidence_root/minecraft-latest.log"
x11_windows="$evidence_root/x11-visible-windows.txt"
product_pid=""

copy_client_log() {
  local client_log="$workspace_root/run/logs/latest.log"
  if [[ -f "$client_log" ]]; then
    cp "$client_log" "$client_log_copy" || true
  fi
}

dump_failure() {
  echo "--- bootstrap stderr ---" >&2
  cat "$bootstrap_log" >&2 || true
  echo "--- bootstrap stdout ---" >&2
  cat "$bootstrap_json" >&2 || true
  echo "--- headless build stderr ---" >&2
  cat "$build_log" >&2 || true
  echo "--- headless build stdout ---" >&2
  cat "$build_json" >&2 || true
  echo "--- headless run-client stderr ---" >&2
  cat "$runclient_stderr" >&2 || true
  echo "--- headless run-client stdout ---" >&2
  cat "$runclient_stdout" >&2 || true
  copy_client_log
  if [[ -f "$client_log_copy" ]]; then
    echo "--- Minecraft latest.log tail ---" >&2
    tail -n 200 "$client_log_copy" >&2 || true
  fi
  if [[ -f "$x11_windows" ]]; then
    echo "--- visible X11 windows ---" >&2
    cat "$x11_windows" >&2 || true
  fi
}

capture_visible_windows() {
  : >"$x11_windows"
  local window window_pid window_pgid window_name
  while read -r window; do
    [[ -n "$window" ]] || continue
    window_pid="$(xdotool getwindowpid "$window" 2>/dev/null || true)"
    window_pgid=""
    if [[ "$window_pid" =~ ^[0-9]+$ ]]; then
      window_pgid="$(ps -o pgid= -p "$window_pid" 2>/dev/null || true)"
      window_pgid="${window_pgid//[[:space:]]/}"
    fi
    window_name="$(xdotool getwindowname "$window" 2>/dev/null || true)"
    printf 'window=%s pid=%s pgid=%s title=%q\n' \
      "$window" "${window_pid:-unknown}" "${window_pgid:-unknown}" "$window_name" >>"$x11_windows"
  done < <(xdotool search --onlyvisible --name '.*' 2>/dev/null || true)
}

find_runclient_window() {
  capture_visible_windows
  local window window_pid window_pgid
  while read -r window; do
    [[ -n "$window" ]] || continue
    window_pid="$(xdotool getwindowpid "$window" 2>/dev/null || true)"
    [[ "$window_pid" =~ ^[0-9]+$ ]] || continue
    window_pgid="$(ps -o pgid= -p "$window_pid" 2>/dev/null || true)"
    window_pgid="${window_pgid//[[:space:]]/}"
    if [[ "$window_pgid" == "$product_pid" ]]; then
      printf '%s\n' "$window"
      return 0
    fi
  done < <(xdotool search --onlyvisible --name '.*' 2>/dev/null || true)
  return 1
}

cleanup() {
  copy_client_log
  if [[ -n "$product_pid" ]] && kill -0 -- "-$product_pid" 2>/dev/null; then
    kill -TERM -- "-$product_pid" 2>/dev/null || true
    for ((attempt = 0; attempt < 30; attempt++)); do
      if ! kill -0 -- "-$product_pid" 2>/dev/null; then
        return
      fi
      sleep 0.2
    done
    kill -KILL -- "-$product_pid" 2>/dev/null || true
  fi
}
trap cleanup EXIT

test -n "${DISPLAY:-}"
test -x "$portable_root/copperbench.sh"
test -x "$portable_root/jdk/bin/java"
test -x "$portable_root/jdk/bin/javac"
test -x "$portable_root/jdk21/bin/java"
command -v setsid >/dev/null 2>&1
command -v xdotool >/dev/null 2>&1
rm -rf "$isolated_home" "$evidence_root" "$fixture_classes"
mkdir -p "$isolated_home/runtime" "$evidence_root" "$fixture_classes"
chmod 700 "$isolated_home/runtime"

export HOME="$isolated_home"
export XDG_DATA_HOME="$isolated_home/data"
export XDG_CONFIG_HOME="$isolated_home/config"
export XDG_CACHE_HOME="$isolated_home/cache"
export XDG_STATE_HOME="$isolated_home/state"
export XDG_RUNTIME_DIR="$isolated_home/runtime"
export COPPERBENCH_GRADLE_USER_HOME="$isolated_home/cache/copperbench/gradle"
export JAVA_TOOL_OPTIONS="-Duser.home=$isolated_home"
export LIBGL_ALWAYS_SOFTWARE=1

echo "[stage15-runclient] compiling deterministic Fabric workspace fixture"
if ! "$portable_root/jdk/bin/javac" \
  -cp "$portable_root/lib/copperbench.jar:$portable_root/lib/*" \
  -d "$fixture_classes" \
  "$GITHUB_WORKSPACE/src/test/java/dev/copperbench/headless/Stage15GraphicalWorkspaceFixture.java" \
  >"$evidence_root/fixture-javac.log" 2>&1; then
  echo "Stage 15 Fabric workspace fixture did not compile against the packaged candidate" >&2
  cat "$evidence_root/fixture-javac.log" >&2 || true
  exit 1
fi

if ! (
  cd "$portable_root"
  timeout 420s "$portable_root/jdk/bin/java" \
    --add-opens=java.base/java.lang=ALL-UNNAMED \
    -cp "$fixture_classes:$portable_root/lib/copperbench.jar:$portable_root/lib/*" \
    dev.copperbench.headless.Stage15GraphicalWorkspaceFixture \
    "$workspace_root" "fabric-1.21.1" "Stage15 Fabric RunClient" "stage15_fabric_runclient"
) >"$bootstrap_json" 2>"$bootstrap_log"; then
  echo "Stage 15 Fabric workspace fixture failed" >&2
  dump_failure
  exit 1
fi
grep -q '"status":"committed"' "$bootstrap_json"
grep -q '"generatorId":"fabric-1.21.1"' "$bootstrap_json"
workspace_file="$(find "$workspace_root" -maxdepth 1 -type f -name '*.mcreator' -print -quit)"
test -n "$workspace_file"
test -f "$workspace_file"
echo "[stage15-runclient] Fabric workspace fixture committed"

echo "[stage15-runclient] building through packaged headless Core"
if ! timeout 600s /usr/bin/bash "$portable_root/copperbench.sh" \
  headless --workspace "$workspace_file" build >"$build_json" 2>"$build_log"; then
  echo "Packaged Copperbench headless build failed" >&2
  dump_failure
  exit 1
fi
grep -q '"status":"succeeded"' "$build_json"
test -d "$workspace_root/build"
echo "[stage15-runclient] packaged headless build succeeded"

echo "[stage15-runclient] starting packaged headless run-client under X11"
setsid /usr/bin/bash "$portable_root/copperbench.sh" \
  headless --workspace "$workspace_file" run-client \
  >"$runclient_stdout" 2>"$runclient_stderr" &
product_pid=$!

client_log="$workspace_root/run/logs/latest.log"
ready=0
for ((attempt = 0; attempt < 480; attempt++)); do
  if [[ -f "$client_log" ]] \
      && grep -q 'Loading Minecraft 1.21.1 with Fabric Loader' "$client_log" \
      && grep -q 'Reloading ResourceManager:' "$client_log"; then
    ready=1
    break
  fi
  if ! kill -0 -- "$product_pid" 2>/dev/null; then
    echo "Packaged Copperbench run-client exited before Minecraft readiness" >&2
    dump_failure
    exit 1
  fi
  sleep 1
done

if [[ "$ready" -ne 1 ]]; then
  echo "Timed out waiting for Fabric 1.21.1 client loading/resource readiness" >&2
  dump_failure
  exit 1
fi
copy_client_log
echo "[stage15-runclient] Minecraft/Fabric client loading and resource reload markers observed"

minecraft_window=""
for ((attempt = 0; attempt < 30; attempt++)); do
  minecraft_window="$(find_runclient_window || true)"
  if [[ -n "$minecraft_window" ]]; then
    break
  fi
  sleep 1
done
if [[ -z "$minecraft_window" ]]; then
  echo "Minecraft emitted client startup markers but no visible X11 window belonged to the run-client process group" >&2
  dump_failure
  exit 1
fi

kill -0 -- "$product_pid"
for ((attempt = 0; attempt < 10; attempt++)); do
  kill -0 -- "$product_pid"
  if ! xdotool getwindowname "$minecraft_window" >/dev/null 2>&1; then
    echo "Minecraft X11 window disappeared during the stability window" >&2
    dump_failure
    exit 1
  fi
  sleep 0.5
done
if grep -Eqi 'crash report|failed to start minecraft|exception in thread \"Render thread\"' "$client_log"; then
  echo "Minecraft client log contains a fatal startup signature" >&2
  dump_failure
  exit 1
fi
capture_visible_windows
echo "Stage 15 packaged Fabric 1.21.1 runClient X11 preflight passed for process group $product_pid, window $minecraft_window"
