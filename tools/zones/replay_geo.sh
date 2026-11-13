#!/usr/bin/env bash
# 시연 경로를 에뮬레이터에 1초마다 넣는다: adb emu geo fix <경도> <위도>
# 사용: tools/zones/replay_geo.sh [경로 CSV] [시작 초]
#   기본 CSV: tools/zones/demo_route_k11_k12.csv
#   시작 초를 주면 그 줄부터(예: 170 → 보호구역 직전). 다른 기기는 ANDROID_SERIAL로 고른다.
set -euo pipefail
CSV="${1:-$(dirname "$0")/demo_route_k11_k12.csv}"
FROM="${2:-0}"
start=$(python3 -c 'import time;print(time.time())')
n=0
grep -v '^#' "$CSV" | tail -n +2 | while IFS=, read -r t lat lon kmh note limit; do
  [ "$t" -lt "$FROM" ] && continue
  adb emu geo fix "$lon" "$lat" >/dev/null
  echo "t=${t}s ${lat},${lon} ${kmh}km/h ${note}${limit:+ (지도 제한 $limit)}"
  n=$((n + 1))
  # 1초 간격을 밀리지 않게 시작 시각 기준으로 잔다
  python3 -c "import time;d=$start+$n-time.time();time.sleep(d if d>0 else 0)"
done
