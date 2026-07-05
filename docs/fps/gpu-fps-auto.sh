#!/usr/bin/env bash
set -euo pipefail

DEVICE="${1:-Y5WWBMJVOZSK4HU8}"

cleanup() {
    adb -s "$DEVICE" shell "echo 0 > /sys/kernel/tracing/events/dma_fence/dma_fence_signaled/enable" 2>/dev/null || true
    echo ""
    exit 0
}
trap cleanup INT TERM

echo "===== GPU-FPS Monitor (Ctrl+C to stop) ====="
echo "Device: $DEVICE | dma_fence | auto-detecting foreground app"
echo ""

adb -s "$DEVICE" shell "echo 1 > /sys/kernel/tracing/events/dma_fence/dma_fence_signaled/enable"

FG_PKG=$(adb -s "$DEVICE" shell "dumpsys window" 2>/dev/null | grep mCurrentFocus | sed 's/.*u0 \([^/]*\).*/\1/' | head -1)
FG_PKG="${FG_PKG:-unknown}"
FG_PID=$(adb -s "$DEVICE" shell "ps -A | grep $FG_PKG" 2>/dev/null | awk '{print $2}' | head -1)
FG_PID="${FG_PID:-0}"

echo "Foreground: $FG_PKG (PID: $FG_PID)"
echo ""

AWK_FILE=$(mktemp)
cat > "$AWK_FILE" << 'AWKEOF'
BEGIN { prev_sec = -1 }
/dma_fence_signaled.*driver=mali/ {
  line = $0
  ts = $4; sub(/:$/, "", ts)
  dot = index(ts, ".")
  sec = int(substr(ts, 1, dot - 1))

  pos = index(line, "timeline=0-")
  rest = substr(line, pos + 11)
  under = index(rest, "_")
  pid = substr(rest, 1, under - 1)

  key = sec ":" pid
  count[key]++

  if (sec != prev_sec && prev_sec >= 0) {
    n = 0
    for (k in count) {
      split(k, p, ":")
      if (p[1] == prev_sec) { n++; arr_pid[n] = p[2]; arr_cnt[n] = count[k] }
    }
    for (i = 1; i <= n; i++)
      for (j = i + 1; j <= n; j++)
        if (arr_cnt[j] > arr_cnt[i]) { t = arr_pid[i]; arr_pid[i] = arr_pid[j]; arr_pid[j] = t; t = arr_cnt[i]; arr_cnt[i] = arr_cnt[j]; arr_cnt[j] = t }
    printf "=== sec %s ===\n", prev_sec
    for (i = 1; i <= (n < 6 ? n : 6); i++) {
      if (arr_cnt[i] > 0) {
        mark = (arr_pid[i] == target) ? "  ◀── foreground" : ""
        printf "  pid=%-6s  fps=%-4d%s\n", arr_pid[i], arr_cnt[i], mark
      }
    }
    fflush()
    delete count; delete arr_pid; delete arr_cnt
    prev_sec = sec
  } else if (prev_sec < 0) prev_sec = sec
}
END { printf "\n" }
AWKEOF

adb -s "$DEVICE" shell "cat /sys/kernel/tracing/trace_pipe" | \
    grep --line-buffered 'dma_fence_signaled.*driver=mali' | \
    awk -v "target=$FG_PID" -f "$AWK_FILE" &
PIPE_PID=$!

wait $PIPE_PID 2>/dev/null
cleanup
