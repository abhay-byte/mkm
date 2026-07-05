#!/usr/bin/env bash
# gpu-fps-snapdragon.sh — Adreno GPU per-app FPS via ftrace
#
# Discovers app KGSL context IDs via dma_fence, then counts
# adreno_cmdbatch_retired events for those contexts with
# gap-based burst detection (gap > 5ms = new frame).
#
# Requires: root on device, kgsl ftrace support
set -euo pipefail

POLL="${1:-2}"

detect_snapdragon() {
    local s chip
    for s in $(adb devices 2>/dev/null | awk 'NR>1 && $2=="device" {print $1}'); do
        chip=$(adb -s "$s" shell "getprop ro.board.platform 2>/dev/null" | tr -d '\r' || true)
        [[ "$chip" =~ ^(pineapple|kona|lahaina|taro|kalama|msm|sdm|sm[0-9]|cliffs|anorak|pitti) ]] && { echo "$s"; return; }
    done
    for s in $(adb devices 2>/dev/null | awk 'NR>1 && $2=="device" {print $1}'); do
        chip=$(adb -s "$s" shell "getprop ro.board.platform 2>/dev/null" | tr -d '\r' || true)
        [[ ! "$chip" =~ ^(mt[0-9]|mt6[0-9]|mt7[0-9]|mt8[0-9]) ]] && { echo "$s"; return; }
    done
    echo ""
}

DEVICE=$(detect_snapdragon)
[[ -z "$DEVICE" ]] && { echo "ERROR: No Snapdragon device found."; adb devices; exit 1; }

TRACE=/sys/kernel/tracing
cleanup() { adb -s "$DEVICE" shell "echo 0 > $TRACE/tracing_on 2>/dev/null; echo 0 > $TRACE/events/dma_fence/dma_fence_signaled/enable 2>/dev/null; echo 0 > $TRACE/events/kgsl/adreno_cmdbatch_retired/enable 2>/dev/null" 2>/dev/null || true; }
trap cleanup EXIT

fg_pkg() {
    local pkg
    pkg=$(adb -s "$DEVICE" shell "dumpsys activity activities 2>/dev/null" | grep -m1 -E 'mResumedActivity|topResumedActivity' | sed -nE 's/.*u0 *([^ /}]+).*/\1/p' | tr -d '\r')
    [[ -z "$pkg" || "$pkg" == "null" ]] && pkg=$(adb -s "$DEVICE" shell "dumpsys window 2>/dev/null" | grep -m1 'mCurrentFocus' | grep -v 'null' | sed -nE 's/.*u0 *([^ /}]+).*/\1/p' | tr -d '\r')
    [[ -z "$pkg" || "$pkg" == "null" ]] && { echo ""; return; }
    pid=$(adb -s "$DEVICE" shell "pidof '$pkg'" 2>/dev/null | tr -d '\r' | awk '{print $1}')
    [[ -z "$pid" ]] && { echo ""; return; }
    echo "$pkg $pid"
}

# Discover KGSL ctx IDs via dma_fence (timeline names carry PID)
discover_ctx_ids() {
    local pfx="$1"
    local cts
    cts=$(adb -s "$DEVICE" shell "
        echo 0 > $TRACE/tracing_on
        echo > $TRACE/trace
        echo 1 > $TRACE/events/dma_fence/dma_fence_signaled/enable
        echo 1 > $TRACE/tracing_on
        sleep 0.5
        echo 0 > $TRACE/tracing_on
        echo 0 > $TRACE/events/dma_fence/dma_fence_signaled/enable
        grep 'driver=kgsl-timeline.*(${pfx}' $TRACE/trace 2>/dev/null | grep -o 'kgsl-3d0_[0-9]*' | sed 's/kgsl-3d0_//' | sort -nu | tr '\n' ' '
    " 2>/dev/null | tr -d '\r' | xargs)
    echo "$cts"
}

adb -s "$DEVICE" shell "echo 16384 > $TRACE/buffer_size_kb" 2>/dev/null || true
adb -s "$DEVICE" shell "[ -f $TRACE/events/dma_fence/dma_fence_signaled/enable ]" 2>/dev/null || { echo "ERROR: ftrace not available."; exit 1; }

echo "====== GPU-FPS Snapdragon ======"
echo "Device: $DEVICE | Poll: ${POLL}s | Ctrl+C to stop"
printf "%-10s %-8s %-8s %-8s %-8s %s\n" "TIME" "FPS" "EVENTS" "FRAMEMS" "CTXS" "APP(PID)"
echo "--------------------------------------------------------"

LAST_PID=""
CTX_IDS=""
CTX_COUNT=0

while true; do
    info=$(fg_pkg)
    if [[ -z "$info" ]]; then
        printf "%-10s %-8s (no fg app)\n" "$(date +%H:%M:%S)" "---"
        LAST_PID=""; CTX_IDS=""
        sleep 1; continue
    fi

    PKG="${info% *}"
    PID="${info##* }"
    pfx="${PID:0:3}"

    # On app change: rediscover contexts
    if [[ "$PID" != "$LAST_PID" ]]; then
        CTX_IDS=$(discover_ctx_ids "$pfx")
        CTX_COUNT=$(echo "$CTX_IDS" | wc -w)
        LAST_PID="$PID"
    fi

    if [[ "$CTX_COUNT" -eq 0 ]]; then
        CTX_IDS=$(discover_ctx_ids "$pfx")
        CTX_COUNT=$(echo "$CTX_IDS" | wc -w)
        if [[ "$CTX_COUNT" -eq 0 ]]; then
            printf "%-10s %-8s (waiting, %s/%s)\n" "$(date +%H:%M:%S)" "---" "$PKG" "$PID"
            sleep 1; continue
        fi
    fi

    # Build grep pattern for app contexts
    ctx_grep=""
    for c in $CTX_IDS; do
        [[ -n "$ctx_grep" ]] && ctx_grep="$ctx_grep|"
        ctx_grep="${ctx_grep}ctx=${c} "
    done

    # Start trace
    adb -s "$DEVICE" shell "
        echo 0 > $TRACE/tracing_on
        echo 0 > $TRACE/events/dma_fence/dma_fence_signaled/enable
        echo > $TRACE/trace
        echo 1 > $TRACE/events/kgsl/adreno_cmdbatch_retired/enable
        echo 1 > $TRACE/tracing_on
    " 2>/dev/null

    t0=$(adb -s "$DEVICE" shell "cat /proc/uptime" | awk '{print $1}')
    sleep "$POLL"

    # Pull data: uptime on first line, timestamps on rest
    data=$(adb -s "$DEVICE" shell "
        echo 0 > $TRACE/tracing_on
        echo 0 > $TRACE/events/kgsl/adreno_cmdbatch_retired/enable
        cat /proc/uptime | awk '{print \$1}'
        grep -E '(${ctx_grep})' $TRACE/trace 2>/dev/null | awk '{print \$4}' | sed 's/://'
    " 2>/dev/null | tr -d '\r' || echo "0")

    t1=$(echo "$data" | head -1)
    timestamps=$(echo "$data" | tail -n +2)
    event_count=$(echo "$timestamps" | grep -c '[0-9]' || echo 0)

    if [[ "$event_count" -lt 2 ]]; then
        printf "%-10s %-8s (idle, %s/%s)\n" "$(date +%H:%M:%S)" "---" "$PKG" "$PID"
        continue
    fi

    # Gap-burst detection: gap > 5ms and < 1000ms = new frame
    burst_count=$(awk '
    { ts = $1 + 0 }
    NR == 1 { frames = 1; prev = ts; next }
    { d = (ts - prev) * 1000; if (d > 5 && d < 1000) frames++; prev = ts }
    END { print frames + 0 }
    ' <<< "$timestamps")

    span=$(awk "BEGIN {printf \"%.3f\", $t1 - $t0}")
    fps=$(awk "BEGIN {printf \"%.1f\", $burst_count / $span}")
    framems=$(awk "BEGIN {printf \"%.1f\", 1000.0/($burst_count/$span)}")

    printf "%-10s %-8s %-8s %-8s %-8s %s/%s\n" \
        "$(date +%H:%M:%S)" "$fps" "$event_count" "${framems}ms" "$CTX_COUNT" "$PKG" "$PID"
done
