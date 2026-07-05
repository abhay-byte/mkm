#!/usr/bin/env bash
# gpu-fps-snapdragon.sh — Adreno GPU per-app FPS via ftrace
#
# Uses kgsl/syncpoint_fence events. Pattern: 4 events per frame
# (2 KGSL contexts × 2 syncpoints per context). Stable across scenes.
# Auto-detects foreground app PID + package name on screen change.
#
# Requires: root on device, kgsl ftrace support
set -euo pipefail

if [[ "${1:-}" =~ ^[0-9]+$ ]]; then
    POLL="${1}"
elif [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
    echo "Usage: ./gpu-fps-snapdragon.sh [poll_seconds=2]"
    exit 0
else
    POLL="${1:-2}"
fi

detect_snapdragon() {
    local serials
    serials=$(adb devices 2>/dev/null | awk 'NR>1 && $2=="device" {print $1}')
    for s in $serials; do
        local chip
        chip=$(adb -s "$s" shell "getprop ro.board.platform 2>/dev/null" | tr -d '\r' || true)
        [[ "$chip" =~ ^(pineapple|kona|lahaina|taro|kalama|msm|sdm|sm[0-9]|cliffs|anorak|pitti) ]] && { echo "$s"; return; }
    done
    for s in $serials; do
        local chip
        chip=$(adb -s "$s" shell "getprop ro.board.platform 2>/dev/null" | tr -d '\r' || true)
        [[ ! "$chip" =~ ^(mt[0-9]|mt6[0-9]|mt7[0-9]|mt8[0-9]) ]] && { echo "$s"; return; }
    done
    echo ""
}

DEVICE=$(detect_snapdragon)
[[ -z "$DEVICE" ]] && { echo "ERROR: No Snapdragon device found."; adb devices; exit 1; }

TRACE="/sys/kernel/tracing"
EVENT="kgsl/syncpoint_fence"
DMA_EVENT="dma_fence/dma_fence_signaled"

cleanup() {
    [[ -n "${DEVICE:-}" ]] && adb -s "$DEVICE" shell "
        echo 0 > $TRACE/tracing_on 2>/dev/null
        echo 0 > $TRACE/events/$EVENT/enable 2>/dev/null
        echo 0 > $TRACE/events/$DMA_EVENT/enable 2>/dev/null
    " 2>/dev/null || true
}
trap cleanup EXIT

fg_app_info() {
    local pkg pid
    pkg=$(adb -s "$DEVICE" shell "dumpsys activity activities 2>/dev/null" \
        | grep -m1 -E 'mResumedActivity|topResumedActivity' \
        | sed -nE 's/.*u0 *([^ /}]+).*/\1/p' | tr -d '\r')
    if [[ -z "$pkg" || "$pkg" == "null" ]]; then
        pkg=$(adb -s "$DEVICE" shell "dumpsys window 2>/dev/null" \
            | grep -m1 'mCurrentFocus' | grep -v 'null' \
            | sed -nE 's/.*u0 *([^ /}]+).*/\1/p' | tr -d '\r')
    fi
    [[ -z "$pkg" || "$pkg" == "null" ]] && { echo ""; return; }
    pid=$(adb -s "$DEVICE" shell "pidof '$pkg'" 2>/dev/null | tr -d '\r' | awk '{print $1}')
    [[ -z "$pid" ]] && { echo ""; return; }
    echo "$pkg $pid"
}

# Discover context count from a short dma_fence probe.
# dma_fence_signaled fires more frequently = reliable 0.5s probe.
discover_ctx_count() {
    local pid_prefix="$1"
    adb -s "$DEVICE" shell "
        echo 0 > $TRACE/tracing_on
        echo > $TRACE/trace
        echo 1 > $TRACE/events/$DMA_EVENT/enable
        echo 1 > $TRACE/tracing_on
        sleep 0.5
        echo 0 > $TRACE/tracing_on
        echo 0 > $TRACE/events/$DMA_EVENT/enable
        grep 'dma_fence_signaled.*driver=kgsl-timeline.*(${pid_prefix}' $TRACE/trace 2>/dev/null \
            | grep -o 'kgsl-3d0_[0-9]*' \
            | sed 's/kgsl-3d0_//' \
            | sort -nu | wc -l
    " 2>/dev/null | tr -d '\r' || echo "0"
}

adb -s "$DEVICE" shell "[ -f $TRACE/events/$EVENT/enable ]" 2>/dev/null \
  || { echo "ERROR: ftrace $EVENT not found."; exit 1; }

adb -s "$DEVICE" shell "echo 16384 > $TRACE/buffer_size_kb" 2>/dev/null || true

echo "====== GPU-FPS Snapdragon (syncpoint_fence) ======"
echo "Device: $DEVICE | Poll: ${POLL}s | Ctrl+C to stop"
echo "Metric: kgsl/syncpoint_fence (4 events/frame, ratio-based)"
echo "Auto-detects foreground app on screen change."
printf "%-10s %-8s %-8s %-8s %-8s %s\n" "TIME" "FPS" "EVENTS" "FRAMEMS" "CTXS" "APP(PID)"
echo "--------------------------------------------------------"

CTX_COUNT=0
POLL_N=0
LAST_PID=""
LAST_APP=""
INIT_DONE=0

while true; do
    app_info=$(fg_app_info)
    if [[ -z "$app_info" ]]; then
        printf "%-10s %-8s (no fg app)\n" "$(date +%H:%M:%S)" "---"
        CTX_COUNT=0; LAST_PID=""; LAST_APP=""; INIT_DONE=0
        sleep 1; continue
    fi

    PKG=$(echo "$app_info" | awk '{print $1}')
    PID=$(echo "$app_info" | awk '{print $2}')
    pfx="${PID:0:3}"

    # On app change: rediscover context count
    if [[ "$PID" != "$LAST_PID" ]]; then
        CTX_COUNT=$(discover_ctx_count "$pfx")
        LAST_PID="$PID"
        LAST_APP="$PKG"
        POLL_N=0
        INIT_DONE=0
    fi

    if [[ "$CTX_COUNT" -eq 0 ]]; then
        # Retry context discovery — app may not have submitted GPU work yet
        CTX_COUNT=$(discover_ctx_count "$pfx")
        if [[ "$CTX_COUNT" -eq 0 ]]; then
            printf "%-10s %-8s (waiting on %s/%s)\n" "$(date +%H:%M:%S)" "---" "$PKG" "$PID"
            sleep 1; continue
        fi
    fi

    INIT_DONE=1

    # Start trace
    adb -s "$DEVICE" shell "
        echo 0 > $TRACE/tracing_on
        echo 0 > $TRACE/events/$DMA_EVENT/enable
        echo > $TRACE/trace
        echo 1 > $TRACE/events/$EVENT/enable
        echo 1 > $TRACE/tracing_on
    " 2>/dev/null

    t0=$(adb -s "$DEVICE" shell "cat /proc/uptime" | awk '{print $1}')
    sleep "$POLL"

    data=$(adb -s "$DEVICE" shell "
        echo 0 > $TRACE/tracing_on
        echo 0 > $TRACE/events/$EVENT/enable
        cat /proc/uptime | awk '{print \$1}'
        grep -c 'syncpoint_fence.*(${pfx}' $TRACE/trace 2>/dev/null || echo 0
    " 2>/dev/null | tr -d '\r' || echo "0 0")

    t1=$(echo "$data" | head -1)
    events=$(echo "$data" | tail -1)
    events="${events:-0}"
    POLL_N=$((POLL_N + 1))

    if [[ "$events" -eq 0 ]]; then
        printf "%-10s %-8s (idle, %s/%s)\n" "$(date +%H:%M:%S)" "---" "$PKG" "$PID"
        continue
    fi

    sp_per_frame=$((CTX_COUNT * 2))
    frames=$((events / sp_per_frame))
    span=$(awk "BEGIN {printf \"%.3f\", $t1 - $t0}")
    fps=$(awk "BEGIN {printf \"%.1f\", $frames / $span}")
    framems=$(awk "BEGIN {printf \"%.1f\", 1000.0/($frames/$span)}")

    # Every 10 polls: revalidate ctx count from the data we already captured
    if [[ $((POLL_N % 10)) -eq 0 ]]; then
        live_ctx=$(adb -s "$DEVICE" shell "
            grep 'syncpoint_fence.*(${pfx}' $TRACE/trace 2>/dev/null \
                | awk -F'ctx=' '{print \$2}' \
                | awk '{print \$1}' \
                | sort -nu | wc -l
        " 2>/dev/null | tr -d '\r' || echo "$CTX_COUNT")
        [[ "$live_ctx" -gt 0 ]] && CTX_COUNT="$live_ctx"
    fi

    printf "%-10s %-8s %-8s %-8s %-8s %s/%s\n" \
        "$(date +%H:%M:%S)" "$fps" "$events" "${framems}ms" "$CTX_COUNT" "$PKG" "$PID"
done
