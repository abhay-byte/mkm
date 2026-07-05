#!/usr/bin/env bash
set -euo pipefail

DEVICE="${1:-d30a1726}"

cleanup() { echo ""; exit 0; }
trap cleanup INT TERM

gpu_stats() {
    local raw hist sum p50 p90 p99
    raw=$(adb -s "$DEVICE" shell "dumpsys gfxinfo $1 framestats 2>/dev/null")
    
    # Get histogram line, sum frame counts, skip 4950ms overflow bucket
    hist=$(echo "$raw" | grep "GPU HISTOGRAM:" | head -1 | sed 's/GPU HISTOGRAM: //')
    sum=$(echo "$hist" | awk '{
        for(i=1;i<=NF;i++){
            split($i,a,"=")
            ms=a[1]; gsub(/ms/,"",ms)
            if(ms!=4950) s+=a[2]
        }
    } END{print s+0}')
    
    # Get percentile values from the summary section  
    p50=$(echo "$raw" | grep "50th gpu percentile:" | sed 's/.*: *//' | sed 's/ms//')
    p90=$(echo "$raw" | grep "90th gpu percentile:" | sed 's/.*: *//' | sed 's/ms//')
    p99=$(echo "$raw" | grep "99th gpu percentile:" | sed 's/.*: *//' | sed 's/ms//')
    
    # 4950ms = overflow/empty bucket, show as --
    [[ "$p50" == "4950" ]] && p50="--"
    [[ "$p90" == "4950" ]] && p90="--"
    [[ "$p99" == "4950" ]] && p99="--"
    
    echo "$sum ${p50:-?} ${p90:-?} ${p99:-?}"
}

echo "GPU-FPS (gfxinfo framestats) | No root needed | Ctrl+C to stop"
echo "Device: $DEVICE"
echo ""

FG_PKG=""
LAST_SUM=0

while true; do
    NEW_PKG=$(adb -s "$DEVICE" shell "dumpsys window" 2>/dev/null | grep mCurrentFocus | sed 's/.*u0 \([^/]*\).*/\1/' | head -1)
    NEW_PKG="${NEW_PKG:-unknown}"

    if [[ "$NEW_PKG" != "$FG_PKG" ]]; then
        FG_PKG="$NEW_PKG"
        SHORT=$(echo "$FG_PKG" | sed 's/^com\.//; s/\.[^.]*$//')
        printf "\n--- %s ---\n" "$SHORT"
        adb -s "$DEVICE" shell "dumpsys gfxinfo $FG_PKG reset" 2>/dev/null >/dev/null
        LAST_SUM=0
        sleep 1
        continue
    fi

    read -r CUR_SUM P50 P90 P99 <<< "$(gpu_stats "$FG_PKG")"
    DELTA=$((CUR_SUM - LAST_SUM))
    if [[ "$DELTA" -lt 0 ]]; then DELTA=0; LAST_SUM=0; fi
    LAST_SUM="$CUR_SUM"

    printf "\r[%s] gpu_fps=%-3d | p50=%s p90=%s p99=%s | total_gpu_frames=%d" \
        "$(date +%H:%M:%S)" "$DELTA" "${P50}ms" "${P90}ms" "${P99}ms" "$CUR_SUM"
    sleep 1
done
