#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 3 ]]; then
  echo "Usage: verify-stage15-linux-runclient-ci-smoke.sh <portable-root> <smoke-root> <generator-id>" >&2
  exit 2
fi

portable_root="$(cd -- "$1" && pwd)"
smoke_root="$(mkdir -p -- "$2" && cd -- "$2" && pwd)"
generator_id="$3"
case "$generator_id" in
  fabric-1.21.1)
    loader_name="Fabric"
    loader_marker="Loading Minecraft 1.21.1 with Fabric Loader"
    mod_id="stage15_fabric_runclient"
    ;;
  neoforge-1.21.1)
    loader_name="NeoForge"
    loader_marker="NeoForge 21.1.232 (neoforge)"
    mod_id="stage15_neoforge_runclient"
    ;;
  *)
    echo "Unsupported Stage 15 runClient generator: $generator_id" >&2
    exit 2
    ;;
esac
isolated_home="$smoke_root/home"
workspace_root="$isolated_home/MCreatorWorkspaces/${loader_name,,}-runclient-smoke"
evidence_root="$smoke_root/evidence"
fixture_classes="$smoke_root/fixture-classes"
bootstrap_json="$evidence_root/bootstrap-create.json"
bootstrap_log="$evidence_root/bootstrap-create.log"
build_json="$evidence_root/headless-build.json"
build_log="$evidence_root/headless-build.log"
runclient_stdout="$evidence_root/headless-run-client.stdout"
runclient_stderr="$evidence_root/headless-run-client.stderr"
client_log_copy="$evidence_root/minecraft-latest.log"
render_proof="$evidence_root/minecraft-render-proof.txt"
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
  if [[ -f "$render_proof" ]]; then
    echo "--- Minecraft render proof ---" >&2
    cat "$render_proof" >&2 || true
  fi
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

echo "[stage15-runclient] compiling deterministic $loader_name workspace fixture"
if ! "$portable_root/jdk/bin/javac" \
  -cp "$portable_root/lib/copperbench.jar:$portable_root/lib/*" \
  -d "$fixture_classes" \
  "$GITHUB_WORKSPACE/src/test/java/dev/copperbench/headless/Stage15GraphicalWorkspaceFixture.java" \
  >"$evidence_root/fixture-javac.log" 2>&1; then
  echo "Stage 15 $loader_name workspace fixture did not compile against the packaged candidate" >&2
  cat "$evidence_root/fixture-javac.log" >&2 || true
  exit 1
fi

if ! (
  cd "$portable_root"
  timeout 420s "$portable_root/jdk/bin/java" \
    --add-opens=java.base/java.lang=ALL-UNNAMED \
    -cp "$fixture_classes:$portable_root/lib/copperbench.jar:$portable_root/lib/*" \
    dev.copperbench.headless.Stage15GraphicalWorkspaceFixture \
    "$workspace_root" "$generator_id" "Stage15 $loader_name RunClient" "$mod_id"
) >"$bootstrap_json" 2>"$bootstrap_log"; then
  echo "Stage 15 $loader_name workspace fixture failed" >&2
  dump_failure
  exit 1
fi
grep -q '"status":"committed"' "$bootstrap_json"
grep -Fq "\"generatorId\":\"$generator_id\"" "$bootstrap_json"
workspace_file="$(find "$workspace_root" -maxdepth 1 -type f -name '*.mcreator' -print -quit)"
test -n "$workspace_file"
test -f "$workspace_file"
echo "[stage15-runclient] $loader_name workspace fixture committed"

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
      && grep -Fq -- "$loader_marker" "$client_log" \
      && grep -q 'Backend library: LWJGL version' "$client_log" \
      && grep -q 'Reloading ResourceManager:' "$client_log" \
      && grep -q 'minecraft:textures/atlas/blocks.png-atlas' "$client_log"; then
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
  echo "Timed out waiting for $loader_name 1.21.1 client render readiness" >&2
  dump_failure
  exit 1
fi
copy_client_log
{
  grep -F -- "$loader_marker" "$client_log"
  grep -F 'Backend library: LWJGL version' "$client_log"
  grep -F 'Reloading ResourceManager:' "$client_log"
  grep -F 'minecraft:textures/atlas/blocks.png-atlas' "$client_log"
} >"$render_proof"
echo "[stage15-runclient] Minecraft/$loader_name Render thread, LWJGL, resource reload and atlas markers observed"

kill -0 -- "$product_pid"
for ((attempt = 0; attempt < 20; attempt++)); do
  kill -0 -- "$product_pid"
  sleep 0.5
done
if grep -Eqi 'crash report|failed to start minecraft|exception in thread \"Render thread\"|GLFW error|failed to initialize the mod loading system and display|could not initialize GLFW' "$client_log"; then
  echo "Minecraft client log contains a fatal startup signature" >&2
  dump_failure
  exit 1
fi
copy_client_log
echo "Stage 15 packaged $loader_name 1.21.1 runClient X11 render preflight passed for process group $product_pid"
