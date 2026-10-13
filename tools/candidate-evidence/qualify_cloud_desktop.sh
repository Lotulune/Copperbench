#!/usr/bin/env bash
# Disposable runner preflight. It does not claim installed-product or gameplay acceptance.
set -euo pipefail
desktop=${1:?Expected xorg or wayland}
evidence=${2:?Expected a fresh evidence directory}
case "$desktop" in xorg|wayland) ;; *) exit 2;; esac
test ! -e "$evidence"
mkdir -p "$evidence"
evidence=$(realpath "$evidence")
cp /etc/os-release "$evidence/os-release.txt"
gnome-shell --version > "$evidence/gnome-version.txt"
gnome-shell --help > "$evidence/gnome-help.txt"
uname -a > "$evidence/kernel.txt"
export P263_DESKTOP="$desktop" P263_DESKTOP_EVIDENCE="$evidence"
dbus-run-session -- bash tools/candidate-evidence/qualify_cloud_desktop_session.sh
