#!/usr/bin/env bash
# Usage: tools/emulator.sh {start|shot <file>|stop} <TabS7Plus|S25Ultra>
#   start <avd>        boot the emulator headless and wait until Android is ready
#   shot <file> <avd>  save a screenshot (PNG) to <file>
#   stop <avd>         shut the emulator down
set -euo pipefail

SDK="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"
EMULATOR="$SDK/emulator/emulator"
ADB="$SDK/platform-tools/adb"
export ANDROID_SDK_ROOT="$SDK"

# Software graphics. On this machine (Fedora, emulator 37.x) "swiftshader_indirect"
# crashes with a segfault right after boot starts; "angle_indirect" is also
# software rendering and works. Override with: EMU_GPU=swiftshader_indirect
GPU="${EMU_GPU:-angle_indirect}"

usage() {
  echo "Usage: $0 {start|shot <file>|stop} <TabS7Plus|S25Ultra>" >&2
  exit 1
}

cmd="${1:-}"
case "$cmd" in
  start|stop) [ $# -eq 2 ] || usage; file=""; avd="$2" ;;
  shot)       [ $# -eq 3 ] || usage; file="$2"; avd="$3" ;;
  *)          usage ;;
esac

case "$avd" in
  TabS7Plus|S25Ultra) ;;
  *) usage ;;
esac

# Each AVD gets its own fixed port so both can run side by side.
case "$avd" in
  TabS7Plus) port=5554 ;;
  S25Ultra)  port=5556 ;;
esac
serial="emulator-$port"
log="${TMPDIR:-/tmp}/emulator-$avd.log"

case "$cmd" in
  start)
    if "$ADB" devices | grep -q "^$serial"; then
      echo "$avd is already running"
    else
      setsid nohup "$EMULATOR" -avd "$avd" -port "$port" -no-window -no-audio -no-snapshot \
        -gpu "$GPU" >"$log" 2>&1 &
      echo "Starting $avd (log: $log)..."
    fi
    timeout "${BOOT_TIMEOUT:-300}" "$ADB" -s "$serial" wait-for-device || { echo "$avd did not appear within ${BOOT_TIMEOUT:-300}s. See the log: $log" >&2; exit 1; }
    # Give up after BOOT_TIMEOUT seconds (default 300) instead of waiting forever.
    timeout_s="${BOOT_TIMEOUT:-300}"
    waited=0
    until [ "$("$ADB" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
      if [ "$waited" -ge "$timeout_s" ]; then
        echo "$avd did not finish booting within ${timeout_s}s. See the log: $log" >&2
        exit 1
      fi
      sleep 3
      waited=$((waited + 3))
    done
    echo "$avd is ready ($serial)"
    ;;
  shot)
    "$ADB" -s "$serial" exec-out screencap -p >"$file"
    echo "Saved $file"
    ;;
  stop)
    "$ADB" -s "$serial" emu kill >/dev/null 2>&1 || true
    pkill -f "emulator.*-avd $avd" 2>/dev/null || true
    echo "$avd stopped"
    ;;
esac
