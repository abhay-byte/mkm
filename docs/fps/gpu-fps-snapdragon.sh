#!/usr/bin/env bash
# gpu-fps-snapdragon.sh — Adreno GPU per-app FPS via ftrace
#
# Uses adreno_cmdbatch_submitted events. Discovers app's KGSL context
# IDs via dma_fence timeline names. Merges all app contexts sorted by
# timestamp, then counts gap-based BURSTS (gap > 12ms = new frame).
#
# Requires: root on device, CONFIG_DMA_FENCE_TRACE in kernel
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
DMA_EVENT="dma_fence/dma_fence_signaled"
ADR_EVENT="kgsl/adreno_cmdbatch_submitted"

cleanup() {
    [[ -n "${DEVICE:-}" ]] && adb -s "$DEVICE" shell "
        echo 0 > $TRACE/tracing_on 2>/dev/null
        echo 0 > $TRACE/events/$DMA_EVENT/enable 2>/dev/null
        echo 0 > $TRACE/events/$ADR_EVENT/enable 2>/dev/null
    " 2>/dev/null || true
}
trap cleanup EXIT

fg_pid() {
    local fg
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

# Discover adreno ctx IDs for this PID via dma_fence timeline parsing
discover_ctx_ids() {
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
            | sed -E 's/.*timeline=kgsl-3d0_([0-9]+)-.*/\1/' \
            | sort -nu | tr '\n' ' '
    " 2>/dev/null | tr -d '\r' || echo ""
}

# Count bursts: gap > GAP_THRESHOLD ms between consecutive timestamps = new frame
count_bursts() {
    local gap_ms="${2:-12}"
    awk -v gap="$gap_ms" '
    { ts = $1 + 0; if (ts == 0) next }
    NR == 1 || restart { frames = 1; prev = ts; restart = 0; next }
    { if ((ts - prev) * 1000 > gap) frames++; prev = ts }
    END { print frames + 0 }
    ' <<< "$1"
}

adb -s "$DEVICE" shell "[ -f $TRACE/events/$DMA_EVENT/enable ]" 2>/dev/null \
  || { echo "ERROR: ftrace $DMA_EVENT not found. Kernel lacks CONFIG_DMA_FENCE_TRACE."; exit 1; }

adb -s "$DEVICE" shell "echo 16384 > $TRACE/buffer_size_kb" 2>/dev/null || true

echo "====== GPU-FPS Snapdragon (Adreno burst-detect) ======"
echo "Device: $DEVICE | Poll: ${POLL}s | Ctrl+C to stop"
echo "Metric: adreno_cmdbatch_submitted (gap-based frame bursts)"
printf "%-10s %-8s %-8s %-8s %-8s %s\n" "TIME" "FPS" "EVENTS" "FRAMEMS" "CTXS" "APP(PID)"
echo "--------------------------------------------------------"

CTX_IDS=""
REFRESH=0
LAST_PID=""

while true; do
    PID=$(fg_pid)
    if [[ -z "$PID" ]]; then
        printf "%-10s %-8s (no fg app)\n" "$(date +%H:%M:%S)" "---"
        CTX_IDS=""; sleep 1; continue
    fi

    pfx="${PID:0:3}"

    # Refresh context IDs on PID change or every 15 polls
    if [[ "$PID" != "$LAST_PID" ]] || [[ $REFRESH -eq 0 ]] || [[ $((REFRESH % 15)) -eq 0 ]]; then
        CTX_IDS=$(discover_ctx_ids "$pfx")
        REFRESH=1; LAST_PID="$PID"
    else
        REFRESH=$((REFRESH + 1))
    fi

    if [[ -z "$CTX_IDS" ]]; then
        # Retry once immediately — context may not be visible in the first 0.5s probe
        CTX_IDS=$(discover_ctx_ids "$pfx")
        if [[ -z "$CTX_IDS" ]]; then
            printf "%-10s %-8s (no ctx, PID %s)\n" "$(date +%H:%M:%S)" "---" "$PID"
            sleep 1; continue
        fi
    fi

    # Build grep pattern for all app contexts
    ctx_grep=""
    for c in $CTX_IDS; do
        [[ -n "$ctx_grep" ]] && ctx_grep="$ctx_grep|"
        ctx_grep="${ctx_grep}ctx=${c} "
    done

    # Start trace, capture for POLL seconds
    adb -s "$DEVICE" shell "
        echo 0 > $TRACE/tracing_on
        echo 0 > $TRACE/events/$DMA_EVENT/enable
        echo > $TRACE/trace
        echo 1 > $TRACE/events/$ADR_EVENT/enable
        echo 1 > $TRACE/tracing_on
    " 2>/dev/null

    t0=$(adb -s "$DEVICE" shell "cat /proc/uptime" | awk '{print $1}')
    sleep "$POLL"

    # Stop trace and pull raw data
    trace_data=$(adb -s "$DEVICE" shell "
        echo 0 > $TRACE/tracing_on
        echo 0 > $TRACE/events/$ADR_EVENT/enable
        cat /proc/uptime | awk '{print \$1}'
        grep -E '(${ctx_grep})' $TRACE/trace 2>/dev/null \
            | awk '{print \$4}' \
            | sed 's/://'
    " 2>/dev/null | tr -d '\r' || echo "0")

    t1=$(echo "$trace_data" | head -1 | tr -d '\r')
    timestamps=$(echo "$trace_data" | tail -n +2 | tr -d '\r')
    event_count=$(echo "$timestamps" | wc -l)
    if [[ "$event_count" -lt 2 ]]; then
        printf "%-10s %-8s (idle, PID %s)\n" "$(date +%H:%M:%S)" "---" "$PID"
        continue
    fi

    burst_count=$(count_bursts "$timestamps" 12)

    span=$(awk "BEGIN {printf \"%.3f\", $t1 - $t0}")
    fps=$(awk "BEGIN {printf \"%.1f\", $burst_count / $span}")
    framems=$(awk "BEGIN {printf \"%.1f\", 1000.0/($burst_count/$span)}")

    printf "%-10s %-8s %-8s %-8s %-8s PID %s\n" \
        "$(date +%H:%M:%S)" "$fps" "$event_count" "${framems}ms" "$(echo "$CTX_IDS" | tr '\n' ' ')" "$PID"
done
