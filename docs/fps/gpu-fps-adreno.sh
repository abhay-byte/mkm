#!/usr/bin/env bash
set -euo pipefail

DEVICE="${1:-d30a1726}"

cleanup() {
    adb -s "$DEVICE" shell "echo 0 > /sys/kernel/tracing/events/dma_fence/dma_fence_signaled/enable" 2>/dev/null || true
    echo ""
    exit 0
}
trap cleanup INT TERM

echo "===== GPU-FPS Adreno (dma_fence kgsl-timeline) ====="
echo "Device: $DEVICE | Root required | Ctrl+C to stop"
echo ""

# Enable trace
adb -s "$DEVICE" shell "echo 1 > /sys/kernel/tracing/events/dma_fence/dma_fence_signaled/enable"

# Get foreground package
FG_PKG=$(adb -s "$DEVICE" shell "dumpsys window" 2>/dev/null | grep mCurrentFocus | sed 's/.*u0 \([^/]*\).*/\1/' | head -1 || echo "unknown")
SHORT=$(echo "$FG_PKG" | sed 's/^com\.//; s/\.[^.]*$//')
echo "Foreground: $SHORT ($FG_PKG)"
echo ""

AWK_FILE=$(mktemp)
# Use the last segment of the package name for matching in the timeline
FG_PATTERN=$(echo "$FG_PKG" | sed 's/.*\.//')
cat > "$AWK_FILE" << AWKEOF
/dma_fence_signaled.*driver=kgsl-timeline/ {
    ts = \$4; sub(/:$/, "", ts)
    dot = index(ts, ".")
    if (dot == 0) next
    sec = int(substr(ts, 1, dot - 1))

    # timeline looks like: kgsl-3d0_8-processname(PID
    # Extract up to the '(' for process name
    pos = index(\$0, "timeline=")
    if (pos == 0) next
    rest = substr(\$0, pos + 9)
    under = index(rest, "_")
    if (under == 0) next
    rest2 = substr(rest, under + 1)
    paren = index(rest2, "(")
    if (paren == 0) { process = rest2 }
    else { process = substr(rest2, 1, paren - 1) }

    key = sec ":" process
    count[key]++
}
END {
    for (k in count) {
        split(k, p, ":")
        printf "sec=%-12s proc=%-30s fps=%-4d\n", p[1], p[2], count[k]
    }
}
AWKEOF

adb -s "$DEVICE" shell "cat /sys/kernel/tracing/trace_pipe" | \
    grep --line-buffered 'dma_fence_signaled.*driver=kgsl-timeline' | \
    awk -v "pkg=${FG_PATTERN}" -f "$AWK_FILE" &
PIPE_PID=$!

wait $PIPE_PID 2>/dev/null
cleanup
