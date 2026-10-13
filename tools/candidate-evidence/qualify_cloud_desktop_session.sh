#!/usr/bin/env bash
set -euo pipefail
evidence=${P263_DESKTOP_EVIDENCE:?}
desktop=${P263_DESKTOP:?}
shell_pid=''
xorg_pid=''
cleanup() {
  if [ -n "$shell_pid" ]; then kill "$shell_pid" 2>/dev/null || true; wait "$shell_pid" 2>/dev/null || true; fi
  if [ -n "$xorg_pid" ]; then sudo kill "$xorg_pid" 2>/dev/null || true; fi
}
trap cleanup EXIT
export XDG_RUNTIME_DIR="${RUNNER_TEMP:?}/p263-desktop-runtime"
mkdir -m 700 "$XDG_RUNTIME_DIR"
export XDG_CURRENT_DESKTOP=GNOME
export LIBGL_ALWAYS_SOFTWARE=1
if [ "$desktop" = xorg ]; then
  export DISPLAY=:99 XDG_SESSION_TYPE=x11
  export XAUTHORITY="$XDG_RUNTIME_DIR/xauthority"
  touch "$XAUTHORITY"
  chmod 600 "$XAUTHORITY"
  cookie=$(mcookie)
  xauth -f "$XAUTHORITY" add "$DISPLAY" . "$cookie"
  unset cookie
  cat > "$evidence/xorg.conf" <<'EOF'
Section "Device"
  Identifier "TaskDummy"
  Driver "dummy"
  VideoRam 256000
EndSection
Section "Monitor"
  Identifier "TaskMonitor"
  HorizSync 28.0-80.0
  VertRefresh 48.0-75.0
  Modeline "1280x720" 74.50 1280 1344 1472 1664 720 723 728 748 -hsync +vsync
EndSection
Section "Screen"
  Identifier "TaskScreen"
  Device "TaskDummy"
  Monitor "TaskMonitor"
  DefaultDepth 24
  SubSection "Display"
    Depth 24
    Modes "1280x720"
  EndSubSection
EndSection
EOF
  sudo Xorg "$DISPLAY" -noreset -nolisten tcp -auth "$XAUTHORITY" \
    -config "$evidence/xorg.conf" -logfile "$evidence/Xorg.log" > "$evidence/xorg.stdout" 2>&1 &
  xorg_pid=$!
  for attempt in $(seq 1 30); do xdpyinfo > "$evidence/xdpyinfo.txt" 2>&1 && break; sleep 1; done
  xdpyinfo > "$evidence/xdpyinfo.txt"
  gnome-shell --x11 --replace > "$evidence/gnome-shell.log" 2>&1 &
else
  export XDG_SESSION_TYPE=wayland
  unset DISPLAY
  gnome-shell --headless --wayland --no-x11 --virtual-monitor 1280x720 \
    > "$evidence/gnome-shell.log" 2>&1 &
fi
shell_pid=$!
for attempt in $(seq 1 45); do
  if gdbus introspect --session --dest org.gnome.Mutter.RemoteDesktop \
      --object-path /org/gnome/Mutter/RemoteDesktop > "$evidence/remote-desktop-interface.txt" 2>&1; then break; fi
  kill -0 "$shell_pid"
  sleep 1
done
gdbus introspect --session --dest org.gnome.Mutter.RemoteDesktop \
  --object-path /org/gnome/Mutter/RemoteDesktop > "$evidence/remote-desktop-interface.txt"
gdbus introspect --session --dest org.gnome.Mutter.ScreenCast \
  --object-path /org/gnome/Mutter/ScreenCast > "$evidence/screencast-interface.txt"
gdbus call --session --dest org.gnome.Mutter.DisplayConfig \
  --object-path /org/gnome/Mutter/DisplayConfig \
  --method org.gnome.Mutter.DisplayConfig.GetCurrentState > "$evidence/display-state.txt"
ps -p "$shell_pid" -o pid,args > "$evidence/compositor-process.txt"
if [ "$desktop" = xorg ]; then glxinfo -B > "$evidence/opengl.txt"; fi
if [ "$desktop" = wayland ]; then
  mapfile -t sockets < <(find "$XDG_RUNTIME_DIR" -maxdepth 1 -type s -name 'wayland-*')
  test "${#sockets[@]}" -eq 1
  export WAYLAND_DISPLAY="$(basename "${sockets[0]}")" GDK_BACKEND=wayland
fi
python3 tools/candidate-evidence/inspect_gnome_session.py
printf '{"status":"interfaces_observed","desktop":"%s","displayBackend":"%s","clientAcceptance":"not_run","formalSupportClaim":false}\n' \
  "$desktop" "$XDG_SESSION_TYPE" > "$evidence/result.json"
