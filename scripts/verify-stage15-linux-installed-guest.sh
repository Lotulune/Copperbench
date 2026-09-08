#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 5 ]]; then
  echo "Usage: verify-stage15-linux-installed-guest.sh <candidate.deb> <expected-sha256> <workspace.mcreator> <fabric-1.21.1|neoforge-1.21.1> <evidence-dir>" >&2
  exit 2
fi

candidate_deb="$(realpath "$1")"
expected_sha="${2,,}"
workspace_file="$(realpath "$3")"
generator_id="$4"
evidence_root="$(mkdir -p "$5" && realpath "$5")"
script_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
workspace_root="$(dirname -- "$workspace_file")"
probe="$evidence_root/graphical-product-probe.json"
product_log="$evidence_root/installed-product.log"
build_json="$evidence_root/headless-build.json"
build_stderr="$evidence_root/headless-build.stderr"
run_stdout="$evidence_root/headless-run-client.stdout"
run_stderr="$evidence_root/headless-run-client.stderr"
client_log_copy="$evidence_root/minecraft-latest.log"
result_json="$evidence_root/installed-gate-result.json"
product_pid=""
run_pid=""

case "$generator_id" in
  fabric-1.21.1)
    loader_marker="Loading Minecraft 1.21.1 with Fabric Loader"
    ;;
  neoforge-1.21.1)
    loader_marker="NeoForge 21.1.232 (neoforge)"
    ;;
  *)
    echo "Unsupported Stage 15 installed-gate generator: $generator_id" >&2
    exit 2
    ;;
esac

cleanup() {
  if [[ -n "$run_pid" ]] && kill -0 -- "-$run_pid" 2>/dev/null; then
    kill -TERM -- "-$run_pid" 2>/dev/null || true
    sleep 2
    kill -KILL -- "-$run_pid" 2>/dev/null || true
  fi
  if [[ -n "$product_pid" ]] && kill -0 "$product_pid" 2>/dev/null; then
    kill -TERM "$product_pid" 2>/dev/null || true
    wait "$product_pid" 2>/dev/null || true
  fi
}
trap cleanup EXIT

fail() {
  echo "Stage 15 installed Linux guest preflight failed: $*" >&2
  exit 1
}

[[ -f "$candidate_deb" ]] || fail "candidate .deb does not exist"
[[ -f "$workspace_file" ]] || fail "workspace file does not exist"
[[ "$expected_sha" =~ ^[0-9a-f]{64}$ ]] || fail "expected SHA-256 must be 64 lowercase/uppercase hex characters"
[[ "$(uname -m)" == "x86_64" ]] || fail "guest architecture is not x86_64"
[[ -f /etc/os-release ]] || fail "/etc/os-release is missing"
# shellcheck disable=SC1091
source /etc/os-release
[[ "${ID:-}" == "ubuntu" && "${VERSION_ID:-}" == "24.04" ]] || fail "guest is not Ubuntu 24.04"

desktop_name="${XDG_CURRENT_DESKTOP:-${DESKTOP_SESSION:-}}"
[[ "${desktop_name,,}" == *gnome* ]] || fail "guest desktop is not GNOME"

session_type="${XDG_SESSION_TYPE:-}"
[[ "$session_type" == "wayland" || "$session_type" == "x11" ]] || fail "XDG_SESSION_TYPE is not wayland or x11"
if [[ "$session_type" == "wayland" ]]; then
  [[ -n "${WAYLAND_DISPLAY:-}" ]] || fail "Wayland session has no WAYLAND_DISPLAY"
else
  [[ -n "${DISPLAY:-}" ]] || fail "X11 session has no DISPLAY"
fi

unexpected_system_tool=0
: >"$evidence_root/preinstall-system-tooling.txt"
for tool in java gradle git; do
  resolved="$(command -v "$tool" 2>/dev/null || true)"
  if [[ -n "$resolved" ]]; then
    printf '%s=%s\n' "$tool" "$resolved" >>"$evidence_root/preinstall-system-tooling.txt"
    unexpected_system_tool=1
  else
    printf '%s=absent\n' "$tool" >>"$evidence_root/preinstall-system-tooling.txt"
  fi
done
[[ "$unexpected_system_tool" -eq 0 ]] || fail "clean guest unexpectedly has system Java, Gradle, or Git"

{
  printf 'os=%s %s\n' "${ID:-unknown}" "${VERSION_ID:-unknown}"
  printf 'arch=%s\n' "$(uname -m)"
  printf 'desktop=%s\n' "$desktop_name"
  printf 'session=%s\n' "$session_type"
  printf 'waylandDisplayPresent=%s\n' "$([[ -n "${WAYLAND_DISPLAY:-}" ]] && printf true || printf false)"
  printf 'x11DisplayPresent=%s\n' "$([[ -n "${DISPLAY:-}" ]] && printf true || printf false)"
} >"$evidence_root/guest-environment.txt"

actual_sha="$(sha256sum "$candidate_deb" | awk '{print $1}')"
[[ "$actual_sha" == "$expected_sha" ]] || fail "candidate SHA-256 does not match the expected exact binary"
printf '%s  %s\n' "$actual_sha" "$candidate_deb" >"$evidence_root/candidate.sha256"

echo "[stage15-installed] installing exact candidate $candidate_deb"
sudo apt-get install -y --reinstall "$candidate_deb" >"$evidence_root/apt-install.log" 2>&1
dpkg-query -W -f='${Package} ${Version} ${Architecture}\n' copperbench >"$evidence_root/dpkg-package.txt"

installed_root="/opt/copperbench"
[[ -x /usr/bin/copperbench ]] || fail "/usr/bin/copperbench is not executable after install"
[[ -x "$installed_root/copperbench.sh" ]] || fail "installed Copperbench launcher is not executable"
[[ -x "$installed_root/jdk/bin/java" ]] || fail "installed JBR/JCEF Java is not executable"
[[ -x "$installed_root/jdk21/bin/java" ]] || fail "installed Java 21 sidecar is not executable"
[[ -f "$installed_root/LINUX-CANDIDATE.md" ]] || fail "installed candidate guidance is missing"
[[ -f "$installed_root/linux-candidate-manifest.json" ]] || fail "installed candidate manifest is missing"

python3 - "$installed_root/linux-candidate-manifest.json" <<'PY'
import json, pathlib, sys
manifest = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
if manifest.get("status") != "development-not-certified" or manifest.get("formalSupportClaim") is not False:
    raise SystemExit("installed candidate manifest makes an unexpected Linux support claim")
PY

rm -f "$probe"
export COPPERBENCH_GRAPHICAL_PROBE_RESULT="$probe"
echo "[stage15-installed] launching installed graphical product in $session_type session"
/usr/bin/copperbench "$workspace_file" >"$product_log" 2>&1 &
product_pid=$!

for ((attempt = 0; attempt < 120; attempt++)); do
  [[ -f "$probe" ]] && break
  kill -0 "$product_pid" 2>/dev/null || fail "installed Copperbench exited before the graphical probe was written"
  sleep 1
done
[[ -f "$probe" ]] || fail "timed out waiting for installed JCEF graphical probe"

"$installed_root/jdk/bin/java" \
  -cp "$installed_root/lib/copperbench.jar:$installed_root/lib/*" \
  "$script_root/stage15/Stage15GraphicalProbeVerifier.java" \
  "$probe" "$workspace_root" "$session_type" \
  >"$evidence_root/graphical-probe-verifier.log" 2>&1

connection_file="$workspace_root/.copperbench/mcp-connection.json"
[[ -f "$connection_file" ]] || fail "Desktop MCP descriptor was not created by installed product"
[[ "$(stat -c '%a' "$connection_file")" == "600" ]] || fail "Desktop MCP descriptor is not mode 0600"
[[ "$(stat -c '%a' "$workspace_root/.copperbench")" == "700" ]] || fail "Desktop MCP directory is not mode 0700"
kill -0 "$product_pid" || fail "installed graphical product did not remain alive after probe"
sleep 5
kill -0 "$product_pid" || fail "installed graphical product did not survive the stability window"

kill -TERM "$product_pid"
wait "$product_pid" 2>/dev/null || true
product_pid=""

echo "[stage15-installed] building workspace through installed Copperbench Core"
timeout 900s /usr/bin/copperbench headless --workspace "$workspace_file" build >"$build_json" 2>"$build_stderr"
grep -q '"status":"succeeded"' "$build_json" || fail "installed Copperbench Core build did not succeed"

echo "[stage15-installed] launching Minecraft through installed Copperbench Core"
setsid /usr/bin/copperbench headless --workspace "$workspace_file" run-client >"$run_stdout" 2>"$run_stderr" &
run_pid=$!
client_log="$workspace_root/run/logs/latest.log"
ready=0
for ((attempt = 0; attempt < 600; attempt++)); do
  if [[ -f "$client_log" ]] \
      && grep -Fq -- "$loader_marker" "$client_log" \
      && grep -Fq 'Backend library: LWJGL version' "$client_log" \
      && grep -Fq 'Reloading ResourceManager:' "$client_log" \
      && grep -Fq 'minecraft:textures/atlas/blocks.png-atlas' "$client_log"; then
    ready=1
    break
  fi
  kill -0 "$run_pid" 2>/dev/null || fail "installed run-client exited before render readiness"
  sleep 1
done
[[ "$ready" -eq 1 ]] || fail "timed out waiting for installed Minecraft render readiness"
cp "$client_log" "$client_log_copy"

for ((attempt = 0; attempt < 20; attempt++)); do
  kill -0 "$run_pid" 2>/dev/null || fail "installed run-client exited during the stability window"
  sleep 0.5
done
if grep -Eqi 'crash report|failed to start minecraft|exception in thread "Render thread"|GLFW error|failed to initialize the mod loading system and display|could not initialize GLFW' "$client_log"; then
  fail "installed Minecraft log contains a fatal startup signature"
fi

cat >"$result_json" <<EOF
{"schemaVersion":"1.0","status":"automated-preflight-passed-manual-gates-pending","candidateSha256":"$actual_sha","sessionType":"$session_type","generatorId":"$generator_id","ubuntu2404GnomeVerified":true,"systemJavaGradleGitAbsentBeforeInstall":true,"formalSupportClaim":false,"manualGatesPending":["confirm-workspace-create-save-reopen-via-installed-ui","confirm-user-visible-copperbench-jcef-window","confirm-user-visible-minecraft-window","confirm-interactive-runclient-remains-running-until-user-closes-minecraft","authorize-desktop-mcp-via-ui-and-run-independent-external-agent-loop","close-installed-product-and-confirm-descriptor-removed-old-connection-rejected","exercise-installed-blockbench-open-edit-close"]}
EOF

echo "Stage 15 installed Linux automated preflight passed; manual GNOME/UI/external-tool gates remain pending. Evidence: $result_json"
