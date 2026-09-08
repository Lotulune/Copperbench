#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

export CLASSPATH="$SCRIPT_DIR/lib/copperbench.jar:$SCRIPT_DIR/lib/*"
exec "$SCRIPT_DIR/jdk/bin/java" \
  --add-opens=java.base/java.lang=ALL-UNNAMED \
  --enable-native-access=ALL-UNNAMED,jcef \
  -Dcopperbench.productShell=true \
  -Dcopperbench.stage15LinuxCandidate=true \
  net.mcreator.Launcher "$@"
