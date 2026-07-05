#!/usr/bin/env bash
# gpu-fps-snapdragon.sh — Adreno GPU per-app FPS via ftrace
#
# Counts dma_fence_signaled events per kgsl-timeline context.
# Uses the highest-frequency context count as the FPS estimate.
#
# Requires: root on device, CONFIG_DMA_FENCE_TRACE in kernel
set -euo pipefail

# Auto-detect Snapdragon device from connected devices
DEVICE=""
if [[ "${1:-}" =~ ^[0-9]+$ ]]; then
    POLL="${1}"
elif [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
    echo "Usage: ./gpu-fps-snapdragon.sh [poll_seconds=2]"
    echo "  Auto-detects connected Snapdragon device."
    exit 0
else
    POLL="${1:-2}"
fi

# Auto-detect snapdragon device
detect_snapdragon() {
    local serials
    serials=$(adb devices 2>/dev/null | awk 'NR>1 && $2=="device" {print $1}')
    for s in $serials; do
        local chip
        chip=$(adb -s "$s" shell "getprop ro.board.platform 2>/dev/null" 2>/dev/null | tr -d '\r' || echo "")
        # Snapdragon platforms: pineapple, kona, lahaina, taro, kalama, sm8*, msm*, sdm*, etc.
        if [[ "$chip" =~ ^(pineapple|kona|lahaina|taro|kalama|msm|sdm|sm[0-9]|cliffs|anorak|pitti) ]]; then
            echo "$s"
            return
        fi
    done
    # Fallback: pick any device that's NOT mali
    for s in $serials; do
        local chip
        chip=$(adb -s "$s" shell "getprop ro.board.platform 2>/dev/null" 2>/dev/null | tr -d '\r' || echo "")
        if [[ ! "$chip" =~ ^(mt[0-9]|mt6[0-9]|mt7[0-9]|mt8[0-9]) ]]; then
            echo "$s"
            return
        fi
    done
    echo ""
}

DEVICE=$(detect_snapdragon)
if [[ -z "$DEVICE" ]]; then
    echo "ERROR: No Snapdragon device found via ADB."
    echo "Connected devices:"
    adb devices
    exit 1
fi

TRACE="/sys/kernel/tracing"
EVENT="dma_fence/dma_fence_signaled"
POLL="${POLL:-2}"

cleanup() {
    [[ -n "${DEVICE:-}" ]] && \
        adb -s "$DEVICE" shell "echo 0 > $TRACE/events/$EVENT/enable; echo 0 > $TRACE/tracing_on" 2>/dev/null || true
}
trap cleanup EXIT

fg_pid() {
    local fg pkg
    fg=$(adb -s "$DEVICE" shell "dumpsys activity activities 2>/dev/null" \
        | grep -E 'mResumedActivity|topResumedActivity' | head -1 \
        | sed -E 's/.*u0 +([^ /]+).*/\1/' | tr -d '\r')
    [[ -z "$fg" || "$fg" == "null" ]] && \
        fg=$(adb -s "$DEVICE" shell "dumpsys window 2>/dev/null" \
            | grep 'mCurrentFocus' | grep -v 'null' | head -1 \
            | sed 's/.*u0 \([^/}]*\).*/\1/' | tr -d '\r')
    [[ -z "$fg" || "$fg" == "null" ]] && { echo ""; return; }
    adb -s "$DEVICE" shell "pidof '$fg'" 2>/dev/null | tr -d '\r' | awk '{print $1}'
}

if ! adb -s "$DEVICE" shell "[ -f $TRACE/events/$EVENT/enable ]" 2>/dev/null; then
    echo "ERROR: ftrace $EVENT not found. Kernel lacks CONFIG_DMA_FENCE_TRACE."
    exit 1
fi

echo "====== GPU-FPS Snapdragon (Adreno ftrace) ======"
echo "Device: $DEVICE | Poll: ${POLL}s | Ctrl+C to stop"
printf "%-10s %-8s %-8s %-8s %-20s\n" "TIME" "FPS" "COUNT" "AVGms" "TOP-CTX"
echo "--------------------------------------------------------"

while true; do
    PID=$(fg_pid)
    if [[ -z "$PID" ]]; then
        printf "%-10s %-8s (no fg app)\n" "$(date +%H:%M:%S)" "--"
        sleep 1
        continue
    fi

    pid_prefix="${PID:0:3}"

    adb -s "$DEVICE" shell "echo 0 > $TRACE/tracing_on; echo > $TRACE/trace; echo 1 > $TRACE/events/$EVENT/enable; echo 1 > $TRACE/tracing_on" 2>/dev/null

    t0=$(adb -s "$DEVICE" shell "cat /proc/uptime" | awk '{print $1}')
    sleep "$POLL"

    raw=$(adb -s "$DEVICE" shell "
        echo 0 > $TRACE/tracing_on
        echo 0 > $TRACE/events/$EVENT/enable
        cat /proc/uptime
        grep 'dma_fence_signaled.*driver=kgsl-timeline.*(${pid_prefix}' $TRACE/trace 2>/dev/null \
            | awk -F'timeline=' '{print \$2}' | awk '{print \$1}' \
            | sort | uniq -c | sort -rn | head -1
    " 2>/dev/null || echo "0 0")

    t1=$(echo "$raw" | head -1 | awk '{print $1}')
    ctx_info=$(echo "$raw" | tail -1)
    count=$(echo "$ctx_info" | awk '{print $1}')
    count="${count:-0}"
    ctx_name=$(echo "$ctx_info" | awk '{print $2}')

    if [[ "$count" == "0" || -z "$count" ]]; then
        printf "%-10s %-8s (idle, PID %s)\n" "$(date +%H:%M:%S)" "--" "$PID"
    else
        span=$(awk "BEGIN {printf \"%.3f\", $t1 - $t0}")
        fps=$(awk "BEGIN {printf \"%.1f\", $count / $span}")
        avg_ms=$(awk "BEGIN {printf \"%.1f\", (1000.0/($count/$span))}")
        printf "%-10s %-8s %-8s %-8s %-20s\n" \
            "$(date +%H:%M:%S)" "$fps" "$count" "${avg_ms}ms" "$ctx_name"
    fi
done
