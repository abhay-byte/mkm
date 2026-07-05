#!/usr/bin/env bash
set -eu

DEVICE="${1:-Y5WWBMJVOZSK4HU8}"
TARGET="${2:-}"
METHOD="${3:-dma_fence}"
DURATION="${4:-0}"

[[ "${1:-}" == "-h" || "${1:-}" == "--help" ]] && {
    cat <<'EOF'
Usage: gpu-fps [DEVICE] [TARGET] [METHOD] [DURATION]

  TARGET   PID, UID, or package name (e.g. 29738, 10006, com.futuremark.dmandroid.application)
  METHOD   dma_fence | work_period | ged_frame  (default: dma_fence)
  DURATION Seconds to sample (default: 10, 0=infinite)
EOF
    exit 0
}

APP_PID=""
APP_UID=""

if [[ -n "$TARGET" ]]; then
    if [[ "$TARGET" =~ ^[0-9]+$ ]]; then
        if adb -s "$DEVICE" shell "ps -p $TARGET -o PID=" 2>/dev/null | grep -q .; then
            APP_PID="$TARGET"
            APP_UID=$(adb -s "$DEVICE" shell "ps -p $TARGET -o UID=" 2>/dev/null | tr -d ' ')
        else
            APP_UID="$TARGET"
        fi
    else
        echo "Looking up PID for $TARGET..."
        APP_PID=$(adb -s "$DEVICE" shell "ps -A -o PID,NAME | grep '$TARGET' | awk '{print \$1}' | head -1")
        if [[ -n "$APP_PID" ]]; then
            APP_UID=$(adb -s "$DEVICE" shell "ps -p $APP_PID -o UID=" 2>/dev/null | tr -d ' ')
            echo "Found: PID=$APP_PID UID=$APP_UID"
        fi
    fi
fi

echo "Device: $DEVICE  Method: $METHOD  Target: ${APP_PID:-${APP_UID:-all}}  Duration: ${DURATION}s"
echo "---"

case "$METHOD" in
    dma_fence)
        adb -s "$DEVICE" shell "echo 0 > /sys/kernel/tracing/tracing_on; echo > /sys/kernel/tracing/trace"
        adb -s "$DEVICE" shell "echo 1 > /sys/kernel/tracing/events/dma_fence/dma_fence_signaled/enable"
        adb -s "$DEVICE" shell "echo 1 > /sys/kernel/tracing/tracing_on"

        cleanup_fence() {
            adb -s "$DEVICE" shell "echo 0 > /sys/kernel/tracing/tracing_on; echo 0 > /sys/kernel/tracing/events/dma_fence/dma_fence_signaled/enable" 2>/dev/null || true
            [[ -f "$AWK_TMP" ]] && rm -f "$AWK_TMP" 2>/dev/null || true
        }
        trap cleanup_fence EXIT INT TERM

        AWK_TMP=$(mktemp)
        if [[ -n "$APP_PID" ]]; then
            cat > "$AWK_TMP" << AWKEOF
/dma_fence_signaled.*driver=mali.*timeline=0-${APP_PID}_/ {
    split(\$4, ts, /[:.]/); sec = ts[1]
    match(\$0, /timeline=0-([0-9]+)_/, tl); pid = tl[1]
    k = sec SUBSEP pid; count[k]++
    if (sec != last_sec && last_sec != "") {
        for (k2 in count) { split(k2, p, SUBSEP); if (p[1] == last_sec) printf "sec=%-12s pid=%-6s fps=%-4d\\n", p[1], p[2], count[k2]; delete count[k2] }
        fflush()
    }
    last_sec = sec
}
AWKEOF
        else
            cat > "$AWK_TMP" << 'AWKEOF'
/dma_fence_signaled.*driver=mali/ {
    split($4, ts, /[:.]/); sec = ts[1]
    match($0, /timeline=0-([0-9]+)_/, tl); pid = tl[1]
    k = sec SUBSEP pid; count[k]++
    if (sec != last_sec && last_sec != "") {
        for (k2 in count) { split(k2, p, SUBSEP); if (p[1] == last_sec) printf "sec=%-12s pid=%-6s fps=%-4d\n", p[1], p[2], count[k2]; delete count[k2] }
        fflush()
    }
    last_sec = sec
}
AWKEOF
        fi
        ;;

    work_period)
        adb -s "$DEVICE" shell "echo 0 > /sys/kernel/tracing/tracing_on; echo > /sys/kernel/tracing/trace; echo 1 > /sys/kernel/tracing/events/gpu_work_period/enable 2>/dev/null; echo 1 > /sys/kernel/tracing/tracing_on"
        AWK_TMP=$(mktemp)
        if [[ -n "$APP_UID" ]]; then
            GREP_PAT="gpu_work_period.*uid=${APP_UID}"
        else
            GREP_PAT="gpu_work_period"
        fi
        cat > "$AWK_TMP" << 'AWKEOF'
/uid=/ {
    match($0, /([0-9]+)\.[0-9]+:/, a)
    match($0, /uid=([0-9]+)/, u)
    match($0, /total_active_duration_ns=([0-9]+)/, d)
    sec = a[1]; uid = u[1]; dur = d[1]
    key = sec SUBSEP uid
    count[key]++; gpu_ns[key] += dur
}
END {
    for (k in count) { split(k, p, SUBSEP); printf "sec=%-12s uid=%-6s fps=%-4d gpu_avg_us=%-7d gpu_util_ms=%-5d\n", p[1], p[2], count[k], int(gpu_ns[k]/count[k]/1000), int(gpu_ns[k]/1000000) }
}
AWKEOF
        trap 'rm -f "$AWK_TMP"' EXIT
        ;;

    ged_frame)
        adb -s "$DEVICE" shell "echo 1 > /sys/kernel/tracing/events/ged/GPU_DVFS__Policy__Frame_based__GPU_Time/enable"
        trap 'adb -s "$DEVICE" shell "echo 0 > /sys/kernel/tracing/events/ged/GPU_DVFS__Policy__Frame_based__GPU_Time/enable"' EXIT

        AWK_TMP=$(mktemp)
        GREP_PAT="GPU_DVFS__Policy__Frame_based__GPU_Time"
        cat > "$AWK_TMP" << 'AWKEOF'
/GPU_DVFS__Policy__Frame_based__GPU_Time/ {
    match($0, /([0-9]+)\.[0-9]+:/, a)
    match($0, /real=([0-9]+)/, r)
    sec = a[1]; real = r[1]
    count[sec]++; gpu_us[sec] += real
}
END {
    for (k in count) { printf "sec=%-12s fps=%-4d gpu_time_us=%-5d\n", k, count[k], int(gpu_us[k]/count[k]) }
}
AWKEOF
        trap 'rm -f "$AWK_TMP"' EXIT
        ;;

    *)
        echo "Unknown method: $METHOD"
        exit 1
        ;;
esac

if [[ "$DURATION" -eq 0 ]]; then
    adb -s "$DEVICE" shell "cat /sys/kernel/tracing/trace_pipe" 2>/dev/null | awk -f "$AWK_TMP"
else
    timeout "$DURATION" adb -s "$DEVICE" shell "cat /sys/kernel/tracing/trace_pipe" 2>/dev/null | awk -f "$AWK_TMP" 2>/dev/null || true
fi 2>/dev/null
