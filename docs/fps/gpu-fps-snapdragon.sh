#!/usr/bin/env bash
# gpu-fps-snapdragon.sh — Adreno GPU per-app FPS via ftrace
#
# Uses kgsl/syncpoint_fence events. Pattern: 4 events per frame
# (2 KGSL contexts × 2 syncpoints per context). Stable across scenes.
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

cleanup() {
    [[ -n "${DEVICE:-}" ]] && adb -s "$DEVICE" shell "
        echo 0 > $TRACE/tracing_on 2>/dev/null
        echo 0 > $TRACE/events/$EVENT/enable 2>/dev/null
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

# Discover unique syncobj context IDs for this PID from syncpoint_fence trace
# Each unique ctx = one syncobj context contributing 2 events per frame
discover_ctx_count() {
    local pid_prefix="$1"
    adb -s "$DEVICE" shell "
        echo 0 > $TRACE/tracing_on
        echo > $TRACE/trace
        echo 1 > $TRACE/events/$EVENT/enable
        echo 1 > $TRACE/tracing_on
        sleep 0.5
        echo 0 > $TRACE/tracing_on
        grep 'syncpoint_fence.*(${pid_prefix}' $TRACE/trace 2>/dev/null \
            | sed 's/.*ctx=\\([0-9]*\\).*/\\1/' \
            | sort -nu | wc -l
    " 2>/dev/null | tr -d '\r' || echo "0"
}

adb -s "$DEVICE" shell "[ -f $TRACE/events/$EVENT/enable ]" 2>/dev/null \
  || { echo "ERROR: ftrace $EVENT not found."; exit 1; }

adb -s "$DEVICE" shell "echo 16384 > $TRACE/buffer_size_kb" 2>/dev/null || true

echo "====== GPU-FPS Snapdragon (syncpoint_fence) ======"
echo "Device: $DEVICE | Poll: ${POLL}s | Ctrl+C to stop"
echo "Metric: kgsl/syncpoint_fence (4 events/frame, ratio-based)"
printf "%-10s %-8s %-8s %-8s %-8s %s\n" "TIME" "FPS" "EVENTS" "FRAMEMS" "CTXS" "APP(PID)"
echo "--------------------------------------------------------"

CTX_COUNT=0
REFRESH=0
LAST_PID=""

while true; do
    PID=$(fg_pid)
    if [[ -z "$PID" ]]; then
        printf "%-10s %-8s (no fg app)\n" "$(date +%H:%M:%S)" "---"
        CTX_COUNT=0; sleep 1; continue
    fi

    pfx="${PID:0:3}"

    # Refresh context count on PID change or every 15 polls
    if [[ "$PID" != "$LAST_PID" ]] || [[ $REFRESH -eq 0 ]] || [[ $((REFRESH % 15)) -eq 0 ]]; then
        new_ctx=$(discover_ctx_count "$pfx")
        if [[ "$new_ctx" -gt 0 ]]; then
            CTX_COUNT="$new_ctx"
        fi
        REFRESH=1; LAST_PID="$PID"
    else
        REFRESH=$((REFRESH + 1))
    fi

    if [[ "$CTX_COUNT" -eq 0 ]]; then
        CTX_COUNT=$(discover_ctx_count "$pfx")
        if [[ "$CTX_COUNT" -eq 0 ]]; then
            printf "%-10s %-8s (no ctx, PID %s)\n" "$(date +%H:%M:%S)" "---" "$PID"
            sleep 1; continue
        fi
    fi

    # Start trace
    adb -s "$DEVICE" shell "
        echo 0 > $TRACE/tracing_on
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

    if [[ "$events" -eq 0 ]]; then
        printf "%-10s %-8s (idle, PID %s)\n" "$(date +%H:%M:%S)" "---" "$PID"
        continue
    fi

    # 4 events per frame: 2 contexts × 2 syncpoints per context
    sp_per_frame=$((CTX_COUNT * 2))
    frames=$((events / sp_per_frame))
    span=$(awk "BEGIN {printf \"%.3f\", $t1 - $t0}")
    fps=$(awk "BEGIN {printf \"%.1f\", $frames / $span}")
    framems=$(awk "BEGIN {printf \"%.1f\", 1000.0/($frames/$span)}")

    printf "%-10s %-8s %-8s %-8s %-8s PID %s\n" \
        "$(date +%H:%M:%S)" "$fps" "$events" "${framems}ms" "$CTX_COUNT" "$PID"
done
