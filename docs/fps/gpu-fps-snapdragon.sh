#!/usr/bin/env bash
# gpu-fps-snapdragon.sh — Adreno GPU per-app FPS via ftrace
#
# Method: adreno_cmdbatch_submitted burst grouping.
# Adreno submits several cmdbatches per frame. At high FPS, inflight is queue
# depth, not a frame boundary, so counting inflight drops aliases low.
#
# Requires: root on device, kgsl ftrace support
set -euo pipefail

POLL="${1:-2}"
FRAME_GAP_MS="${FRAME_GAP_MS:-3.0}"

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
DMA_EVENT="dma_fence/dma_fence_signaled"
SUBMIT_EVENT="kgsl/adreno_cmdbatch_submitted"

cleanup() {
    adb -s "$DEVICE" shell "
        echo 0 > $TRACE/tracing_on 2>/dev/null
        echo 0 > $TRACE/events/$DMA_EVENT/enable 2>/dev/null
        echo 0 > $TRACE/events/$SUBMIT_EVENT/enable 2>/dev/null
    " 2>/dev/null || true
}
trap cleanup EXIT

fg_info() {
    local pkg pid
    pkg=$(adb -s "$DEVICE" shell "dumpsys window 2>/dev/null" \
        | grep -m1 'mCurrentFocus' | grep -v 'null' \
        | sed -nE 's/.*u0 *([^ /}]+).*/\1/p' | tr -d '\r')
    [[ -z "$pkg" || "$pkg" == "null" ]] && \
        pkg=$(adb -s "$DEVICE" shell "dumpsys activity activities 2>/dev/null" \
            | grep -m1 -E 'mResumedActivity|topResumedActivity' \
            | sed -nE 's/.*u0 *([^ /}]+).*/\1/p' | tr -d '\r')
    [[ -z "$pkg" || "$pkg" == "null" ]] && { echo ""; return; }
    pid=$(adb -s "$DEVICE" shell "pidof '$pkg'" 2>/dev/null | tr -d '\r' | awk '{print $1}')
    [[ -z "$pid" ]] && { echo ""; return; }
    echo "$pkg $pid"
}

discover_ctxs() {
    local pfx="$1"
    adb -s "$DEVICE" shell "
        echo 0 > $TRACE/tracing_on
        echo > $TRACE/trace
        echo 1 > $TRACE/events/$DMA_EVENT/enable
        echo 1 > $TRACE/tracing_on
        sleep 0.5
        echo 0 > $TRACE/tracing_on
        echo 0 > $TRACE/events/$DMA_EVENT/enable
        grep 'driver=kgsl-timeline.*(${pfx}' $TRACE/trace 2>/dev/null \
            | grep -o 'kgsl-3d0_[0-9]*' \
            | sed 's/kgsl-3d0_//' \
            | sort -nu | tr '\n' ' '
    " 2>/dev/null | tr -d '\r' | xargs
}

# Count frames from inflight drops on cmdbatch_submitted
# inflight rises within a frame, drops = frame boundary
count_inflight_frames() {
    local ctx_ids="$1" ctrace="${TRACE}/trace"
    local ctx_grep=""
    for c in $ctx_ids; do
        [[ -n "$ctx_grep" ]] && ctx_grep="$ctx_grep|"
        ctx_grep="${ctx_grep}ctx=${c}[^0-9]"
    done

    adb -s "$DEVICE" shell "
        echo 0 > $TRACE/tracing_on
        echo 0 > $TRACE/events/$DMA_EVENT/enable
        echo > $ctrace
        echo 1 > $TRACE/events/$SUBMIT_EVENT/enable
        echo 1 > $TRACE/tracing_on
    " 2>/dev/null

    t0=$(adb -s "$DEVICE" shell "cat /proc/uptime" | awk '{print $1}')
    sleep "$POLL"

    local data
    data=$(adb -s "$DEVICE" shell "
        echo 0 > $TRACE/tracing_on
        echo 0 > $TRACE/events/$SUBMIT_EVENT/enable
        cat /proc/uptime | awk '{print \$1}'
        grep -E '(${ctx_grep})' $ctrace 2>/dev/null \
            | awk '{for(i=1;i<=NF;i++) if(\$i ~ /^inflight=/) {sub(/inflight=/,\"\",\$i); print \$i}}'
    " 2>/dev/null | tr -d '\r' || echo "0")

    local uptime_end=$(echo "$data" | head -1)
    local inflight_vals=$(echo "$data" | tail -n +2)
    local events=$(echo "$inflight_vals" | wc -l)

    if [[ "$events" -lt 3 ]]; then
        echo "0 0 0 0"
        return
    fi

    # Count inflight drops = frame boundaries
    local frames=$(echo "$inflight_vals" | awk '
    NR==1 { prev=$1; f=1; next }
    { if ($1 < prev) f++; prev=$1 }
    END { print f + 0 }
    ')

    local span=$(awk "BEGIN {printf \"%.3f\", $uptime_end - $t0}")
    echo "$span $events $frames"
}

adb -s "$DEVICE" shell "echo 16384 > $TRACE/buffer_size_kb" 2>/dev/null || true
adb -s "$DEVICE" shell "[ -f $TRACE/events/$DMA_EVENT/enable ]" 2>/dev/null \
    || { echo "ERROR: ftrace not available."; exit 1; }

echo "====== GPU-FPS Snapdragon (inflight drop) ======"
echo "Device: $DEVICE | Poll: ${POLL}s | Ctrl+C to stop"
echo "Method: cmdbatch_submitted inflight drops = frame boundaries"
printf "%-10s %-8s %-8s %-8s %-8s %s\n" "TIME" "FPS" "EVENTS" "FRAMEMS" "CTXS" "APP(PID)"
echo "--------------------------------------------------------"

LAST_PID=""
CTX_IDS=""
CTX_COUNT=0

while true; do
    info=$(fg_info)
    if [[ -z "$info" ]]; then
        printf "%-10s %-8s (no fg app)\n" "$(date +%H:%M:%S)" "---"
        LAST_PID=""; CTX_IDS=""
        sleep 1; continue
    fi

    PKG="${info% *}"
    PID="${info##* }"
    pfx="${PID:0:3}"

    if [[ "$PID" != "$LAST_PID" ]]; then
        CTX_IDS=$(discover_ctxs "$pfx")
        CTX_COUNT=$(echo "$CTX_IDS" | wc -w)
        LAST_PID="$PID"
    fi

    if [[ "$CTX_COUNT" -eq 0 ]]; then
        CTX_IDS=$(discover_ctxs "$pfx")
        CTX_COUNT=$(echo "$CTX_IDS" | wc -w)
        if [[ "$CTX_COUNT" -eq 0 ]]; then
            printf "%-10s %-8s (waiting, %s/%s)\n" "$(date +%H:%M:%S)" "---" "$PKG" "$PID"
            sleep 1; continue
        fi
    fi

    result=$(count_inflight_frames "$CTX_IDS")
    span=$(echo "$result" | awk '{print $1}')
    events=$(echo "$result" | awk '{print $2}')
    frames=$(echo "$result" | awk '{print $3}')
    events="${events:-0}"
    frames="${frames:-0}"
    span="${span:-0}"

    if [[ "$frames" -lt 2 ]]; then
        printf "%-10s %-8s (idle, %s/%s)\n" "$(date +%H:%M:%S)" "---" "$PKG" "$PID"
        continue
    fi

    fps=$(awk "BEGIN {printf \"%.1f\", $frames / $span}")
    framems=$(awk "BEGIN {printf \"%.1f\", 1000.0/($frames/$span)}")

    printf "%-10s %-8s %-8s %-8s %-8s %s/%s\n" \
        "$(date +%H:%M:%S)" "$fps" "$events" "${framems}ms" "$CTX_COUNT" "$PKG" "$PID"
done
